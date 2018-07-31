import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;

import java.util.AbstractMap.SimpleEntry;
import org.slf4j.*;

/**
 * SDRDataParser Version 3.1
 * 
 * 						CHANGELOG:
 * ---------------------- v3.1---------------------------------------------
 * -------------------------------------------------------------------------
 * 
 * Program intended to make parse data from SDR from locations such as IceTradeVault.
 * Using XML and Transforms for parsing logic.
 * 
 * Added options feature
 * 
 * TBD:
 * - Make {Schema, header transform, and group} properties belong to thread class - currently can only run one instance unless they share the same properties
 * 
 * @input Dlog4j.properties File
 * @input Properties file
 * @param dbUrl, Access point of database
 * @param dbUser, Username for Database connection
 * @param dbPassword, Password for Database connection
 * @param options (depreciated), boolean value to update the option table or not
 * @param ticks, amount of times the program ticks before termination
 * @param frequency, how often the program fetches data (in seconds)
 * @param schema, the schema for the data to be parsed (Use single equals = after to denote fields to be inserted into database, use double equals == to denote required fields to be inserted into database, and triple equals to denote update fields ===, and asterix * for special cases with hard coded logic) 
 * @param header, the associated primary key in the database to match with parsed data
 * @param dateFormat,
 * 			  optional DateFormat for formatting the URL
 * @param param,
 * 			  
 * @param URL,
 *            the address of the request(s) to be made      
 * @param run,
 *            the program(s) to be run after all threads make a successful
 *            return.
 *            
 * Note: Order matters in dynamic evaluation, make sure associated param and dateformat is before URL.
 */

public class SDRDataParser {
	public static Logger logger = LoggerFactory.getLogger(SDRDataParser.class); //!*
	//public static Logger logger = Logger.getLogger(SDRDataParser.class);
	
	public static String ccPath; //< Currency Conversion instance
	// Global variables due to retaining data, and reusing them until otherwise overriden
	private static int batchLimit = 9000;
	private static int ticks = 1;
	private static int frequency = 0;
	private static List<NameValuePair> params;
	private static URL cookie_url;
	public static enum MODETYPE { DEBUG, PROD };
	public static enum MODETYPE_ { UPDATE_ALL, UPDATE_BIG, UPDATE_SMALL};
	private static MODETYPE_ mode;
	public static MODETYPE sql; //< DEBUG FOR MYSQL, PROD FOR ORACLE, ETC.
	private static Source transform; //< Transformation file of XML file prior to DB
	private static Path output; //< Output dump of raw data in XML format
	private static XStream xstream; //< Stream that reads inputs XML and outputs XML
	private static Transformer transformer; //< Transformer that does the actual transformation
	static String group; //< Hashmap unique key
	
	private static SDRDataParser MAIN; //< Main Instance, that controls the child threads
	
	private static Connection conn;
	private static PreparedStatement pstmt;
    private final String query; //< The final query mold
    private final int marks; //< The amount of question marks in our mold
	
	private static Map<String, List<List<Map.Entry<String, String> > > > finalMap; // ! Implementation logic alternative, using contract maps for every URL instead of one overall one
	private static Map<String, String> contractMap; //Contract Map
	private static List<String> schemaList = new ArrayList<String>();
	private static String today; //< Today's date (excluding weekends), using dateFormat from properties, to replace placeholders %s in URLs
	
	// Default variables
	private static final String DEFAULT_SCHEMA = "TVProductMnemonic,TradeAction,ContinuationEvent,TradeReportID,TradeReportRefID,ExecutionTime,ClearedOrBilateral,Collateralization,EndUserException,BespokeSwap,Block/LargeNotional,BlockUnit,FuturesContract,ExecutionVenue,StartDate,EndDate,Price,TotalQuantity,SettlementCurrency,SettlementFrequency,AssetClass,ResetFrequency,NotionalAmount,NotionalUnit,OptionStrikePrice,OptionType,OptionStyle,OptionPremium,OptionExpirationFrequency,OptionExpirationDate,PriceType,OtherPriceTerm,MultiAssetClassSwap,MacsPrimaryAssetClass,MacsSecondaryAssetClass,MixedSwap,mixedSwapOtherReportedSDR,DayCountConvention,PaymentFrequency1,PaymentFrequency2,ResetFrequency2,OptionLockoutPeriod,EmbeddedOption,NotionalAmount1,NotionalAmount2,NotionalCurrency1,NotionalCurrency2";
	private static String initialQuery_sql = "INSERT INTO daily_sdr_data_t (%s) VALUES(%s) ON DUPLICATE KEY UPDATE %s"; //< Mold for SQL prepared statement
	private static String initialQuery_oracle = "MERGE INTO en.ar_surveillance_data_t a USING (select %s from dual) b on (%s) WHEN MATCHED THEN UPDATE SET %s WHEN NOT MATCHED THEN INSERT(%s) VALUES(%s)"; //< Mold for SQL prepared statement
	
