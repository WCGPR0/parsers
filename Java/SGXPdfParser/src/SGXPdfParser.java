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
import java.io.OutputStream;
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

public class SGXPdfParser {

	private static final String numberWithComma = "(?<=\\d),(?=\\d)";
	private static Logger logger = Logger.getLogger(SGXPdfParser.class);	
	private Properties props = new Properties();
	private Pattern optPattern;
	private Pattern futPattern;
	private String optHeaderRow;
	private String futHeaderRow;
	private String URL;
	private static SimpleDateFormat sdf;
	private String outputFileName;
	private FileWriter outputWriter;
	private String dateString;
	private int lastFileID;
	private Calendar cal;
	private BufferedWriter bufferedWriter;
	
	public SGXPdfParser(String propsFile) {
		try {
			props.load(new FileInputStream(propsFile));			
			outputFileName = props.getProperty("outputFile");
			optPattern = Pattern.compile(props.getProperty("optPattern"));
			futPattern = Pattern.compile(props.getProperty("futPattern"));
			outputWriter = new FileWriter(outputFileName);						
			optHeaderRow = props.getProperty("optHeaderRow");
			futHeaderRow = props.getProperty("futHeaderRow");
			URL = props.getProperty("url");
			sdf = new SimpleDateFormat(props.getProperty("dateFormat"));
			lastFileID = Integer.parseInt(props.getProperty("lastFileID"));
			lastFileID++;
			cal = Calendar.getInstance();
			if (cal.get(Calendar.DAY_OF_WEEK)==Calendar.MONDAY)
				cal.add(Calendar.DATE, -3);
			else
				cal.add(Calendar.DATE, -1);
			dateString = sdf.format(cal.getTime());
			bufferedWriter = new BufferedWriter(outputWriter);
			bufferedWriter.write(String.format(optHeaderRow,dateString));
			bufferedWriter.newLine();			
			parse("OPT");
			bufferedWriter.newLine();			
			bufferedWriter.write(String.format(futHeaderRow,dateString));
			bufferedWriter.newLine();						
			parse("FUT");
			bufferedWriter.close();
			OutputStream outputStream = new FileOutputStream(propsFile);
			props.setProperty("lastFileID", String.valueOf(lastFileID));
			props.store(outputStream, "Updated after parsing "+dateString);
		}
		catch (Exception ex) {
			ex.printStackTrace();
			logger.error(ex.getMessage());
		}
	}
	
	private void parse(String format) throws Exception {
		String urlString = String.format(URL, format+dateString, lastFileID, format+dateString);
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
		if (format.equals("OPT"))
			parse(tmpPdfFile, optPattern, 11);
		else
			parse(tmpPdfFile, futPattern, 9);
	}
	
	private void parse(File pdf, Pattern p, int volumeCol) throws Exception {
		PDFTextStripper stripper = new PDFTextStripper();
		PDDocument doc = null;
		try {
			doc = PDDocument.load(pdf);
		}
		catch (IOException ex) {
			logger.error(ex);
			throw ex;
		}
		if (doc!=null) {
			stripper.setSortByPosition(true);
			long time1 = System.currentTimeMillis();
			String pdfText = stripper.getText(doc);
			long time2 = System.currentTimeMillis();
			logger.debug("stripper.getText took "+((time2-time1)/1000)+" seconds");
			Matcher m;
			BufferedReader br = new BufferedReader(new StringReader(pdfText));
			
			String line = null;
			while ((line = br.readLine()) != null) {
				String[] data = line.split(" ");
				if (data.length>0) {
					m = p.matcher(data[0]);
					if (m.matches()) {
						try {
							if (Integer.parseInt(data[volumeCol].replaceAll(numberWithComma, ""))>0)
								writeLine(bufferedWriter, data);
						}
						catch (NumberFormatException ex) {
							logger.error(ex);
						}
					}									
				}
			}
			doc.close();			
		}		
	}	
		
    public static void main(String[] args) {
    	SGXPdfParser parser = new SGXPdfParser(args[0]);
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
    
 }
