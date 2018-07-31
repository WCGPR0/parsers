package reportparser.reader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.regex.Pattern;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.JobDataMap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class EurexActiveSeriesReportReader extends SFTPReportReader implements ExecutableReport {

	private static final Logger logger = LogManager.getLogger(EurexActiveSeriesReportReader.class);
	private static final int DEFAULT_BATCH_SIZE = 50;
	private DynamicString filename;
	private int batch_size;
	private String destinationroot;
	private static final String DEFAULT_PROCESS_ELEMENT_NAME = "ta111Grp";
	private String process_element_name;
	private String pre_process_request;
	protected String process_request;
	private String process_element_parent_name;
	private String process_element_parent_namespace;

	@Override
	public boolean initialise(Element config)
	{
		pre_process_request = Util.getNodeValueFromNodeDOM(config, "PreProcessRequest");
		process_request = Util.getNodeValueFromNodeDOM(config, "ProcessRequest");
		destinationroot = Util.getNodeValueFromNodeDOM(config, "DestinationRoot");
		batch_size = Integer.parseInt(Util.getNodeValueFromNodeDOM(config, "BatchSize", String.valueOf(DEFAULT_BATCH_SIZE)));
		String szfilename = Util.getNodeValueFromNodeDOM(config, "Filename");
		if (StringUtils.isNotBlank(szfilename))
		{
			filename = new DynamicString(szfilename);
		}
		process_element_name = Util.getNodeValueFromNodeDOM(config, "ProcessElement", DEFAULT_PROCESS_ELEMENT_NAME);
		process_element_parent_name = Util.getNodeValueFromNodeDOM(config, "ProcessElement/@parent_name");
		process_element_parent_namespace = Util.getNodeValueFromNodeDOM(config, "ProcessElement/@parent_namespace");

		logger.info("Using batch size = {}", batch_size);
		return super.initialise(config);
	}

	@Override
	public void execute(JobDataMap job_data_map)
	{
		//run the report
		try
		{
			if (filename!=null)
			{
				super.getAndProcessFile(filename.toString());
			}
			else
			{
				//use the filename pattern instead
				Pattern filter_pattern = Pattern.compile(filename_filter_pattern.toString());
				super.getAndProcessFile(filter_pattern);
			}
		}
		catch(Exception e)
		{
			logger.error("Exception thrown obtaining report [reportreader id = {}]", id, e);
		}
		finally
		{
			super.disconnect();
		}

	}


	@Override
	protected void processData(String filename, Document data)
	{
		logger.info("processData() called");

		//apply transform
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put("report.size", String.valueOf(batch_size));
	    data = applyTransform(data, Level.TRACE, params);

		NodeList report_nodes = data.getElementsByTagName("Report");
		int report_count = report_nodes.getLength();
		logger.info("Found {} Report elements in transformed data", report_count);
		for (int i=0; i<report_count; i++)
		{
			try
		    {
		    	logger.info("Calling "+ process_request);
		    	String szdata = Util.stringFromDocument(report_nodes.item(i), true);

		    	String xmltemplate = 	"<Request.Params>" +
						"<Request.Param>%s</Request.Param>" +
					"</Request.Params>";
		    	String xmlparam = String.format(xmltemplate, szdata);

		    	//logger.debug(szdata);
				String ret = Controller.getInstance().getMethods().execute(process_request, xmlparam);
				logger.info(process_request + " returned " + ret);
			}
		    catch (Exception e)
		    {
				logger.error("Exception thrown calling " + process_request, e);
			}
		}
	}

	//override the default file processing in ReportReader (which expects csv files)
	@Override
	protected void processFile(String filename, InputStream inputstream)
	{
		//copy file from remote server to local location
		String destinationpath = destinationroot + File.separator + System.currentTimeMillis() + ".zip";
		File localcopy = new File(destinationpath);
		try
		{
			FileUtils.copyInputStreamToFile(inputstream, localcopy);
		}
		catch(Exception e)
		{
			logger.error("Exception thrown copying remote file to local drive", e);
			return;
		}

		//decompress
		if (decompress!=null)
		{
			//truncate
			if (StringUtils.isNotBlank(pre_process_request))
			{
				try
				{
					Controller.getInstance().getMethods().execute(pre_process_request, new String[] {});
				}
				catch (Exception e)
				{
					logger.error("Exception thrown running pre-process_request {}", pre_process_request, e);
				}
			}

			File[] outputfiles = decompress.process(localcopy);
			for (final File outputfile : outputfiles)
			{
				//parse
				try
				{
					SAXParserFactory spf = SAXParserFactory.newInstance();
					spf.setNamespaceAware(true);
					SAXParser saxParser = spf.newSAXParser();
					XMLReader xmlReader = saxParser.getXMLReader();
					xmlReader.setErrorHandler(new ErrorHandler() {

						@Override
						public void warning(SAXParseException e) throws SAXException {
							logger.warn("Exception thrown parsing " + outputfile.toString(), e);
						}

						@Override
						public void fatalError(SAXParseException e) throws SAXException {
							logger.fatal("Exception thrown parsing " + outputfile.toString(), e);
						}

						@Override
						public void error(SAXParseException e) throws SAXException {
							logger.error("Exception thrown parsing " + outputfile.toString(), e);
						}
					});

					xmlReader.setContentHandler(new FileContentHandler(outputfile));
					FileInputStream fis = new FileInputStream(outputfile);
					try
					{
						xmlReader.parse(new InputSource(fis));
					}
					finally
					{
						try {fis.close();} catch(Throwable t) {};
					}
				}
				catch(Exception e)
				{
					logger.error("Exception thrown processing file " + outputfile, e);
				}
			}
		}
		else
		{
			logger.error("Missing Decompress section in config");
		}
	}

	private class FileContentHandler extends DefaultHandler
	{
		private Document extracted_sub_doc;
		private Node current_node;
		private File filename;

		public FileContentHandler(File _filename)
		{
			filename = _filename;
		}

		@Override
		public void startDocument() throws SAXException {
			// TODO Auto-generated method stub
			super.startDocument();
		}

		@Override
		public void endDocument() throws SAXException
		{
			//process anything remaining
			if (extracted_sub_doc!=null)
			{
				processData(filename.toString(), extracted_sub_doc);
			}

			super.endDocument();
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException
		{
			if (process_element_name.equals(localName))
			{
				logger.debug("startElement() : {}", process_element_name);

				//process what's been extracted so far
				if (extracted_sub_doc!=null)
				{
					processData(filename.toString(), extracted_sub_doc);
				}

				//start a new root elem for storing extracted data
				extracted_sub_doc = Util.newDocument(true);
				if (StringUtils.isNotBlank(process_element_parent_name) && process_element_parent_namespace!=null)
				{
					Element newrootelem = extracted_sub_doc.createElementNS(process_element_parent_namespace, process_element_parent_name);
					extracted_sub_doc.appendChild(newrootelem);
					current_node = extracted_sub_doc.getDocumentElement();
				}
				else
				{
					current_node = extracted_sub_doc;
				}
			}

			if (current_node!=null)
			{
				Element newelem = extracted_sub_doc.createElementNS(uri, qName);
				for (int i=0; i<atts.getLength(); i++)
				{
					newelem.setAttributeNS(atts.getURI(i), atts.getQName(i), atts.getValue(i));
				}
				current_node = current_node.appendChild(newelem);
			}
		}


		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException
		{
			if (current_node!=null)
			{
				current_node = current_node.getParentNode();
			}
		}


		@Override
		public void characters(char[] ch, int start, int length) throws SAXException
		{
			if (current_node!=null)
			{
				Text text = extracted_sub_doc.createTextNode(new String(ch, start, length));
				current_node.appendChild(text);
			}
		}
	}



	protected void xxxprocessFile(String filename, Reader reader)
	{
		try (BufferedReader br = new BufferedReader(new FileReader(filename)))
		{
			Document reportdoc = Util.documentFromString("<Report/>");
			Element rootelem = reportdoc.getDocumentElement();

			int linecount = 0;
		    String line;
		    while ((line = br.readLine()) != null)
		    {
		    	if (StringUtils.isNotBlank(line))
		    	{
		    		char firstchar = line.charAt(0);
		    		if (firstchar!='!' && firstchar!='#')
		    		{
		    			int actionseparator = line.indexOf('|');
		    			if (actionseparator>-1)
		    			{
		    				linecount++;
		    				String action = line.substring(0, actionseparator);
							Element lineelem = reportdoc.createElement("Row");
							lineelem.setAttribute("action", "".equals(action) ? "I" : action);
							rootelem.appendChild(lineelem);
							String[] columns = line.substring(actionseparator+1).trim().split(" +");
							for (String column : columns)
							{
								Element colelem = reportdoc.createElement("Col");
								lineelem.appendChild(colelem);
								colelem.setTextContent(column);
							}
							//process data if batch is full
							if (linecount%batch_size==0)
					    	{
							    processData(filename, reportdoc);
							    //reset the document
							    reportdoc = Util.documentFromString("<Report/>");
								rootelem = reportdoc.getDocumentElement();
					    	}
		    			}
		    		}
		    	}
		    }

		    //process any elements in last batch
		    if (rootelem.getChildNodes().getLength()>0) {
		    	processData(filename, reportdoc);
		    }

		}
		catch(Exception e)
		{
			logger.error("Exception thrown processingFile", e);
		}
	}

	@Override
	protected void terminate() {
		// TODO Auto-generated method stub

	}


}
