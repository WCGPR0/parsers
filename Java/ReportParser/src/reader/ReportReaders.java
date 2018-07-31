package reportparser.reader;

import java.util.HashMap;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ReportReaders extends HashMap<String, ReportReader>
{
	private static final long serialVersionUID = 3682511390329762238L;
	private static Logger logger = Logger.getLogger(ReportReaders.class);

	public boolean initialise()
	{
		Element config = (Element)Controller.getInstance().getConfig().getNode("/Configuration/Application/ReportReaders");
		if (config!=null)
		{
			NodeList reader_nodes = config.getElementsByTagName("ReportReader");
			for (int i=0; i<reader_nodes.getLength(); i++)
			{
				try
				{
					Element reader_elem = (Element)reader_nodes.item(i);
					String readerclassname = reader_elem.getAttribute("class");
					try {
						ReportReader reader = (ReportReader)Class.forName(readerclassname).newInstance();
						if (!reader.initialise(reader_elem))
						{
							logger.error("Problem initialising ReportReader. Aborting startup");
							return false;
						}
						this.put(reader.getID(), reader);
					}
					catch (Exception e)
					{
						logger.error("Exception thrown creating ReportReader of type : " + readerclassname + ". Aborting startup", e);
						return false;
					}
				}
				catch(Exception e)
				{
					logger.error("Exception thrown creating ReportReader", e);
				}
			}
		}
		else
		{
			logger.info("No ReportReader defined in config file");
		}
		return true;
	}

	public void terminate()
	{
		for (ReportReader file_reader : this.values())
		{
			file_reader.terminate();
		}
	}
}
