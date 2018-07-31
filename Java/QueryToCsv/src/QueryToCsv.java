import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.apache.log4j.Logger;


public class QueryToCsv {
	private static Logger logger = Logger.getLogger(QueryToCsv.class);
	private Properties props = new Properties();
	private String DB_URL;
	private String DB_USER;
	private String DB_PASSWORD;
	private Connection conn;
	private String QUERY;
	private String HEADER_ROW;
	private String outputFileName;

	public static void main(String[] args) throws Exception {
		QueryToCsv q = new QueryToCsv(args[0]);
		q.process();
	}

	public QueryToCsv(String propsFile) throws SQLException {
		try {
			props.load(new FileInputStream(propsFile));
			DB_URL = props.getProperty("dbUrl");
			DB_USER = props.getProperty("dbUser");
			DB_PASSWORD = props.getProperty("dbPassword");
			conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
			QUERY = props.getProperty("query");
			HEADER_ROW = props.getProperty("headerRow");
			outputFileName = props.getProperty("outputFileName");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Connection getConnection(String dbUrl, String dbUser, String dbPassword) throws Exception {
	    String driver = "oracle.jdbc.driver.OracleDriver";
	    Class.forName(driver);
	    Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
	    return conn;
	  }

	public void process() {
		StringBuffer sb = new StringBuffer();
		try {
			Statement stmt = null;
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(QUERY);
			ResultSetMetaData metadata = rs.getMetaData();

			sb.append(HEADER_ROW).append("\n");
			while (rs.next()) {
				sb.append(getData(rs, metadata, 1, false));
				for (int i=2; i<=metadata.getColumnCount(); i++) {
					sb.append(getData(rs, metadata, i, true));
				}
				sb.append("\n");
			}
			File outFile = new File(outputFileName);
			BufferedWriter out = new BufferedWriter(new FileWriter(outFile));
			out.write(sb.toString());
			out.close();
			logger.debug("Wrote "+outputFileName);
		}
		catch (SQLException e) {
			logger.error(e);
			e.printStackTrace();
		}
		catch (IOException e) {
			logger.error(e);
			e.printStackTrace();
		}
	}

	private String getData(ResultSet rs, ResultSetMetaData metadata, int num, boolean prependComma) throws SQLException {
		StringBuffer sb = new StringBuffer();
		if (prependComma)
			sb.append(",");
		if (metadata.getColumnType(num)==java.sql.Types.TIMESTAMP)
			sb.append(rs.getDate(num));
		else
			sb.append(rs.getObject(num));
		return sb.toString();
	}
}
