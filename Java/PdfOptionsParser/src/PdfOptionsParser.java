import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;

public class PdfOptionsParser {

	private static final String numberWithComma = "(?<=\\d),(?=\\d)";
	private static Logger logger = Logger.getLogger(PdfOptionsParser.class);
	private Properties props = new Properties();
	private Pattern datePattern;
	private Pattern pattern;
	private String HEADER_ROW;
	private String URL;
	private static SimpleDateFormat sdf;
	private String outputFileName;
	private FileWriter outputWriter;
	private String DB_URL;
	private String DB_USER;
	private String DB_PASSWORD;
	protected Connection conn;
	private DateFormat iceDateFormat;
	private PreparedStatement pstmt;
	protected String codesFile;
	protected HashMap<String, Integer> contractMap;
	private Pattern icePattern;
	private boolean isUK = false;

	public PdfOptionsParser(String propsFile) {
		try {
			props.load(new FileInputStream(propsFile));
			outputFileName = props.getProperty("outputFile");
			datePattern = Pattern.compile(props.getProperty("datePattern"));
			pattern = Pattern.compile(props.getProperty("pattern"));
			outputWriter = new FileWriter(outputFileName);

			HEADER_ROW = props.getProperty("headerRow");
			URL = props.getProperty("url");
			isUK = Boolean.parseBoolean(props.getProperty("uk", "false"));
			sdf = new SimpleDateFormat(props.getProperty("dateFormat"));
			Calendar cal = Calendar.getInstance();
			if (cal.get(Calendar.DAY_OF_WEEK)==Calendar.MONDAY)
				cal.add(Calendar.DATE, -3);
			else
				cal.add(Calendar.DATE, -1);
			String urlString = String.format(URL, sdf.format(cal.getTime()));
			String timestamp = (new SimpleDateFormat("yyyyMMddHHmmss")).format(new Date());

			String tmpPdfFileName = (new StringBuilder()).append(File.separator).append("Archive").append(File.separator).append("temppdf").append(timestamp).append(".pdf").toString();
			try
			{
				File fileDir = new File(".");
				logger.debug((new StringBuilder()).append("Archive path: ").append(fileDir.getCanonicalPath()).append(tmpPdfFileName).toString());
				boolean flag2 = (new File((new StringBuilder()).append(fileDir.getCanonicalPath()).append(File.separator).append("Archive").toString())).mkdirs();
				if(flag2)
					logger.debug((new StringBuilder()).append("Directory: ").append(fileDir.getCanonicalPath()).append(" created").toString());
				tmpPdfFileName = (new StringBuilder()).append(fileDir.getCanonicalPath()).append(tmpPdfFileName).toString();
			}
			catch(Exception exception3)
			{
				logger.error((new StringBuilder()).append("Exception").append(exception3.getMessage()).toString());
				System.out.println((new StringBuilder()).append("Exception").append(exception3.getMessage()).toString());
			}
			logger.debug((new StringBuilder()).append("Temp pdf Destination path::").append(tmpPdfFileName).toString());

			try {
				downloadPdf(urlString, tmpPdfFileName);
			}
			catch (FileNotFoundException ex) {
				//Holiday - Go back a day
				cal.add(Calendar.DATE, -1);
				urlString = String.format(URL, sdf.format(cal.getTime()));
				downloadPdf(urlString, tmpPdfFileName);
			}
			File tmpPdfFile = new File(tmpPdfFileName);
			parse(tmpPdfFile);
			//populate DB with PHE data containing volume
			/*DB_URL = props.getProperty("dbUrl");
			DB_USER = props.getProperty("dbUser");
			DB_PASSWORD = props.getProperty("dbPassword");
			conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
			iceDateFormat = new SimpleDateFormat(props.getProperty("iceDateFormat"));
			codesFile = props.getProperty("codesFile");
			icePattern = Pattern.compile(props.getProperty("icePattern"));
		    populateDB();*/
		}
		catch (Exception ex) {
			ex.printStackTrace();
			logger.error(ex.getMessage());
		}

	}

