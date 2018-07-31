package reportparser.reader;

import java.io.File;
import java.io.FileInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class OneChicagoProductsReportReader extends URLReportReader
{
	private static final Logger logger = LogManager.getLogger(OneChicagoProductsReportReader.class);

	@Override
	public void processDownloadedFile(File downloadedfile)
	{
		//this will convert csv to xml and apply transform
		try
		{
			super.processFile(downloadedfile.getName(), new FileInputStream(downloadedfile));
		}
		catch (Exception e)
		{
			logger.error("processDownloadedFile() : Exception thrown converting downloaded file to xml or applyingtransform", e);
		}
	}

	protected void processData(String filename, Document data)
	{
		NodeList report_nodes = data.getElementsByTagName("Report");
		int report_count = report_nodes.getLength();
		logger.info("Found {} Report elements in transformed data", report_count);
		for (int i=0; i<report_count; i++)
		{
			try
		    {
		    	logger.info("Calling fundingSaveOneChicagoProducts()");
		    	String szdata = Util.stringFromDocument(report_nodes.item(i), true);
				Controller.getInstance().getMethods().fundingSaveOneChicagoProducts(szdata, i==0);
			}
		    catch (Exception e)
		    {
				logger.error("processData() : Exception thrown calling fundingSaveOneChicagoProducts()", e);
		    }
		}
	}


}
