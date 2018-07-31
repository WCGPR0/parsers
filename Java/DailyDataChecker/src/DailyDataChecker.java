import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Properties;

/**
 * DailyDataChecker Version 1.02
 * 
 * 						CHANGELOG:
 * ---------------------- v1.02---------------------------------------------
 * 	TBD: Making Nasdaq compatible (FTP Requests)
 *  Made a run mode for production logic, with args --prod.
 *  Performs validation tests on files.
 * -------------------------------------------------------------------------
 * 
 * Program intended to make HTTP requests for URLS, such as ICE, CME, or SGX,
 * and then (optionally) run the appropriate parsers. It will run until all
 * successful threads makes successful requests.
 * 
 * @input Dlog4j.properties File
 * @input Properties file
 * @param Frequency,
 *            per second of how often the requests are made. This must be UNIQUE
 * @param URL,
 *            the address of the request(s) to be made
 * @param Ticks,
 *            the amount of times the thread will fetch data before dying         
 * @param output,
 *            location of the HTML UI of status (red for query, red for
 *            successful return). This must be UNIQUE
 * @param run,
 *            the program(s) to be run after all threads make a successful
 *            return. This is OPTIONAL.
 * 
 */

public class DailyDataChecker {
	public static Logger logger = Logger.getLogger(DailyDataChecker.class);
	public static BufferedWriter output_writer;
	private static Properties props;
	private static SimpleDateFormat sdf;
	private static Date today;
	public static enum MODETYPE { DEBUG, PROD };
	protected static MODETYPE mode;
	private static long guess = 0;

	/**
	 * @param args[0]
	 *            The Filepath for the properties file
	 * @param args[1] opt.
	 * 			  To enter DEBUG or RUN mode. It will be in DEBUG by default.
	 */
	public static void main(String[] args) {
		try {
			Path path = FileSystems.getDefault().getPath("./", args[0]);
			List<String> urls = Files.readAllLines(path);
			props = new Properties();
			props.load(new FileInputStream(path.toString()));
			today = new Date();
			mode = ((args.length > 1) && (args[1].toLowerCase().contains("--prod"))) ? MODETYPE.PROD : MODETYPE.DEBUG;
			new DailyDataChecker(urls);
		} catch (Exception e) {
			logger.debug("Error during initialization: " + e);
		}
	}

	DailyDataChecker(List<String> data) {
		parse(data);
	}

	public void parse(List<String> data) {
		int frequency = Integer.parseInt(props.getProperty("frequency")) * 1000;
		String output = props.getProperty("output");
		int ticks = Integer.parseInt(props.getProperty("ticks"));
		List<String> targets = data.stream().filter(item -> item.contains("URL=") || item.contains("dateFormat=") || item.contains("guess=") || item.contains("run=")).collect(Collectors.toList());
		List<Map.Entry<Integer, String> > runList = new ArrayList<Map.Entry<Integer,String> > ();
		try {
			boolean header = false;
			if (!Files.exists(Paths.get(output))) header = true;
			output_writer = Files.newBufferedWriter(Paths.get(output), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND, java.nio.file.StandardOpenOption.DSYNC);
			if (header) { output_writer.write("URL, Frequency, Start Time, ID, File Size");
			output_writer.newLine(); 
			}
		} 
		catch (FileAlreadyExistsException e) {
			
		} //Hacky way of handling writing header, consider refractoring! <<
		catch (Exception e) {
			FatalError(e);
		}
			try {
				String dateString = "";
				int size = targets.size();
				List<Integer> url_key = new ArrayList<Integer>();
				ChildDataSpawn[] ChildDataSpawns = new ChildDataSpawn[size]; // !*TBD: Wrong size! Could be improved
				for (int i = 0; i < size; i++) {
					String element = targets.get(i);
					int index;
					if ((index = element.indexOf("dateFormat=")) != -1) { //DATES
						sdf = new SimpleDateFormat(element.substring(index + 11));
						
						//Different dates for different servers, e.g TheIce.com is yesterday
						if (element.contains("ice") || element.contains("nasdaq")) {
							java.util.Calendar cal = java.util.Calendar.getInstance();
							if (cal.get(Calendar.DAY_OF_WEEK)==Calendar.MONDAY)
								cal.add(Calendar.DATE, -3);
							else
								cal.add(Calendar.DATE, -1);
							dateString = sdf.format(cal.getTime());
						}
						else
						dateString = sdf.format(today);
					}
					else if ((index = element.indexOf("guess=")) != -1){ // Guess
						guess = Long.parseLong(targets.get(i).substring(index + 6));
					}					
					else if ((index = element.indexOf("URL=")) != -1){ // URLS
						url_key.add(i);
						(ChildDataSpawns[i] = new ChildDataSpawn(new URL(String.format(targets.get(i).substring(index + 4),dateString)), frequency, ticks, guess)).start();
					}
					else if ((index = element.indexOf("run=")) != -1) { // Runs
						runList.add(new AbstractMap.SimpleEntry<Integer, String>(url_key.size()-1,targets.get(i).substring(index+4)));
					}
				}
				for (int i = 0; i < url_key.size(); i++) {
					ChildDataSpawns[url_key.get(i)].join();
					logger.debug("Thread complete. Running programs:");
					Iterator<Map.Entry<Integer, String> > it = runList.iterator();
					while (it.hasNext()) {
						Map.Entry<Integer, String> it_ = it.next();
						if (it_.getKey() == i) {
							logger.debug(i + "\t" + it_.getValue());
							it.remove();
							run(it_);
						}
					}
				}
				logger.debug("Threads completed. Requests verified. Program ran with parameters: " + targets);
				output_writer.close();
			} catch (Exception e) {
				FatalError(99, e.toString());
			}
			
			/** ! This should never happen */
			if (runList.size() != 0)	{
				try {
				runList.forEach(target -> {
					logger.debug("Extra parsers running:" + target.getValue());					
					run(target);
				});
			} catch (Exception e) {
					FatalError(e);
			}
			/** ! 						 */
			}

		}
	
