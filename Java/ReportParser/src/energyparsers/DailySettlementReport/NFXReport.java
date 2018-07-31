package reportparser.energyparsers.DailySettlementReport;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NFXReport extends DailySettlementReport {

	protected void initialize() {
		source = SOURCE.NFX;
	}

	@Override
	protected RecordList parse(String path, DateFormat dateFormat, String pattern, boolean isOptTrade) {
		return isOptTrade ? optParse(path, dateFormat, pattern) : nonOptParse(path, dateFormat, pattern);
	}

	private RecordList nonOptParse(String path, DateFormat dateFormat, String pattern) {
		RecordList recordList = null;
		try {
		recordList = new RecordList(false);
		Pattern p = Pattern.compile(createNasdaqRegex(contractMap.keySet()));
		String dateString = dateFormat.format(getTime());

		//Establishing connection to URL
		URLConnection urlConnection = new URL(path).openConnection();
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));

		//Reading data
		String line;
		String[] lineData;
		Matcher m;
		while ((line = bufferedReader.readLine()) != null) {
			try {
			lineData = line.split(",");
			m = p.matcher(lineData[0]);
			if (m.matches() && lineData.length >= 12) {
				String code = m.group(0);
				String contract_name = code.substring(0, code.length() - 2);
				for (Integer contract_ID : contractMap.get(contract_name)) {
					Record record = new Record(dateFormat);
					record.updateRecord(dateString, contract_ID,
						contract_name, code.substring(code.length() - 2), lineData[11], null,
						lineData[5], lineData[3], lineData[6], "NASDAQ");
					recordList.add(record);
				}
			}
			}
			catch (Exception e) {
				logger.error("Bad data while parsing nasdaq: " + e);
			}
		}
		bufferedReader.close();
		}
		catch (Exception e) {
			logger.error("Encountered error prior to parsing NFX: " + e);
		}
		return recordList;
	}

	/**
	 * Options and Nonoptions are the same source for nasdaq.
	 * @todo Merge into one parse that does both options/non-options to improve efficiency
	 */
	private RecordList optParse(String path, DateFormat dateFormat, String pattern) {
		RecordList recordList = null;
		try {
		recordList = new RecordList(true);
		Pattern p = Pattern.compile(createNasdaqOptionsRegex(contractMap.keySet()));
		Calendar cal = Calendar.getInstance();
		if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY)
			cal.add(Calendar.DATE, -3);
		else
			cal.add(Calendar.DATE, -1);

		//Establishing connection to URL
		URLConnection urlConnection = new URL(path).openConnection();
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));

		//Reading data
		String line;
		String[] lineData;
		Matcher m;
		while ((line = bufferedReader.readLine()) != null) {
			try {
			lineData = line.split(",");
			m = p.matcher(lineData[0]);
			if (m.matches() && lineData.length >= 12 && (lineData[5].length() > 0 || lineData[3].length() > 0
					|| (lineData[6].length() > 0 && !lineData[6].equals("0")))) {
				String[] code = m.group(0).split("_");
				if (code.length != 2) {
					logger.debug("Bad data not added: " + line);
					continue;
				}
				int[] monthDayYear = getMonthDayYear(code[0].substring(code[0].length() - 2));
				String contract_name = code[0].substring(0, code[0].length() - 2);
				for (Integer contract_ID : contractMap.get(contract_name)) {
				Record record = new Record();
				record.updateOptRecord(cal.getTime(),
						contract_ID,
						code[0].substring(0, code[0].length() - 2), monthDayYear[0], monthDayYear[2],
						lineData[11], null, lineData[3], lineData[6], "NASDAQ",
						Double.parseDouble(code[1].substring(0, code[1].length() - 1)),
						code[1].substring(code[1].length() - 1), lineData[5]);
				recordList.add(record);
				}
			}
			}
			catch (Exception e) {
				logger.error("Bad data while parsing nasdaq: " + e);
			}
		}
		bufferedReader.close();
		}
		catch (Exception e) {
			logger.error("Encountered error prior to parsing NFX: " + e);
		}
		return recordList;
	}


	private String createNasdaqRegex(Set<String> keys) {
		StringBuffer sb = new StringBuffer();
		for (String key : keys) {
			sb.append("|").append(key).append("\\w\\d");
		}
		return sb.length() != 0 ? sb.toString().substring(1) : "";
	}

	private String createNasdaqOptionsRegex(Set<String> keys) {
		StringBuffer sb = new StringBuffer();
		for (String key : keys) {
			sb.append("|").append(key).append("\\w\\d_.+");
		}
		return sb.length() != 0 ? sb.toString().substring(1) : "";
	}
}
