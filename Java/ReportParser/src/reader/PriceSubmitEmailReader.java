package reportparser.reader;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.tidy.Tidy;

public class PriceSubmitEmailReader extends EmailReader {

	public static Logger logger = LogManager.getLogger(PriceSubmitEmailReader.class);
	private static final String MAPPINGS_REQUEST = "BONDS.CONVERTIBLE.ISINS.GET";
	private static final String TRADING_ENTITY_REQUEST = "BONDS.TRADING.ENTITY.GET";
	private static final String PERSON_EMAIL_LOOKUP = "PERSON.GET.BY.EMAIL";
	private static final String PRICE_SAVE_REQUEST = "BONDS.PRICES.SAVE.ADD.STOCK.REF";
	private Document mappings;

	@Override
	public boolean initialise(Element config)
	{
		if (super.initialise(config))
		{
			try
			{
				String szmappings = Controller.getInstance().getMethods().execute(MAPPINGS_REQUEST, new String[] {});
				mappings = Util.documentFromString(szmappings);
				logger.info("initialise() : Obtained and stored mappings : " + szmappings);
				return true;
			}
			catch(Exception e)
			{
				logger.error("initialise() : Exception thrown obtaining or parsing mappings", e);
				return false;
			}
		}
		else
		{
			return false;
		}
	}

	@Override
	protected void processData(File file) {
		//not called. prices are in body of email so processContent gets called instead

	}

