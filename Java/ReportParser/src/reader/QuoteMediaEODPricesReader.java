package reportparser.reader;

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;

public class QuoteMediaEODPricesReader extends URLReportReader {

	private static Logger logger = Logger.getLogger(QuoteMediaEODPricesReader.class);

	@Override
	public void processDownloadedFile(File file)
	{
		try
		{
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document data = documentBuilder.parse(file);

			//apply transform to file and send results to market data hub
			Document transformed_data = applyTransform(data, Level.INFO, null);
			Controller.getInstance().getMethods().extdataAddData(Util.stringFromDocument(transformed_data));
		}
		catch(Exception e)
		{
			logger.error("Exception thrown processing file downloaded from QuoteMedia", e);
		}
	}

}
