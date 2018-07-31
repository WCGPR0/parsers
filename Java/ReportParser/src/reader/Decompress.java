package reportparser.reader;

import java.io.File;
import java.util.LinkedList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

public class Decompress {

	private static Logger logger = LogManager.getLogger(Decompress.class);
	private LinkedList<String> passwords;
	private String destinationroot;

	public Decompress(Node confignode)
	{
		passwords = new LinkedList<String>();
		NodeList childnodes = confignode.getChildNodes();
		for (int i=0; i<childnodes.getLength(); i++)
		{
			Node childnode = childnodes.item(i);
			if (childnode.getNodeType()==Node.ELEMENT_NODE)
			{
				if (childnode.getNodeName().equals("Password"))
				{
					String password = childnode.getTextContent();
					logger.info("Adding password = {}", password);
					passwords.add(password);
				}
				else if (childnode.getNodeName().equals("DestinationRoot"))
				{
					destinationroot = childnode.getTextContent();
					logger.info("Adding DestinationRoot = {}", destinationroot);
				}
			}
		}
	}

	/* returns extracted files */
	public File[] process(File file)
	{
		String destinationpath = destinationroot + File.separator + System.currentTimeMillis();

		if (passwords.isEmpty())
		{
			//no password(s) specified, add a dummy one so we do the loop below to unzip
			passwords.add("");
		}

		for (String password : passwords)
		{
			try
			{
			    ZipFile zipFile = new ZipFile(file);
			    if (zipFile.isEncrypted())
			    {
			        zipFile.setPassword(password);
			    }
			    zipFile.extractAll(destinationpath);
			    return new File(destinationpath).listFiles();
			}
			catch (ZipException e)
			{
				logger.info("Exception extracting files", e);
			}
		}

		return null;
	}

}