	/**
	 * @param args[0]
	 *            The Filepath for the properties file
	 * @param args[1] opt.
	 * 			  To enter DEBUG or RUN mode. It will be in DEBUG by default.
	 */
	public static void main(String[] args) {
		try {
			///Reading in Properties file, and initialization
			Path path = FileSystems.getDefault().getPath(args[0]);
			Properties props = new Properties();
			props.load(new FileInputStream(path.toString()));;
			
			///Configuration for Database Connection
			String DB_URL = props.getProperty("dbUrl");
			String DB_USER = props.getProperty("dbUser");
			String DB_PASSWORD = props.getProperty("dbPassword");
			
			if (DB_URL.contains("jdbc:mysql")) {
				Class.forName("com.mysql.jdbc.Driver");
				sql = MODETYPE.DEBUG;
			}
			else {
				if (DB_URL.contains("log4jdbc")) Class.forName("net.sf.log4jdbc.DriverSpy");
				else Class.forName("oracle.jdbc.driver.OracleDriver");
				oracle.jdbc.driver.OracleLog.setTrace(true);
				sql = MODETYPE.PROD;
			}

			conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
			
			transform = new StreamSource(props.getProperty("transform"));
			output = Paths.get(props.getProperty("output"));
			
			group = props.getProperty("group");
			
		    ///Processing the other attributes of the Properties file
			String datesUrlsParams = Files.readAllLines(path).stream().filter(item -> item.contains("URL=") || item.contains("dateFormat=") || item.contains("params=") || item.contains("run=") || item.contains("cookie=")).collect(Collectors.joining(","));
			ticks = Integer.parseInt(props.getProperty("ticks"));
			frequency = Integer.parseInt(props.getProperty("frequency"));
			mode = (args.length > 1) ?
					(args[1].toUpperCase().contains("UPDATE_BIG")) ? 
							MODETYPE_.UPDATE_BIG : (args[1].toUpperCase().contains("UPDATE_SMALL")) ? 
									MODETYPE_.UPDATE_SMALL : MODETYPE_.UPDATE_ALL : MODETYPE_.UPDATE_ALL;
			MAIN = new SDRDataParser(props);
			//MAIN.test_small_01(); //!* TESTING
			MAIN.parse(Arrays.asList(datesUrlsParams.split(",")), mode);
			logger.debug("Program terminated.");
			pstmt.close();
		} catch (Exception e) {
			logger.debug("Error: " + e);
		}
	}
	

