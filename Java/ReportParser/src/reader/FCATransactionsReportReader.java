package reportparser.reader;

import java.io.File;
import java.io.FileReader;

import org.apache.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class FCATransactionsReportReader extends EmailReader
{
	private static Logger logger = LogManager.getLogger(FCATransactionsReportReader.class);

	@Override
	public boolean initialise(Element config) {
		return super.initialise(config);
	}

	@Override
	protected void processData(File zipfile)
	{
		//attempt to extract file (will be password protected)
		if (decompress!=null)
		{
			File[] decompressedfiles = decompress.process(zipfile);
			//bizarrely the zip file emailed by FCA contains yet another zip file
			for (File file : decompressedfiles)
			{
				File[] innerfiles = decompress.process(file);
				for (File innerfile : innerfiles)
				{
					//read files;
					logger.info("File {}", innerfile);
					try
					{
						FileReader reader = new FileReader(innerfile);
						logger.debug("Converting file to XML");
						Document data_document = Util.documentFromCSVReader(reader, delimiter, Util.DEFAULTCSVQUOTECHAR, startline);
						if (logger.isDebugEnabled()) {
							logger.debug("XML version of file " + file + " = " + Util.stringFromDocument(data_document));
						}
						data_document = applyTransform(data_document, Level.DEBUG, null);
					}
					catch(Exception e)
					{
						logger.error("Exception thrown processing File {}", innerfile, e);
					}
				}
			}
		}
	}



}
