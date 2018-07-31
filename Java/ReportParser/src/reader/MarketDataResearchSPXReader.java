package reportparser.reader;

import java.io.File;
import java.io.IOException;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class MarketDataResearchSPXReader extends MarketDataResearchFTPReader {

	@Override
	protected void processData(File file) {
		// TODO Auto-generated method stub
		logger.debug("processData");
	}

	@Override
	protected void processContent(String subject, String from, String content)
	{
		try {
			logger.debug("processContent");
			Document doc = Jsoup.parse(content);
			Element table = doc.select("table").get(0);
			StringBuffer sb = new StringBuffer();
			//System.out.println("table");
			Elements rows = table.select("tr");
			Element row = rows.get(0);
			Elements cols = row.select("td");
			sb.append(cols.get(0).text()).append(",,,,Future\n");
			sb.append("HUB,TERM,BID,OFFER,").append(cols.get(1).text()).append("\n");
			for (int i=1; i<rows.size(); i++) {
				row = rows.get(i);
				cols = row.select("td");
				sb.append("SPXVAR,").append(cols.get(0).text()).append(",").append(cols.get(2).text()).append(",").append(cols.get(3).text()).append(",").append("\n");
			}
			String date = DateTimeFormat.forPattern(dateFormat).print(DateTime.now());
			writeToFile(attachment_save_folder+File.separator+String.format(outputFile, date), sb);

			File file = new File(attachment_save_folder+File.separator+String.format(outputFile, date));
			super.sendFTP(file);
			super.sendSFTP(file);

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