	/**
	 * Constructor, which reads in our header file to initialize our data HashMap with relevant products and IDs
	 * ! TBD: Change fields in initialQuery to be dynamic (ex. REPORT_DATE, TRADE_ID, etc).
	 * @param props
	 * @throws Exception 
	 */
	SDRDataParser(Properties props) throws Exception {
		//Options
		//if (props.getProperty("options", "false").toLowerCase().equals("true")) initialQuery_oracle = initialQuery_oracle.replace("daily_settlement_data_t", "daily_settlement_opt_data_t");
		//Validating Currency Conversion
		ccPath = props.getProperty("currency", "false");
		if(ccPath != "false" && !(new File(ccPath).exists())) FatalError(12);
		
		//Initializing Xstream
		xstream = new XStream();
		xstream.registerConverter(new MapConverter());
		xstream.alias("trades", Map.class);
		
		//Initializing the Maps
		finalMap = new HashMap<String, List<List<Map.Entry<String,String > > > >();	
		contractMap = new HashMap<String, String>();
		Path header = Paths.get(props.getProperty("header"));
		if (header != null) {
			Files.lines(header).forEach( s -> {
				List<List<Map.Entry<String, String> > > outerList = new ArrayList<List<Map.Entry<String, String> > >();
				finalMap.put(s.substring(0, s.lastIndexOf(",")), outerList);
				contractMap.put(s.substring(0, s.lastIndexOf(",")), s.substring(s.lastIndexOf(",") + 1)); // Primary key is ticker name, and value is ID
				} 
			);
		}
		Path schema = Paths.get(props.getProperty("schema", DEFAULT_SCHEMA));
		if (schema != null) {
			Files.lines(schema).limit(1).forEach( s -> {
				schemaList = Arrays.asList(s.split(","));
				} 
			);
		}
		transformer = TransformerFactory.newInstance().newTransformer(transform);
		
		logger.debug("Preparing initial query statement...");
		//Running transform against schema to create initial query
		//Converting schema into an empty structure similar to finalMap
		Map<String, List<List<Map.Entry<String, String> > > > tempMap = new HashMap<String, List<List<Map.Entry<String, String> > > >();
		List<List<Map.Entry<String, String> > > tempOuterList =  new ArrayList<List<Map.Entry<String, String> > >();
		List<Map.Entry<String, String> > tempInnerlist = new ArrayList<Map.Entry<String, String> >();
		for (String s : schemaList) {
			tempInnerlist.add(new SimpleEntry<>(s,""));
		}
		tempOuterList.add(tempInnerlist);
		tempMap.put(contractMap.keySet().iterator().next(),tempOuterList); // Uses first item in contract map as placeholder
		outputXML(tempMap);
		//Does a XSL Transform
		File transformFile = //< Temporary file after XSL Transform 
		File.createTempFile(output.getFileName().toString(), null);
	
		transformer.setParameter("PREPARE", true);
		Transform(transformFile);
	    List<Map.Entry<List<Map<String, String> >,List<Map<String, String> > > > queryList = inputXML(transformFile.toPath());
		//Prepare the query from the data structure
	    if (sql == MODETYPE.DEBUG) {
	    //MYSQL LOGIC
		String headers_select = queryList.get(0).getKey().stream().map(s_ -> s_.get("KEY").toString()).collect(Collectors.joining(","));
		int marks = headers_select.length() - headers_select.replace(",", "").length() + 1;
		String questionMarks_key = new String(new char[marks]).replace("\0", "?,");
		questionMarks_key = questionMarks_key.substring(0, questionMarks_key.length() - 1); //Remove the additional comma at the end
		
		String headers_value = queryList.get(0).getValue().stream().map(s_ -> s_.get("KEY").toString() + "=?").collect(Collectors.joining(","));
		marks += headers_value.length() - headers_value.replace(",", "").length() + 1;
		query = String.format(initialQuery_sql, headers_select, questionMarks_key, headers_value);
	    this.marks = marks;
	    }
	    else {
		//ORACLE LOGIC
	    String headers_select = Stream.concat(queryList.get(0).getKey().stream(),queryList.get(0).getValue().stream()).map(s_ -> "? as " + s_.get("KEY").toString()).collect(Collectors.joining(","));
		String string2 = queryList.get(0).getKey().stream().map(s_ -> "a." + s_.get("KEY").toString() + " = b." + s_.get("KEY")).collect(Collectors.joining(" and "));
		String string3 = queryList.get(0).getValue().stream().map(s_ -> "a." + s_.get("KEY").toString() + " = b." + s_.get("KEY").toString() ).collect(Collectors.joining(","));
		int marks = headers_select.length() - headers_select.replaceAll(",(?![^(]*\\))", "").length() + 1;
		
		String string4 = queryList.get(0).getKey().stream().map(s_ -> "b." + s_.get("KEY").toString()).collect(Collectors.joining(","));
		string4 = string4.equals("") ? string4 + queryList.get(0).getValue().stream().map(s_ -> "b." + s_.get("KEY").toString()).collect(Collectors.joining(",")) : string4 + "," +  queryList.get(0).getValue().stream().map(s_ -> "b." + s_.get("KEY").toString()).collect(Collectors.joining(",")) ;
		query = String.format(initialQuery_oracle, headers_select, string2, string3, string4.replace("b.",""), string4);
		this.marks = marks;
	    }
		pstmt = conn.prepareStatement(query);
		if (true)
		if (sql == MODETYPE.DEBUG) 
			logger.debug("Finished prepared initial query mold: " + query);
	    else logger.debug("Finished preparing mold for SQL Update.");
	}

	public void parse(List<String> datesUrlsParams, MODETYPE_ mode) throws InterruptedException, IOException {
			int size = datesUrlsParams.size();
			List<Integer> url_key = new ArrayList<Integer>();
			Thread[] ChildDataSpawns = new Thread[size]; //< Too big of a size, could be improved in terms of space-efficiency!
			
			//Skip the weekends
			Calendar cal = Calendar.getInstance();
			if (cal.get(Calendar.DAY_OF_WEEK)==Calendar.MONDAY)
				cal.add(Calendar.DATE, -3);
			else
				cal.add(Calendar.DATE, -1);
			today = (new SimpleDateFormat("YYYY-MM-DD")).format(cal.getTime()); //Initialization of today
			
			for (int i = 0; i < size; i++) {
				String element = datesUrlsParams.get(i);
				int index;
				if ((index = element.indexOf("dateFormat=")) != -1) {
					today = (new SimpleDateFormat(element.substring(index+11))).format(cal.getTime());
				}
				else if ((index = element.indexOf("params=")) != -1)
				{
					params = new ArrayList<NameValuePair>();
					for (String s : element.substring(index+7).split("&") ) {
						String s_[] = s.split("=");
						if (s_.length > 2) FatalError(1);
						String value = s_.length == 1 ? "" : s_[1]; //When params have no value, make them empty
						value = value.replace("%s", today);
						params.add(new BasicNameValuePair(s_[0], value));
					}
				}
				else if ((index = element.indexOf("URL=")) != -1 && (mode != MODETYPE_.UPDATE_SMALL)){
					element = element.substring(4);
					element = element.replace("%s", today);
					url_key.add(i);
					(ChildDataSpawns[i] = new Thread(new ChildDataSpawn(new URL(element), cookie_url, params, 0, 1, mode))).start();
				}
				else if ((index = element.indexOf("cookie=")) != -1 && (mode != MODETYPE_.UPDATE_SMALL)){
					cookie_url = new URL(element.substring(7));
				}
				else if ((index = element.indexOf("run=")) != -1 && (mode != MODETYPE_.UPDATE_BIG)){
					element = element.replace("%s", today);
					element = element.substring(4);
					url_key.add(i);
					(ChildDataSpawns[i] = new Thread(new ChildDataSpawn(new URL(element), cookie_url, params,frequency,ticks, mode))).start();
				}
			}
			for (int i = 0; i < url_key.size(); i++) {
				ChildDataSpawns[url_key.get(i)].join();
			}
			logger.debug("Updates has been complete.");
		}
	
