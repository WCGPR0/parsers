import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;


public class COTParser {
	private static Logger logger = Logger.getLogger(COTParser.class);	
	private Properties props = new Properties();
	private String DB_URL;
	private String DB_USER;
	private String DB_PASSWORD;
	private String CONTRACT_REGEX;
	private String DATE_REGEX;
	protected Connection conn;
	protected PreparedStatement pstmt;
	protected URL url;
	protected DateFormat df;
	protected HashMap<String, String> contractMap = new HashMap<String, String>();	
	
	public static void main(String[] args) {
		try {
			COTParser parser = new COTParser(args[0]);
			parser.parse();
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	COTParser(String propsFile) throws SQLException {
		try {
			logger.debug("Begin COTParser");
			props.load(new FileInputStream(propsFile));
			DB_URL = props.getProperty("dbUrl");
			DB_USER = props.getProperty("dbUser");
			DB_PASSWORD = props.getProperty("dbPassword");
			CONTRACT_REGEX = props.getProperty("contractRegex");
			DATE_REGEX = props.getProperty("dateRegex");
			String mapString = props.getProperty("contractMap");
			String[] contracts = mapString.split(",");
			for (String contract : contracts) {
				String[] pair = contract.split("=");
				contractMap.put(pair[0], pair[1]);
			}
			url = new URL(props.getProperty("url"));
			df = new SimpleDateFormat(props.getProperty("dateFormat"));
		}
		catch (Exception e) {
			e.printStackTrace();
		}	
	}
	
	public void parse() throws SQLException {
		try {
		conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
	    String update = "INSERT INTO NATGAS_COT_T VALUES (?,?,?,?,?,?)";
	    pstmt = conn.prepareStatement(update); // create a statement
	    logger.debug("Parsing "+url);
	    BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
			String line = null;
			// jump to beginning of report
			while ((line = br.readLine()) != null) {
				if (line.trim().startsWith("<pre>"))
					break;
			}
			Pattern p = Pattern.compile(CONTRACT_REGEX);
			Matcher m;
			while ((line = br.readLine()) != null) {
				m = p.matcher(line);
				if (m.find()) {
					String key = m.group();
					String contract = contractMap.get(key);
					logger.debug("Found Contract: "+contract);					
					line = br.readLine();
					Pattern datePattern = Pattern.compile(DATE_REGEX);
					m = datePattern.matcher(line);
					if (m.matches()) {
						String date = m.group(1);
						Date dt = df.parse(date);
						//logger.debug(date);
						logger.debug(dt);
						for (int i=0; i<9; i++)
							line = br.readLine();
						//Position
						String[] total = line.split("\\s+");
						//Change
						for (int i=0; i<5; i++)
							line = br.readLine();
						String[] change = line.split("\\s+");
						//Traders
						for (int i=0; i<8; i++)
							line = br.readLine();
						String[] traders = line.split("\\s+");
						insert(dt, contract, "Open Interest", parseInt(total[2].replaceAll("[,:\\.]", "")), parseInt(change[2].replaceAll("[,:\\.]", "")), parseInt(traders[2].replaceAll("[,:\\.]", "")));
						insert(dt, contract, "P/M/P/U Long", parseInt(total[3].replaceAll("[,:\\.]", "")), parseInt(change[3].replaceAll("[,:\\.]", "")), parseInt(traders[3].replaceAll("[,:\\.]", "")));
						insert(dt, contract, "P/M/P/U Short", parseInt(total[4].replaceAll("[,:\\.]", "")), parseInt(change[4].replaceAll("[,:\\.]", "")), parseInt(traders[4].replaceAll("[,:\\.]", "")));
						insert(dt, contract, "Swap Dealers Long", parseInt(total[5].replaceAll("[,:\\.]", "")), parseInt(change[5].replaceAll("[,:\\.]", "")), parseInt(traders[5].replaceAll("[,:\\.]", "")));
						insert(dt, contract, "Swap Dealers Short", parseInt(total[6].replaceAll("[,:\\.]", "")), parseInt(change[6].replaceAll("[,:\\.]", "")), parseInt(traders[6].replaceAll("[,:\\.]", "")));
						insert(dt, contract, "Swap Dealers Spreading", parseInt(total[7].replaceAll("[,:\\.]", "")), parseInt(change[7].replaceAll("[,:\\.]", "")), parseInt(traders[7].replaceAll("[,:\\.]", "")));
						insert(dt, contract, "Managed Money Long", parseInt(total[8].replaceAll("[,:\\.]", "")), parseInt(change[8].replaceAll("[,:\\.]", "")), parseInt(traders[8].replaceAll("[,:\\.]", "")));
						insert(dt, contract, "Managed Money Short", parseInt(total[9].replaceAll("[,:\\.]", "")), parseInt(change[9].replaceAll("[,:\\.]", "")), parseInt(traders[9].replaceAll("[,:\\.]", "")));
						insert(dt, contract, "Managed Money Spreading", parseInt(total[10].replaceAll("[,:\\.]", "")), parseInt(change[10].replaceAll("[,:\\.]", "")), parseInt(traders[10].replaceAll("[,:\\.]", "")));
						insert(dt, contract, "Other Reportables Long", parseInt(total[11].replaceAll("[,:\\.]", "")), parseInt(change[11].replaceAll("[,:\\.]", "")), parseInt(traders[11].replaceAll("[,:\\.]", "")));
						insert(dt, contract, "Other Reportables Short", parseInt(total[12].replaceAll("[,:\\.]", "")), parseInt(change[12].replaceAll("[,:\\.]", "")), parseInt(traders[12].replaceAll("[,:\\.]", "")));
						insert(dt, contract, "Other Reportables Spreading", parseInt(total[13].replaceAll("[,:\\.]", "")), parseInt(change[13].replaceAll("[,:\\.]", "")), parseInt(traders[13].replaceAll("[,:\\.]", "")));
						insert(dt, contract, "Nonreportable Long", parseInt(total[14].replaceAll("[,:\\.]", "")), parseInt(change[14].replaceAll("[,:\\.]", "")), null);
						insert(dt, contract, "Nonreportable Short", parseInt(total[15].replaceAll("[,:\\.]", "")), parseInt(change[15].replaceAll("[,:\\.]", "")), null);
						
					}
					else {
						logger.debug("Did not match date: "+line);
					}
				}
			}
			
		}
		catch (Exception ex) {
			ex.printStackTrace();
			logger.error(ex);
		}
	}
	
	private void insert(Date date, String contract, String type, Integer total, Integer delta, Integer trader) throws SQLException {
		pstmt.setDate(1, new java.sql.Date(date.getTime()));
		pstmt.setString(2, contract);
		pstmt.setString(3, type);
		if (total == null)
			pstmt.setNull(4, java.sql.Types.NUMERIC);
		else
			pstmt.setInt(4, total);
		if (delta == null)
			pstmt.setNull(5, java.sql.Types.NUMERIC);
		else
			pstmt.setInt(5, delta);
		if (trader==null)
			pstmt.setNull(6, java.sql.Types.NUMERIC);
		else
			pstmt.setInt(6, trader);
		pstmt.executeUpdate();
		
	}

	private Connection getConnection(String dbUrl, String dbUser, String dbPassword) throws Exception {
	    String driver = "oracle.jdbc.driver.OracleDriver";
	    Class.forName(driver);
	    Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
	    return conn;
	  }
			
	private Integer parseInt(String input) {
		input.replaceAll("[,:\\.]", "");
		if (input.equals(""))
			return null;
		else
			return Integer.parseInt(input);
	}
}
