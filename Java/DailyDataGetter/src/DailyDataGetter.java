import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.gargoylesoftware.htmlunit.ConfirmHandler;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.WebWindowEvent;
import com.gargoylesoftware.htmlunit.WebWindowListener;
import com.gargoylesoftware.htmlunit.util.NameValuePair;

/** DailyDataGetter v0.01
 *  Mimics users through Selenium; bypass javascript filters on scrappers
 */
public class DailyDataGetter implements Runnable {
	
	/* DEFAULT VARIABLES */
	private static final int DEFAULT_TIMEOUT = 5;
	private static final String DEFAULT_OUTPUT = System.getProperty("user.home") + "\\Downloads\\%s.pdf";
	private static final int DEFAULT_LIMIT = 5; //<Amount of times it attempts to retry when blocked by Captcha
	/* ----------------- */
	
	/* Main DataSpawn variables */
	String url;
	List<Map.Entry<String, String> > framesAndTasks;
	String output;
	WebDriver driver;
	/* ----------------------- */
	
	
	/* Main DataSpawn constants */
	final int TIMEOUT; //< Maximum time it waits before each task
	/* ------------------------ */
	
	public static Logger logger = Logger.getLogger(DailyDataGetter.class);

	private static String secretKey = "", //< Secret API Key, keep empty unless overriding
			decaptchaPath = ""; //< Path of the decaptcha program

	/**
	 * @arg The Properties file to set up the configuration
	 *  @param secretKey (unique, opt) that overrides current API key
	 *  @param decaptchaPath (unique, opt) the path for the decoding process, defaults to current folder
	 *  @param...  TIMEOUT (opt), seconds it waits and times out on; if excluded, defaults to 5 seconds
	 *  @param... PROXY (opt), Http Proxy setting
	 *  @param... frame (DEPRECIATED), the frame to switch into to execute task in
	 * 	@param... task, the CSS selector to click on
	 *  @param... dateFormat (opt), for formatting output
	 *  @param... output, where to output the file; if excluded, defaults to Downloads folder
	 *  @param... URL, to navigate to; Sequentially, must come last
	 *
	 */
	public static void main(String[] args) throws IOException {

		Path props_path = FileSystems.getDefault().getPath(args[0]);
		secretKey = Files.readAllLines(props_path).stream().filter(item -> item.contains("secretKey=")).findFirst().orElse("");
		decaptchaPath = Files.readAllLines(props_path).stream().filter(item -> item.contains("decaptchaPath=")).findFirst().orElse(Paths.get("decaptcha.exe").toAbsolutePath().toString());
		List<String> filteredList = Files.readAllLines(props_path).stream().filter(item -> item.contains("URL=") || item.contains("run=") || item.contains("frame=") || item.contains("task=") || item.contains("output=") || item.contains("dateFormat=") || item.contains("TIMEOUT=") || item.contains("PROXY=")).collect(Collectors.toList());
		List<Map.Entry<String, String> > framesAndTasks = new ArrayList<Map.Entry<String, String> >();
		String output = DEFAULT_OUTPUT;
		List<Thread> childDataSpawns = new ArrayList<Thread>();
		SimpleDateFormat sdf = null; 
		int TIMEOUT = DEFAULT_TIMEOUT;
		String PROXY = null;
		boolean debug = (args.length > 1) ? args[1].equals("DEBUG") : false;  
		DailyDataGetter instance = null; //< Instance created by run
		
		for(String s : filteredList) {
			if (s.contains("frame="))
				framesAndTasks.add(new AbstractMap.SimpleEntry<String, String>("FRAME", s.substring(6)));
			if (s.contains("task="))
				framesAndTasks.add(new AbstractMap.SimpleEntry<String, String>("TASK", s.substring(5)));
			else if (s.contains("dateFormat="))
				sdf = new SimpleDateFormat(s.substring(11));
			else if (s.contains("output=")) {
				output = s.substring(7);
				if (output.contains("%s") && sdf != null) {
					Calendar cal = Calendar.getInstance();
					if (cal.get(Calendar.DAY_OF_WEEK)==Calendar.MONDAY)
						cal.add(Calendar.DATE, -3);
					else
						cal.add(Calendar.DATE, -1);
					Date today = cal.getTime();
					output = output.replace("%s", sdf.format(today));
				}
			}
			else if (s.contains("TIMEOUT="))  {
				TIMEOUT = Integer.parseInt(s.substring(8));
			}
			else if (s.contains("PROXY=")) {
				PROXY = s.substring(6);
			}
			else if (s.contains("URL=")) {
				String url = s.substring(4);
				Thread thread = new Thread(new DailyDataGetter(url, framesAndTasks, output, TIMEOUT, PROXY, debug));
				childDataSpawns.add(thread);
				thread.start();
			}
			else if (s.contains("run=")) {
				String run = s.substring(4);
				if (instance == null) instance = new DailyDataGetter(run, framesAndTasks, output, TIMEOUT, PROXY, debug);
				else {
					instance.url = run;
					instance.framesAndTasks = framesAndTasks;
					instance.output = output;
				}
				try {
					instance.execute();
					
					/** Flushes the parameters */
					framesAndTasks = new ArrayList<Map.Entry<String, String> >();
					output = null;
					sdf = null;
					
					
				} catch (InterruptedException e) {
					logger.error(e);
				}
			}
		}
		childDataSpawns.parallelStream().forEach( thread -> {
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});
		logger.debug("Program has successfully exeucting. Terminating..");
		
		//Additional cleanup
		if (instance != null) {
			instance.driver.close();
			instance = null;
		}
	}
	
