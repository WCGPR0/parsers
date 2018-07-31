import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;

public class PdfParser {

	private static final String numberWithComma = "(?<=\\d),(?=\\d)";
	private static Logger logger = Logger.getLogger(PdfParser.class);	
	private Properties props = new Properties();
	private Pattern datePattern;
	private String HEADER_ROW;
	private String URL;
	private boolean newDay = false;
	private HashMap<String, String> fileNameMap;
	private HashMap<String, File> fileMap;
	private HashMap<String, FileWriter> fileWriterMap;
	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
	
	public PdfParser(String propsFile) {
		try {
			props.load(new FileInputStream(propsFile));
			newDay = Boolean.parseBoolean(props.getProperty("newDay"));
			datePattern = Pattern.compile(props.getProperty("datePattern"));
			HEADER_ROW = props.getProperty("headerRow");
			URL = props.getProperty("url");
			String fileMapLocation = props.getProperty("fileMap");
			fileNameMap = new HashMap<String, String>();
			fileMap = new HashMap<String, File>();
			fileWriterMap = new HashMap<String, FileWriter>();			
			try {
				BufferedReader in = new BufferedReader(new FileReader(fileMapLocation));
				String str;
				while ((str = in.readLine()) != null) {
					String[] contractTitlePair = str.split(",");
					fileNameMap.put(contractTitlePair[0], contractTitlePair[1]);					
				}
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			Calendar cal = Calendar.getInstance();
			if (cal.get(Calendar.DAY_OF_WEEK)==Calendar.MONDAY)
				cal.add(Calendar.DATE, -3);
			else
				cal.add(Calendar.DATE, -1);
			String urlString = String.format(URL, sdf.format(cal.getTime())); 
			URL url = new URL(urlString);
	    	parse(url);
		}
		catch (Exception ex) {
			ex.printStackTrace();
			logger.error(ex.getMessage());
		}

	}
	
	private void parse(URL url) throws Exception {
		logger.debug("Parsing "+url.toString());
		PDFTextStripper stripper = new PDFTextStripper();
		PDDocument doc = null;
		File file = null;
		try {
			doc = PDDocument.load(url);
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
			
			Pattern p = Pattern.compile(getPattern());
			populateFileMap(dateString);

			while ((line = br.readLine()) != null) {
				String[] data = line.split(" ");
				if (data.length>0) {
					m = p.matcher(data[0]);
					if (m.matches()) {
						//System.out.println(line);
						FileWriter fw = fileWriterMap.get(data[0]);
						writeLine(fw, data);		
					}									
				}
			}
			doc.close();
			for (FileWriter fw : fileWriterMap.values())				
				fw.close();
		}
		for (String key : fileNameMap.keySet()) {
			File latestFilename = new File(String.format(props.getProperty("latestOutputFile"),fileNameMap.get(key),fileNameMap.get(key)));
			if (newDay) {
				File previousFilename = new File(String.format(props.getProperty("previousOutputFile"),fileNameMap.get(key),fileNameMap.get(key)));
				copy(latestFilename, previousFilename);
				logger.debug("Copied to "+previousFilename);
			}
			if (fileMap.get(key)!=null) {
				copy(fileMap.get(key), latestFilename);
				logger.debug("Copied to "+latestFilename);
			}
		}
	}	
		
    public static void main(String[] args) {
    	PdfParser parser = new PdfParser(args[0]);
	}
	
	private void writeLine(FileWriter fw, String[] line) throws IOException {
		for (int i=1; i<line.length; i++) {
			fw.write(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				fw.write(",");
		}
		fw.write("\r\n");
	}
	
	private void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
    
        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

	private String encode(String input) {
		String output = input.replaceAll(" ", "%20");
		//output = output.replaceAll("&", "%26");
		return output;
	}
	
	private String getPattern() {
		StringBuffer sb = new StringBuffer();
		for (String s : fileNameMap.keySet()) {
			sb.append(s).append("|");
		}
		return sb.toString().substring(0, sb.toString().length()-1);
	}
	
	private void populateFileMap(String dateString) throws IOException {
		for (String key : fileNameMap.keySet()) {
			File f = new File(String.format(props.getProperty("outputFile"), fileNameMap.get(key), dateString));
			fileMap.put(key, f);
			FileWriter fw = new FileWriter(f);
			fw.write(String.format(HEADER_ROW, dateString));
			fileWriterMap.put(key, fw);						
		}
	}
}
