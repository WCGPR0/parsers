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

public class IceBasisSwapParser {

	private static final String numberWithComma = "(?<=\\d),(?=\\d)";
	private static Logger logger = Logger.getLogger(IceBasisSwapParser.class);	
	private Properties props = new Properties();
	private Pattern datePattern;
	private String HEADER_ROW;
	private String URL;
	private String PATTERN;
	private boolean newDay = false;
	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd");
	
	public IceBasisSwapParser(String propsFile) {
		try {
			props.load(new FileInputStream(propsFile));
			newDay = Boolean.parseBoolean(props.getProperty("newDay"));
			datePattern = Pattern.compile(props.getProperty("datePattern"));
			HEADER_ROW = props.getProperty("headerRow");
			URL = props.getProperty("url");
			PATTERN = props.getProperty("pattern");
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
			File f = new File(String.format(props.getProperty("outputFile"), dateString));
			FileWriter fw = new FileWriter(f);
			fw.write(String.format(HEADER_ROW, dateString));
			
			Pattern p = Pattern.compile(PATTERN);
			while ((line = br.readLine()) != null) {
				String[] data = line.split(" ");
				if (data.length>0) {
					m = p.matcher(data[0]);
					if (m.matches()) {
						// No OHLC or OI/Change
						if (data.length==9)
							writeLine(fw, new String[]{data[0],data[1],data[2],data[4],data[7],data[3],"0"});
						//No open,high,low.close
						if (data.length==11)
							writeLine(fw, new String[]{data[0],data[1],data[2],data[4],data[9],data[3],data[5]});
						//NO OI or Change
						else if (data.length == 13)
							writeLine(fw, new String[]{data[0],data[1],data[6],data[8],data[11],data[7],"0"});
						else if (data.length==15)
							writeLine(fw, new String[]{data[0],data[1],data[6],data[8],data[13],data[7],data[9]});
						else
							logger.error("bad data - data length: "+data.length+"  "+line);
					}									
				}
			}
			doc.close();
			fw.close();
			File latestFilename = new File(props.getProperty("latestOutputFile"));
			copy(f, latestFilename);
			logger.debug("Copied to "+latestFilename);
		}
	}	
		
    public static void main(String[] args) {
    	IceBasisSwapParser parser = new IceBasisSwapParser(args[0]);
	}
	
	private void writeLine(FileWriter fw, String[] line) throws IOException {
		for (int i=0; i<line.length; i++) {
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
	
	
}
