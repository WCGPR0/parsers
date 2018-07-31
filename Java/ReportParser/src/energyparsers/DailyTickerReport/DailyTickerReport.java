package reportparser.energyparsers.DailyTickerReport;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.joda.time.DateTime;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.XmlFriendlyNameCoder;

/**
 *
 * An abstract class of a Daily Ticker Report - classes that extends this: ICETickerReport, DTCCTickerSliceReport
 *
 * This class parses real time data from Swap Data Repositories (SDR), including locations such as DTCC and ICE TVUS.
 * TickerReport is intended for real time pooling, and updates; currently, it is capable of parsing historical data from ICE TVUS.
 * For historical data from DTCC, please use DTCCReport or DTCCSliceReport.
 *
 * Uses input from scheduled tasks
 * @param URL, the source of the reports to be parsed
 * @dateFormat dateFormat, the dateFormat used by the DailySettlementDataParser
 * @pattern pattern, the search pattern used by the DailySettlementDataParser
 *
 *  <!-- Original source from EnergyParsers/SDRDataParser -->
 */
@PersistJobDataAfterExecution
public abstract class DailyTickerReport extends EnergyReport {
	public enum SOURCE implements isource {TVUS, DTCC }; //< Types of sources for the Daily Settlement Report
	protected isource source; //< Current source mode
	private HttpClient client; //< Current HTTP Session
	protected List< List<String[] > > recordList; //< Queue of records to be inserted into the DB
	CachedTransform data_transform;
	private int tick; //< Current ticker counter
	private CookieStore cookieStore; //< Current Cookie Store

	@SuppressWarnings("unchecked")
	@Override
	public void execute(JobExecutionContext arg0) throws JobExecutionException
	{
		initialize();
		if (arg0.getJobDetail().getJobDataMap().containsKey("cookieStore")) {
			cookieStore =  (CookieStore) arg0.getJobDetail().getJobDataMap().get("cookieStore");

		}
		if (cookieStore == null) client = HttpClients.createDefault();
		else client = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();
		runReport(url_transform_name, data_transform_name, save_request, new DateTime());
		arg0.getJobDetail().getJobDataMap().put("cookieStore", cookieStore);
	}

	public void runReport(String _url_transform_name, String _data_transform_name, String _save_request, DateTime _effective_date)
	{
		logger.info(String.format("About to execute Report with URLTransform [%s], DataTransform [%s], SaveRequest [%s], EffectiveDate [%s]", _url_transform_name, _data_transform_name, _save_request, date_time_format.print(_effective_date)));
		String output_folder_root = Controller.getInstance().getConfig().getValue("/Configuration/Application/OutputFolder");
		String output_path = output_folder_root + File.separator + date_time_format_file_name.print(_effective_date);
		File output_folder = new File(output_path);
		output_folder.mkdirs();

		items_per_save_request = Integer.parseInt(Controller.getInstance().getConfig().getValue("/Configuration/Application/SDRTicker/ItemsPerSaveRequest", String.valueOf(DEFAULT_ITEMS_PER_SAVE_REQUEST)));

		CachedTransform url_transform = Controller.getInstance().getConfig().getTransformTemplate(_url_transform_name);
		if (url_transform==null)
		{
			String errmsg = "Unable to execute SDRTicker Report. Transform " + _url_transform_name + " not found.";
			logger.error(errmsg);
			maillogger.error(errmsg);
			return;
		}
		data_transform = Controller.getInstance().getConfig().getTransformTemplate(_data_transform_name);
		if (data_transform==null)
		{
			String errmsg = "Unable to execute SDRTicker Report. Transform " + _data_transform_name + " not found.";
			logger.error(errmsg);
			maillogger.error(errmsg);
			return;
		}
		if (StringUtils.isBlank(_save_request))
		{
			String errmsg = "Unable to execute SDRTicker Report. SaveRequest is not specified.";
			logger.error(errmsg);
			maillogger.error(errmsg);
			return;
		}

		HashMap<String, Object> url_request_params = new HashMap<String, Object>();
		String date_time_current = date_time_format.print(_effective_date);
		String date_time_yesterday = date_time_format.print(_effective_date.minusDays(1));
		url_request_params.put("date.current", date_time_current);
		url_request_params.put("date.yesterday", date_time_yesterday);
		url_request_params.put("source", source.toString());
		Element rootNode = Util.performTransform(url_transform, Util.documentFromString("<dummy/>"), url_request_params, true).getDocumentElement();


		//get report
		try
		{
			NodeList urlNodeList = getURLNodeList(rootNode);
			parseReport(urlNodeList);
			if (!recordList.isEmpty()) {
				 String save_errors = saveReport(save_request, Controller.getInstance().getMethods());
				 recordList.clear();
				 logger.debug("Received following errors when saving report " + save_errors);
			}

		}
		catch(Exception e)
		{
			String errmsg = "Exception thrown processing SDRTicker report";
			logger.error(errmsg, e);
			maillogger.error(errmsg, e);
		}
	}

