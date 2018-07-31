package reportparser.energyparsers.DailyTickerReport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;


public class ICETickerReport extends DailyTickerReport {


protected void initialize() {
	super.initialize();
	source = SOURCE.TVUS;
}

protected void parse(URL path, int tick, List<NameValuePair> params, List<String> schemaList, String group) throws ClientProtocolException, IOException, URISyntaxException {
	HttpEntity input = postRequest(path, params);
	if (input == null) logger.error("No data found during parse");

	BufferedReader br = new BufferedReader(new InputStreamReader(input.getContent()));
	String line = "";
	boolean firstRun = true;
	while ((line = br.readLine()) != null) {
		String[] array = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"); //Ignores commas in quotes
		String[] header = schemaList.toArray(new String[0]);

		//Note: it's possible to use the headers directly, since the first line from the source is the headers. But safer to force it as an input, to ensure integrity with Slice Report
		if (array.length != schemaList.size())
			logger.error("ICE Schema size does not match feed data. Please verify schema matches data:\t"+line);
		if (firstRun) {
			if (!Arrays.equals(array, header)) logger.error("ICE Schema does not match feed data. Please verify schema matches data:\t"+line);
			firstRun = false;
			continue;
		}

		int pos = Arrays.asList(header).indexOf(group);
		pos = pos == -1 ? 0 : pos;

		List<String[]> innerList = new ArrayList<String[]>();

		for (int i = 0; i < Math.min(array.length,header.length); i++) {
		if (array[i] == null)
			logger.error("Unmatched null error with ICE Schema, please check schema matches with data correctly:\t"+line);
		innerList.add(new String[]{header[i], array[i]});
		}

		recordList.add(innerList);

	}
	}
}