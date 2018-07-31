package reportparser.reader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class NodalExchangeEODFuturesReportReader extends URLReportReader
{
	private static Logger logger = Logger.getLogger(NodalExchangeEODFuturesReportReader.class);
	private static final String SAVE_DATA_REQUEST = "AR.DAILY.SETTLEMENT.DATA.SAVE";
	private static final String SOURCE = "NODAL";
	private static final DateTimeFormatter report_date_formatter = DateTimeFormat.forPattern("dd-MMM-yyyy");
	private static final DateTimeFormatter expiry_date_formatter = DateTimeFormat.forPattern("MM/dd/yy");

	@Override
	public void processDownloadedFile(File downloadedfile)
	{
		//extract text from the pdf file
		File tempfile2;
		try
		{
			PDFParser parser = new PDFParser(new FileInputStream(downloadedfile));
			parser.parse();
			COSDocument cosDoc = parser.getDocument();
	        PDFTextStripper pdfStripper = new PDFTextStripper();
	        PDDocument pdDoc = new PDDocument(cosDoc);
	        String parsedText = pdfStripper.getText(pdDoc);
	        tempfile2 = new File(downloadedfile.getAbsolutePath() + ".txt");
			FileUtils.writeStringToFile(tempfile2, parsedText);
		}
		catch(Exception e)
		{
			logger.error("Exception thrown using PDF parser to extract text from " + downloadedfile.getAbsolutePath() + " (or error saving extracted text to file)", e);
			return;
		}

		String report_date = report_date_formatter.print(new DateTime()); //current date

		//read text file line by line and process any line that consists of 10 space separate data items
		//e.g. AAA 5/31/16 30.0200 -0.5600 1520 0 0 0 0 0
		try (BufferedReader br = new BufferedReader(new FileReader(tempfile2)))
		{
			Methods methods = Controller.getInstance().getMethods();
		    String line;
		    while ((line = br.readLine()) != null)
		    {
		    	String[] lineparts = line.split(" ");
		    	if (lineparts.length==10)
		    	{
		    		logger.debug("Processing line " + line);
		    		try
		    		{
			    		String contract_code = lineparts[0];
			    		String szexpiry_date = lineparts[1];
			    		DateTime expiry_date = expiry_date_formatter.parseDateTime(szexpiry_date);
			    		String settlement_price = lineparts[2];
			    		String price_change = lineparts[3];
			    		String open_interest = lineparts[4];
			    		String open_interest_change = lineparts[5];
			    		String total_volume = lineparts[6];
			    		String block_volume = lineparts[9];

			    		String[] request_parameters =
			    			{	report_date,
			    				SOURCE,
			    				contract_code,
			    				String.valueOf(expiry_date.getMonthOfYear()),
			    				String.valueOf(expiry_date.getYear()),
			    				String.valueOf(expiry_date.getDayOfMonth()),
			    				settlement_price,
			    				total_volume,
			    				open_interest,
			    				block_volume,
			    				price_change,
			    				open_interest_change	};

			    		try
			    		{
			    			methods.execute(SAVE_DATA_REQUEST, request_parameters);
			    		}
			    		catch(Exception e)
			    		{
			    			logger.error("Exception calling " + SAVE_DATA_REQUEST + " with parameters " + Arrays.toString(request_parameters), e);
			    		}
		    		}
		    		catch(Exception e)
		    		{
		    			logger.error("Exception thrown extracting data from line : " + line + " of file " + tempfile2.getAbsolutePath(), e);
		    		}
		    	}
		    }
		}
		catch(Exception e)
	    {
	    	logger.error("Exception thrown processing extracted text file " + tempfile2.getAbsolutePath(), e);
	    }
	}



}