	/**
	 * Accessor method to get schema list
	 * @return schemaList, a List<String> of the schema
	 */
	protected static List<String> getSchema() {
		return schemaList;
	}
	
	/**
	 * Filter criteria, matches products with our data extracted from the header file.
	 * Insert custom logic here, if loosening or tightening filter criterion.
	 * @param key_outer, String[] array that contains the data
	 * @param pos, the position of the key to map with
	 * @return outerList, List<List<Map.Entry<String, String> > >
	 */
	protected static List<List<Map.Entry<String, String> > > getList(String[] key_outer, int pos) {
		List<List<Map.Entry<String, String> > > outerList = finalMap.get(key_outer[pos]);
		/** Custom filter logic here **/
		if (outerList == null && Arrays.stream(key_outer).anyMatch(s -> s.toLowerCase().contains("elec") || s.toLowerCase().contains("natgas"))) {
			outerList = new ArrayList<List<Map.Entry<String, String> > >();	
			finalMap.put(key_outer[pos], outerList);
			contractMap.put(key_outer[pos], "0");
		}
		/** </Custom filter logic here /> **/
		return outerList;
	}
	
	/**
	 * Mutator method to modify the values in the finalMap.
	 * @param key
	 * @param header
	 * @param update, updates the database with most recent insert if set to true
	 * @throws SQLException 
	 * @throws TransformerException 
	 * @throws IOException 
	 */
	protected synchronized static void updateList (String[] array, String[] header, boolean update) throws SQLException, IOException, TransformerException {
		int pos = Arrays.asList(header).indexOf(group);
		pos = pos == -1 ? 0 : pos;
		List<List<Map.Entry<String, String> > > outerList = getList(array,pos);
		if(outerList == null) return;
		
		List<Map.Entry<String,String> > innerList = new ArrayList<Map.Entry<String, String> >();
		innerList.add(new SimpleEntry<>("ID", contractMap.get(array[pos]))); //Adds preset ID into list 

		for (int i = 0; i < Math.min(array.length,header.length); i++) {
		if (array[i] == null) FatalError(8);
			innerList.add(new SimpleEntry<>(header[i], array[i]));
		}
		
		outerList.add(innerList);
		
		if (update) updateDB(innerList, array[pos]);
	}
	
	//!* TESTING2 fakes data
	private void test_small_01() throws SQLException, IOException, TransformerException {
		String[] fakeData = "Index,OAQYK,NEW,,C-1-120151-26541109461520167,null,2017-05-12T14:30:00,U,UC,,false,NA,bbl,HO(NYMEX),OFF,2017-06-01,2017-06-30,1.4933,20000,USD,Monthly,C,null,null,null,null,null,null,null,null,,,,false,,,false,,,,,,,false,,,,".split(",");
		String[] fakeHeader = "NULL,TVProductMnemonic,TradeAction,ContinuationEvent,TradeReportID,TradeReportRefID,ExecutionTime,ClearedOrBilateral,Collateralization,EndUserException,BespokeSwap,Block/LargeNotional,BlockUnit,FuturesContract,ExecutionVenue,StartDate,EndDate,Price,TotalQuantity,SettlementCurrency,SettlementFrequency,AssetClass,ResetFrequency,NotionalAmount,NotionalUnit,OptionStrikePrice,OptionType,OptionStyle,OptionPremium,OptionExpirationFrequency,OptionExpirationDate,PriceType,OtherPriceTerm,MultiAssetClassSwap,MacsPrimaryAssetClass,MacsSecondaryAssetClass,MixedSwap,mixedSwapOtherReportedSDR,DayCountConvention,PaymentFrequency1,PaymentFrequency2,ResetFrequency2,OptionLockoutPeriod,EmbeddedOption,NotionalAmount1,NotionalAmount2,NotionalCurrency1,NotionalCurrency2".split(",");
		updateList(fakeData, fakeHeader, true);
	}
	