	DailyDataGetter(String url, List<Map.Entry<String, String> > framesAndTasks, String output, int TIMEOUT, String PROXY, boolean debug) {
		this.url = url;
		this.framesAndTasks = framesAndTasks;
		this.output = output;
		this.driver = null;
		this.TIMEOUT = TIMEOUT;
		try {
		if (debug) {
			System.setProperty("webdriver.chrome.driver","C:\\Selenium\\chromedriver.exe");
			driver = new ChromeDriver();
			//System.setProperty("webdriver.ie.driver", "C:\\Selenium\\IEDriverServer.exe");
			//driver = new InternetExplorerDriver();
		}
		else
		driver = new HtmlUnitDriver(true) {
			protected WebClient modifyWebClient(WebClient client)
			{
				 ConfirmHandler okHandler = new ConfirmHandler(){
					 	@Override
	                    public boolean handleConfirm(Page page, String message) {
	                        return true;
	                    }
	             };
	             client.setConfirmHandler(okHandler);

	             client.addWebWindowListener(new WebWindowListener() {

	                public void webWindowOpened(WebWindowEvent event) {}

	                public void webWindowContentChanged(WebWindowEvent event) {

	                    WebResponse response = event.getWebWindow().getEnclosedPage().getWebResponse();
	                    System.out.println(response.getLoadTime());
	                    System.out.println(response.getStatusCode());
	                    System.out.println(response.getContentType());

	                    List<NameValuePair> headers = response.getResponseHeaders();
	                    for(NameValuePair header: headers){
	                        System.out.println(header.getName() + " : " + header.getValue());
	                    }

	                    //Downloads only PDF files
	                    if(response.getContentType().equals("application/pdf")){
	                    	// Gets the file
	            	        InputStream inputStream = null;

	            	        // write the inputStream to a FileOutputStream
	            	        OutputStream outputStream = null; 

	            	        try {       
	            	            inputStream = response.getContentAsStream();
	            	            // write the inputStream to a FileOutputStream
	            	            outputStream = new FileOutputStream(new File(output));
	            	            int read = 0;
	            	            byte[] bytes = new byte[1024];

	            	            while ((read = inputStream.read(bytes)) != -1) {
	            	                outputStream.write(bytes, 0, read);
	            	            }

	            	            System.out.println("Done!");

	            	        } catch (IOException e) {
	            	            e.printStackTrace();
	            	        } finally {
	            	            if (inputStream != null) {
	            	                try {
	            	                    inputStream.close();
	            	                } catch (IOException e) {
	            	                    e.printStackTrace();
	            	                }
	            	            }
	            	            if (outputStream != null) {
	            	                try {
	            	                    // outputStream.flush();
	            	                    outputStream.close();
	            	                } catch (IOException e) {
	            	                    e.printStackTrace();
	            	                }

	            	            }
	            	        }
	                    }
	                }

	                public void webWindowClosed(WebWindowEvent event) {}
	            });          

	             return client; 
	       }  
		};
		if (PROXY != null) {
			logger.debug("Setting up Proxy, using proxy settings: " + PROXY);
			Proxy proxy = new Proxy();
			proxy.setHttpProxy(PROXY);
			if (!debug)
				((HtmlUnitDriver)driver).setProxySettings(proxy);
			}
		}
		catch (Exception e) {
			logger.error("ERROR: " + e);
		}
	}

