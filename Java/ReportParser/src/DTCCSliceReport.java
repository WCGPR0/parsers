package reportparser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@DisallowConcurrentExecution
public class DTCCSliceReport extends DTCCReport {
	private static Logger logger = Logger.getLogger(DTCCSliceReport.class);
	private static final DateTimeFormatter date_time_format = DateTimeFormat.forPattern("dd-MM-yyyy HH:mm:ss").withZoneUTC();
	protected static final DateTimeFormatter date_format_file_name = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();
	private static final String Last_Read_file_Name = "dtcc.slice.reports.last.read.txt";
	private static String _downloadFileNames_transform_name;

	@Override
	public void execute(JobExecutionContext arg0) throws JobExecutionException
	{
		runReport(url_transform_name, data_transform_name, save_request, new DateTime());
	}

	public void setFilesNamesTransform(String value)
	{
		_downloadFileNames_transform_name = value;
	}

	public static void runReport(String _url_transform_name, String _data_transform_name, String _save_request, DateTime _effective_date)
	{
		logger.info(String.format("About to execute Report with URLTransform [%s], DataTransform [%s], SaveRequest [%s], EffectiveDate(UTC) [%s]", _url_transform_name, _data_transform_name, _save_request, date_time_format.print(_effective_date)));
		String output_folder_root = Controller.getInstance().getConfig().getValue("/Configuration/Application/OutputFolder");
		String output_path = output_folder_root + File.separator + date_format_file_name.print(_effective_date);
		File output_folder = new File(output_path);
		output_folder.mkdirs();

		items_per_save_request = Integer.parseInt(Controller.getInstance().getConfig().getValue("/Configuration/Application/DTCC/ItemsPerSaveRequest", String.valueOf(DEFAULT_ITEMS_PER_SAVE_REQUEST)));

		CachedTransform url_transform = Controller.getInstance().getConfig().getTransformTemplate(_url_transform_name);
		if (url_transform==null)
		{
			String errmsg = "Unable to execute DTCC Slice Report. Transform " + _url_transform_name + " not found.";
			logger.error(errmsg);
			maillogger.error(errmsg);
			return;
		}
		CachedTransform data_transform = Controller.getInstance().getConfig().getTransformTemplate(_data_transform_name);
		if (data_transform==null)
		{
			String errmsg = "Unable to execute DTCC Slice Report. Transform " + _data_transform_name + " not found.";
			logger.error(errmsg);
			maillogger.error(errmsg);
			return;
		}
		if (StringUtils.isBlank(_save_request))
		{
			String errmsg = "Unable to execute DTCC Slice Report. SaveRequest is not specified.";
			logger.error(errmsg);
			maillogger.error(errmsg);
			return;
		}
		CachedTransform downloadFileNames_transform = Controller.getInstance().getConfig().getTransformTemplate(_downloadFileNames_transform_name);
		if (downloadFileNames_transform == null)
		{
			String errmsg = "Unable to execute DTCC Slice Report. Transform " + _downloadFileNames_transform_name + " is not specified.";
			logger.error(errmsg);
			maillogger.error(errmsg);
			return;
		}

		//get URL for report
		HashMap<String, Object> url_request_params = new HashMap<String, Object>();
		String date_time_current = date_time_format.print(_effective_date);
		String date_time_yesterday = date_time_format.print(_effective_date.minusDays(1));
		logger.info("date.current string passed to url transform = " + date_time_current);
		logger.info("date.yesterday string passed to url transform = " + date_time_yesterday);
		url_request_params.put("date.current", date_time_current);
		url_request_params.put("date.yesterday", date_time_yesterday);
		String url_string = Util.performTransformToString(url_transform, Util.documentFromString("<dummy/>"), true, url_request_params);
		logger.info("Result of running url transform = " + url_string);

		//get report
		try
		{
			String last_read_file = "";

			try
			{
				logger.debug("reading file = " + output_folder_root + File.separator + Last_Read_file_Name);
				last_read_file = FileUtils.readFileToString(new File(output_folder_root + File.separator + Last_Read_file_Name));
				logger.debug("reading file = " + output_folder_root + File.separator + Last_Read_file_Name + ". Result = " + last_read_file);
			}
			catch (IOException ioe)
			{
				logger.debug("File " + output_folder_root + File.separator + Last_Read_file_Name + " is not found. Will parse all files from the web.");
			}

			URI uri = new URI(url_string);

			//DefaultHttpAccessorFactory factory = new DefaultHttpAccessorFactory();
			//HttpPost httpPost = factory.getPostMethod();
				//HttpEntity requestEntity = new StringEntity(payload);
				//httpPost.setEntity(requestEntity);
			//HttpGet httpget = new HttpGet(uri);
			//HttpResponse httpResponse = factory.getHttpClient().execute(httpget);
			//HttpEntity responseEntity = httpResponse.getEntity();
			//String response = EntityUtils.toString(responseEntity);

			File htmlfile = new File(output_folder, "index.html");
			downloadReport(uri, htmlfile);

			String proxy_host = Controller.getInstance().getProxyHost();
			int proxy_port = Controller.getInstance().getProxyPort();
			HttpHost proxy = null;
			if (StringUtils.isNotBlank(proxy_host))
			{
				logger.info(String.format("downloadReport() : Setting proxy [%s:%s] for connection to %s", proxy_host, proxy_port, url_string));
				proxy = new HttpHost(proxy_host, proxy_port);
			}

			Document result = Util.performTransform(downloadFileNames_transform, htmlfile, null, true);
			logger.debug("transform result of " + downloadFileNames_transform);
			logger.debug(Util.stringFromDocument(result));

			LinkedList<FileKey> list = readDocumentToList(result);

			if (list.size() == 0)
			{
				logger.info("No links found. No parsing any zip files.");
				return;
			}
			logger.debug("size of entries = " + list.size());

			int key = getLastReadIndex(list, last_read_file);
			logger.debug("key is " + key);

			for (int i = key - 1; i >= 0; i--)
			{
				FileKey filekey = list.get(i);
				File zipfile = new File(output_folder, filekey.key);
				downloadReport(new URI(filekey.link), zipfile);
				List<File> extracted_files = Report.extractZip(zipfile);
				if (extracted_files==null || extracted_files.size()==0)
				{
					String errmsg = String.format("Zip [%s] did not contain any files", zipfile);
					logger.error(errmsg);
					maillogger.error(errmsg);
				}
				else
				{
					int index = 0;
					for (File extracted_file : extracted_files)
					{
						if (extracted_file!=null)
						{
							index++;
							//covert csv file into an xml format
							String file_name = extracted_file.getName().indexOf('.') > 0 ? extracted_file.getName().substring(0, extracted_file.getName().indexOf('.')) : extracted_file.getName();
							File csvxmlfile = new File(output_folder, file_name + "_" + index + ".xml");
							Util.documentFromCSVReader(new FileReader(extracted_file), new FileWriter(csvxmlfile));

							//apply transform

							long start = System.currentTimeMillis();
							String transformed_report = Util.performTransformToString(data_transform, csvxmlfile, true, null);
							logger.info("Time taken to transform = " + (System.currentTimeMillis() - start) + "ms");
							File transformedoutputfile = new File(output_folder, file_name + "_report_transformed_file_" + index + ".xml");
							FileUtils.writeStringToFile(transformedoutputfile, transformed_report);

							//pass to request
							String errors = saveReport(_save_request, transformed_report, index);
							File errorsfile = new File(output_folder, file_name + "_errors_file_" + index + ".xml");
							FileUtils.writeStringToFile(errorsfile, errors);

							logger.info("Received following errors when saving report " + errors);

						}
					}
				}
				last_read_file = filekey.key;
			}
			FileUtils.write(new File(output_folder_root + File.separator + Last_Read_file_Name), last_read_file);

		}
		catch(Exception e)
		{
			String errmsg = "Exception thrown processing DTCC report";
			logger.error(errmsg, e);
			maillogger.error(errmsg, e);
		}
	}

	private static LinkedList<FileKey> readDocumentToList(Document document)
	{
		NodeList nodelist = Util.getNodeListFromNode(document, "//File");
		LinkedList<FileKey> linkedlist = new LinkedList<FileKey>();
		for (int i = 0; i < nodelist.getLength(); i++)
		{
			Node node = nodelist.item(i);
			FileKey filekey  = new FileKey();
			filekey.key = Util.getNodeValueFromNode(node, "Key");
			filekey.link = Util.getNodeValueFromNode(node, "Link");
			linkedlist.add(filekey);
		}
		return linkedlist;
	}

	private static int getLastReadIndex(LinkedList<FileKey> list, String toFindKey)
	{
		for (int i = list.size() - 1; i >= 0; i--)
		{
			FileKey filekey = list.get(i);
			if (StringUtils.equals(filekey.key, toFindKey))
			{
				return i;
			}
		}
		return list.size();
	}

	private static class FileKey
	{
		private String key;
		private String link;
	}
}
