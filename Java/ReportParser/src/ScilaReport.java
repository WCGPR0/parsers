package reportparser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class ScilaReport extends Report
{

	public List<Interval> m_intervals = new ArrayList<Interval>();

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException
	{
		try
		{
			//LocalDateTime start = LocalDateTime.of(2015, Month.JANUARY, 01, 0, 0);
			DateTime end = DateTime.now().withTimeAtStartOfDay();
			//LocalDateTime end = start.plusYears(1);//start.plusDays(200);
			DateTime start = end.minusDays(1);

			logger.info(String.format("About to generate reports : from [%s] to [%s]", start, end));
			logger.info(String.format("Error Email Config : [%s] [%s] [%s] [%s]", isEmailErrorsEnabled(),
					getEmailHost(), getEmailErrorsSendFrom(), getEmailErrorsSendTo()));

			List<String> generatedFilenames = new ArrayList<String>();
			generateReport(start, end, generatedFilenames);
			logger.info("Generate reports complete");
			if (!m_sftpDisabled)
			{
				logger.info("About to transfer reports");
				transferReport(generatedFilenames);
			}
			else
			{
				logger.info("SFTP Report transfer disabled");
			}
			logger.info("Transfer reports complete");
		}
		catch (Exception e)
		{
			logger.error("Exception thrown executing job", e);

			if (isEmailErrorsEnabled())
			{
				StringWriter writer = new StringWriter();
				PrintWriter printWriter = new PrintWriter(writer);
				e.printStackTrace(printWriter);
				printWriter.flush();

				String stackTrace = writer.toString();

				EmailHelper.sendEmail(getEmailHost(), getEmailErrorsSendFrom(), getEmailErrorsSendTo(),
						"Scila report generation error", stackTrace);
			}

			throw new JobExecutionException(e);
		}
	}

	public void initialise()
	{
		setOutputDirectory(Controller.getInstance().getConfig().getValue("/Configuration/Application/OutputFolder"));
	}

	public void setMethods(Methods methods)
	{
		m_methods = methods;
	}

	public Methods getMethods() throws Exception
	{

		if (m_methods != null)
		{
			return m_methods;
		}
		return Controller.getInstance().getMethods();
	}

	public void transferReport(List<String> files) throws Exception
	{
		try
		{
			connect();
			ChannelSftp sftp = (ChannelSftp) m_channel;
			if (m_channel != null && StringUtils.isNotBlank(m_sftpPath))
			{
				sftp.cd(m_sftpPath);
			}

			logger.debug("Uploading " + files.size() + " files");

			for (String filename : files)
			{
				logger.debug("Uploading : " + filename);
				File file = new File(filename);
				//				String fileText = FileUtils.readFileToString(file);
				//				sftp.put(new StringInputStream(fileText), file.getName());
				sftp.put(filename, file.getName());
				logger.debug("Uploading : " + filename + " complete.");
			}

			logger.debug("All files uploaded");
		}
		finally
		{
			disconnect();
		}
	}

	public void generateReport(DateTime start, DateTime end, List<String> generatedFilenames) throws Exception
	{
		buildIntervals(start, end);
		if (StringUtils.isBlank(getNonStandardRequest()))
		{
			createStandardReport(generatedFilenames);
		}
		else
		{
			createNoneStandardReport(generatedFilenames);
		}

	}

	private void createNoneStandardReport(List<String> generatedFilenames) throws Exception
	{
		Path outputDirectoryPath = Paths.get(String.format("%s/outputFiles", getOutputDirectory()));
		if (Files.exists(outputDirectoryPath))
		{
			Files.createDirectories(outputDirectoryPath);
		}
		String requestResult = getMethods().execute(getNonStandardRequest(), "");
		String sourceFilename = String.format("%s/%s", outputDirectoryPath, getNonStandardFilename());

		sourceFilename = fixFileName(sourceFilename);
		FileUtils.write(new File(sourceFilename), requestResult);
		generatedFilenames.add(sourceFilename);
	}

	private void createStandardReport(List<String> generatedFilenames) throws Exception
	{
		DateTimeFormatter formatter = ISODateTimeFormat.dateTimeNoMillis();
		m_cachedTransform = new CachedTransform(getTransform(), false);

		Path outputDirectoryPath = Paths.get(String.format("%s/outputFiles", getOutputDirectory()));
		if (Files.exists(outputDirectoryPath))
		{
			Files.createDirectories(outputDirectoryPath);
		}

		DateTime filenameStart = m_intervals.get(0).getStart();
		String filenameDatePattern = s_fileTimeFormat.print(filenameStart);

		for (Interval i : m_intervals)
		{
			DateTime start = i.getStart();
			DateTime end = i.getEnd();

			String sourceFilename = String.format("%s/source-%s_%s.xml", outputDirectoryPath, formatter.print(start),
					formatter.print(end));

			String scilaInstrFilename = String.format("%s/scila-instr_%s.xml", outputDirectoryPath,
					filenameDatePattern);

			String scilaOrderFilename = String.format("%s/scila-order_%s.xml", outputDirectoryPath,
					filenameDatePattern);

			String scilaTradeFilename = String.format("%s/scila-trade_%s.xml", outputDirectoryPath,
					filenameDatePattern);

			// adjust the start and end to be database time (after we've generated the filenames)
			start = start.plus(Dates.getSystemTimeOffset());
			end = end.plus(Dates.getSystemTimeOffset());

			String startText = s_timeFormat.print(start);
			String endText = s_timeFormat.print(end);

			logger.info(String.format("Getting combined data for : [%s][%s]", startText, endText));

			String data = null;
			if (m_getSplitData)
			{
				data = "<REP>";
				data += getMethods().execute(getOrderRequest(), String.format("%s|%s", startText, endText));
				data += getMethods().execute(getTradeRequest(), String.format("%s|%s", startText, endText));
				data += "</REP>";
			}
			else
			{
				data = getMethods().execute(getCombinedRequest(), String.format("%s|%s", startText, endText));
			}

			logger.info(String.format("Data returned size : [%s][%s][%d]", startText, endText, data.length()));

			sourceFilename = fixFileName(sourceFilename);
			FileUtils.write(new File(sourceFilename), data);

			Map<String, Object> propertiesMap = new HashMap<String, Object>();
			propertiesMap.put("run.mode", "instruments");
			Document instrDoc = Util.performTransformFromString(m_cachedTransform, data, propertiesMap);
			scilaInstrFilename = fixFileName(scilaInstrFilename);
			generatedFilenames.add(scilaInstrFilename);
			writeSubNode(instrDoc, "/Instruments", scilaInstrFilename);

			propertiesMap.put("run.mode", "orders");
			Document orderDoc = Util.performTransformFromString(m_cachedTransform, data, propertiesMap);
			scilaOrderFilename = fixFileName(scilaOrderFilename);
			generatedFilenames.add(scilaOrderFilename);
			writeSubNode(orderDoc, "/Orders", scilaOrderFilename);

			propertiesMap.put("run.mode", "trades");
			Document tradeDoc = Util.performTransformFromString(m_cachedTransform, data, propertiesMap);
			scilaTradeFilename = fixFileName(scilaTradeFilename);
			generatedFilenames.add(scilaTradeFilename);
			writeSubNode(tradeDoc, "/Trades", scilaTradeFilename);

			if (isZipFiles())
			{
				String zipFileName = String.format("%s/%s-%s.zip", outputDirectoryPath, getZipFilePrefix(),
						filenameDatePattern);
				// out put file
				ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(zipFileName));

				for (String filename : generatedFilenames)
				{
					File file = new File(filename);
					FileInputStream inputStream = new FileInputStream(filename);
					ZipEntry zipEntry = new ZipEntry(file.getName());
					// name the file inside the zip  file
					outputStream.putNextEntry(zipEntry);
					pipe(inputStream, outputStream);
					outputStream.closeEntry();
					inputStream.close();
				}
				outputStream.close();
				// remove the old entries and add the zipped version..
				generatedFilenames.clear();
				generatedFilenames.add(zipFileName);

			}
		}

	}

	private static final int BUFFER_SIZE = 512;

	public static void pipe(InputStream ins, OutputStream out) throws IOException
	{
		byte[] buffer = new byte[BUFFER_SIZE];

		int len = ins.read(buffer);
		while (len != -1)
		{
			out.write(buffer, 0, len);
			len = ins.read(buffer);
		}
		out.flush();
	}

	private String fixFileName(String sourceFilename)
	{
		sourceFilename = sourceFilename.replace(':', '_');
		sourceFilename = sourceFilename.replace("c_", "c:");
		return sourceFilename;
	}

	private void writeSubNode(Document sourceDoc, String xpath, String filename)
	{
		try
		{
			Node subNode = Util.getNodeFromNode(sourceDoc, xpath);
			if (subNode != null)
			{
				Document subDoc = Util.documentFromNode(Util.getNodeFromNode(sourceDoc, xpath));
				String data = Util.prettyPrintDocument(subDoc, true);
				FileUtils.write(new File(filename), data);
			}
		}
		catch (Exception e)
		{
			logger.error("", e);
		}
	}

	public void buildIntervals(DateTime startDate, DateTime endDate)
	{
		DateTime start = startDate;
		DateTime end = startDate.plusDays(1);

		while (!end.isAfter(endDate))
		{
			Interval i = new Interval(start, end);
			m_intervals.add(i);
			start = end;
			end = start.plusDays(1);
			//end = start.plusHours(1);
		}

	}

	public void connect() throws Exception
	{

		JSch jsch = new JSch();

		logger.info(String.format("Setting SFTP Connection to u[%s] h[%s] i[%s] k[%s].", m_sftpUser, m_sftpHost,
				m_sftpIdentityFilename, m_sftpKnownHostsFilename));

		if (StringUtils.isNotBlank(m_sftpKnownHostsFilename))
		{
			jsch.setKnownHosts(new FileInputStream(new File(m_sftpKnownHostsFilename)));
		}

		if (StringUtils.isNotBlank(m_sftpIdentityFilename))
		{
			logger.info("Adding identity : " + m_sftpIdentityFilename);
			jsch.addIdentity(m_sftpIdentityFilename);
		}

		m_session = jsch.getSession(getSftpUser(), getSftpHost(), 22);
		m_session.connect();
		m_channel = m_session.openChannel("sftp");
		m_channel.connect();

	}

	public void disconnect()
	{
		if (m_channel != null)
		{
			m_channel.disconnect();
		}
		if (m_session != null)
		{
			m_session.disconnect();
		}
	}

	public final String getSftpUser()
	{
		return m_sftpUser;
	}

	public final void setSftpUser(String sftpUser)
	{
		m_sftpUser = sftpUser;
	}

	public final String getSftpPath()
	{
		return m_sftpPath;
	}

	public final void setSftpPath(String sftpPath)
	{
		m_sftpPath = sftpPath;
	}

	public final String getSftpHost()
	{
		return m_sftpHost;
	}

	public final void setSftpHost(String sftpHost)
	{
		m_sftpHost = sftpHost;
	}

	public final String getSftpKnownHostsFilename()
	{
		return m_sftpKnownHostsFilename;
	}

	public final void setSftpKnownHostsFilename(String sftpKnownHostsFilename)
	{
		m_sftpKnownHostsFilename = sftpKnownHostsFilename;
	}

	public final String getSftpIdentityFilename()
	{
		return m_sftpIdentityFilename;
	}

	public final void setSftpIdentityFilename(String sftpIdentityFilename)
	{
		m_sftpIdentityFilename = sftpIdentityFilename;
	}

	public final String getOrderRequest()
	{
		return m_orderRequest;
	}

	public final void setOrderRequest(String orderRequest)
	{
		m_orderRequest = orderRequest;
	}

	public final String getTradeRequest()
	{
		return m_tradeRequest;
	}

	public final String getCombinedRequest()
	{
		return m_combinedRequest;
	}

	public final void setCombinedRequest(String combinedRequest)
	{
		m_combinedRequest = combinedRequest;
	}

	public final void setTradeRequest(String tradeRequest)
	{
		m_tradeRequest = tradeRequest;
	}

	public final String getTransform()
	{
		return m_transform;
	}

	public final void setTransform(String transform)
	{
		m_transform = transform;
	}

	public final String getOutputDirectory()
	{
		return m_outputDirectory;
	}

	public final void setOutputDirectory(String outputDirectory)
	{
		m_outputDirectory = outputDirectory;
	}

	public final boolean isSftpDisabled()
	{
		return m_sftpDisabled;
	}

	public final void setSftpDisabled(boolean sftpDisabled)
	{
		m_sftpDisabled = sftpDisabled;
	}

	public final boolean isEmailErrorsEnabled()
	{
		return m_emailErrorsEnabled;
	}

	public final void setEmailErrorsEnabled(boolean emailErrorsEnabled)
	{
		m_emailErrorsEnabled = emailErrorsEnabled;
	}

	public final String getEmailErrorsSendTo()
	{
		return m_emailErrorsSendTo;
	}

	public final void setEmailErrorsSendTo(String emailErrorsSendTo)
	{
		m_emailErrorsSendTo = emailErrorsSendTo;
	}

	public final String getEmailErrorsSendFrom()
	{
		return m_emailErrorsSendFrom;
	}

	public final void setEmailErrorsSendFrom(String emailErrorsSendFrom)
	{
		m_emailErrorsSendFrom = emailErrorsSendFrom;
	}

	public final String getEmailHost()
	{
		return m_emailHost;
	}

	public final void setEmailHost(String emailHost)
	{
		m_emailHost = emailHost;
	}

	public final String getNonStandardRequest()
	{
		return m_nonStandardRequest;
	}

	public final void setNonStandardRequest(String nonStandardRequest)
	{
		m_nonStandardRequest = nonStandardRequest;
	}

	public final String getNonStandardFilename()
	{
		return m_nonStandardFilename;
	}

	public final void setNonStandardFilename(String nonStandardFilename)
	{
		m_nonStandardFilename = nonStandardFilename;
	}

	public boolean isZipFiles()
	{
		return m_zipFiles;
	}

	public void setZipFiles(boolean zipFiles)
	{
		m_zipFiles = zipFiles;
	}

	public String getZipFilePrefix()
	{
		return m_zipFilePrefix;
	}

	public void setZipFilePrefix(String zipFilePrefix)
	{
		m_zipFilePrefix = zipFilePrefix;
	}

	private boolean m_getSplitData = true;

	private String m_sftpUser;
	private String m_sftpPath;
	private String m_sftpHost;
	private String m_sftpKnownHostsFilename;
	private String m_sftpIdentityFilename;
	private boolean m_sftpDisabled;
	private boolean m_zipFiles;
	private String m_zipFilePrefix;

	private String m_nonStandardRequest;
	private String m_nonStandardFilename;

	private String m_orderRequest;
	private String m_tradeRequest;
	private String m_combinedRequest;

	private String m_outputDirectory;
	private String m_transform;

	private boolean m_emailErrorsEnabled;
	private String m_emailErrorsSendTo;
	private String m_emailErrorsSendFrom;

	private String m_emailHost;

	private Session m_session = null;
	private Channel m_channel = null;

	private CachedTransform m_cachedTransform = null;
	private Methods m_methods;

	public static DateTimeFormatter s_timeFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
	public static DateTimeFormatter s_fileTimeFormat = DateTimeFormat.forPattern("yyyyMMdd");
	private static Logger logger = Logger.getLogger(ScilaReport.class);

}