	@Override
	protected void processContent(String subject, String from, String content)
	{
		logger.info("processContent() : Received [Subject " + subject + "], Fron [" + from + "], Content = " + content);
		String email_response_subject = "RE: " + subject;

		String price_owner_org = null;
		String price_owner_trading_entity = null;
		String price_broker_alias_org = null;
		String price_broker_alias_code = null;

		//trading entity is provided in the email subject; e.g. if subject = "Prices for DB BN" then the trading entity is
		subject = subject.trim();
		subject = subject.toUpperCase();
		subject = subject.replaceAll("\\*", " ");//remove * in case they were already included
		subject = subject.replaceAll("  ", " ");
		String[] subjectparts = subject.split(" ");
		if (subjectparts.length >= 2)
		{
			String trading_entity = subjectparts[subjectparts.length-2] + " *" + subjectparts[subjectparts.length-1] + "*";
			try
			{
				String sztradingentitydoc = Controller.getInstance().getMethods().execute(TRADING_ENTITY_REQUEST, new String[] {trading_entity});
				Document tradingentitydoc = Util.documentFromString(sztradingentitydoc);
				if (tradingentitydoc!=null)
				{
					price_owner_org = Util.getNodeValueFromNodeDOM(tradingentitydoc, "/Trading.Entity/@org_code");
					price_owner_trading_entity = Util.getNodeValueFromNodeDOM(tradingentitydoc, "/Trading.Entity/@code");
					logger.info("Obtained org and trading entity codes from database based on email subject. " + price_owner_org + ", " + price_owner_trading_entity);
				}
				else
				{
					logger.warn("tradingentitydoc is null");
					sendHtmlEmail(from, email_response_subject, "Unable to find valid trading entity based on last part of email subject line [" + subjectparts[subjectparts.length-2] + " " + subjectparts[subjectparts.length-1] + "]");
					return;
				}

			}
			catch (Exception e)
			{
				logger.error("Exception thrown looking up trading entity info", e);
				sendHtmlEmail(from, email_response_subject, "An error occurred trying to find valid trading entity " + e.getMessage());
				return;
			}
		}
		else
		{
			logger.info("email subject not in the correct format");
			sendHtmlEmail(from, email_response_subject, "Unable to find valid trading entity in email subject. Subject must included organisation and entity names (separated by a space)");
			return;
		}

		//lookup the user that sent the email. this will be used as the broker alias
		if (from.contains("<") && from.contains(">"))
		{
			from = from.substring(from.indexOf("<") + 1, from.indexOf(">"));
		}
		from = from.toLowerCase();

		try
		{
			String szpeopledoc = Controller.getInstance().getMethods().execute(PERSON_EMAIL_LOOKUP, new String[] {from});
			Document peopledoc = Util.documentFromString(szpeopledoc);
			if (peopledoc!=null)
			{
				//note we just look at the first person. it is possible more than one person has the same contact email! :(
				price_broker_alias_org = Util.getNodeValueFromNode(peopledoc, "/People/Person[1]/@owner_org");
				price_broker_alias_code = Util.getNodeValueFromNode(peopledoc, "/People/Person[1]/@owner_code");
				if (StringUtils.isBlank(price_broker_alias_org) || StringUtils.isBlank(price_broker_alias_code))
				{
					logger.error("Failed to obtain valid broker alias info based on email address [" + from + "]. Is the email address added under the broker's contact in the Admin tool?");
					sendHtmlEmail(from, email_response_subject, "Unable to find broker information from email address [" + from +"]. Is the email address added under the broker's contact in the Admin tool?");
					return;
				}
				logger.info("Obtained broker alias org and code from database based on email sender. " + price_broker_alias_org + ", " + price_broker_alias_code);
			}
			else
			{
				logger.error("peopledoc is null");
				sendHtmlEmail(from, email_response_subject, "Error occurred trying to lookup broker information based on email address [" + from +"]. (Error = peopledoc is null)");
				return;
			}

		}
		catch (Exception e)
		{
			logger.error("Exception thrown looking up person info", e);
			sendHtmlEmail(from, email_response_subject, "An error occurred trying to lookup broker information based on email address [" + from +"]" + e.getMessage());
			return;
		}

		//now process the content
		content = content.replaceAll("&nbsp;", " ");
		content = content.replaceAll("<!\\[if", "<!--\\[if"); //Excel seems to send dodgy CSS if statements
		content = content.replaceAll("endif\\]>", "endif\\]-->");

		//use JTidy to create a DOM document from HTML input (doesn't seem to work too well but produces transformable output at least)
		Tidy tidy = new Tidy();
		tidy.setXHTML(true);
		tidy.setDocType("omit");
		tidy.setPrintBodyOnly(true);
		tidy.setForceOutput(true);

		StringReader reader = new StringReader(content);
		StringWriter writer = new StringWriter();
		tidy.parse(reader, writer);
		StringBuffer buff = writer.getBuffer();

		if (logger.isDebugEnabled())
		{
			logger.debug("Results of JTidy-ing the email content = " + buff);
		}

		Document htmldoc = Util.documentFromString(buff.toString());

		if (logger.isDebugEnabled())
		{
			logger.debug("Results of JTidy-ing the email content (DOM) = " + Util.stringFromDocument(htmldoc));
		}

		//transform output to create price (note we pass in the isin to interest id mappings document as a parameter)
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put("mappings", mappings);
		params.put("owner.org", price_owner_org);
		params.put("owner.trading.entity", price_owner_trading_entity);
		params.put("broker.alias.org", price_broker_alias_org);
		params.put("broker.alias.code", price_broker_alias_code);
		Document price_doc = applyTransform(htmldoc, Level.DEBUG, params);

		Node transform_email_response_node = extractResponseNode(price_doc);

		//check if we have any prices and if so, submit them to the database
		if (Util.getNodeValueFromNodeDOM(price_doc, "/Submit.Price/i:Price.Entry")!=null)
		{
			String szpricexml = Util.stringFromDocument(price_doc, true);
			logger.debug("found price(s) to submit : " + szpricexml);
			try
			{
				String szresponse = Controller.getInstance().getMethods().execute(PRICE_SAVE_REQUEST, szpricexml);
				logger.info("submitted prices. Response = " + szresponse);
				if (transform_email_response_node!=null)
				{
					sendHtmlEmail(from, email_response_subject, Util.stringFromDocument(transform_email_response_node, true));
				}
				else
				{
					sendHtmlEmail(from, email_response_subject, "There was a problem extracting information from the email (transform_email_response_node=null). Please contact suppport.");
				}
			}
			catch (Exception e)
			{
				logger.error("Exception thrown submitting prices to database", e);
				sendHtmlEmail(from, email_response_subject, "An error occurred submitting prices to the database. Error = " + e.getMessage());
			}
		}
		else
		{
			logger.error("No /Submit.Price/i:Price.Entry nodes found in document");
			sendHtmlEmail(from, email_response_subject, "No valid prices found in email");
		}
	}

	private Node extractResponseNode(Document price_doc)
	{
		Node submit_price_node = Util.getNodeFromNode(price_doc, "/Submit.Price");
		if (submit_price_node!=null)
		{
			Node email_response_node = Util.getNodeFromNode(price_doc, "/Submit.Price/response");
			if (email_response_node!=null)
			{
				//remove from the document - we don't want to submit to the database
				submit_price_node.removeChild(email_response_node);
				return email_response_node.getFirstChild();
			}
			else
			{
				logger.error("extractAndSendResponse() : No response node found in price_doc");
			}
		}
		else
		{
			logger.error("extractAndSendResponse() : No Submit.Price node found in price_doc");
		}
		return null;
	}
}



