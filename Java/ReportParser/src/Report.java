package reportparser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.quartz.Job;

public abstract class Report implements Job {

	private static Logger logger = Logger.getLogger(Report.class);
	protected URI url_source; //the source that the url transform tow which the URL transform is applied (can be null, file, web document etc)
	protected String url_transform_name;
	protected String data_transform_name;
	protected String save_request;

	public void setURLSource(String value)
	{
		String szurl_source = value;
		if (StringUtils.isNotBlank(szurl_source))
		{
			try
			{
				url_source = new URI(szurl_source);
			}
			catch (URISyntaxException e)
			{
				logger.error("setURLSource() : Exception thrown parsing value supplied [" + value + "]", e);
			}
		}
	}

	public void setURLTransform(String value)
	{
		url_transform_name = value;
	}

	public void setDataTransform(String value)
	{
		data_transform_name = value;
	}

	public void setSaveRequest(String value)
	{
		save_request = value;
	}


	//extract zip file and return references to the extracted files
	protected static List<File> extractZip(File zipfile) throws IOException
	{
		LinkedList<File> zipfileentries = new LinkedList<File>();

		// create a buffer to improve copy performance later.
		byte[] buffer = new byte[2048];

		// open the zip file stream
		InputStream theFile = new FileInputStream(zipfile);
		ZipInputStream stream = new ZipInputStream(theFile);
		String outdir = zipfile.getParent();
		try
		{
			// now iterate through each item in the stream.The get next
			// entry call will return a ZipEntry for each file in the
			// stream
			ZipEntry entry;
			while((entry = stream.getNextEntry())!=null)
			{
				String s = String.format("Entry: %s len %d added %TD", entry.getName(), entry.getSize(), new Date(entry.getTime()));
				logger.debug(s);

				// Once we get the entry from the stream, the stream is
				// positioned ready to read the raw data, and we keep
				// reading until read returns 0 or less.
				String outpath = outdir + "/" + entry.getName();
				FileOutputStream output = null;
				try
				{
					output = new FileOutputStream(outpath);
					int len = 0;
					while ((len = stream.read(buffer)) > 0)
					{
						output.write(buffer, 0, len);
					}
					zipfileentries.add(new File(outpath));
				}
				finally
				{
					// we must always close the output file
					if(output!=null) output.close();
				}
			}
		}
		finally
		{
			// we must always close the zip file.
			stream.close();
		}
		return zipfileentries;
	}


}