	protected synchronized static void updateDB (List<Map.Entry<String, String> > innerList, String key) throws IOException, TransformerException {
		if (!validateMap(key)) return;
		Map<String, List<List<Map.Entry<String, String> > > > updateMap = new HashMap<String, List<List<Map.Entry<String, String> > > >();
		List<List<Map.Entry<String, String> > > outerList = new ArrayList<List<Map.Entry<String, String> > >();
		outerList.add(innerList);
		updateMap.put(key, outerList);
		
		outputXML(updateMap); //Outputs the temporary hashmap into a XML file

		//Does a XSL Transform
		File transformFile = //< Temporary file after XSL Transform 
		File.createTempFile(output.getFileName().toString(), null);
		transformFile.deleteOnExit();
		
		transformer.setParameter("PREPARE",false);
		Transform(transformFile);		
		
	    Map.Entry<List<Map<String, String> >,List<Map<String, String> > > pair = inputXML(transformFile.toPath()).get(0); 
	    
    	List<Map<String, String> > list = pair.getKey(); //Select List
    	list.addAll(pair.getValue()); // Select + Update list
    	if (list.size() != MAIN.marks) FatalError(11);
    	int count = 1;
	    for (Map<String, String> map : list) {
	    	try {
	    	String type = map.get("TYPE");
	    	if (type.contains("INT")) pstmt.setInt(count, Integer.parseInt(map.get("VALUE")));
	      	else if(type.contains("DATE")) 
	      		if (map.get("VALUE").isEmpty()) pstmt.setDate(count, null);
	      		else pstmt.setDate(count,  java.sql.Date.valueOf(map.get("VALUE")));
	      	else if (type.contains("TIME"))
	      		pstmt.setTimestamp(count, java.sql.Timestamp.valueOf(map.get("VALUE")));
	      	else if(type.contains("NUMBER")) 
	      		if (map.get("VALUE").isEmpty()) pstmt.setBigDecimal(count, null);
	      		else pstmt.setBigDecimal(count,  new BigDecimal(map.get("VALUE")));
	    	else  //Varchar and others default to string
	    		pstmt.setString(count, map.get("VALUE"));
	    	++count;
	    	}
	    	catch (Exception e) {
	    		FatalError(99, "BAD DATA FOUND IN TRANSFORMED OUTPUT: " + map + "\tPLEASE MAKE SCHEMA MORE RIGOROUS");
	    	}
	    }
	    try { pstmt.execute(); }
	    catch (SQLException e) {
	    	FatalError(13, "Query failed: " + pstmt + "\nError: " + e.toString());
	    }
	}
	
