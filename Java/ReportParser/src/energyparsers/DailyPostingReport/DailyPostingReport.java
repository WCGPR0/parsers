package reportparser.energyparsers.DailyPostingReport;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * An abstract class of a Daily Posting Report - classes that extends this: DominionPostingReport and ChenierePostingReport
 *
 * This class parses data from Information Posting sites, including locations such as Dominion Energy (Dekaflow) and Cheniere (LNG Connection).
 *
 */
public abstract class DailyPostingReport extends DailyTickerReport {

	DateTimeFormatter dateTimeFormat = null;
	String schemaRegexPattern, urlRegexPattern;
	public enum SOURCE implements isource { CHENIERE, DOMINION }; //< Types of sources for the Daily Posting Report




	protected void parseReport(NodeList urlNodeList) throws MiddleTierConnectionNotFoundException {
		int length = urlNodeList.getLength();
		for (int i = 0; i < length; i++) {
			if (urlNodeList.item(i).getNodeType() == Node.ELEMENT_NODE) {
				URL url = null;
				List<NameValuePair> params;
				List<String> schemaList;

				try {
					/** @param url (Required Parameter) */
					String url_ = Util.getNodeValueFromNode(urlNodeList.item(i), "url");
					logger.info("Parsing url = " + url_);
					url = new URL(url_);
					/** @param dateFormat (Optional Parameter)*/
					String dateTimeFormatString = Util.getNodeValueFromNode(urlNodeList.item(i), "dateFormat");
					if (dateTimeFormatString != "")
						dateTimeFormat = DateTimeFormat.forPattern(dateTimeFormatString);
					logger.info("Using dateFormat = " + dateTimeFormatString);
					/** @param schema (Required Parameter)*/
					String schema = Util.getNodeValueFromNode(urlNodeList.item(i), "schema");
					schemaList = Arrays.asList(schema.split(","));
					/** @param pattern (Optional Parameter)*/
					schemaRegexPattern = Util.getNodeValueFromNode(urlNodeList.item(i), "pattern");
					/** @param urlPattern (Optional Parameter)*/
					urlRegexPattern = Util.getNodeValueFromNode(urlNodeList.item(i), "urlPattern");
					/** @param param, POST parameters (Optional Parameter)*/
					params = new ArrayList<NameValuePair>();
					NodeList paramNodeList = Util.getNodeListFromNode(urlNodeList.item(i), "params/*");
					int param_length = paramNodeList.getLength();
					for (int j = 0; j < param_length; j++) {
						String name = Util.getNodeValueFromNode(paramNodeList.item(j), "name()");
						String value = Util.getNodeValueFromNode(paramNodeList.item(j), ".");
						params.add(new BasicNameValuePair(name, value));
					}
					 parse(url, 1, params, schemaList, "");

				}
				catch (IOException | URISyntaxException e) {
					logger.error("Parsing initialization error:\t" + e);
				}

			}
		}

	}
}