	private void parse(File pdf) throws Exception {
		PDFTextStripper stripper = new PDFTextStripper();
		PDDocument doc = null;
		try {
			doc = PDDocument.load(pdf);
		}
		catch (IOException ex) {
			logger.error(ex);
		}
		if (doc!=null) {
			stripper.setSortByPosition(true);
			long time1 = System.currentTimeMillis();
			String pdfText = stripper.getText(doc);
			long time2 = System.currentTimeMillis();
			logger.debug("stripper.getText took "+((time2-time1)/1000)+" seconds");
			Matcher m;
			BufferedReader br = new BufferedReader(new StringReader(pdfText));
			BufferedWriter bufferedWriter = new BufferedWriter(outputWriter);
			String line = null;
			String dateString = "";
			while ((line = br.readLine()) != null) {
				m = datePattern.matcher(line);
				if (m.matches()) {
					//logger.debug("Date: "+line);
					dateString=line;
					break;
				}
			}
			bufferedWriter.write(String.format(HEADER_ROW,dateString));
			bufferedWriter.newLine();
			while ((line = br.readLine()) != null) {
				String[] data = line.split(" ");
				if (data.length>0) {
					m = pattern.matcher(data[0]);
					if (m.matches()) {
						if (isUK) {
							//BlockVol or EOO
							if (data.length==18 && (!data[15].equals("0") || !data[16].equals("0")))
								writeLine(bufferedWriter, data);
							//else if (data.length==17 && (!data[14].equals("0") || !data[15].equals("0")))
								//writeLineNoDelta(bufferedWriter, data);
							//No OI, Change, Exer
							else if (data.length==15 && (!data[12].equals("0") || !data[13].equals("0")))
								writeLineNoOINoChangeNoExer(bufferedWriter, data);
							//No OHLC
							else if (data.length==14 && (!data[11].equals("0") || !data[12].equals("0")))
								writeLineNoOHLC(bufferedWriter, data);
							//else if (data.length==13 && (!data[10].equals("0") || !data[11].equals("0")))
								//writeLineNoOHLCNoChange(bufferedWriter, data);
							//No OHLC, OI, Change, Exer
							else if (data.length==11 && (!data[8].equals("0") || !data[9].equals("0")))
								writeLineNoOHLCNoOINoChangeNoExer(bufferedWriter, data);
							else if (!(data.length==11 || data.length==14 || data.length==15 || data.length==18))
								logger.debug("data length: "+data.length+"  data: "+toString(data));
						}
						else {
							//Vol or OI
							if (data.length==18 && (!data[11].equals("0") || !data[12].equals("0")))
								writeLine(bufferedWriter, data);
							else if (data.length==17 && (!data[10].equals("0") || !data[11].equals("0")))
								writeLineNoDelta(bufferedWriter, data);
							//else if (data.length==16 && !data[11].equals("0"))
								//writeLineNoOI(bufferedWriter, data);
							else if (data.length==15 && !data[11].equals("0"))
								writeLineNoOINoChangeNoExer(bufferedWriter, data);
							else if (data.length==14 && (!data[7].equals("0") || !data[8].equals("0")))
								writeLineNoOHLC(bufferedWriter, data);
							else if (data.length==13 && (!data[6].equals("0") || !data[7].equals("0")))
								writeLineNoOHLCNoChange(bufferedWriter, data);
							else if (data.length==11 && !data[7].equals("0"))
								writeLineNoOHLCNoOINoChangeNoExer(bufferedWriter, data);
							else logger.debug("data length: "+data.length+"  data: "+toString(data));
						}
					}
				}
			}
			doc.close();
			bufferedWriter.close();
		}
	}

    public static void main(String[] args) {
    	PdfOptionsParser parser = new PdfOptionsParser(args[0]);
	}