	protected synchronized static void updateDB_ALL () throws SQLException, IOException, TransformerException {
		Map<String, List<List<Map.Entry<String, String> > > > updateMap = finalMap.entrySet().stream().filter(
		map -> //Non null, required fields
		validateMap(map.getKey())
				).collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));
		outputXML(updateMap); //Outputs the hashmap into a xml file
		
		//Does a XSL Transform
		File transformFile = //< Temporary file after XSL Transform 
		File.createTempFile(output.getFileName().toString(), null);
		transformFile.deleteOnExit();
	
		transformer.setParameter("PREPARE",false);
		Transform(transformFile);
		
		int batchCount = 0;
	    List<Map.Entry<List<Map<String, String> >,List<Map<String, String> > > > transformedMap = inputXML(transformFile.toPath()); 
	    for (Map.Entry<List<Map<String, String> >,List<Map<String, String> > > pair : transformedMap) {
	    	List<Map<String, String> > list = pair.getKey(); //Select List
	    	list.addAll(pair.getValue()); // Select + Update list
	    	if (list.size() != MAIN.marks) FatalError(11);
	    	int count = 1;
		    for (Map<String, String> map : list) {
		    	try {
		    	String type = map.get("TYPE");
		    	if (type.contains("INT")) pstmt.setInt(count, Integer.parseInt(map.get("VALUE")));
		    	else if(type.contains("DATE"))
		      		if (map.get("VALUE").isEmpty()) pstmt.setDate(count, null);
		      		else pstmt.setDate(count,  java.sql.Date.valueOf(map.get("VALUE")));
		    	else if(type.contains("NUMBER"))
		      		if (map.get("VALUE").isEmpty()) pstmt.setBigDecimal(count, null);
		      		else pstmt.setBigDecimal(count,  new BigDecimal(map.get("VALUE")));
		      	else if (type.contains("TIME"))
		      		pstmt.setTimestamp(count, java.sql.Timestamp.valueOf(map.get("VALUE")));		    	
		    	else  //Varchar and others default to string
		    		pstmt.setString(count, map.get("VALUE"));
		    	++count;
		    	}
		    	catch (Exception e) {
		    		FatalError(99, "BAD DATA FOUND IN TRANSFORMED OUTPUT: " + map + "\tPLEASE MAKE SCHEMA MORE RIGOROUS");
		    	}
		    }
		    pstmt.addBatch();
		    ++batchCount;
		    if (batchCount > batchLimit) FatalError(10);
	    }
	    logger.debug(String.format("Executing %d SQL queries...", batchCount));
		int errors = (int) Arrays.stream(pstmt.executeBatch()).filter(s-> s < 0 && s != pstmt.SUCCESS_NO_INFO).count();
		if (errors > 0) logger.debug(String.format("There was %d failed SQL queries.", errors));
		
	}
	
	/** Helper method to validate map */
	private static boolean validateMap(String key) {
		return contractMap.containsKey(key);
	}

	
	/** Helper method to output the data into XML 
	 * @throws IOException */
	private static void outputXML(Map<String, List<List<Map.Entry<String, String> > > > updateMap) throws IOException {
		String xml = xstream.toXML(updateMap);
		logger.debug("Outputting XML to " + output);
		Files.write(output,xml.getBytes());
	}
	
	/** Helper method to get map from XML 
	 * @throws IOException */
	@SuppressWarnings("unchecked")
	private static List<Map.Entry<List<Map<String, String> >,List<Map<String, String> > > > inputXML(Path inputXML) throws IOException {
		String content = new String(Files.readAllBytes(inputXML));
		return (List<Map.Entry<List<Map<String, String> >,List<Map<String, String> > > >) xstream.fromXML(content);
	}
	
	
	/** Custom Marshaling and Unmarshalling converter
	 *	@toXML returns type of Map<String, List<List<Map.Entry<String, String>>>
	 *  @fromXML returns type of List<Map.Entry<List<Map<String, String>>List<Map<String, String>>>>
	 */
	public static class MapConverter implements Converter {
		@SuppressWarnings("rawtypes")
		public boolean canConvert(Class c) {
			return AbstractMap.class.isAssignableFrom(c);
		}
		@SuppressWarnings("unchecked")
		@Override
	    public void marshal(Object value, HierarchicalStreamWriter writer, MarshallingContext context) {
			Map<String, List<List<Map.Entry<String, String> > > > updateMap = (Map<String, List<List<Map.Entry<String, String> > > >) value;
            for (Map.Entry<String, List<List<Map.Entry<String, String> > > > map : updateMap.entrySet()) {
            	writer.startNode(map.getKey()); // Gets the Name
             	writer.addAttribute("ID",contractMap.get(map.getKey())); //Gets the ID
                for (List<Map.Entry<String, String> > list : map.getValue()) {
                writer.startNode("trade");
                for (Map.Entry<String, String> pair : list) {
                writer.startNode(pair.getKey().replaceAll("(?<![\\uD800-\\uDBFF])[\\uDC00-\\uDFFF]|[\\uD800-\\uDBFF](?![\\uDC00-\\uDFFF])|[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F\\uFEFF\\uFFFE\\uFFFF]|\\/", ""));  //Parses invalid XML
                Object val = pair.getValue();
                if ( null != val ) {
                    writer.setValue(val.toString());
                }
                writer.endNode();
                }
                writer.endNode();
                }
                writer.endNode();
            }
	}
		@Override
	      public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
	            List<Map.Entry<List<Map<String, String> >, //SELECTS
	            List<Map<String, String> > // UPDATES
	            > > list = new ArrayList<Map.Entry<List<Map<String, String> >, List<Map<String, String> > > >();

            	// trades
	            while(reader.hasMoreChildren()) {
	            	List<Map<String, String> > selectList = new ArrayList<Map<String, String> >();
	            	List<Map<String, String> > updateList = new ArrayList<Map<String, String> >();
	                reader.moveDown();
	                // trades/trade
	                while(reader.hasMoreChildren()) {
	                reader.moveDown();
	                String type = reader.getNodeName();
	                if (type.toUpperCase().equals("SELECT") || type.toUpperCase().equals("UPDATE")) {
	                	// trades/trade/SELECT || trades/trade/UPDATE
	                	while(reader.hasMoreChildren()) {
	   		                reader.moveDown();
	   		                	Map<String, String> map_ = new HashMap<String, String>();
   		                		map_.put("TYPE", reader.getAttribute("type"));
	   		                	map_.put("KEY", reader.getNodeName());
	   		                	map_.put("VALUE", reader.getValue());
	   		                	if (type.toUpperCase().equals("SELECT")) selectList.add(map_);
	   		                	else updateList.add(map_);
	   		                reader.moveUp();
	                	}
	                }
	                else logger.debug("Warning: Unrecognized field in transform; should only be SELECT and UPDATE! -- " + reader.getNodeName());
	                //Ignore the rest
	                
	                reader.moveUp();
	                }
	                
	                list.add(new SimpleEntry<List<Map<String, String> >, List<Map<String, String> > >(selectList, updateList));

	                reader.moveUp();
	            }
	            return list;
	        }

}
	
	private static void Transform(File transformFile) throws TransformerException {
	/** Parameters to pass into the transform
		@param REPORT_DATE current time
	*/
	
	// REPORT_DATE
	Calendar cal = Calendar.getInstance();
	if (cal.get(Calendar.DAY_OF_WEEK)==Calendar.MONDAY)
		cal.add(Calendar.DATE, -3);
	else
		cal.add(Calendar.DATE, -1);
	Date today = cal.getTime(); //Different today; not related to instance todays
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd"); //! TBD: Possibly dynamic
	transformer.setParameter("REPORT_DATE",sdf.format(today));
	
	//! TBD: Verify against a  schema before transform
	transformer.transform(new StreamSource(output.toFile()), new StreamResult(transformFile));
	logger.debug("Outputted transformed XML to " + transformFile);
	}
	
	/**
	 * Helper function in handling errors and debugging
	 * 
	 * @param error
	 *            int code specifying the error
	 */
	public synchronized static void FatalError(int error) {
		String errorMsg = "";
		switch (error) {
		case 1:
			errorMsg = "Invalid format for parameters: too many equal (=) signs, should be keyA=valueA&keyB=valueB...";
			break;
		case 2:
			errorMsg = "The header did not match our source. Please verify the header input.";
			break;
		case 3:
			errorMsg = "Thread error - Happened during fetching data";
			break;
		case 4:
			errorMsg = "Error while running Parser.";
			break;
		case 5:
			errorMsg = "Could not get an authentication cookie.";
			break;
		case 6:
			errorMsg = "Invalid parsing of frequent small data, getTickers.";
			break;
		case 7:
			errorMsg = "Incorrect amounts of data. Please update header file and logic.";
			break;
		case 8:
			errorMsg = "Unmatched null error with schema, please check schema matches with data correctly.";
			break;
		case 9:
			errorMsg = "Error occured while updating database.";
			break;
		case 10:
			errorMsg = "Over 9000 SQL queries found. Please increase max to use larger amounts";
			break;
		case 11:
			errorMsg = "Data mismatch with the SQL.";
			break;
		case 12:
			errorMsg = "Error during currency conversion; Executable (CurrencyConversion) not found!";
			break;
		case 13:
			errorMsg = "Error during SQL query; consider increasing level of logging";
			break;
		case 99:
			errorMsg = "A generic error has occured, please set up more specific instances of error reporting";
		default:
			break;
		}
		logger.debug(errorMsg);
		System.exit(error);

	}

	/**
	 * Overloaded Helper function, with additional info, in handling error
	 * feedback and debugging
	 * 
	 * @param error
	 *            an int code specifying the error
	 * @param errorMsg
	 *            a String of additional information to log
	 */
	public synchronized static void FatalError(int error, String errorMsg) {
		logger.debug(errorMsg);
		FatalError(error);
	}

	/**
	 * Overloaded Helper function, try to avoid using this at all times
	 * possible.
	 * 
	 * @param exception
	 *            Uses the ToString() of Exception to get the default info
	 */
	public synchronized static void FatalError(Exception e) {
		logger.debug(e.toString());
		FatalError(99);
	}

}