	private static void run(Map.Entry<Integer, String> target) {
		try {
			ProcessBuilder processB = new ProcessBuilder(target.getValue());
			processB.directory(new File(target.getValue().substring(0,target.getValue().lastIndexOf("\\") - 1)));
			Process process = processB.start();
			BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = br.readLine()) != null) {
				System.out.println(line);
			}
		} catch (IOException e) {
			FatalError(e);
		}
	}

	/**
	 * Helper function in handling error feedback and debugging
	 * 
	 * @param error
	 *            int code specifying the error
	 */
	public static void FatalError(int error) {
		String errorMsg = "";
		switch (error) {
		case 3:
			errorMsg = "Thread error - either invalid URL or thread was interrupted";
		case 4:
			errorMsg = "Error while running Parser.";
		case 99:
			errorMsg = "A generic error has occured, please set up more specific instances of error reporting";
		default:
			break;
		}
		logger.debug(errorMsg);
		if (mode == MODETYPE.DEBUG)		
			System.exit(error);
	}

	/**
	 * Overloaded Helper function, with additional info, in handling error
	 * feedback and debugging
	 * 
	 * @param error
	 *            an int code specifying the error
	 * @param errorMsg
	 *            a String of additional information to log
	 */
	public static void FatalError(int error, String errorMsg) {
		logger.debug(errorMsg);
		FatalError(error);
	}

	/**
	 * Overloaded Helper function, try to avoid using this at all times
	 * possible.
	 * 
	 * @param exception
	 *            Uses the ToString() of Exception to get the default info
	 */
	public static void FatalError(Exception e) {
		logger.debug(e.toString());
		FatalError(99);
	}

}

/**
 * 
 * The child threads that makes the HTTP Requests
 *
 */
class ChildDataSpawn extends Thread {
	int frequency; // < Frequency in seconds of how often to query
	URL url; // < Url of the address to make the HTTP Request
	int ticks; // < Amount of ticks before expiry
	String output;
	Date currentTime;
	long guess; // < The estimated size of the file for validation in bytes

	ChildDataSpawn(URL url, int frequency, int ticks, long guess) {
		this.frequency = frequency;
		this.url = url;
		this.ticks = ticks;
		this.guess = guess;
		currentTime = new Date();
		output = url + "," + (frequency / 1000) + "," + currentTime;
	}

	@Override
	public void run() {
		while (ticks > 0) {
			try {
				DailyDataChecker.logger.debug("Fetching from: " + url);
				ReadableByteChannel rbc = Channels.newChannel(url.openStream());
				String time = String.valueOf(System.currentTimeMillis());
				File file = new File("log/" + time);
				file.getParentFile().mkdirs();
				file.createNewFile();
				FileOutputStream fos = new FileOutputStream(file);
				fos.getChannel().transferFrom(rbc,  0,  Long.MAX_VALUE);
				fos.close();
				if (DailyDataChecker.mode == DailyDataChecker.MODETYPE.PROD) file.deleteOnExit(); //This is treated as a temporary file in production
				ticks -= 1;
				output += "," + time + "," + file.length();
				DailyDataChecker.logger.debug("Thread fetched data at time: " + time);
				if (DailyDataChecker.mode == DailyDataChecker.MODETYPE.PROD) {
					DailyDataChecker.logger.debug("Validating file...");
					if (validate(file.length(), guess)) {
						DailyDataChecker.logger.debug("File has been determined as valid: "  + file);
						output += "*";
						ticks = 0; break; }
					else DailyDataChecker.logger.debug("File has been determined as invalid: " + file);
				}
				Thread.sleep(frequency);
			} catch (Exception e) {
				e.printStackTrace();
				DailyDataChecker.logger.debug(e);
			}
		}
		try {
			DailyDataChecker.logger.debug("Thread complete, successful at time: " + currentTime.toString());
				synchronized(this) {
				DailyDataChecker.output_writer.append(output);
				DailyDataChecker.output_writer.newLine();
			}
		} catch (Exception e) {
			e.printStackTrace();
			DailyDataChecker.logger.debug(e);
		}
	}
	
	private static boolean validate(long size, long guess) {
		return guess <= size;
	}
	
}