    void downloadPdf(String input, String output) throws FileNotFoundException
	{
		try {
			BufferedOutputStream bufferedoutputstream;
			InputStream inputstream;
			System.out.println("Downloading PDF from the URL...");
			logger.debug("Downloading PDF from the URL starts...");
			bufferedoutputstream = null;
			inputstream = null;
			URL url = new URL(input);
			bufferedoutputstream = new BufferedOutputStream(new FileOutputStream(output));
			URLConnection urlconnection = url.openConnection();
			inputstream = urlconnection.getInputStream();
			if(inputstream.available() > 0)
			{
				byte abyte0[] = new byte[1024];
				int i;
				while((i = inputstream.read(abyte0)) != -1)
					bufferedoutputstream.write(abyte0, 0, i);
				logger.debug("Download Completed");
				System.out.println("Download Completed");
			}
			try
			{
				if(inputstream != null)
					inputstream.close();
				bufferedoutputstream.close();
			}
			catch(IOException ioexception)
			{
				logger.error((new StringBuilder()).append("").append(ioexception.getMessage()).toString());
			}
			try
			{
				if(inputstream != null)
					inputstream.close();
				bufferedoutputstream.close();
			}
			catch(IOException ioexception1)
			{
				logger.error((new StringBuilder()).append("").append(ioexception1.getMessage()).toString());
			}
			try
			{
				if(inputstream != null)
					inputstream.close();
				bufferedoutputstream.close();
			}
			catch(IOException ioexception2)
			{
				logger.error((new StringBuilder()).append("").append(ioexception2.getMessage()).toString());
			}
		}
		catch (FileNotFoundException ex) {
			throw ex;
		}
		catch (IOException ex) {
			logger.error((new StringBuilder()).append("").append(ex.getMessage()).toString());
		}
	}


