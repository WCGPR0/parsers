package reportparser.energyparsers.DailyPostingReport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.joda.time.LocalDate;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class ChenierePostingReport extends DailyPostingReport {

	protected void initialize() {
		super.initialize();
		this.source = SOURCE.CHENIERE;

	}

	@Override
	protected void parse(URL path, int tick, List<NameValuePair> params, List<String> schemaList, String group)
			throws ClientProtocolException, IOException, URISyntaxException {

		NameValuePair beginDate_ = params.stream().filter(s -> s.getName().equals("beginDate")).findFirst().orElse(null);
		NameValuePair endDate_ = params.stream().filter(s -> s.getName().equals("endDate")).findFirst().orElse(null);
		boolean nullDates = false;

		LocalDate beginDate = null, endDate = null;

		if ((beginDate_ != null && endDate_ == null) || (beginDate_ == null && endDate_ != null)) {
			logger.error("Missing beginDate/endDate parameters. Please verify either both beginDate (" + beginDate_ + ") and endDate (" + endDate_ + ") are passed or neither are passed");
			return;
		}
		if (beginDate_ != null)
			beginDate = dateTimeFormat.parseLocalDate(beginDate_.getValue());
		if (endDate_ != null)
			endDate = dateTimeFormat.parseLocalDate(endDate_.getValue());
		if (beginDate_ == null && endDate_ == null) {
			nullDates = true;
			beginDate = endDate = LocalDate.now();
		}

		while (beginDate.compareTo(endDate) <= 0)	{

			String path_string = path.toString();
			if (nullDates) {
				path_string += "&beginDate=null";
			}
			else {
				path_string += "&beginDate=" + dateTimeFormat.print(beginDate);
			}

			beginDate = beginDate.plusDays(1);

			URL path_ = new URL(path_string);

			BufferedReader br = new BufferedReader(new InputStreamReader(getRequest(path_).getContent()));
			String line = "";
			while ((line = br.readLine()) != null) {
				Gson gson = new Gson();
				JsonElement o = new JsonParser().parse(line).getAsJsonObject().get("report");
				Type type_ = new TypeToken<List<Map<String, String>>>() {}.getType();
				List<Map<String, String>> recordList_ = gson.fromJson(o, type_);
				 for (Map<String, String> innerList_ : recordList_) {

					 long mismatches = schemaList.stream().map(innerList_::get).filter(Objects::isNull).count();
					 if (mismatches > 0) {
						 logger.error("Unmatched schema error with Cheniere Schema; number of mismatches :\t"+ mismatches +". Please check schema matches data: " + innerList_);
						 continue;
					 }

					 List<String[]> innerList = new ArrayList<String[]>();
					 for (String header : schemaList) {
						 String val = innerList_.get(header);
						 if (val == null) {
							 logger.error("Cheniere Schema does not match feed data. Please verify schema (" + header + ") matches data:\t"+innerList);
							 continue;
						 }
						 innerList.add(new String[]{header, val});
					 }
					 recordList.add(innerList);
				 }

			}

			if (recordList.size() == 0)
				logger.error("Cheniere Schema did not match any data. Please verify schema matches data (likely the site has changed their data format) :\t"+line);

		}
	}

}
