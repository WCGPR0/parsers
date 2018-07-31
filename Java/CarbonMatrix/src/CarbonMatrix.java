import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Properties;

import org.apache.log4j.Logger;


public class CarbonMatrix {
	private static Logger logger = Logger.getLogger(CarbonMatrix.class);
	private int month;
	private int year;
	private int MAX_YEAR;
	private final static int[] QUARTERS = new int[] {3,6,9,12};
	private final static int[] HALVES = new int[] {6,12};
	private static HashMap<Integer, String> vintageMap = new HashMap<Integer, String>();
	private static HashMap<Integer, String> monthMap = new HashMap<Integer, String>();
	private String DB_URL;
	private String DB_USER;
	private String DB_PASSWORD;
	private Properties props = new Properties();
	String dateQuery = "select max(report_date) from daily_settlement_data_t where contract_name like 'CA%'";
	Date reportDate;
	String query = "SELECT SETTLEMENT_PRICE FROM DAILY_SETTLEMENT_DATA_T WHERE REPORT_DATE=? and CONTRACT_NAME=? and MONTH=? and YEAR=?";
	protected Connection conn;
	protected PreparedStatement ps;
	private ArrayList<String> headerList = new ArrayList<String>();
	private ArrayList<Double> dataList = new ArrayList<Double>();

	static {
		vintageMap.put(2014, "CAM");
		vintageMap.put(2015, "CAN");
		vintageMap.put(2016, "CAO");
		vintageMap.put(2017, "CAP");
		vintageMap.put(2018, "CAW");
		monthMap.put(1, "F");
		monthMap.put(2, "G");
		monthMap.put(3, "H");
		monthMap.put(4, "J");
		monthMap.put(5, "K");
		monthMap.put(6, "M");
		monthMap.put(7, "N");
		monthMap.put(8, "Q");
		monthMap.put(9, "U");
		monthMap.put(10, "V");
		monthMap.put(11, "X");
		monthMap.put(12, "Z");
	}

	public CarbonMatrix(String propsFile, int month, int year) {
		this.month=month;
		this.year=year;
		MAX_YEAR=year+3;
		try {
			props.load(new FileInputStream(propsFile));
			DB_URL = props.getProperty("dbUrl");
			DB_USER = props.getProperty("dbUser");
			DB_PASSWORD = props.getProperty("dbPassword");
			conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(dateQuery);
			while(rs.next())
				reportDate = rs.getDate(1);
		    ps = conn.prepareStatement(query); // create a statement
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_YEAR, 7);
		CarbonMatrix matrix = new CarbonMatrix(args[0], cal.get(Calendar.MONTH)+1, cal.get(Calendar.YEAR));
		matrix.getData();
		matrix.createMatrix();
	}

	private void getData() {
		for (int vintageYear=year-1; vintageYear<=year+3; vintageYear++) {
			//logger.debug("Vintage "+vintageYear+" "+vintageMap.get(vintageYear));
			int expirationYear = year;
			addRecord(vintageYear, month, expirationYear);
			while (expirationYear<=vintageYear+2 && expirationYear<=MAX_YEAR) {
				if (vintageYear<year+2) {
					if (expirationYear == year) {
						for (int quarter : QUARTERS) {
							if (month<quarter)
								addRecord(vintageYear, quarter, expirationYear);
						}
					}
					else if (expirationYear == year+1) {
						for (int quarter : QUARTERS) {
							if (month>quarter)
								addRecord(vintageYear, quarter, expirationYear);
						}
						for (int half : HALVES) {
							if (month<half)
								addRecord(vintageYear, half, expirationYear);
						}
					}
					else {
						for (int half : HALVES)
							addRecord(vintageYear, half, expirationYear);
					}
				}
				else {
					if (expirationYear == year) {
						for (int half : HALVES) {
							if (month<half)
								addRecord(vintageYear, half, expirationYear);
						}
					}
					else {
						for (int half : HALVES)
							addRecord(vintageYear, half, expirationYear);
					}
				}
				expirationYear++;
			}
		}
	}

	private void createMatrix() {
		try {
			String[] headers = headerList.toArray(new String[headerList.size()]);
			Double[] data = dataList.toArray(new Double[dataList.size()]);
			File file = new File(props.getProperty("outputFile"));
			// if file doesnt exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);

			StringBuffer sb= new StringBuffer(reportDate+",");
			for (String header : headers)
				sb.append(",").append(header);
			bw.write(sb.toString());
			bw.newLine();
			sb = new StringBuffer(" , ");
			for (Double d : data)
				sb.append(",").append(d);
			bw.write(sb.toString());
			bw.newLine();
			for (int i=0; i<data.length; i++) {
				sb = new StringBuffer(headers[i]);
				sb.append(",").append(data[i]);
				for (int j=0; j<data.length; j++) {
					sb.append(",").append(data[j]-data[i]);
				}
				bw.write(sb.toString());
				bw.newLine();
			}
			bw.close();
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}
	}
	private static Connection getConnection(String dbUrl, String dbUser, String dbPassword) throws Exception {
	    String driver = "oracle.jdbc.driver.OracleDriver";
	    Class.forName(driver);
	    Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
	    return conn;
	 }

	private Double getSettlementPrice(String contractName, int month, int year) {
		try {
			ps.setDate(1, reportDate);
			ps.setString(2, contractName);
			ps.setInt(3, month);
			ps.setInt(4, year);
			ResultSet rs = ps.executeQuery();
			if (rs.next())
				return rs.getDouble(1);
			else
				return null;
		}
		catch (SQLException ex) {
			ex.printStackTrace();
			return null;
		}
	}

	private void addRecord(int vintageYear, int month, int expirationYear) {
		Double settlementPrice = getSettlementPrice(vintageMap.get(vintageYear), month, expirationYear);
		if (settlementPrice !=null) {
			headerList.add("V"+(vintageYear-2000)+"."+monthMap.get(month)+(expirationYear-2000));
			dataList.add(settlementPrice);
			logger.debug("V"+(vintageYear-2000)+" "+monthMap.get(month)+(expirationYear-2000)+" "+settlementPrice);
		}
	}
}
