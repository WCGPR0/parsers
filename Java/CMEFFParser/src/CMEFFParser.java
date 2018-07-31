import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;


public class CMEFFParser {
	private static Logger logger = Logger.getLogger(CMEFFParser.class);	
	private Properties props = new Properties();
	private static String TOTAL = "TOTAL";
	private boolean newDay = false;
	protected String theUrl;
	protected String headerFile;
	protected String outputFile;
	protected String outputWebFile;
	protected String previousFile;
	protected String codeRegex;
	
	public static void main(String[] args) {
		CMEFFParser parser = new CMEFFParser(args[0]);
		if (args.length>1)
			parser.setNewDay(Boolean.parseBoolean(args[1]));
		parser.parse();
	}

	public CMEFFParser(String propsFile) {
		try {
			props.load(new FileInputStream(propsFile));
			theUrl = props.getProperty("url");
			headerFile = props.getProperty("headerFile");
			outputFile = props.getProperty("outputFile");
			outputWebFile = props.getProperty("outputWebFile");
			previousFile = props.getProperty("previousFile");
			codeRegex = props.getProperty("codeRegex");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void parse() {		
		try {			
			/*List<String> titleList = new ArrayList<String>();		
			try {
				BufferedReader in = new BufferedReader(new FileReader(headerFile));
				String str;
				while ((str = in.readLine()) != null) {
					titleList.add(str);
				}
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}*/

			//BufferedReader bufferedReader = new BufferedReader(new FileReader("C://TEMP//cmeInput.txt"));

			// create a url object
			URL url = new URL(theUrl);
			logger.debug("Parsing "+theUrl);
			// create a urlconnection object
			URLConnection urlConnection = url.openConnection();
			// wrap the urlconnection in a bufferedreader
			BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(urlConnection.getInputStream()));

			String line;
			String prevLine="";
			String[] lineData;
			StringBuffer sb = new StringBuffer();
			sb.append(bufferedReader.readLine()).append("\n");
			sb.append("MONTH").append(",").append("VOL").append(",").append("OPEN INT").append("\n");			
			// read from the urlconnection via the bufferedreader
			while ((line = bufferedReader.readLine()) != null) {
				String code = line.split(" ")[0];
				if (code.matches(codeRegex)) {
					sb.append("\n").append(line).append("\n");
					while ((line = bufferedReader.readLine()) != null && !line.startsWith(TOTAL) && line.length()>=75) {
						String month = line.substring(0, 6);
						String vol = line.substring(65, 75);
						String priorOpenInt = (line.length()>109) ? line.substring(102, 110) : "";
						sb.append(month.trim()).append(",").append(vol.trim()).append(",").append(priorOpenInt.trim()).append("\n");
					}
				}
				prevLine=line;
			}
			sb.append("\n").append("END,,END");
			bufferedReader.close();
			logger.debug("done parsing");
			try {
				if (newDay) {
					copy(new File(outputFile), new File(previousFile));
					logger.debug("Copied "+outputFile+" to "+previousFile);
				}
				File outFile = new File(outputFile);
				BufferedWriter out = new BufferedWriter(new FileWriter(outFile));
				out.write(sb.toString());
				out.close();
				logger.debug("Wrote "+outputFile);
				File outWebFile = new File(outputWebFile);							
				copy(outFile, outWebFile);
				logger.debug("Copied to "+outWebFile);				
			} catch (IOException e) {
				logger.error(e);
				e.printStackTrace();
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
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
	
	private void setNewDay(boolean newDay) {
		this.newDay = newDay;
	}
	
}