    private void writeLine(BufferedWriter bw, String[] line) throws IOException {
		for (int i=0; i<line.length; i++) {
			bw.write(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				bw.write(",");
		}
		bw.newLine();
	}

    private void writeLineNoDelta(BufferedWriter bw, String[] line) throws IOException {
		for (int i=0; i<line.length; i++) {
			bw.write(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				bw.write(",");
			if (i==3)
				bw.write(",");
		}
		bw.newLine();
	}

    private void writeLineNoOI(BufferedWriter bw, String[] line) throws IOException {
		for (int i=0; i<line.length; i++) {
			bw.write(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				bw.write(",");
			if (i==11)
				bw.write(",,");
		}
		bw.newLine();
	}

    private void writeLineNoOINoDelta(BufferedWriter bw, String[] line) throws IOException {
		for (int i=0; i<line.length; i++) {
			bw.write(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				bw.write(",");
			if (i==3)
				bw.write(",");
			if (i==10)
				bw.write(",,");
		}
		bw.newLine();
	}

    private void writeLineNoOHLC(BufferedWriter bw, String[] line) throws IOException {
		for (int i=0; i<line.length; i++) {
			bw.write(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				bw.write(",");
			if (i==4)
				bw.write(",,,,");
		}
		bw.newLine();
	}

    private void writeLineNoOHLCNoChange(BufferedWriter bw, String[] line) throws IOException {
		for (int i=0; i<line.length; i++) {
			bw.write(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				bw.write(",");
			if (i==4)
				bw.write(",,,,");
			if (i==5)
				bw.write(",");
		}
		bw.newLine();
	}

    private void writeLineNoOINoChangeNoExer(BufferedWriter bw, String[] line) throws IOException {
		for (int i=0; i<line.length; i++) {
			bw.write(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				bw.write(",");
			if (i==11)
				bw.write(",,,");
		}
		bw.newLine();
	}

    private void writeLineNoOHLCNoOINoChangeNoExer(BufferedWriter bw, String[] line) throws IOException {
		for (int i=0; i<line.length; i++) {
			bw.write(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				bw.write(",");
			if (i==4)
				bw.write(",,,,");
			if (i==7)
				bw.write(",,,");
		}
		bw.newLine();
	}

    private String toString(String[] line) throws IOException {
    	StringBuffer sb = new StringBuffer();
		for (int i=0; i<line.length; i++) {
			sb.append(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				sb.append(",");
		}
		return sb.toString();
	}

    private static Connection getConnection(String dbUrl, String dbUser, String dbPassword) throws Exception {
	    String driver = "oracle.jdbc.driver.OracleDriver";
	    Class.forName(driver);
	    Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
	    return conn;
	}

	private void populateDB() {
		contractMap = new HashMap<String, Integer>();
		Matcher m;
		try {
			BufferedReader in = new BufferedReader(new FileReader(codesFile));
			String str;
			while ((str = in.readLine()) != null) {
				String[] titleContractPair = str.split(",");
				contractMap.put(titleContractPair[0], Integer.parseInt(titleContractPair[1]));
			}
			in.close();

			String query = "INSERT INTO DAILY_SETTLEMENT_OPT_DATA_T VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    		pstmt = conn.prepareStatement(query); // create a statement
			in = new BufferedReader(new FileReader(outputFileName));
			str = in.readLine();
			String[] data = str.split(",");
			String dateStr = data[data.length-1];
			while ((str = in.readLine()) != null) {
				data = str.split(",");
				m = icePattern.matcher(data[0]);
				if (m.matches() && !data[11].equals("0")) {
					insertOptRecord(dateStr, contractMap.get(data[0]), data[0], data[1], data[9], data[11], data[12], "ICE", data[2], data[3]);
				}
			}
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    	catch (SQLException e) {
			e.printStackTrace();
		}
    }

	private void insertOptRecord(String dateString, Integer contractId, String code, String monthString, String settle, String vol, String openInt, String source, String strike, String strategy) throws SQLException {
		Date d = null;
		try {
			d = iceDateFormat.parse(dateString);
		}
		catch (ParseException ex) {
			logger.error("Error parsing "+dateString);
			ex.printStackTrace();
			return;
		}
		pstmt.setString(1, "EN");
		pstmt.setDate(2, new java.sql.Date(d.getTime()));
		pstmt.setInt(3, contractId);
		pstmt.setString(4, code);
		pstmt.setInt(5, getMonth(monthString));
		pstmt.setInt(6, 2000+Integer.parseInt(monthString.substring(4)));
		if (settle!=null)
			pstmt.setDouble(7, Double.parseDouble(settle));
		else
			pstmt.setNull(7, java.sql.Types.NUMERIC);
		pstmt.setInt(8, Integer.parseInt(vol));
		if (openInt!=null && openInt.length()>0)
			pstmt.setInt(9, Integer.parseInt(openInt));
		else
			pstmt.setNull(9, java.sql.Types.NUMERIC);
		pstmt.setString(10, source);
		pstmt.setDouble(11, Double.parseDouble(strike));
		pstmt.setString(12, strategy);
		pstmt.executeUpdate(); // execute insert statement
	}

	private int getMonth(String s) {
		String monthcode = s.substring(0,3);
		int month=0;
		if (monthcode.equals("JAN"))
			month=1;
		else if (monthcode.equals("FEB"))
			month=2;
		else if (monthcode.equals("MAR"))
			month=3;
		else if (monthcode.equals("APR"))
			month=4;
		else if (monthcode.equals("MAY"))
			month=5;
		else if (monthcode.equals("JUN"))
			month=6;
		else if (monthcode.equals("JUL"))
			month=7;
		else if (monthcode.equals("AUG"))
			month=8;
		else if (monthcode.equals("SEP"))
			month=9;
		else if (monthcode.equals("OCT"))
			month=10;
		else if (monthcode.equals("NOV"))
			month=11;
		else if (monthcode.equals("DEC"))
			month=12;
		return month;
	}
}