	@Override
	public void run() {
		try {
			execute();
		} catch (InterruptedException e) {
			logger.error(e);
		}
		finally {
			driver.close();
		}
	}
	
	public void execute() throws InterruptedException {
		driver.navigate().to(url);
		for (Map.Entry<String, String> entry : framesAndTasks) {
			try {
			if (entry.getKey().equals("FRAME")) {
				switchFrame(entry.getValue(), TIMEOUT);
			}
			else if (entry.getKey().equals("TASK")) {
				/** <!-- Checking for Recaptcha Popup --> */
				WebElement captchaChallenge = null;
				boolean recaptchaFlag = false; //< True if the recaptcha dialog is present
				try {
					
					recaptchaFlag = !((JavascriptExecutor) driver).executeScript("return window.getComputedStyle(document.querySelector('#report-captcha')).display").equals("none");

				} catch (Exception e) {}
				
				if (recaptchaFlag) {					
					try {
					
					switchFrame(".g-recaptcha iframe", 5); // Switch to Recaptcha Frame
					
					click("#recaptcha-anchor", 5); //Click recaptcha checkbox
					
					switchFrame("", 5); // Switch back to default frame
					
					captchaChallenge = driver.findElement(By.cssSelector("body > div:last-child"));
					
					if (captchaChallenge != null && ((Long)((JavascriptExecutor) driver).executeScript("return parseInt(window.getComputedStyle(document.querySelector(\"body > div:last-child\")).getPropertyValue(\"top\"))") > 0)) {
						recaptcha(captchaChallenge);  //Challenge Recaptcha
					}
					}
					catch (Exception e) {}
					finally {
						switchFrame("", 5); // Switch back to default frame
						click("input.btn", TIMEOUT); // Click blue, I Accept, button
					}
				}
				/** </!-- Checking for Recaptcha Popup --> */
				
				click(entry.getValue(), TIMEOUT);
				
			}
			}
			catch (Exception e) {
				logger.error("Bad element, please recheck css path:\t" +entry.getValue() + "\t" + e);
				/*File src= ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
				File tempFile = File.createTempFile("DailyDataGetter-",".png");
				FileUtils.copyFile(src, tempFile);
				logger.error("See snapshot: " + tempFile.getAbsolutePath());*/
			}
			}
		TimeUnit.SECONDS.sleep(TIMEOUT*DEFAULT_TIMEOUT); //hacky way to wait for download
	}
	
