package reportparser.reader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.search.FlagTerm;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.FileUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public abstract class EmailReader extends ReportReader
{
	protected static Logger logger = LogManager.getLogger(EmailReader.class);
	private final static int DEFAULTPOLLINGINTERVAL = 10; //seconds
	protected int polling_interval; //seconds
	protected String protocol;
	protected String user;
	protected String password;
	protected String folder;
	protected boolean debug_javamail = false;
	protected String from; //only process messages from this address (actually where address contains this string)
	protected String subject; //only process messages with this subject (NOT case sensitive)
	protected String attachment_save_folder;
	protected String outputFile;
	protected String dateFormat;
	protected final DateTimeFormatter standarddateformatter = DateTimeFormat.forPattern("yyyy-MM-dd");
	protected long attachmentindex = System.currentTimeMillis();

	protected Folder mailfolder;
	protected Store mailstore;

	private ScheduledExecutorService executor_service;
	private Properties mail_properties;
	private boolean connected = false; //connection to mailstore


	protected abstract void processData(File file);

	@Override
	public boolean initialise(Element config)
	{
		logger.info("Initialising EmailReader");
		if (!super.initialise(config)) {
			return false;
		}

		protocol = Util.getNodeValueFromNodeDOM(config, "Email/Protocol");
		mail_properties = Util.getProperties((Element)Util.getNodeFromNode(config, "Email/Properties"));
		user = Util.getNodeValueFromNodeDOM(config, "Email/User");
		password = Util.getNodeValueFromNodeDOM(config, "Email/Password");
		folder = Util.getNodeValueFromNodeDOM(config, "Email/Folder");
		from = Util.getNodeValueFromNodeDOM(config, "Email/From");
		subject = Util.getNodeValueFromNodeDOM(config, "Email/Subject");
		String szpollinginterval = Util.getNodeValueFromNodeDOM(config, "Email/PollingInterval", String.valueOf(DEFAULTPOLLINGINTERVAL));
		polling_interval = Integer.parseInt(szpollinginterval);
		attachment_save_folder = Util.getNodeValueFromNodeDOM(config, "Email/AttachmentSaveFolder", ".");
		debug_javamail = "True".equalsIgnoreCase(Util.getNodeValueFromNodeDOM(config, "Email/Debug"));
		dateFormat = Util.getNodeValueFromNodeDOM(config, "Email/DateFormat");
		outputFile = Util.getNodeValueFromNodeDOM(config, "Email/OutputFile");
		logger.info("Configured EmailReader with Protocol [{}], User [{}], Password [{}], Folder [{}]", protocol, user, StringUtils.isBlank(password) ? "<not set>" : "<set to non-blank value>", folder);
		executor_service = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("EmailReader for checking " + folder));

        try
        {
            connectMailStore();

            //schedule process to periodically check for new mail
            executor_service.scheduleWithFixedDelay(new LoggedRunnable()
            {
				@Override
				public void loggedRun()
				{
					try
					{
						processUnreadMessages();
					}
					catch (Exception e)
					{
						logger.error("Exception thrown calling processUnreadMessages()", e);

						do
						{
							disconnectMailStore();

							//wait before attempting reconnection
							logger.warn("Waiting {}s before attempting to reconnect...", polling_interval);
							try
							{
								Thread.sleep(polling_interval * 1000);
							}
							catch (InterruptedException e1)
							{
								logger.warn("InterruptedException thrown waiting before reconnect attempt", e1);
								break;
							}

							//attempt reconnection
							try
							{
								connectMailStore();
							}
							catch(Exception e1)
							{
								logger.error("Exception thrown attempting to reconnect", e1);
							}
						}
						while (!connected);
					}
				}
			}, polling_interval, polling_interval, TimeUnit.SECONDS);

    		return true;
        }
        catch(Exception e)
        {
        	logger.error("Exception thrown initialising EmailReader", e);
        	return false;
        }
	}

	private void processUnreadMessages() throws MessagingException
	{
		if (connected)
		{
			if (mailfolder.getUnreadMessageCount() > 0)
			{
				mailfolder.open(Folder.READ_WRITE);
				Message[] messages = mailfolder.search(new FlagTerm(new Flags((Flag.SEEN)), false));
				processMessages(messages);
	            mailfolder.close(true);
			}
		}
		else
		{
			logger.warn("processUnreadMessages() Not attempting to check for messages as connected=false");
		}
	}


	@Override //from ReportReader. TODO should combine this with the processData method that takes a file argument. Sort out!!!
	protected void processData(String filename, Document data) {
		// TODO Auto-generated method stub

	}

	//is overridden in some of the derived classes
	protected void processContent(String subject, String from, String content)
	{
		logger.warn("processContent() : Not implemented in derived class");
	}

	private void connectMailStore() throws Exception
	{
		Session session = Session.getInstance(mail_properties);
        if (logger.isDebugEnabled())
        {
        	session.setDebug(debug_javamail);
        }

        // connect to the message store
        logger.info("Connecting to mail store");
        mailstore = session.getStore(protocol);
        mailstore.connect(user, password);


        // get folder
        logger.info("Obtaining folder: " + folder);
        mailfolder = mailstore.getFolder(folder);
        // fetch any new messages from server
        processUnreadMessages();

        connected = true;
	}

	private void disconnectMailStore()
	{
		logger.info("Closing mailfolder");
		connected = false;
		if (mailfolder!=null)
		{
			try {
				mailfolder.close(false);
			} catch (Throwable e) {
				logger.warn("Exception thrown closing mailfolder", e);
			}
			finally {
				mailfolder=null;
			}
		}

		logger.info("Closing mailstore");
		if (mailstore!=null)
		{
			try {
				mailstore.close();
			} catch (Throwable e) {
				logger.warn("Exception thrown closing mailstore", e);
			}
			finally {
				mailstore=null;
			}
		}
	}

	protected void sendHtmlEmail(String to, String subject, String content)
	{
		try
		{
			//note we create a new session for sending
			Session session = Session.getInstance(mail_properties);
			Message message = new MimeMessage(session);
			String[] user_parts = user.split("\\\\");
			String formatted_user = user_parts[1] + "@" + user_parts[0] + ".com";
			message.setFrom(new InternetAddress(formatted_user));
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
			message.setSubject(subject);
			message.setContent(content, "text/html");
			Transport.send(message, user, password);
			logger.info("email sent");
		}
		catch(Exception e)
		{
			logger.error("Exception thrown attempting to send email to " + to + ". Subject = " + subject + ". Content = " + content);
		}
	}


	@Override //from ReportReader
	protected void terminate()
	{
		logger.info("Terminating connection to mail server and stopping polling thread");
		if (executor_service!=null)
		{
			executor_service.shutdownNow();
		}
		disconnectMailStore();
	}

	private void processMessages(Message[] messages)
	{
		for (int i = 0; i < messages.length; i++)
		{
			Message msg = messages[i];
			if (msg!=null)
			{
				try
				{
					//check who email is from
					Address[] fromAddress = msg.getFrom();
			        String szfrom = fromAddress[0].toString();
					if (StringUtils.isBlank(this.from) || (szfrom!=null && szfrom.toUpperCase().contains(this.from.toUpperCase())))
			        {
			        	String szsubject = msg.getSubject();
			        	if (StringUtils.isBlank(this.subject) || (szsubject!=null && szsubject.equalsIgnoreCase(this.subject)))
			        	{
							if (msg.getContent() instanceof Multipart)
							{
								Multipart multipart = (Multipart)msg.getContent();
								for (int j=0; j<multipart.getCount(); j++)
								{
									Part part = multipart.getBodyPart(j);
									String disposition = part.getDisposition();
									if (disposition!=null &&
											((disposition.equalsIgnoreCase(Part.ATTACHMENT) || (disposition.equalsIgnoreCase(Part.INLINE)))))
									{
								        MimeBodyPart mime_body_part = (MimeBodyPart) part;
								        String filename = mime_body_part.getFileName();
								        logger.info("Received attachment with filename {}", filename);
								        Pattern filter_pattern = Pattern.compile(filename_filter_pattern.toString());
										if (ReportReader.checkFilename(filter_pattern, filename))
										{
											logger.info("Processing attachment as attachment matches pattern specified in config");
											File file_to_save;
											if (outputFile != null && dateFormat!=null) {
												File folder = new File(attachment_save_folder);
												//FileUtils.mkdir(folder, true);
												String date = DateTimeFormat.forPattern(dateFormat).print(DateTime.now());
												file_to_save = new File(folder, String.format(outputFile, date));
											}
											else {
												String szfolder =
														attachment_save_folder
														+ File.separator
														+ standarddateformatter.print(DateTime.now());
												File folder = new File(szfolder);
												FileUtils.mkdir(folder, true);
												file_to_save = new File(folder, (attachmentindex++) + "_" + filename);
											}
											mime_body_part.saveFile(file_to_save);

											logger.info("Saved file {}", file_to_save);
											processData(file_to_save);
										}
										else
										{
											logger.info("Not processing attachment as attachment does not match pattern specified in config");
										}
									}
									else
									{
										try
										{
											if (part.getContent() instanceof String)
												processContent(szsubject, szfrom, (String)part.getContent());
										}
										catch(Exception e)
										{
											logger.error("Exception thrown getting or processing content of message 'Part' as string", e);
										}
									}

								}
							}
							else
							{
								try
								{
									if (msg.getContent() instanceof String)
										processContent(szsubject, szfrom, (String)msg.getContent());
								}
								catch(Exception e)
								{
									logger.error("Exception thrown getting or processing content of message as string", e);
								}
							}
			        	}
			        	else
			        	{
			        		logger.info("Not processing message because subject [{}] does not match subject specified in config [{}]", szsubject, this.subject);
			        	}
			        }
				}
				catch(Exception e)
				{
					logger.error("Exception thrown processing message", e);
				}
				finally
				{
					//mark as read
					try
					{
						msg.setFlag(Flag.SEEN, true);
					}
					catch (Exception e)
					{
						logger.error("Exception thrown marking message as read", e);
					}
				}
			}
			else
			{
				logger.error("JavaMail Message object received from mail server is null");
			}
		}
	}


	/**
     * Returns a list of addresses in String format separated by comma
     *
     * @param address an array of Address objects
     * @return a string represents a list of addresses
     */
    private String parseAddresses(Address[] address) {
        String listAddress = "";

        if (address != null) {
            for (int i = 0; i < address.length; i++) {
                listAddress += address[i].toString() + ", ";
            }
        }
        if (listAddress.length() > 1) {
            listAddress = listAddress.substring(0, listAddress.length() - 2);
        }

        return listAddress;
    }

    protected void writeToFile(String fileName, StringBuffer data) throws IOException {
        BufferedWriter out = new BufferedWriter(new FileWriter(fileName));
        String s = data.toString();
        s=s.replaceAll("\u00A0", "");
        out.write(s);
        out.flush();
        out.close();
    }
}
