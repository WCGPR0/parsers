package reportparser.energyparsers.DailyTickerReport;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;


/**
 * @deprecated
 * Use DTCCSliceReport instead.
 *
 */
public class DTCCTickerSliceReport extends DailyTickerReport {


protected void initialize() {
	super.initialize();
	source = SOURCE.DTCC;
}

protected void parse(URL path, int tick, List<NameValuePair> params, List<String> schemaList, String group) throws ClientProtocolException, IOException, URISyntaxException {

	HttpEntity input = getRequest(path);
	if (input == null) logger.error("No data found during parse");

	try {

	DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	Document doc = dbf.newDocumentBuilder().parse(input.getContent());
	Node root = (Node) XPathFactory.newInstance().newXPath().evaluate("//item/description", doc, XPathConstants.NODE); //< Looks for description in the RSS feed; consider making dynamic

	String[] array = root.getTextContent().split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
	if (array.length != schemaList.size())
		logger.error("DTCC Schema does not match feed data. Please verify schema matches data:\t"+root.getTextContent());
	String[] header = schemaList.toArray(new String[0]);

	int pos = Arrays.asList(header).indexOf(group);
	array[pos] = array[pos].replaceAll(" |\\(|\\)",".");

	array = Arrays.stream(array).map(s -> s.replaceAll("(\\r\\n|\\n|\\r)|\"|,", "").replaceAll("__","_")).toArray(size -> new String[size]);

	if(filter(array,pos) == false) return;

	List<String[]> innerList = new ArrayList<String[]>();

	for (int i = 0; i < Math.min(array.length,header.length); i++) {
	if (array[i] == null)
		logger.error("Unmatched null error with DTCC Schema, please check schema matches with data correctly:"+root.getTextContent());
	innerList.add(new String[]{header[i], array[i]});
	}

	recordList.add(innerList);

	}
	catch (XPathExpressionException | UnsupportedOperationException | SAXException | ParserConfigurationException e) {
		logger.error("XML Parse error in handling DTCC data:\t" + e);;
	}
	}

/**
 * Filter criteria, matches products with our data extracted from the contractMap
 * @param key_outer, String[] array that contains the data
 * @param pos, the position of the key to map with
 * @return boolean, true if passes filter condition, false if filtered out
 */
protected boolean filter(String[] key_outer, int pos) {
	String key = key_outer[pos];
	return /*!** <!-- Testing */ true; /* --> */
	//contractMap.containsKey(key);
}

}