	/*** Helper method to switch frames
	 * @param path, String css path to frame 
	 * @throws IOException 
	 * @throws InterruptedException */
	private void switchFrame(String path, int timeout) throws IOException, InterruptedException {
		TimeUnit.SECONDS.sleep(timeout); //Sleeps, to allow slower browsers to load
		WebElement element = null;
		logger.debug(String.format("Changing frames: ", path));
		try { 
			if (path.isEmpty()) driver.switchTo().defaultContent();
			else {
				element = driver.findElement(By.cssSelector(path));
				(new WebDriverWait(driver, TIMEOUT)).until(ExpectedConditions.visibilityOf(element));
				driver.switchTo().frame(element);
			}
		} 
		catch (Exception e) {
			logger.debug("Error switching frame: " + path + "\n" + e);
			File src= ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
			File tempFile = File.createTempFile("DailyDataGetter-",".png");
			FileUtils.copyFile(src, tempFile);
			logger.error("See snapshot: " + tempFile.getAbsolutePath());
		}
	}
	
	/*** Helper method to click on element
	 * @param path, String css path to element 
	 * @throws IOException 
	 * @throws InterruptedException */
	private void click(String path, int timeout) throws IOException, InterruptedException {
		TimeUnit.SECONDS.sleep(timeout); //Sleeps, to allow slower browsers to load
		WebElement element = null;
		logger.debug("Clicking next CSS selector: " + path);			
		try { 
		element = driver.findElement(By.cssSelector(path));		
		(new WebDriverWait(driver, TIMEOUT)).until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		} 
		catch (Exception e) {
			boolean invisible = !element.isDisplayed() && !element.isEnabled();
			logger.debug("Element is unclickable, executing javascript click; invisible = " + (invisible ? "Y" : "N") +  "\t" + e);
			((JavascriptExecutor)driver).executeScript("arguments[0].checked = true; arguments[0].click()", element);
			File src= ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
			File tempFile = File.createTempFile("DailyDataGetter-",".png");
			FileUtils.copyFile(src, tempFile);
			logger.error("See snapshot: " + tempFile.getAbsolutePath());
		}
	}
	
	
	/*** Attempts to bypass recaptcha v2.0
	 * @throws IOException 
	 * @throws MalformedURLException 
	 * @throws InterruptedException */
	private void recaptcha(WebElement captchaChallenge) throws MalformedURLException, IOException, InterruptedException {
		 WebElement siteKeyElement = driver.findElement(By.cssSelector("[data-sitekey]"));
		 String siteKey =siteKeyElement.getAttribute("data-sitekey");
		boolean block = (driver.findElements(By.cssSelector(".rc-doscaptcha-header-text")).size() != 0);
		for (int i = 0; i < DEFAULT_LIMIT && block; i++) {
			block = (driver.findElements(By.cssSelector(".rc-doscaptcha-header-text")).size() != 0);
	        (new Actions(driver)).click().build().perform();
	        logger.debug("Blocked. Reattempting:" + i + "/" + DEFAULT_LIMIT);
		}
		if (block) return;
		 
        // Decodes the file image
        logger.debug("Decoding captcha using siteKey:\t" +siteKey);
        Process decryptProcess = new ProcessBuilder(decaptchaPath,siteKey, driver.getCurrentUrl(),secretKey).start();
        BufferedReader br = new BufferedReader(new InputStreamReader(decryptProcess.getInputStream()));
        String line = "", decodedMsg = "";
        while ((line = br.readLine()) != null) {
			Matcher key = Pattern.compile("([^:]*)").matcher(line);
			Matcher value = Pattern.compile("\\{(.*)\\}").matcher(line);
			if (key.find() && value.find()) {
				String key_ = key.group(1);
				String value_ = value.group(1);
				switch (key_) {
				case "DECODE":
					decodedMsg = value_;
					break;
				default:
					logger.debug(line);
				}
			}
			
		}
        // Finished decoding the file image
        logger.info("Decoded recaptcha response: " + decodedMsg);
        ((JavascriptExecutor) driver).executeScript("document.getElementById(\"g-recaptcha-response\").innerHTML=\"" + decodedMsg + "\";"); 
        (new Actions(driver)).moveByOffset(0, -60).click().build().perform();
		TimeUnit.SECONDS.sleep(TIMEOUT); //Additional small delay here to account for slower server, and to make sure request gets recieved			
	}
}
