package parsercontrol;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.google.common.collect.TreeMultimap;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Small client that makes a single request, AR.PARSER.CONTROL.GET, to the ARR-EN Middletier. Not meant to be robust.
 * @arg Properties file
 * 	@param source, the source of the middle tier environment
 *  @param [source]output, the output file for the parser control; example: iceoutput="...",cmeoutput="...","nasdaqoutput="..."
 *  @param region (opt.), specify the region to filter
 */
public class ParserControl {

	private String[] outputs;
	private static Logger logger = Logger.getLogger(ParserControl.class);

	static ServiceBase service;
	static String configPath, type;

	private static Methods middletier;

	ParserControl(String[] outputs) {
		logger.debug("Started ParserControl...");
		try {
			this.outputs = outputs;


			}
		catch (InvalidPathException e) {
			System.err.println("Invalid path.");
			System.exit(0);
		}
	}

	public static void main(String[] args) throws MiddleTierConnectionNotFoundException, MiddleTierException {
		ParserControl pc;
		try {

		// <!-- Initializiation -->
		configPath = FileSystems.getDefault().getPath(args[0]).toAbsolutePath().toString();
		Properties props = new Properties();
		props.load(new FileInputStream(configPath.toString()));
		type = props.getProperty("type", "");
		// <!-- --------------- -->


		// <!-- Connection to the Middle Tier -->

		logger.debug("Connecting to middle tier");

		String product = "EN",
				middletierid = "",
				username = "reportparser",
				password = "reportparser",
				application = "",
				processname = ParserControl.class.getSimpleName(),
				host = props.getProperty("host").toLowerCase();
		boolean exclusivelogon = false, initialisemessaging = false;
		switch (host) {
			case "uat-ar":
				middletierid = "UAT1";
				break;
			case "sup-ar":
				middletierid = "SUP1";
				break;
			case "prd-ar":
				middletierid = "PROD1";
				break;
		}

		middletier = new Methods();
		if (StringUtils.isNoneBlank(host))
		{
			middletier.setHost(host);
		}

		middletier.initialise(product, middletierid, username, password, application, processname, exclusivelogon, initialisemessaging);
		logger.info("Connected to middle tier");

		// <!-- -------------------------- -->

		String[] outputs = Files.readAllLines(Paths.get(configPath)).stream().filter(item -> item.contains("output=")).toArray(String[]::new);

		pc = new ParserControl(outputs);
		Map<String, TreeMultimap<String, Integer>> map = pc.createMap(type);

		for (String output : pc.outputs) {
			String temp[] = output.split("output=");
			String source = temp[0].toUpperCase();
			String file = temp[1];
			if (map.containsKey(source)) {
				try (Writer writer = Files.newBufferedWriter(Paths.get(file))) {
				for (Map.Entry<String, Integer> entry : map.get(source).entries())
						writer.write(entry.getKey() + "," + entry.getValue() + System.lineSeparator());
				}
			}
		}

		} catch (IOException e) {
			System.err.println("An exception has occurred: " + e);
			e.printStackTrace();
		}
		catch (MiddleTierException e) {
			System.err.println("Error connecting to middle tier: " + e);
			e.printStackTrace();
		}
		finally {
			middletier.terminate();
			pc = null;
		}
	}


	/**
	 * Runs a request AR.PARSER.CONTROL.GET, constructs a map out of the result.
	 * @return a map, with the source as the key, d the values (with duplicates) are a pair<symbol, contractid> sorted by symbol.
	 * @throws MiddleTierException
	 */
	public Map<String, TreeMultimap<String, Integer> > createMap() throws MiddleTierException {
		String returnVal = null;
		returnVal = middletier.execute("AR.PARSER.CONTROL.GET", "");
		Element rootNode = Util.documentFromString(returnVal).getDocumentElement();
		return createMap(rootNode);
	}

	/**
	 * Runs a request AR.PARSER.CONTROL.GET.REGION, constructs a map out of the result.
	 * @return a map, with the source as the key, d the values (with duplicates) are a pair<symbol, contractid> sorted by symbol.
	 * @throws MiddleTierException
	 */
	public Map<String, TreeMultimap<String, Integer> > createMap(String type) throws MiddleTierException {
		if (type == null || type.isEmpty()) return createMap();
		String returnVal = null;
		returnVal = middletier.execute("AR.PARSER.CONTROL.GET.TYPE", type);
		Element rootNode = Util.documentFromString(returnVal).getDocumentElement();
		return createMap(rootNode);
	}

	private Map<String, TreeMultimap<String, Integer> > createMap(Element rootNode) {
		Map<String, TreeMultimap<String, Integer>> map = new HashMap<String, TreeMultimap<String, Integer> >();
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
					String source = Util.getNodeValueFromNode(childnode, "source");
					String contractname = Util.getNodeValueFromNode(childnode, "symbol");
					int contractid = Integer.parseInt(Util.getNodeValueFromNode(childnode, "contractid"));
					TreeMultimap<String, Integer> map_;
					if (map.containsKey(source)) map_ = map.get(source);
					else map_ = TreeMultimap.create();
					if (map_.containsKey(contractname)) logger.debug(String.format("Duplicate entry found for [parser control id: %s] symbol: %s, source: %s, contract_id: %s", Util.getNodeValueFromNode(childnode, "parser_control_id"),  contractname,Util.getNodeValueFromNode(childnode, "source"), contractid));
					map_.put(contractname, contractid);
					map.put(source, map_);
					}
					catch (NumberFormatException e) {
						continue;
					}

				}
			}
		}
		return map;
	}

}
