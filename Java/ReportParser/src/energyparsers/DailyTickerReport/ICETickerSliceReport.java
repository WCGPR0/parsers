package reportparser.energyparsers.DailyTickerReport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.message.BasicNameValuePair;


public class ICETickerSliceReport extends ICETickerReport {

protected void parse(URL path, int tick, List<NameValuePair> params, List<String> schemaList, String group) throws ClientProtocolException, IOException, URISyntaxException {
	List<NameValuePair> params_ = null;
	if (params != null) {
	params_ = params.stream().collect(Collectors.toList());
	for (int i = 0; i < params_.size(); ++i) {
		if(params_.get(i).getValue().contains("%c")) params_.set(i, new BasicNameValuePair(params_.get(i).getName(),params_.get(i).getValue().replace("%c",Integer.toString(tick))));
	}
	logger.debug("Using parameters: " + params_);
	params = params_;
	}

	HttpEntity input = postRequest(path, params);
	if (input == null) logger.error("No data found during parse");

	BufferedReader br = new BufferedReader(new InputStreamReader(input.getContent()));
	String line = "";
	while ((line = br.readLine()) != null) {
	line = line.replace("[\r][\n]", ""); //<Strip unnecessary newlines
	line = line.replace("\"", ""); //<Strip unnecessary quotes
	if (line.matches("(?s)\\[+(.+)\\]+")) { //<Base depth of iteration
		line = line.substring(1, line.length()-1); //<Removes first layer
		for (String line_ : line.split(",+(?![^\\[]*\\])")) {  //< Strict assertion of having atleast []
			line_ = line_.replaceFirst("\\[", "").replaceFirst("(?s)\\](?!.*?\\])", "").replaceAll("(?i)null", "");
			String[] array = line_.split(",(?![^\\[]*\\]| )",-1);
			if (array.length != schemaList.size())
				logger.error("ICE Schema does not match feed data. Please verify schema matches data:\t" + line_);
			String[] header = schemaList.toArray(new String[0]);

			int pos = Arrays.asList(header).indexOf(group);
			pos = pos == -1 ? 0 : pos;


			List<String[] > innerList = new ArrayList<String[]>();

			for (int i = 0; i < Math.min(array.length,header.length); i++) {
			if (array[i] == null)
				logger.error("Unmatched null error with ICE Schema, please check schema matches with data correctly:\t" + line);
			innerList.add(new String[]{header[i], array[i]});

			recordList.add(innerList);


			}


		}
		}
	}
	}

}