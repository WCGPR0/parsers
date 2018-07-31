package reportparser.reader;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public abstract class URLReportReader extends ReportReader implements ExecutableReport
{
	private static Logger logger = Logger.getLogger(URLReportReader.class);
	private static final DateTimeFormatter date_time_format_file_name = DateTimeFormat.forPattern("yyyy-MM-dd_HH-mm-ss");
	private static int nexttempfileid = 1;
	private DynamicString urlstring;
	private String work_folder;
	private int connection_timeout;
	private int read_timeout;
	private int retry_delay;
	private static final int DEFAULT_CONNECTION_TIMEOUT = 30000;
	private static final int DEFAULT_READ_TIMEOUT = 30000;
	private static final int DEFAULT_RETRY_DELAY = 10000;

	@Override
	protected boolean initialise(Element config)
	{
		String url = Util.getNodeValueFromNodeDOM(config, "URL");
		if (StringUtils.isNotBlank(url)) {
			urlstring = new DynamicString(url);
		}
		connection_timeout = Integer.valueOf(Util.getNodeValueFromNodeDOM(config, "ConnectionTimoutMillis", String.valueOf(DEFAULT_CONNECTION_TIMEOUT)));
		read_timeout = Integer.valueOf(Util.getNodeValueFromNodeDOM(config, "ReadTimoutMillis", String.valueOf(DEFAULT_READ_TIMEOUT)));
		retry_delay = Integer.valueOf(Util.getNodeValueFromNodeDOM(config, "RetryDelay", String.valueOf(DEFAULT_RETRY_DELAY)));
		work_folder = Util.getNodeValueFromNodeDOM(config, "WorkFolder");
		return super.initialise(config);
	}



	@Override
	protected void processData(String filename, Document data) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void terminate() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void processFile(String filename, InputStream inputstream) {
		// TODO Auto-generated method stub
		super.processFile(filename, inputstream);
	}

	private File getNextTempFile(String filename)
	{
		DateTime now = new DateTime();
		return new File(work_folder + File.separator + date_time_format_file_name.print(now) + "." + String.format("%03d", nexttempfileid++) + File.separator + filename);
	}

	public abstract void processDownloadedFile(File file);

	@Override
	public void execute(JobDataMap job_data_map) throws JobExecutionException
	{
		synchronized(this) //finish one report before running another
		{
			DynamicString dynamic_string_url;

			String szurl_override = job_data_map.getString("URL");
			if (StringUtils.isNotBlank(szurl_override))
			{
				dynamic_string_url = new DynamicString(szurl_override);
			}
			else
			{
				dynamic_string_url = this.urlstring;
			}

			if (dynamic_string_url!=null)
			{
				String evaluated_urlstring = dynamic_string_url.toString();
				logger.info("About to create URL from string " + evaluated_urlstring);
				try
				{
					URL url = new URL(evaluated_urlstring);
					String[] urlparts = url.getPath().split("/");
					String filename = urlparts[urlparts.length - 1];

					File tempfile = getNextTempFile(filename);
					//copy URL to file
					URLConnection connection = url.openConnection();
					connection.setRequestProperty("User-Agent", "Mozilla/5.0"); //required otherwise we get HTTP 403 (forbidden)
			        connection.setConnectTimeout(connection_timeout);
			        connection.setReadTimeout(read_timeout);
			        InputStream input = connection.getInputStream();
			        FileUtils.copyInputStreamToFile(input, tempfile);
					logger.info("Downloaded data from " + evaluated_urlstring + " and saved as file " + tempfile);
					processDownloadedFile(tempfile);

				}
				catch(Exception e)
				{
					logger.error("Exception thrown downloading report " + evaluated_urlstring, e);
					try
					{
						Thread.sleep(retry_delay);
						JobExecutionException jee = new JobExecutionException(e);
						jee.refireImmediately();
						throw jee;
					}
					catch (InterruptedException e1)
					{
						logger.error("InterruptedException thrown whilst sleeping before retrying URLReport", e);
					}
				}
			}
			else
			{
				logger.error("Unable to execute report as URL is blank (checked both the URL from job and default URL in config)");
			}
		}
	}
}
