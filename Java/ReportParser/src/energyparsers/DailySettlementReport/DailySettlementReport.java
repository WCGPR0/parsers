package reportparser.energyparsers.DailySettlementReport;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

import org.joda.time.DateTime;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.XmlFriendlyNameCoder;

/**
 * An abstract class of a Daily Settlement Report - classes that extends this: ICEREport, CMEReport, NFXReport
 *  @param  URL, the source of the reports to be parsed  (repeatable) (can append underscore, _, for fallback urls; URL_, URL__, etc)
 *   * @dateFormat dateFormat, the dateFormat used by the DailySettlementDataParser
 * @pattern pattern, the search pattern used by the DailySettlementDataParser
 *
 * <!-- Based off: EnergyParsers/DailySettlementDataParser -->
 */
public abstract class DailySettlementReport extends EnergyReport {
	//Constants
	public enum SOURCE implements isource {CME, ICE, NFX, ALL }; //< Types of sources for the Daily Settlement Report
	protected static final int DEFAULT_URL_FALLBACK_AMOUNT = 3; //< The maximum amount of times a user can specify the fallback attempts when a URL fails

	/** <DailySettlement specific variables> */
	protected static final String UNCHANGED = "UNCH";
	protected static final String NEW = "NEW";
	/** </DailySettlement specific variables> */

	protected isource source = SOURCE.ALL; //< Current source mode

