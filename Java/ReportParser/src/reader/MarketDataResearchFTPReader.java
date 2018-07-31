package reportparser.reader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.w3c.dom.Element;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class MarketDataResearchFTPReader extends EmailReader {

	protected String ftpHost;
	protected String ftpUser;
	protected String ftpPassword;
	protected String ftpFolder;
	protected String sftpHost;
	protected String sftpUser;
	protected String sftpPassword;
	protected String sftpFolder;

	@Override
	public boolean initialise(Element config)
	{
		ftpHost = Util.getNodeValueFromNodeDOM(config, "FTP/Host");
		ftpUser = Util.getNodeValueFromNodeDOM(config, "FTP/User");
		ftpPassword = Util.getNodeValueFromNodeDOM(config, "FTP/Password");
		ftpFolder = Util.getNodeValueFromNodeDOM(config, "FTP/Folder");
		sftpHost = Util.getNodeValueFromNodeDOM(config, "SFTP/Host");
		sftpUser = Util.getNodeValueFromNodeDOM(config, "SFTP/User");
		sftpPassword = Util.getNodeValueFromNodeDOM(config, "SFTP/Password");
		sftpFolder = Util.getNodeValueFromNodeDOM(config, "SFTP/Folder");
		return super.initialise(config);
	}


	@Override
	protected void processData(File file) {
		// TODO Auto-generated method stub
		logger.debug("processData");
		sendFTP(file);
		sendSFTP(file);
	}

	protected void sendFTP(File file)
	{
		if (StringUtils.isNotBlank(ftpHost))
		{
			logger.info("FTP'ing file because FTP/Host provided in config");
			try {
				FTPClient client = new FTPClient();
				client.connect(ftpHost);
				client.login(ftpUser, ftpPassword);
				client.enterLocalPassiveMode();
				client.setFileType(FTP.BINARY_FILE_TYPE);
				client.changeWorkingDirectory(ftpFolder);
				logger.debug(client.getStatus());
				InputStream stream = new FileInputStream(file);
				boolean done = client.storeFile(file.getName(), stream);
				stream.close();
				if (done) {
					logger.debug("FTP'd "+file.getName());
				}
				client.disconnect();
			}
			catch (Exception ex) {
				logger.error("sendFTP() : Failed with exception : ", ex);
			}
		}
		else
		{
			logger.info("Not FTP'ing file because FTP/Host not provided in config");
		}
	}

	protected void sendSFTP(File file)
	{
		if (StringUtils.isNotBlank(sftpHost))
		{
			logger.info("SFTP'ing file because SFTP/Host provided in config");
			try {
				JSch jsch = new JSch();
				Session session = jsch.getSession(sftpUser, sftpHost);
				logger.info("JSch session created");
				session.setPassword(sftpPassword);
				Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);
				logger.info("About to connect session");
				session.connect();
				logger.info("session connected");

				logger.info("About to open channel");
				Channel channel = session.openChannel("sftp");
				channel.connect();
				logger.info("channel connected");

				ChannelSftp sftpchannel = (ChannelSftp)channel;

				logger.info("changing remote folder to " + sftpFolder);
				sftpchannel.cd(sftpFolder);
				logger.info("folder changed");

				logger.info("about to put file onto remote server");
				InputStream stream = new FileInputStream(file);
				sftpchannel.put(stream, file.getName());
				logger.info("file put onto remote server");

				sftpchannel.quit();
				session.disconnect();

			}
			catch (Exception ex)
			{
				logger.error("sendSFTP() : Failed with exception : ", ex);
			}
		}
		else
		{
			logger.info("Not SFTP'ing file because SFTP/Host not provided in config");
		}
	}


	@Override
	protected void terminate() {
		// TODO Auto-generated method stub
		super.terminate();
	}


}
