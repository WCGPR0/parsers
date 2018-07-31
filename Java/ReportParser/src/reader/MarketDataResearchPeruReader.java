package reportparser.reader;

import java.io.File;
import java.io.IOException;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class MarketDataResearchPeruReader extends MarketDataResearchFTPReader {

	private String title1;
	private String title2;
	private String title3;
	private String outputFile1;
	private String outputFile2;
	private String outputFile3;
	private StringBuffer sb1 = new StringBuffer();
	private StringBuffer sb2 = new StringBuffer();
	private StringBuffer sb3 = new StringBuffer();

	@Override
	public boolean initialise(org.w3c.dom.Element config)
	{
		title1 = Util.getNodeValueFromNodeDOM(config, "Title1");
		title2 = Util.getNodeValueFromNodeDOM(config, "Title2");
		title3 = Util.getNodeValueFromNodeDOM(config, "Title3");
		outputFile1 = Util.getNodeValueFromNodeDOM(config, "OutputFile1");
		outputFile2 = Util.getNodeValueFromNodeDOM(config, "OutputFile2");
		outputFile3 = Util.getNodeValueFromNodeDOM(config, "OutputFile3");
		return super.initialise(config);
	}

	@Override
	protected void processData(File file) {
		// TODO Auto-generated method stub
		logger.debug("processData");
	}

	@Override
	protected void processContent(String subject, String from, String content) {
		try {
			logger.debug("processContent");
			Document doc = Jsoup.parse(content);
			Elements tables = doc.select("table");
			boolean print = false;
			StringBuffer sb = new StringBuffer();
			for (Element table : tables) {
				//System.out.println("table");
				Elements rows = table.select("tr");
				for (Element row : rows) {
					//System.out.println("row");
					Elements cols = row.select("td");
					if (cols.size()==1) {
						if (cols.get(0).text().trim().equals(title1)) {
							sb = sb1;
							print=true;
							continue;
						}
						else if (cols.get(0).text().trim().equals(title2)) {
							sb = sb2;
							print=true;
							continue;
						}
						else if (cols.get(0).text().trim().equals(title3)) {
							sb = sb3;
							print=true;
							continue;
						}
						else
							print=false;
					}
					else if (cols.size()==4 && cols.get(0).text().trim().isEmpty())
						print=false;
					for (Element col : cols) {
						if (print)
							sb.append(col.text().trim()).append(",");
					}
					if (print)
						sb.append("\n");
				}
			}
			String date = DateTimeFormat.forPattern(dateFormat).print(DateTime.now());

			//write files to disk
			writeToFile(attachment_save_folder+File.separator+String.format(outputFile1, date), sb1);
			writeToFile(attachment_save_folder+File.separator+String.format(outputFile2, date), sb2);
			writeToFile(attachment_save_folder+File.separator+String.format(outputFile3, date), sb3);

			//file1
			File file1 = new File(attachment_save_folder+File.separator+String.format(outputFile1, date));
			sendFTP(file1);
			sendSFTP(file1);

			//file2
			File file2 = new File(attachment_save_folder+File.separator+String.format(outputFile2, date));
			sendFTP(file2);
			sendSFTP(file2);

			//file3
			File file3 = new File(attachment_save_folder+File.separator+String.format(outputFile3, date));
			sendFTP(file3);
			sendSFTP(file3);

		}
		catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	@Override
	protected void terminate() {
		// TODO Auto-generated method stub
		super.terminate();
	}


}