	@Override
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		try {
			initialize();
			runReport(url_transform_name, data_transform_name, save_request, new DateTime(getTime()));
		} catch (MiddleTierConnectionNotFoundException e) {
			logger.error("Middletier Error: " + e);
		}

	}

	/** Sets up the URL, Dateformat, and pattern for parsing
	 * @throws MiddleTierConnectionNotFoundException */
	public void runReport(String url_transform_name, String data_transform_name, String save_request, DateTime last_business_date) throws MiddleTierConnectionNotFoundException {
		logger.info(String.format("About to execute Report with URLTransform [%s], DataTransform [%s], SaveRequest [%s]", url_transform_name, data_transform_name, save_request));
		CachedTransform url_transform = Controller.getInstance().getConfig().getTransformTemplate(url_transform_name);
		if (url_transform==null)
		{
			String errmsg = "Unable to execute DailySettlement Report. Transform " + url_transform_name + " not found.";
			logger.error(errmsg);
			maillogger.error(errmsg);
			return;
		}
		// Gets the destination urls
		HashMap<String, Object> url_request_params = new HashMap<String, Object>();
		String date_time_yesterday = date_time_format.print(last_business_date);
		url_request_params.put("date.yesterday", date_time_yesterday);
		url_request_params.put("source", source.toString());
		Element rootNode = Util.performTransform(url_transform, Util.documentFromString("<dummy/>"), url_request_params, true).getDocumentElement();
		if (rootNode != null && rootNode.getNodeType() == Node.ELEMENT_NODE)
		{
			NodeList urlNodeList = getURLNodeList(rootNode);
			parseReport(urlNodeList);
		}
	}

	/**
	 * Get the first valid URL Node List. Backup/Fallbacks can be declared in the transform by appending a underscore (ex. URL_, URL__, URL___)
	 * @param rootNode
	 * @return
	 */
	public NodeList getURLNodeList(Element rootNode) {
		NodeList nl = null;
		NodeList nl_ = null; //One iterator behind than nl

		int size;
		int i;
		for (nl = Util.getNodeListFromNode(rootNode, "URL"), i = 0; (size = nl.getLength()) > 0 && (i < DEFAULT_URL_FALLBACK_AMOUNT); nl = Util.getNodeListFromNode(rootNode, "URL" + new String(new char[i]).replace("\0", "_")), i++) {
			for (int j = 0; j < size; j++) {
				Node node = nl.item(j);
				if (node == null || node.getNodeType() != Node.ELEMENT_NODE) continue;
				String url_ = Util.getNodeValueFromNode(nl.item(j), "url");
				try {
					URL url = new URL(url_);
					URLConnection huc = (URLConnection) url.openConnection();
					if (huc instanceof HttpURLConnection || huc instanceof HttpsURLConnection) {
						((HttpURLConnection)huc).setRequestMethod("HEAD");
						((HttpURLConnection)huc).connect();
						if (((HttpURLConnection)huc).getResponseCode() == HttpURLConnection.HTTP_OK) return nl;
					}
							} catch (Exception e) { logger.debug("Bad URL found (" + url_.toString() + ") attempting to find fallback if exists"); }
			}
			nl_ = nl;
		}
		return nl_;
	}


	/** Parses the report, then saves the report
	 * @param urlNodeList, Nodelist of URL destinations
	 * @throws MiddleTierConnectionNotFoundException
	 */
	protected void parseReport(NodeList urlNodeList) throws MiddleTierConnectionNotFoundException {
		int length = urlNodeList.getLength();
		for (int i = 0; i < length; i++) {
			if (urlNodeList.item(i).getNodeType() == Node.ELEMENT_NODE) {
				String url = Util.getNodeValueFromNode(urlNodeList.item(i), "url");
				logger.info("Parsing url = " + url);
				DateFormat dateFormat = new SimpleDateFormat(Util.getNodeValueFromNode(urlNodeList.item(i), "dateFormat"));
				logger.info("Using dateFormat = " + Util.getNodeValueFromNode(urlNodeList.item(i), "dateFormat"));
				String pattern = Util.getNodeValueFromNode(urlNodeList.item(i), "pattern");
				logger.info("Using Pattern = " + pattern);
				boolean isOptTrade = Util.getNodeValueFromNode(urlNodeList.item(i), "url/@isOptTrade").equals("true");
				logger.info("Is options type trade: " + isOptTrade);

				contractMap = getHeaders(isOptTrade ? "OPTIONS" : "FUTURES",source.toString(), Controller.getInstance().getMethods());//< Gets the headers

				RecordList recordList = parse(url, dateFormat, pattern, isOptTrade);
				String save_errors = saveReport(recordList, save_request, Controller.getInstance().getMethods());
				logger.debug("Received following errors when saving report " + save_errors);
			}
		}
	}


	/**
	 * Saves the report using a request
	 * @param recordList
	 * @param request, the request to save the report with
	 * @param m_methods
	 * @return String of errors
	 */
	public String saveReport(RecordList recordList, String request, Methods m_methods) {
		return saveReport(recordList, 0, request, m_methods);
	}

	public String saveReport(RecordList recordList, int index, String request, Methods m_methods)
	{
		Document errorsdoc = Util.documentFromString("<errors/>");
		while (index < recordList.list.size()) {
			RecordList recordList_ = recordList.subList(index, index + items_per_save_request > recordList.size() ? recordList.size() : index + items_per_save_request);
			index += items_per_save_request;
			Document reportdoc  = recordsToXML(recordList_);
			Element errorselem = errorsdoc.getDocumentElement();
			//Util.nodeToFile(reportdoc, new File("C:\\DailySettlementDataParser\\logs\\temp.xml"));  //!* Testing


			Element errors = saveReportPart(reportdoc.getDocumentElement(), index, request, m_methods);
			if (errors!=null)
			{
				NodeList errornodes = errors.getElementsByTagName("error");
				for (int j=0; j<errornodes.getLength(); j++)
				{
					Node importederror = errorsdoc.importNode(errornodes.item(j), true);
					errorselem.appendChild(importederror);
				}
			}
		}
		return Util.stringFromDocument(errorsdoc);
	}


	/**
	 * Abstract method that is to be implemented.
	 * @param path, the URL or filepath for the input data
	 * @param dateFormat, the dateformat for any uris
	 * @param pattern, the pattern to look for
	 * @return List<Record>, containing all of the important SQL data fields
	 */
	protected abstract RecordList parse(String path, DateFormat dateFormat, String pattern, boolean isOptTrade);

	private static Document recordsToXML(RecordList recordList) {
		XStream xstream = new XStream(new DomDriver("UTF-8", new XmlFriendlyNameCoder("_-", "_")));
		xstream.processAnnotations(RecordList.class);
		xstream.autodetectAnnotations(true);
		String xmlString = xstream.toXML(recordList);
		return Util.documentFromString(xmlString);
	}

	// < DailySettlementDataParser Helper methods >

	protected String createRegex(Set<String> keys) {
		StringBuffer sb = new StringBuffer();
		for (String key : keys) {
			sb.append("|").append(key);
		}
		return sb.toString().substring(1);
	}

	protected static int[] getMonthDayYear(String input) throws ParseException {
		int month = -1;
		int day = -1;
		int year = -1;
		if (input.length() == 2 || input.length() == 3) {
			switch (input.charAt(0)) {
			case 'F':
				month = 1;
				break;
			case 'G':
				month = 2;
				break;
			case 'H':
				month = 3;
				break;
			case 'J':
				month = 4;
				break;
			case 'K':
				month = 5;
				break;
			case 'M':
				month = 6;
				break;
			case 'N':
				month = 7;
				break;
			case 'Q':
				month = 8;
				break;
			case 'U':
				month = 9;
				break;
			case 'V':
				month = 10;
				break;
			case 'X':
				month = 11;
				break;
			case 'Z':
				month = 12;
				break;
			}
			if (input.length() == 2) {
				int iYear = Integer.parseInt(input.substring(1));
				if (iYear >= 6)
					year = 2010 + iYear;
				else
					year = 2020 + iYear;
			} else
				year = 2000 + Integer.parseInt(input.substring(1));
		} else if (input.length() == 7) {
			// logger.warn("DDMMMYY: "+input);
			day = Integer.parseInt(input.substring(0, 2));
			month = getMonth(input.substring(2, 5));
			year = 2000 + Integer.parseInt(input.substring(5));
		} else if (input.length() == 9) {
			// logger.warn("MMM-DD-YY: "+input);
			day = Integer.parseInt(input.substring(4, 6));
			month = getMonth(input.substring(0, 3));
			year = 2000 + Integer.parseInt(input.substring(7));
		} else {
			String monthcode = input.substring(0, 3);
			month = getMonth(monthcode);
			year = 2000 + Integer.parseInt(input.substring(input.length() - 2));
		}
		return new int[] { month, day, year };
	}

	/** Helper method for getMonthDayYear */
	private static int getMonth(String monthcode) throws ParseException {
		int month = -1;
		if (monthcode.equalsIgnoreCase("JAN"))
			month = 1;
		else if (monthcode.equalsIgnoreCase("FEB"))
			month = 2;
		else if (monthcode.equalsIgnoreCase("MAR"))
			month = 3;
		else if (monthcode.equalsIgnoreCase("APR"))
			month = 4;
		else if (monthcode.equalsIgnoreCase("MAY"))
			month = 5;
		else if (monthcode.equalsIgnoreCase("JUN"))
			month = 6;
		else if (monthcode.equalsIgnoreCase("JUL"))
			month = 7;
		else if (monthcode.equalsIgnoreCase("AUG"))
			month = 8;
		else if (monthcode.equalsIgnoreCase("SEP"))
			month = 9;
		else if (monthcode.equalsIgnoreCase("OCT"))
			month = 10;
		else if (monthcode.equalsIgnoreCase("NOV"))
			month = 11;
		else if (monthcode.equalsIgnoreCase("DEC"))
			month = 12;
		else {
			logger.error("could not parse monthcode: " + monthcode);
			throw new ParseException("Invalid monthcode: " + monthcode, 0);
		}
		return month;
	}

	// </ DailySettlementDataParser Helper methods >

	/** A class that is a representation of individual Daily Settlement Datas */
	@XStreamAlias ("trade")
	public static class Record {
		public String prd_code = "EN";
		public String source;
		public java.sql.Date report_date;
		public Integer contract_ID;
		public String contract_name;
		public Integer month;
		public Integer year;
		public Double settlement_price;
		public Integer volume;
		public Integer open_int;
		public Double block_volume;
		public Double change;
		public Double strike; //Options Only
		public String strategy; // Options Only
		public Double oi_change; //Non Options Only
		public Integer day; //Non Options Only
		@XStreamOmitField
		public DateFormat dateFormat;

		public Record(Record record) {
			this.prd_code = record.prd_code;
			this.source = record.source;
			this.report_date = record.report_date;
			this.contract_ID = record.contract_ID;
			this.contract_name = record.contract_name;
			this.month = record.month;
			this.year = record.year;
			this.settlement_price = record.settlement_price;
			this.volume = record.volume;
			this.open_int = record.open_int;
			this.block_volume = record.block_volume;
			this.change = record.change;
			this.strike = record.strike;
			this.strategy = record.strategy;
			this.oi_change = record.oi_change;
			this.day = record.day;
		}

		/** Do not use this constructor; this is depreciated */
		public Record() {
			dateFormat = null;
		}

		public Record(DateFormat dateFormat) {
			this.dateFormat = dateFormat;
		}

		void updateRecord(Date report_date, Integer contract_ID, String contract_name, String term, Double settlement_price,
				Double change, String block_volume, String volume, String open_int, double oi_change, String source)
				throws SQLException, ParseException {
			if (settlement_price != null)
				this.settlement_price = settlement_price;
			if (volume != null && volume.length() > 0)
				this.volume = Integer.parseInt(volume);
			if (open_int != null && open_int.length() > 0)
				this.open_int = Integer.parseInt(open_int);
			if (block_volume != null && block_volume.length() > 0)
				this.block_volume = NumberFormat.getInstance(Locale.US).parse(block_volume).doubleValue();
			if (change != null)
				this.change = change;
			this.oi_change = oi_change;
			this.report_date = new java.sql.Date(report_date.getTime());
			this.contract_ID = contract_ID;
			this.contract_name = contract_name;
			int[] monthDayYear = getMonthDayYear(term);
			if (monthDayYear[0] == -1 || monthDayYear[2] == -1)
				return;
			this.month = monthDayYear[0];
			this.year = monthDayYear[2];
			this.source = source;
			if (monthDayYear[1] != -1) {
				this.day = monthDayYear[1];
			}
		}

		void updateRecord(String dateString, Integer contractId, String code, String term, String settle,
				String settleChg, String blockVol, String vol, String openInt, String source)
				throws NumberFormatException, SQLException, ParseException {
			Date d = null;
			try {
					d = dateFormat.parse(dateString);
			} catch (ParseException ex) {
				logger.error("Error parsing " + dateString);
				return;
			}
			updateRecord(d, contractId, code, term, NumberFormat.getInstance(Locale.US).parse(settle).doubleValue(),
					(settleChg != null) ? NumberFormat.getInstance(Locale.US).parse(settleChg).doubleValue() : null, blockVol, vol, openInt, 0, source);
		}


		void updateOptRecord(String dateString, Integer contractId, String code, String month, String year,
				String settle, String settleChg, String vol, String openInt, String source, String strike,
				String strategy, String blockVol) throws SQLException, ParseException {
			Date d = null;
			try {
					d = dateFormat.parse(dateString);
			} catch (ParseException ex) {
				logger.error("Error parsing " + dateString);
				ex.printStackTrace();
			}
			updateOptRecord(d, contractId, code, month, year, settle, settleChg, vol, openInt, source,
					NumberFormat.getInstance(Locale.US).parse(strike).doubleValue(), strategy, blockVol);
		}

		void updateOptRecord(Date d, Integer contractId, String code, String month, String year, String settle,
				String settleChg, String vol, String openInt, String source, double strike, String strategy,
				String blockVol) throws SQLException, NumberFormatException, ParseException {
			updateOptRecord(d, contractId, code, Integer.parseInt(month), Integer.parseInt(year), settle, settleChg,
					vol, openInt, source, strike, strategy, blockVol);
		}

		void updateOptRecord(Date report_date, Integer contract_ID, String contract_name, int month, int year, String settlement_price,
				String settleChg, String volume, String open_int, String source, double strike, String strategy,
				String block_volume) throws SQLException, ParseException {
			if (settlement_price != null && settlement_price.length() > 0)
				this.settlement_price = NumberFormat.getInstance(Locale.US).parse(settlement_price).doubleValue();
			if (volume != null && volume.length() > 0)
				this.volume = Integer.parseInt(volume);
			if (open_int != null && open_int.length() > 0)
				this.open_int = Integer.parseInt(open_int);
			if (block_volume != null && block_volume.length() > 0)
				this.block_volume = NumberFormat.getInstance(Locale.US).parse(block_volume).doubleValue();
			double change = 0;
			if (settleChg != null && !settleChg.equals(UNCHANGED))
				change = NumberFormat.getInstance(Locale.US).parse(settleChg).doubleValue();
			this.change = change;
			this.report_date = new java.sql.Date(report_date.getTime());
			this.contract_ID = contract_ID;
			this.contract_name = contract_name;
			this.month = month;
			this.year = year;
			this.strike = strike;
			this.strategy = strategy;
			this.source = source;
		}


		@Override
		public boolean equals(Object obj) {
			if (obj == null) return false;
			else {
				Record record = (Record) obj;
				return this.prd_code.equals(record.prd_code) &&
						((this.source == null && record.source == null) || (this.source != null && record.source != null && this.source.equals(record.source))) &&
						((this.report_date == null && record.report_date == null) || (this.report_date != null && record.report_date != null && this.report_date.equals(record.report_date))) &&
						((this.contract_ID == null && record.contract_ID == null) || (this.contract_ID != null && record.contract_ID != null && this.contract_ID.equals(record.contract_ID))) &&
						((this.contract_name == null && record.contract_name == null) || (this.contract_name != null && record.contract_name != null && this.contract_name.equals(record.contract_name))) &&
						((this.month == null && record.month == null) || (this.month != null && record.month != null && this.month.equals(record.month))) &&
						((this.year == null && record.year == null) || (this.year != null && record.year != null && this.year.equals(record.year))) &&
						((this.settlement_price == null && record.settlement_price == null) || (this.settlement_price != null && record.settlement_price != null && this.settlement_price.equals(record.settlement_price))) &&
						((this.volume == null && record.volume == null) || (this.volume != null && record.volume != null && this.volume.equals(record.volume))) &&
						((this.open_int == null && record.open_int == null) || (this.open_int != null && record.open_int != null && this.open_int.equals(record.open_int))) &&
						((this.block_volume == null && record.block_volume == null) || (this.block_volume != null && record.block_volume != null && this.block_volume.equals(record.block_volume))) &&
						((this.change == null && record.change == null) || (this.change != null && record.change != null && this.change.equals(record.change))) &&
						((this.strike == null && record.strike == null) || (this.strike != null && record.strike != null && this.strike.equals(record.strike))) &&
						((this.strategy == null && record.strategy == null) || (this.strategy != null && record.strategy != null && this.strategy.equals(record.strategy))) &&
						((this.oi_change == null && record.oi_change == null) || (this.oi_change != null && record.oi_change != null && this.oi_change.equals(record.oi_change))) &&
						((this.day == null && record.day == null) || (this.day != null && record.day != null && this.day.equals(record.day)));
			}
		}

		@Override
		public int hashCode() {
			int hash = this.contract_ID;
			hash = 31 * hash + (this.prd_code != null ? this.prd_code.hashCode() : 0);
			hash = 31 * hash + (this.source != null ? this.source.hashCode() : 0);
			hash = 31 * hash + (this.report_date != null ? this.report_date.toString().hashCode() : 0);
			hash = 31 * hash + (this.contract_name != null ? this.contract_name.hashCode() : 0);
			hash = 31 * hash + this.month;
			hash = 31 * hash + this.year;
			hash = 31 * hash + (this.settlement_price != null ? this.settlement_price.hashCode() : 0);
			hash = 31 * hash + this.volume;
			hash = 31 * hash + this.open_int;
			hash = 31 * hash + (this.block_volume != null ? this.block_volume.hashCode() : 0);
			hash = 31 * hash + (this.change != null ? this.change.hashCode() : 0);
			hash = 31 * hash + (this.strike != null ? this.strike.hashCode() : 0);
			hash = 31 * hash + (this.strategy != null ? this.strategy.hashCode() : 0);
			hash = 31 * hash + (this.oi_change != null ? this.oi_change.hashCode() : 0);
			hash = 31 * hash + this.day;
			return hash;
		}
	}

	/** A Record Wrapper class to hold Records */
	@XStreamAlias ("trades")
	public static class RecordList {
		@XStreamAsAttribute
		public boolean isOptionTrade; //< Type of RecordList; OPT (options) or NONOPT (nonoptions)
		@XStreamImplicit
		public List<Record> list;
		private RecordList() { list = new ArrayList<Record>(); }
		public RecordList(boolean isOptionTrade) {this(); this.isOptionTrade = isOptionTrade; }
		public RecordList(List<Record> list) { this.list = list; }
		public void add (Record record) { list.add(record); }
		public RecordList subList(int start, int end) {
			RecordList recordList = new RecordList(list.subList(start,end));
			recordList.isOptionTrade = isOptionTrade;
			return recordList;
		}
		public int size() { return list.size(); }
	}
}