/**
 * 
 * The child threads that makes the HTTP Requests
 *
 */
class ChildDataSpawn implements Runnable {
	int frequency; // < Frequency in seconds of how often to query
	URL url; // < Url of the address to make the POST Request
	URL cookie_url; // Url of the address to make the GET Request
	int ticks; // < Amount of ticks before expiry
	List<NameValuePair> params;
	int count = 0;
	HttpClient client;
	SDRDataParser.MODETYPE_ mode;

	ChildDataSpawn(URL url, URL cookie_url, List<NameValuePair> params, int frequency, int ticks, SDRDataParser.MODETYPE_ mode) {
		this.frequency = frequency * 1000;
		this.url = url;
		this.ticks = ticks;
		this.params = params;
		this.cookie_url = cookie_url;
		this.mode = mode;
	}

	@Override
	public void run() {
		try {
		client = HttpClients.createDefault();
		///Setting cookie via a GET request for sites that prevent bots
		if (cookie_url != null) {
			SDRDataParser.logger.debug("Getting cookie details from:\t" + cookie_url);
			String cookie = client.execute(new HttpGet(cookie_url.toURI())).getFirstHeader("Set-Cookie").getValue(); //!!! Need to make this more robust and dynamic, TBD!
			if ( cookie == "" ) SDRDataParser.FatalError(5);
			SDRDataParser.logger.debug("Successfully retrieved cookie details!");
		}
		///Making POST requests
		while (ticks > 0) {
				List<NameValuePair> params_ = null;
				if (params != null) {
				params_ = params.stream().collect(Collectors.toList());
				for (int i = 0; i < params_.size(); ++i) {
					if(params_.get(i).getValue().contains("%c")) params_.set(i, new BasicNameValuePair(params_.get(i).getName(),params_.get(i).getValue().replace("%c",Integer.toString(count))));
				}
				}
				SDRDataParser.logger.debug("Fetching from: " + url);
				SDRDataParser.logger.debug("Using parameters: " + params_);


					if (url.toString().contains("icetradevault")) {
						HttpEntity output_raw = postRequest(url, params_);
						if (output_raw == null) SDRDataParser.logger.error("ERROR: Thread got no data at: " + new Date().toString());
						if (mode != SDRDataParser.MODETYPE_.UPDATE_SMALL)
							parse_ice_large(output_raw);
						if (mode != SDRDataParser.MODETYPE_.UPDATE_BIG)
							parse_ice_small(output_raw);
					}
					else if (url.toString().contains("RSS_FEED")) {
						HttpEntity output_raw = getRequest(url);			
						if (output_raw == null) SDRDataParser.logger.error("ERROR: Thread got no data at: " + new Date().toString());
						if (mode != SDRDataParser.MODETYPE_.UPDATE_BIG)
							parse_dttc_small(output_raw);
					}
					else
						SDRDataParser.FatalError(99, "The parser for the url has not been implemented: " + url);
					
				count += 1; //!* TBD: Move outside to increment only when updated data					
				ticks -= 1;
				SDRDataParser.logger.debug("Thread finished parsing at: " + new Date().toString());
				Thread.sleep(frequency);
			}
		}
				catch (Exception e) {
				e.printStackTrace();
				SDRDataParser.FatalError(3);
			}
			SDRDataParser.logger.debug("Thread complete, successful at time:	 " + new Date().toString());
	}
	
