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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;

public class SGXParser {

	private static final String numberWithComma = "(?<=\\d),(?=\\d)";
	private static Logger logger = Logger.getLogger(SGXParser.class);	
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
	
	public SGXParser(String propsFile) {
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
			bufferedWriter.write(optHeaderRow);
			bufferedWriter.newLine();			
			parse("OPT");
			bufferedWriter.newLine();			
			bufferedWriter.write(futHeaderRow);
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
		String urlString = String.format(URL, lastFileID, dateString+format);
		String timestamp = (new SimpleDateFormat("yyyyMMddHHmmss")).format(new Date());
		String tmpZipDirectory = "";
		String tmpZipFileName = (new StringBuilder()).append("temp").append(timestamp).append(".zip").toString();
		try
		{
			File fileDir = new File(".");
			tmpZipDirectory = (new StringBuilder()).append(fileDir.getCanonicalPath()).append(File.separator).append("Archive").append(File.separator).toString();
			logger.debug((new StringBuilder()).append("Archive path: ").append(tmpZipDirectory).append(tmpZipFileName).toString());
			boolean flag2 = (new File((new StringBuilder()).append(fileDir.getCanonicalPath()).append(File.separator).append("Archive").toString())).mkdirs();
			if(flag2)
				logger.debug((new StringBuilder()).append("Directory: ").append(fileDir.getCanonicalPath()).append(" created").toString());
			tmpZipFileName = tmpZipDirectory+tmpZipFileName;
		}
		catch(Exception exception3)
		{
			logger.error((new StringBuilder()).append("Exception").append(exception3.getMessage()).toString());
			System.out.println((new StringBuilder()).append("Exception").append(exception3.getMessage()).toString());
		}
		logger.debug((new StringBuilder()).append("Temp pdf Destination path::").append(tmpZipFileName).toString());
		
		try {
			download(urlString, tmpZipFileName);
		}
		catch (FileNotFoundException ex) {
			//Holiday - Go back a day
			cal.add(Calendar.DATE, -1);
			urlString = String.format(URL, sdf.format(cal.getTime()));
			download(urlString, tmpZipFileName);
		}
		unzipFileIntoDirectory(new ZipFile(tmpZipFileName), new File(tmpZipDirectory));
		if (format.equals("OPT"))
			parse(new File(tmpZipDirectory+dateString+"OPT.txt"), optPattern, 11);
		else
			parse(new File(tmpZipDirectory+dateString+"FUT.txt"), futPattern, 9);
	}
	
	private void parse(File txtFile, Pattern p, int volumeCol) throws Exception {
		Matcher m;
		BufferedReader br = new BufferedReader(new FileReader(txtFile));			
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] data = line.split("\\t");
			if (data.length>0) {
				m = p.matcher(data[1].trim());
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
	
	}	
			
    public static void main(String[] args) {
    	SGXParser parser = new SGXParser(args[0]);
	}
	
    private void download(String input, String output) throws FileNotFoundException
	{
		try {
			BufferedOutputStream bufferedoutputstream;
			InputStream inputstream;
			logger.debug("Downloading from the URL starts...");
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

    public static void unzipFileIntoDirectory(ZipFile zipFile, File folder) {
        Enumeration files = zipFile.entries();
        File f = null;
        FileOutputStream fos = null;
        
        while (files.hasMoreElements()) {
          try {
            ZipEntry entry = (ZipEntry) files.nextElement();
            InputStream eis = zipFile.getInputStream(entry);
            byte[] buffer = new byte[1024];
            int bytesRead = 0;
      
            f = new File(folder.getAbsolutePath() + File.separator + entry.getName());
            
            if (entry.isDirectory()) {
              f.mkdirs();
              continue;
            } else {
              f.getParentFile().mkdirs();
              f.createNewFile();
            }
            
            fos = new FileOutputStream(f);
      
            while ((bytesRead = eis.read(buffer)) != -1) {
              fos.write(buffer, 0, bytesRead);
            }
          } catch (IOException e) {
            e.printStackTrace();
            continue;
          } finally {
            if (fos != null) {
              try {
                fos.close();
              } catch (IOException e) {
                // ignore
              }
            }
          }
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