	/** Parses the report, then saves the report
	 * @param urlNodeList, Nodelist of URL destinations
	 * @throws MiddleTierConnectionNotFoundException
	 */
	protected void parseReport(NodeList urlNodeList) throws MiddleTierConnectionNotFoundException {
		int length = urlNodeList.getLength();
		for (int i = 0; i < length; i++) {
			if (urlNodeList.item(i).getNodeType() == Node.ELEMENT_NODE) {
				URL url = null;
				URI cookie_uri = null;
				List<NameValuePair> params;
				String group;
				List<String> schemaList;

				try {

				//Skip the weekends
				Calendar cal = Calendar.getInstance();
				if (cal.get(Calendar.DAY_OF_WEEK)==Calendar.MONDAY)
					cal.add(Calendar.DATE, -3);
				else
					cal.add(Calendar.DATE, -1);

				/** @param dateFormat (Optional Parameter) */
				String dateFormat = Util.getNodeValueFromNode(urlNodeList.item(i), "dateFormat");
				if (dateFormat != null && dateFormat != "") (new SimpleDateFormat(dateFormat)).format(cal.getTime());

				/** @param url (Required Parameter) */
				String url_ = Util.getNodeValueFromNode(urlNodeList.item(i), "url");
				logger.info("Parsing url = " + url_);
				url = new URL(url_);

				/** @param group (Required Parameter) */
				group = Util.getNodeValueFromNode(urlNodeList.item(i), "group");

				/** @param cookie_url (Optional Parameter) */
				String cookie_url = Util.getNodeValueFromNode(urlNodeList.item(i), "cookie_url");
				logger.info("Using Cookie at URL = " + cookie_url);
				if (cookie_url != "" ) cookie_uri = new URI(cookie_url);

				/** @param schema (Required Parameter)*/
				String schema = Util.getNodeValueFromNode(urlNodeList.item(i), "schema");
				schemaList = Arrays.asList(schema.split(","));

				/** @param param, POST parameters (Optional Parameter)*/
				params = new ArrayList<NameValuePair>();
				NodeList paramNodeList = Util.getNodeListFromNode(urlNodeList.item(i), "params/*");
				int param_length = paramNodeList.getLength();
				for (int j = 0; j < param_length; j++) {
					String name = Util.getNodeValueFromNode(paramNodeList.item(j), "name()");
					String value = Util.getNodeValueFromNode(paramNodeList.item(j), ".");
					params.add(new BasicNameValuePair(name, value));
				}

				// Sets the cookie
				if (cookie_uri != null && cookieStore == null) {
					HttpClientContext context = HttpClientContext.create();
					String cookie = client.execute(new HttpGet(cookie_uri), context).getFirstHeader("Set-Cookie").getValue();
					cookieStore = context.getCookieStore();
					if (cookie == "" ) logger.debug("Empty cookie obtained at: " + cookie_url);
					else logger.debug("Cookie {" + cookie + "}" + " obtained at: " + cookie_url);
				}
					 parse(url, tick, params, schemaList, group);

				}
				catch (URISyntaxException e) {logger.error("Error creating cookie URI:\t" + e); }
				catch (IOException e) {
					logger.error("IOException error:\t" + e);
				}
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
	public String saveReport(String request, Methods m_methods) {
		return saveReport(0, request, m_methods);
	}

	public String saveReport(int index, String request, Methods m_methods)
	{
		Document errorsdoc = Util.documentFromString("<errors/>");
		while (index < recordList.size()) {
			List<List<String[] > > recordList_ = new ArrayList<>(recordList.subList(index, index + items_per_save_request > recordList.size() ? recordList.size() : index + items_per_save_request));
			index += items_per_save_request;
			Document reportdoc  = recordsToXML(recordList_);
			Element errorselem = errorsdoc.getDocumentElement();
			reportdoc = Util.performTransform(data_transform, reportdoc);
			Element errors = null;
			if (reportdoc.getFirstChild().getChildNodes().getLength() != 0) //Ignore empty record lists <trades />
				errors = saveReportPart(reportdoc.getDocumentElement(), index, request, m_methods);
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

	private Document recordsToXML(List<List<String[] > > recordList) {
			XStream xstream = new XStream(new DomDriver("UTF-8", new XmlFriendlyNameCoder("_-", "_")));
			xstream.alias("string-array", Map.Entry.class);
			String xmlString = xstream.toXML(recordList);
			return Util.documentFromString(xmlString);
	}

	protected abstract void parse(URL path, int tick, List<NameValuePair> params, List<String> schemaList, String group) throws ClientProtocolException, IOException, URISyntaxException;

	protected static String saveReport(String request, String report, int index)
	{
		Document reportdoc = Util.documentFromString(report);
		Document errorsdoc = Util.documentFromString("<errors/>");
		Element errorselem = errorsdoc.getDocumentElement();
		NodeList tradenodes = reportdoc.getElementsByTagName("trade");
		int i = 0;
		while (i < tradenodes.getLength())
		{
			int nextdivider = i + items_per_save_request;
			Document reportpartdoc = Util.documentFromString("<trades/>");
			Element reportpartelem = reportpartdoc.getDocumentElement();
			while (i < nextdivider && i<tradenodes.getLength())
			{
				Node importednode = reportpartdoc.importNode(tradenodes.item(i), true);
				reportpartelem.appendChild(importednode);
				i++;
			}
			Element errors = saveReportPart(request, reportpartelem, index);
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


	private static Element saveReportPart(String request, Element reportpart, int index)
	{
		try
		{
			String xmltemplate = 	"<Request.Params>" +
										"<Request.Param>%s</Request.Param>" +
									"</Request.Params>";
			String xmlparam = String.format(xmltemplate, Util.stringFromDocument(reportpart, true));
			String errors = Controller.getInstance().getMethods().execute(request, xmlparam);
			Document errorsdoc = Util.documentFromString(errors);
			return errorsdoc.getDocumentElement();
		}
		catch (Exception e)
		{
			logger.error("Exception thrown executing SaveRequest for Report (or parsing response)", e);
			return null;
		}

	}

	protected HttpEntity getRequest (URL url) throws URISyntaxException, ClientProtocolException, IOException {
		HttpGet get = new HttpGet(url.toURI());
		return client.execute(get).getEntity();
	}

	protected HttpEntity postRequest (URL url, List<NameValuePair> params) throws URISyntaxException, ClientProtocolException, IOException {
		HttpPost post = new HttpPost(url.toURI());
		if (params != null)
		post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
		return client.execute(post).getEntity();
	}

	@Override
	public NodeList getURLNodeList(Element rootNode) {
		return Util.getNodeListFromNode(rootNode, "URL");
	}

	@Override
	protected void initialize() {
		recordList = new ArrayList<List<String[] > >();
		tick = 0; //< LatestTickId, used by ICE TVUS source (Not too sure, what this param is for, but maybe to do truncating data when buffer too big, so set to 0 always for now)
		cookieStore = null;
	}
}
