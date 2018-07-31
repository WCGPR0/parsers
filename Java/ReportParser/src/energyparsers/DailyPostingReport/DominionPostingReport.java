package reportparser.energyparsers.DailyPostingReport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.regex.Matcher;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.util.EntityUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;
import org.joda.time.DateTime;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DominionPostingReport extends DailyPostingReport {

	private static String _downloadFileNames_transform_name;

	protected void initialize() {
		super.initialize();
		this.source = SOURCE.DOMINION;
	}

	public void setFilesNamesTransform(String value)
	{
		_downloadFileNames_transform_name = value;
	}

	@Override
	protected void parse(URL path, int tick, List<NameValuePair> params, List<String> schemaList, String group)
			throws ClientProtocolException, IOException, URISyntaxException {
		PDDocument doc = null;
		PDFTextStripper stripper;
		String line = null;
		String[] header = schemaList.toArray(new String[0]);
		String postingDate = null, postingTime = null;
		boolean schemaMatch = false;
		String url_string = "";
		NodeList urlNodeList = null;
		int urlNodeList_length = 0;

		HttpEntity input = getRequest(path);

		Matcher m_ = Pattern.compile(urlRegexPattern).matcher(EntityUtils.toString(input));
		List<String> matches_ = new ArrayList<String>();

		while (m_.find()) {
			String match = m_.group(1);
			matches_.add(match);
		}
		String matchDocString = "<URLS>" + matches_.stream().map(Object::toString).collect(Collectors.joining()) + "</URLS>";

		CachedTransform downloadFileNames_transform = Controller.getInstance().getConfig().getTransformTemplate(_downloadFileNames_transform_name);
		urlNodeList = getURLNodeList(Util.performTransform(downloadFileNames_transform, Util.documentFromString(matchDocString), new HashMap<String, Object>(), true).getDocumentElement());
		urlNodeList_length = urlNodeList.getLength();

		if (urlNodeList_length == 0 || urlNodeList == null) {
			logger.error("No matches found with URL posting at: " + path + " with regex: " + urlRegexPattern + "using transform: " + _downloadFileNames_transform_name);
			return;
		}

		for (int j = 0; j < urlNodeList_length; j++) {

		if (urlNodeList.item(j).getNodeType() != Node.ELEMENT_NODE) continue;

		url_string = Util.getNodeValueFromNode(urlNodeList.item(j), "url");

		try {

			doc = PDDocument.load(new URL(url_string));

			if (doc != null) {
			stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			String pdfText = stripper.getText(doc);

			BufferedReader br = new BufferedReader(new StringReader(pdfText));

			while ((line = br.readLine()) != null) {

				if (line.matches("Posting Date .*")) {
					postingDate = line.substring(line.lastIndexOf(" ")+1);
				}
				else if (line.matches("Posting Time .*")) {
					postingTime = line.substring(line.lastIndexOf(" ")+1);
				}
				else if(line.matches(Pattern.quote(String.join(" ", schemaList)))) {
						schemaMatch = true;
						br.readLine();
				}
				else if (schemaMatch && line.matches(schemaRegexPattern)){

						List<String> matches = new ArrayList<String>();
						Matcher m = Pattern.compile(schemaRegexPattern).matcher(line);
						while (m.find())
							for (int i = 1; i < m.groupCount() + 1; i++)
								matches.add(m.group(i));


						List<String[]> innerList = new ArrayList<String[]>();

						for (int i = 0; i < Math.min(matches.size(),header.length); i++) {
							if (i != 0 && matches.get(i) == null)
								logger.error("Unmatched null error with Dominion Schema, please check schema size is bigger than regex matches:\t"+line);
							innerList.add(new String[]{header[i], matches.get(i)});
							}

						if (postingDate == null || postingTime == null) {
							logger.error("Null posting date and posting time");
							return;
						}

						DateTime dt = dateTimeFormat.parseDateTime(postingDate + " " + postingTime);
						innerList.add(new String[]{"Posting DateTime", dt.toString() });

						recordList.add(innerList);

				}
			}
			}
			}
			catch (UnsupportedOperationException e) {
				logger.error("Parse error in handling Dominion data:\t" + e);;
			}
			finally {
				if (doc != null)
					doc.close();
			}

			if (recordList.size() == 0)
				logger.error("Dominion Schema did not match any data. Please verify schema matches data (likely the site has changed their data format) :\t"+line);

		}

	}

}
