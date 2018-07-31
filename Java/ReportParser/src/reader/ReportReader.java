package reportparser.reader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public abstract class ReportReader {

	private static Logger logger = Logger.getLogger(ReportReader.class);
	private CachedTransform transform;
	protected DynamicString filename_filter_pattern;
	protected char delimiter;
	protected int startline;
	protected Decompress decompress;
	protected String id;

	protected abstract void processData(String filename, Document data);
	protected abstract void terminate();

	protected boolean initialise(Element config)
	{
		id = Util.getNodeValueFromNodeDOM(config, "ID", UUID.randomUUID().toString());//create a UUID if ID not provided
		String sztransform = Util.getNodeValueFromNode(config, "Transform");
		if (StringUtils.isNotBlank(sztransform))
		{
			transform = Controller.getInstance().getConfig().getTransformTemplate(sztransform);
			if (transform==null) {
				logger.warn("No transform found for ReportReader. Transform name = " + sztransform);
			}
		}
		String szfilename_filter_pattern = Util.getNodeValueFromNodeDOM(config, "FilenameRegularExpression");
		delimiter = Util.getNodeValueFromNodeDOM(config, "TextDelimiter", String.valueOf(Util.DEFAULTCSVSEPARATOR)).charAt(0);
		startline = Integer.parseInt(Util.getNodeValueFromNodeDOM(config, "StartLine", "0"));

		if (StringUtils.isNotBlank(szfilename_filter_pattern)) {
			//filename_filter_pattern = Pattern.compile(szfilename_filter_pattern);
			filename_filter_pattern = new DynamicString(szfilename_filter_pattern);
		}

		Node descompressconfig = Util.getNodeFromNode(config, "Decompress");
		if (descompressconfig!=null)
		{
			this.decompress = new Decompress(descompressconfig);
		}

		return true;

	}

	//helper function
	public static boolean checkFilename(Pattern filename_filter_pattern, String filename)
	{
		if (filename!=null)
		{
			if (filename_filter_pattern==null)
			{
				return true;
			}
			else
			{
				Matcher matcher = filename_filter_pattern.matcher(filename);
				return matcher.matches();
			}
		}
		else
		{
			return false;
		}
	}

	protected Document applyTransform(Document data, Level loglevel, HashMap<String, Object> params)
	{
		if (transform!=null)
		{
			logger.debug("Applying transform to XML file");
			data = Util.performTransform(transform, data, params);
		}
		if (loglevel!=null && loglevel.isGreaterOrEqual(logger.getEffectiveLevel())) {
			logger.log(loglevel, "XML after transforming = " + Util.stringFromDocument(data));
		}

		return data;
	}

	protected void processFile(String filename, InputStream inputstream)
	{
		logger.debug("Converting file to XML");
		Reader reader = new InputStreamReader(inputstream);
		Document data_document = Util.documentFromCSVReader(reader, delimiter, Util.DEFAULTCSVQUOTECHAR, startline);
		if (logger.getEffectiveLevel()==Level.TRACE) {
			logger.trace("XML version of file " + filename + " = " + Util.stringFromDocument(data_document));
		}
		data_document = applyTransform(data_document, Level.TRACE, null);
		processData(filename, data_document);
	}

	public String getID()
	{
		return id;
	}

}
