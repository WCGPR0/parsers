package reportparser.energyparsers;


import java.lang.invoke.MethodHandles;
import java.util.Calendar;
import java.util.Date;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * An extension of the Report Class, with some base methods made available to all Energy styled reports (such as the headers from ParserControl)
 *
 * Uses input from scheduled tasks
 * @param URL, the source of the reports to be parsed
 * @dateFormat dateFormat, the dateFormat used by the DailySettlementDataParser
 * @pattern pattern, the search pattern used by the DailySettlementDataParser
 */
public abstract class EnergyReport extends Report {
	//Constants
	protected static final int DEFAULT_ITEMS_PER_SAVE_REQUEST = 50; //< Default number of items to save when calling the save request

	//Setting up the logger
	protected static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());
	protected static Logger maillogger = Logger.getLogger("MAIL");

	//Default initialization
	protected static int items_per_save_request = DEFAULT_ITEMS_PER_SAVE_REQUEST;
	protected static final DateTimeFormatter date_time_format = DateTimeFormat.forPattern("dd-MM-yyyy HH:mm:ss");
	protected static final DateTimeFormatter date_time_format_file_name = DateTimeFormat.forPattern("yyyy-MM-dd_HH-mm-ss");

	/** <DailySettlement specific variables> */
	protected static final String UNCHANGED = "UNCH";
	protected static final String NEW = "NEW";
	/** </DailySettlement specific variables> */

	public Multimap<String, Integer> contractMap; //< The header symbols to filter data with

	protected interface isource {}

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
	public abstract void runReport(String url_transform_name, String data_transform_name, String save_request, DateTime last_business_date) throws MiddleTierConnectionNotFoundException;

	/**
	 * Abstract method to gets the URL Node List properties from a rootNode
	 */
	public abstract NodeList getURLNodeList(Element rootNode);


	/** Parses the report, then saves the report
	 * @param urlNodeList, Nodelist of URL destinations
	 * @throws MiddleTierConnectionNotFoundException
	 */
	protected abstract void parseReport(NodeList urlNodeList) throws MiddleTierConnectionNotFoundException;

	protected static Element saveReportPart(Element reportpart, int index, String request, Methods m_methods)
	{
		try
		{
			String xmltemplate = 	"<Request.Params>" +
										"<Request.Param>%s</Request.Param>" +
									"</Request.Params>";
			String xmlparam = String.format(xmltemplate, Util.stringFromDocument(reportpart, true));
			String errors = m_methods.execute(request, xmlparam);
			Document errorsdoc = Util.documentFromString(errors);
			return errorsdoc.getDocumentElement();
		}
		catch (Exception e)
		{
			logger.error("Exception thrown executing SaveRequest for Report (or parsing response)", e);
			return null;
		}

	}


	/**
	 * Abstract method that has all initialization logic.
	 */
	protected abstract void initialize();


	public static Date getTime() {
		Calendar cal = Calendar.getInstance();
		if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY)
			cal.add(Calendar.DATE, -3);
		else
			cal.add(Calendar.DATE, -1);
		return cal.getTime();
	}


	/**
	 * <!-- Based off: EnergyParsers/ParserControl -->
	 *  Gets the data from the request AR.PARSER.CONTROL.GET.TYPE, and creates a map out of it
	 *
	 *  @param source, the type of exchange: ICE, CME, NFX
	 *  @param Method, the middle tier method to excute the request with
	 */
	public static Multimap<String, Integer> getHeaders(String type, String source, Methods m_methods) {
		logger.debug("Running request \"AR.PARSER.CONTROL.GET.TYPE\" to get headers");
		Multimap<String, Integer> map = null;
		String returnVal = null;
		try {
		map = ArrayListMultimap.<String, Integer>create();
		returnVal = m_methods.execute("AR.PARSER.CONTROL.GET.TYPE", type + "|" + source, 0);
		Element rootNode = Util.documentFromString(returnVal).getDocumentElement();
		if (rootNode != null && rootNode.getNodeType() == Node.ELEMENT_NODE)
		{
			Element node = (Element) rootNode;
			NodeList childNodes = node.getChildNodes();
			for (int i = 0; i < childNodes.getLength(); i++) {
				Node childnode = childNodes.item(i);
				String childnode_name = childnode.getNodeName();
				if (childnode_name.equals("parser"))
				{
					try {
						String contractname = Util.getNodeValueFromNode(childnode, "symbol");
						int contractid = Integer.parseInt(Util.getNodeValueFromNode(childnode, "contractid"));
						if (map.containsKey(contractname)) logger.debug(String.format("Duplicate entry found for [parser control id: %s] symbol: %s, source: %s, contract_id: %s", Util.getNodeValueFromNode(childnode, "parser_control_id"),  contractname,Util.getNodeValueFromNode(childnode, "source"), contractid));
						map.put(contractname, contractid);
					}
					catch (NumberFormatException e) {
						continue;
					}

				}
			}
		}
		logger.debug(String.format("Request succesfully returned %s contracts; of which, %s are unique contracts",map.size(),map.keys().size()));
		}
		catch (MiddleTierException e2) {
			logger.error("Middle tier exception: "  + e2);
		}
		return map;
	}

}
