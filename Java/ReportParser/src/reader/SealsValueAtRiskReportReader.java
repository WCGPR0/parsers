package reportparser.reader;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.jcraft.jsch.ChannelSftp.LsEntry;

public class SealsValueAtRiskReportReader extends SFTPReportReader
{
	private static Logger logger = Logger.getLogger(SealsValueAtRiskReportReader.class);
	private RemoteFileList current_file_list;
	private ScheduledExecutorService executor;
	private int polling_interval_millis;
	private final static int DEFAULTPOLLINGINTERVALMILLIS = 30000;

	@Override
	protected boolean initialise(Element config)
	{
		super.initialise(config);
		String szpollinginterval = Util.getNodeValueFromNodeDOM(config, "SFTP/PollingInterval", ""+DEFAULTPOLLINGINTERVALMILLIS);
		polling_interval_millis = Integer.parseInt(szpollinginterval);

		current_file_list = getRemoteFileList();
		if (current_file_list==null)
		{
			logger.error("Unable to obtain initial list of files on remote server");
			return false;
		}

		//start scheduled task to check remote file list
		executor = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("SealsValueAtRiskReportReader"));
		executor.scheduleAtFixedRate(new LoggedRunnable()
		{
			@Override
			public void loggedRun()
			{
				checkRemoteFileList();
			}
		}, polling_interval_millis, polling_interval_millis, TimeUnit.MILLISECONDS);

		return true;
	}


	@Override
	protected void processData(String filename, Document data) {

		NodeList tradenodes = Util.getNodeListFromNode(data, "Trades/Trade");
		for (int i=0; i<tradenodes.getLength(); i++)
		{
			try
			{
				Trade trade = new Trade(tradenodes.item(i));
				if (trade.isRiskTrade())
				{
					if (trade.getProductISIN())
					{
						if (trade.getBloombergData())
						{
							trade.save();
						}
					}
				}
			}
			catch (InvalidTradeException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	private void checkRemoteFileList()
	{
		RemoteFileList newfilelist = getRemoteFileList();
		if (current_file_list!=null)
		{
			RemoteFileList diff = current_file_list.compareWith(newfilelist);
			current_file_list = newfilelist;
			if (diff!=null && !diff.isEmpty())
			{
				processDifferences(diff);
			}
		}
		else
		{
			current_file_list = newfilelist;
			processDifferences(current_file_list);
		}
	}

	private void processDifferences(RemoteFileList filelist)
	{
		Pattern filter_pattern = Pattern.compile(filename_filter_pattern.toString());
		for (LsEntry entry : filelist)
		{
			if (ReportReader.checkFilename(filter_pattern, entry.getFilename()))
        	{
				getAndProcessFile(entry.getFilename());
        	}
		}
	}

	@Override
	protected void terminate()
	{
		if (executor!=null)
		{
			executor.shutdownNow();
		}

	}

}
