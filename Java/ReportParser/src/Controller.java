package reportparser;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class Controller extends ServiceBase
{
	private static Logger logger = Logger.getLogger(Controller.class);
	private static Controller instance;
	private ReportReaders reportreaders;
	private String proxy_host;
	private static final int DEFAULT_PROXY_PORT = 8080;
	private int proxy_port = DEFAULT_PROXY_PORT;


	public static Controller getInstance()
	{
		if (instance == null)
			instance = new Controller();
		return instance;
	}

	private Controller() {
		super();
	}


	public static void main(String[] args) throws Exception
	{
		instance = getInstance();
		instance.run(args);
	}

	public ServiceExecutor getServiceExecutor()
	{
		return (ServiceExecutor)m_serviceExecutorBase;
	}

	protected ReportReaders getReportReaders()
	{
		return this.reportreaders;
	}

	@Override
	protected void startDependentProcesses() {
		//just for testing
		/*ReportReaderJob job = new ReportReaderJob();
		job.setJobID("Eurex-Full");
		try {
			job.execute(null);
		} catch (JobExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/

	}

	@Override
	protected boolean start()
	{
		getConfig().initialiseTransforms("/Configuration/Application/Transforms");
		this.proxy_host = getConfig().getValue("/Configuration/Application/ProxyServer/Host");
		String szproxy_port = getConfig().getValue("/Configuration/Application/ProxyServer/Port");
		if (StringUtils.isNotBlank(szproxy_port))
		{
			try
			{
				this.proxy_port = Integer.parseInt(szproxy_port);
			}
			catch (Exception e)
			{
				logger.error(String.format("Exception converting ProxyPort value [%s] to an int. Will use default value [%s]", szproxy_port, DEFAULT_PROXY_PORT), e);
				this.proxy_port = DEFAULT_PROXY_PORT;
			}
		}
		reportreaders = new ReportReaders();
		if (!reportreaders.initialise())
		{
			return false;
		}
		loadHistoricReports();
		return true;
	}

	@Override
	protected void shutdown()
	{
		if (reportreaders!=null) {
			reportreaders.terminate();
		}
	}

	@Override
	protected void createServiceExecutor()
	{
		this.m_serviceExecutorBase = new ServiceExecutor(this);
	}

	private void loadHistoricReports()
	{
		String url_transform = "DTCC.Daily.Download.URL";
		String data_transform = "DTCC.Daily.Download.Data";
		String save_request = "DTCC.DAILY.DOWNLOAD.SAVE";
		DateTimeFormatter date_time_format = DateTimeFormat.forPattern("yyyy-MM-dd");

		NodeList datenodes = getConfig().getNodeList("/Configuration/Application/DTCC/HistoricReports/Date");
		for (int i=0; i<datenodes.getLength(); i++)
		{
			String date = ((Element)datenodes.item(i)).getTextContent();
			DateTime effective_date_for_report = date_time_format.parseDateTime(date);
			DTCCReport.runReport(url_transform, data_transform, save_request, effective_date_for_report);
		}
	}

	public String getProxyHost()
	{
		return this.proxy_host;
	}

	public int getProxyPort()
	{
		return this.proxy_port;
	}

}
