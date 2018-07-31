package reportparser.reader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

public abstract class LocalFileReportReader extends ReportReader {

	private static Logger logger = Logger.getLogger(LocalFileReportReader.class);

	protected String monitored_folder;
	protected Thread watcherthread;

	@Override
	public boolean initialise(Element config)
	{
		super.initialise(config);

		monitored_folder = Util.getNodeValueFromNodeDOM(config, "MonitoredFolder");
		logger.info("Will look at contents of " + monitored_folder + " folder for new data files");

		watcherthread = new Thread(new Runnable() {

			@Override
			public void run() {
				watchFolder(monitored_folder);
			}
		}, "LocalFileReportReader watching thread");

		watcherthread.start();
		return true;
	}

	@Override
	public void terminate()
	{
		if (watcherthread!=null) {
			watcherthread.interrupt();
		}
	}

	private void watchFolder(String szwatchedfolder)
	{
		WatchService watcher;
		Path watchedfolderpath;
		try
		{
			watcher = FileSystems.getDefault().newWatchService();
			watchedfolderpath = new File(szwatchedfolder).toPath();
			watchedfolderpath.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
			watchedfolderpath.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
		}
		catch(Exception e)
		{
			logger.error("Exception thrown setting up watcher for " + szwatchedfolder, e);
			return;
		}

		while(true)
		{
		    // wait for key to be signaled
		    WatchKey key;
		    try {
		        key = watcher.take();
		    } catch (InterruptedException x) {
		    	logger.info("Watching thread has been interrupted");
		        return;
		    }

		    for (WatchEvent<?> event: key.pollEvents()) {
		        WatchEvent.Kind<?> kind = event.kind();

		        // This key is registered only for ENTRY_CREATE and ENTRY_MODIFY events,
		        // but an OVERFLOW event can occur regardless if events are lost or discarded.
		        if (kind == StandardWatchEventKinds.OVERFLOW) {
		            continue;
		        }

		        // The filename is the context of the event.
		        @SuppressWarnings("unchecked")
				WatchEvent<Path> ev = (WatchEvent<Path>)event;
		        Path newormodifiedfile = ev.context();
		        try
		        {
		        	logger.info("New or modified file found " + newormodifiedfile + " in monitored folder");
		        	Pattern filter_pattern = Pattern.compile(filename_filter_pattern.toString());
		        	if (ReportReader.checkFilename(filter_pattern, newormodifiedfile.toString()))
		        	{
			            // Resolve the filename against the directory. If the filename is "test" and the directory is "foo",
			            // the resolved name is "test/foo".
			        	Path fullpath = watchedfolderpath.resolve(newormodifiedfile);
			        	String szfullpath = fullpath.toString();
			            logger.info("About to read data from " + newormodifiedfile);
			            InputStream inputstream = null;
			    		try
			    		{
			    			inputstream = new FileInputStream(szfullpath);
			    			processFile(szfullpath, inputstream);
			    		}
			    		catch (Exception e)
			    		{
			    			logger.error("Exception thrown loading data from file " + newormodifiedfile, e);
			    		}
			    		finally
			    		{
			    			if (inputstream!=null)
			    			{
			    				try {
			    					inputstream.close();
			    				} catch (Exception e) {
			    					logger.warn("Exception thrown closing FileReader", e);
			    				}
			    			}
			    		}
		        	}
		        	else
		        	{
		        		logger.info("Filename does not match expected pattern. Ignoring");
		        	}
		        }
		        catch (Exception e)
		        {
		            logger.error("Exception thrown reading or processing new/modified file", e);
		            continue;
		        }
		    }

		    // Reset the key -- this step is critical if you want to
		    // receive further watch events.  If the key is no longer valid,
		    // the directory is inaccessible so exit the loop.
		    boolean valid = key.reset();
		    if (!valid) {
		        break;
		    }
		}
	}
}
