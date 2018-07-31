package reportparser.reader;

import java.io.InputStream;
import java.util.Vector;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public abstract class SFTPReportReader extends ReportReader
{
	private static Logger logger = Logger.getLogger(SFTPReportReader.class);
	private String host;
	private int port;
	private int connect_timeout;

	private String known_hosts_file;
	private final static int DEFAULTPORT = 22;
	private final static int DEFAULTCONNECTTIMEOUTMILLIS = 60000; //(0 = no timeout)
	private DynamicString folder;
	private String username;
	private String password;
	private String authentication_type;
	private String keyfile;
	// the SFTP connection. we open a new connection each time we want to do something, then close it
	private Session session;
	private ChannelSftp channel;

	@Override
	protected boolean initialise(Element config)
	{
		super.initialise(config);
		host = Util.getNodeValueFromNodeDOM(config, "SFTP/Host");
		known_hosts_file = Util.getNodeValueFromNodeDOM(config, "SFTP/KnownHostsFile");
		authentication_type = Util.getNodeValueFromNodeDOM(config, "SFTP/AuthenticationType");
		keyfile = Util.getNodeValueFromNodeDOM(config, "SFTP/KeyFile");
		folder = new DynamicString(Util.getNodeValueFromNodeDOM(config, "SFTP/Folder"));
		username = Util.getNodeValueFromNodeDOM(config, "SFTP/Username");
		password = Util.getNodeValueFromNodeDOM(config, "SFTP/Password");
		String szport = Util.getNodeValueFromNodeDOM(config, "SFTP/Port", ""+DEFAULTPORT);
		port = Integer.parseInt(szport);
		String szconnecttimeout = Util.getNodeValueFromNodeDOM(config, "SFTP/ConnectTimeout", ""+DEFAULTCONNECTTIMEOUTMILLIS);
		connect_timeout = Integer.parseInt(szconnecttimeout);

		return true;
	}


	protected void connect() throws Exception
	{
		JSch jsch = new JSch();
		JSch.setConfig("PreferredAuthentications", authentication_type);
		if (StringUtils.isNotBlank(known_hosts_file))
		{
			jsch.setKnownHosts(known_hosts_file);
		}
		else
		{
			JSch.setConfig("StrictHostKeyChecking", "no");
		}
		switch (authentication_type)
		{
		case "publickey" :
			jsch.addIdentity(this.keyfile, this.password);
			break;
		case "password" :
			break;
			default : throw new Exception("AuthenticationType " + authentication_type + " not handled");
		}

		this.session = jsch.getSession(username, host, port);
		this.session.setPassword(password); //this step not required for publickey type authentication - but doesn't seem to hurt
		this.session.connect(connect_timeout);
		this.channel = (ChannelSftp)session.openChannel("sftp");
		channel.connect();
	}


	/*private void connectPassword() throws Exception
	{
		JSch jsch = new JSch();
		JSch.setConfig("PreferredAuthentications", "password");
		if (StringUtils.isNotBlank(known_hosts_file))
		{
			jsch.setKnownHosts(known_hosts_file);
		}
		else
		{
			JSch.setConfig("StrictHostKeyChecking", "no");
		}
		this.session = jsch.getSession(username, host, port);
		this.session.setPassword(password);
		this.session.connect(connect_timeout);
		this.channel = (ChannelSftp)session.openChannel("sftp");
		channel.connect();
	}*/

	protected void disconnect()
	{
		if (channel!=null)
		{
			try
			{
				channel.disconnect();
			}
			catch(Throwable t)
			{
				logger.error("Exception thrown disconnecting SFTP channel", t);
			}
		}
		if (session!=null)
		{
			try
			{
				session.disconnect();
			}
			catch(Throwable t)
			{
				logger.error("Exception thrown disconnecting SFTP session", t);
			}
		}
	}

	protected RemoteFileList getRemoteFileList()
	{
		RemoteFileList filelist = null;
		synchronized(this)
		{
			try
			{
				connect();
				filelist = new RemoteFileList(this.channel.ls(folder.toString()));
			}
			catch(Exception e)
			{
				logger.error("Exception thrown connecting to getRemoteFileList", e);
			}
			finally
			{
				disconnect();
			}
		}
		return filelist;
	}

	protected void getAndProcessFile(Pattern filenamepattern)
	{
		synchronized(this)
		{
			try
			{
				connect();

				channel.cd(folder.toString());

				@SuppressWarnings("unchecked")
				Vector<LsEntry> v = (Vector<LsEntry>)channel.ls(".");
				boolean matched = false;
				for (LsEntry entry : v)
				{
					if (entry.getAttrs().isReg())
					{
						String szfilename = entry.getFilename();
						if (ReportReader.checkFilename(filenamepattern, szfilename))
						{
							InputStream is = channel.get(entry.getFilename());
							processFile(szfilename, is);
							matched = true;
							is.close();
						}
					}
				}
				if (!matched)
				{
					logger.warn("No file matching pattern " + filenamepattern.toString() + " found in folder " + folder);
				}
			}
			catch (Exception e)
			{
				logger.error("Exception thrown connecting to getAndProcessFile " + filenamepattern, e);
			}
			finally
			{
				disconnect();
			}
		}
	}

	protected void getAndProcessFile(String szfilename)
	{
		synchronized(this)
		{
			try
			{
				connect();
				InputStream is = channel.get(folder.toString() + "/" + szfilename);
				processFile(szfilename, is);
				is.close();
			}
			catch (Exception e)
			{
				logger.error("Exception thrown connecting to getAndProcessFile " + szfilename, e);
			}
			finally
			{
				disconnect();
			}
		}
	}



	protected static class RemoteFileList extends Vector<LsEntry>
	{
		private static final long serialVersionUID = -3482198829636953345L;

		public RemoteFileList() {
			super();
		}

		public RemoteFileList(Vector<LsEntry> entries)
		{
			super(entries);
		}

		public RemoteFileList compareWith(RemoteFileList newlist)
		{
			RemoteFileList difference = new RemoteFileList();
			//check if every file in newlist exists in currentlist (file will be added to difference list if it is new or if it has been modified in any way)
			//doesn't worry about files that have been deleted 'cos there is not much we can do with those!
			for (LsEntry newlistentry : newlist)
			{
				if (!newlistentry.getAttrs().isDir())
				{
					if (!this.contains(newlistentry))
					{
						difference.add(newlistentry);
					}
				}
			}

			return difference;
		}

		@Override
		public boolean contains(Object o)
		{
			LsEntry input = (LsEntry)o;
			for (LsEntry entry : this)
			{
				if (checkEntriesEqual(input, entry))
				{
					return true;
				}
			}
			return false;
		}

		private static boolean checkEntriesEqual(LsEntry entry1, LsEntry entry2)
		{
			if (entry1==null)
			{
				return entry2==null;
			}
			else
			{
				if (entry2==null)
				{
					return false;
				}
				else
				{
					if (entry1.getFilename().equals(entry2.getFilename()))
					{
						return entry1.getAttrs().toString().equals(entry2.getAttrs().toString());
					}
					else
					{
						return false;
					}
				}
			}
		}


	}


}
