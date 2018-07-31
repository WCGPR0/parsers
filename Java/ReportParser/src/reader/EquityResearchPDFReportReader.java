package reportparser.reader;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Element;

public class EquityResearchPDFReportReader extends EmailReader {

	private static final Logger logger = LogManager.getLogger(EquityResearchPDFReportReader.class);
	private static final String WEBPATHSEPARATOR = "/";
	private static final String WEBURLDELIMITER = "://";
	private static final String TEMP_WEBPATHSEPARATOR = "***FileSeparator***";
	private static final String TEMP_WEBURLDELIMITER = "***HttpSeparator***";


	private String save_request;
	private String web_url_prefix;

	@Override
	public boolean initialise(Element config)
	{
		save_request = Util.getNodeValueFromNodeDOM(config, "SaveRequest");
		web_url_prefix = Util.getNodeValueFromNodeDOM(config, "WebURLPrefix");
		return super.initialise(config);
	}


	@Override
	protected void processData(File file)
	{
		logger.info("processData() called with file : {}", file);
		try
		{
			//convert filename to web url
			String szweburl = getWebURL(file);
			logger.info("About to execute {} to save {}", save_request, file);

			Controller.getInstance().getMethods().execute(save_request, new String[] {szweburl});
		}
		catch (Exception e)
		{
			logger.error("Exception thrown trying to execute request " + save_request + " to save file " + file, e);
		}
	}

	private String getWebURL(File file) throws IOException, URISyntaxException
	{
		String fileroot = new File(super.attachment_save_folder).getCanonicalPath();
		String filename = file.getCanonicalPath();
		String remainder = StringUtils.substringAfter(filename, fileroot);

		remainder = remainder.replace(File.separator, TEMP_WEBPATHSEPARATOR);
		String tempweburl = this.web_url_prefix.replace(WEBURLDELIMITER, TEMP_WEBURLDELIMITER).replace(WEBPATHSEPARATOR, TEMP_WEBPATHSEPARATOR);
		String weburl = tempweburl + remainder;
		String encodedweburl = URLEncoder.encode(weburl, "UTF-8").replace("+", "%20");//note we have to additionally replace spaces (which have beeen changed to +) with ascii %20

		String finalweburl = encodedweburl.replace(TEMP_WEBPATHSEPARATOR, WEBPATHSEPARATOR).replace(TEMP_WEBURLDELIMITER, WEBURLDELIMITER);
		logger.info("WebURL for file {} = {}", file, finalweburl);

		return finalweburl;
	}
}