	private HttpEntity getRequest (URL url) throws URISyntaxException, ClientProtocolException, IOException {
		HttpGet get = new HttpGet(url.toURI());
		return client.execute(get).getEntity();
	}	
	
	private HttpEntity postRequest (URL url, List<NameValuePair> params) throws URISyntaxException, ClientProtocolException, IOException {
		HttpPost post = new HttpPost(url.toURI());
		if (params != null)
		post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
		return client.execute(post).getEntity();
	}
	
	private void parse_ice_large(HttpEntity input) throws UnsupportedOperationException, IOException, SQLException, TransformerException {
		BufferedReader br = new BufferedReader(new InputStreamReader(input.getContent()));
		String header_ = br.readLine(), line = "";
		int validate = validate(header_);
		if (validate == 0 ) {SDRDataParser.logger.debug("No data found"); return; }
		else if (validate == -1) SDRDataParser.FatalError(2);
		String[] header = header_.split(",");
		while ((line = br.readLine()) != null) {
			String[] s = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"); //Ignores commas in quotes
			if (s.length > header.length) SDRDataParser.FatalError(7); //< Consider a strict assertion; if (s.length != 46)
			SDRDataParser.updateList(s,header, false); 
		}
		SDRDataParser.updateDB_ALL();
	}
	
	private void parse_ice_small(HttpEntity input) throws UnsupportedOperationException, IOException, SQLException, TransformerException {
		BufferedReader br = new BufferedReader(new InputStreamReader(input.getContent()));
		String line = "";
		while ((line = br.readLine()) != null) {
		line = line.replace("[\r][\n]", ""); //<Strip unnecessary newlines
		line = line.replace("\"", ""); //<Strip unnecessary quotes
		if (line.matches("(?s)\\[+(.+)\\]+")) { //<Base depth of iteration
			line = line.substring(1, line.length()-1); //<Removes first layer
			for (String line_ : line.split(",+(?![^\\[]*\\])")) {  //< Strict assertion of having atleast []
				line_ = line_.replaceFirst("\\[", "").replaceFirst("(?s)\\](?!.*?\\])", "");
				String[] s = line_.split(",(?![^\\[]*\\])");
				if (s.length > SDRDataParser.getSchema().size()) SDRDataParser.FatalError(7); //< Consider a strict assertion; if (s.length != 48)
				
				// Converting foreign currency to USD
				int currency_index = SDRDataParser.getSchema().indexOf("SettlementCurrency");
				if (s[currency_index].length() >= 3 && s[currency_index] != "USD" && !SDRDataParser.ccPath.equals("false")) {
					CurrencyConversion cc = new CurrencyConversion(SDRDataParser.ccPath);
					s[currency_index] = Float.toString(cc.get(s[currency_index]));
					
				}
				SDRDataParser.updateList(s, SDRDataParser.getSchema().toArray(new String[0]), true);
		}
		}
	}
	}
	
	private void parse_dttc_small(HttpEntity input) throws SAXException, ParserConfigurationException, UnsupportedOperationException, IOException, SQLException, TransformerException, XPathExpressionException {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		Document doc = dbf.newDocumentBuilder().parse(input.getContent());
		Node root = (Node) XPathFactory.newInstance().newXPath().evaluate("//item/description", doc, XPathConstants.NODE); //< Looks for description in the RSS feed; consider making dynamic
		
		String[] s_arr = root.getTextContent().split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
		String[] header = SDRDataParser.getSchema().toArray(new String[0]);
		
		int pos = Arrays.asList(header).indexOf(SDRDataParser.group);
		s_arr[pos] = s_arr[pos].replace(':','.');
		
		s_arr = Arrays.stream(s_arr).map(s -> s.replaceAll("(\\r\\n|\\n|\\r)|\"", "").replace(",","")).toArray(size -> new String[size]);
	
		SDRDataParser.updateList(s_arr, header, true);
	}
	
	
	
	/** Helper function for parse_ice_large */
	private int validate(String header) {
		if (header.equals("[]")) return 0;
		else if (header.equals(SDRDataParser.getSchema().toString().replaceAll("\\[|\\]| ", ""))) return 1;
		else return -1;
	}
}