package reportparser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DTCCReport extends Report {

	private static Logger logger = Logger.getLogger(DTCCReport.class);
	public static Logger maillogger = Logger.getLogger("MAIL");
	private static final DateTimeFormatter date_time_format = DateTimeFormat.forPattern("dd-MM-yyyy HH:mm:ss");
	protected static final DateTimeFormatter date_time_format_file_name = DateTimeFormat.forPattern("yyyy-MM-dd_HH-mm-ss");
	protected static final int DEFAULT_ITEMS_PER_SAVE_REQUEST = 50;
	protected static int items_per_save_request;

	@Override
	public void execute(JobExecutionContext arg0) throws JobExecutionException
	{
		runReport(url_transform_name, data_transform_name, save_request, new DateTime());
	}

	public static void runReport(String _url_transform_name, String _data_transform_name, String _save_request, DateTime _effective_date)
	{
		logger.info(String.format("About to execute Report with URLTransform [%s], DataTransform [%s], SaveRequest [%s], EffectiveDate [%s]", _url_transform_name, _data_transform_name, _save_request, date_time_format.print(_effective_date)));
		String output_folder_root = Controller.getInstance().getConfig().getValue("/Configuration/Application/OutputFolder");
		String output_path = output_folder_root + File.separator + date_time_format_file_name.print(_effective_date);
		File output_folder = new File(output_path);
		output_folder.mkdirs();

		items_per_save_request = Integer.parseInt(Controller.getInstance().getConfig().getValue("/Configuration/Application/DTCC/ItemsPerSaveRequest", String.valueOf(DEFAULT_ITEMS_PER_SAVE_REQUEST)));

		CachedTransform url_transform = Controller.getInstance().getConfig().getTransformTemplate(_url_transform_name);
		if (url_transform==null)
		{
			String errmsg = "Unable to execute DTCC Report. Transform " + _url_transform_name + " not found.";
			logger.error(errmsg);
			maillogger.error(errmsg);
			return;
		}
		CachedTransform data_transform = Controller.getInstance().getConfig().getTransformTemplate(_data_transform_name);
		if (data_transform==null)
		{
			String errmsg = "Unable to execute DTCC Report. Transform " + _data_transform_name + " not found.";
			logger.error(errmsg);
			maillogger.error(errmsg);
			return;
		}
		if (StringUtils.isBlank(_save_request))
		{
			String errmsg = "Unable to execute DTCC Report. SaveRequest is not specified.";
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
			URI uri = new URI(url_string);
			File zipfile = new File(output_folder, "report.zip");
			downloadReport(uri, zipfile);
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
						File csvxmlfile = new File(output_folder, "report_csv_file_" + index + ".xml");
						Util.documentFromCSVReader(new FileReader(extracted_file), new FileWriter(csvxmlfile));

						//apply transform
						long start = System.currentTimeMillis();
						String transformed_report = Util.performTransformToString(data_transform, csvxmlfile, true, null);
						logger.info("Time taken to transform = " + (System.currentTimeMillis() - start) + "ms");
						File transformedoutputfile = new File(output_folder, "report_transformed_file_" + index + ".xml");
						FileUtils.writeStringToFile(transformedoutputfile, transformed_report);

						//pass to request
						String errors = saveReport(_save_request, transformed_report, index);
						File errorsfile = new File(output_folder, "errors_file_" + index + ".xml");
						FileUtils.writeStringToFile(errorsfile, errors);

						logger.info("Received following errors when saving report " + errors);

					}
				}
			}

		}
		catch(Exception e)
		{
			String errmsg = "Exception thrown processing DTCC report";
			logger.error(errmsg, e);
			maillogger.error(errmsg, e);
		}
	}

	protected static String saveReport(String request, String report, int index)
	{
		Document reportdoc = Util.documentFromString(report);
		Document errorsdoc = Util.documentFromString("<errors/>");
		Element errorselem = errorsdoc.getDocumentElement();
		NodeList tradenodes = reportdoc.getElementsByTagName("trade");
		int i = 0;
		while (i < tradenodes.getLength())
		{
			int nextdivider = i + items_per_save_request;
			Document reportpartdoc = Util.documentFromString("<trades/>");
			Element reportpartelem = reportpartdoc.getDocumentElement();
			while (i < nextdivider && i<tradenodes.getLength())
			{
				Node importednode = reportpartdoc.importNode(tradenodes.item(i), true);
				reportpartelem.appendChild(importednode);
				i++;
			}
			Element errors = saveReportPart(request, reportpartelem, index);
			if (errors!=null)
			{
				NodeList errornodes = errors.getElementsByTagName("error");
				for (int j=0; j<errornodes.getLength(); j++)
				{
					Node importederror = errorsdoc.importNode(errornodes.item(j), true);
					errorselem.appendChild(importederror);
				}
			}
		}
		return Util.stringFromDocument(errorsdoc);
	}


	private static Element saveReportPart(String request, Element reportpart, int index)
	{
		try
		{
			String xmltemplate = 	"<Request.Params>" +
										"<Request.Param>%s</Request.Param>" +
									"</Request.Params>";
			String xmlparam = String.format(xmltemplate, Util.stringFromDocument(reportpart, true));
			String errors = Controller.getInstance().getMethods().execute(request, xmlparam);
			Document errorsdoc = Util.documentFromString(errors);
			return errorsdoc.getDocumentElement();
		}
		catch (Exception e)
		{
			logger.error("Exception thrown executing SaveRequest for Report (or parsing response)", e);
			return null;
		}

	}


	//download report. save to disk and return reference to the saved file
	protected static void downloadReport(URI uri, File out)
	{
		FileOutputStream fos = null;
		try
		{
			fos = new FileOutputStream(out);

			final HttpParams httpParams = new BasicHttpParams();
		    HttpConnectionParams.setConnectionTimeout(httpParams, 30000);
			HttpClient client = new DefaultHttpClient(httpParams);

			String proxy_host = Controller.getInstance().getProxyHost();
			int proxy_port = Controller.getInstance().getProxyPort();
			if (StringUtils.isNotBlank(proxy_host))
			{
				logger.info(String.format("downloadReport() : Setting proxy [%s:%s] for connection to %s", proxy_host, proxy_port, uri));
				HttpHost proxy = new HttpHost(proxy_host, proxy_port);
				client.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
			}
			HttpGet get = new HttpGet(uri);
			HttpResponse httpResponse = client.execute(get);
			HttpEntity responseEntity = httpResponse.getEntity();
			responseEntity.writeTo(fos);
		}
		catch(Exception e)
		{
			String errmsg = String.format("Exception thrown downloading report [%s]", uri);
			logger.error(errmsg, e);
			maillogger.error(errmsg, e);
		}
		finally
		{
			if (fos!=null)
			{
				try {
					fos.close();
				} catch (IOException e) {
					logger.error("Exception closing fos", e);
				}
			}
		}
	}
}
