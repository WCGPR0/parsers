package reportparser.energyparsers.DailySettlementReport;

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;

public class CMEReport extends DailySettlementReport {

	protected void initialize() {
		source = SOURCE.CME;
	}

	@Override
	public RecordList parse(String path, DateFormat dateFormat, String pattern, boolean isOptTrade) {
		return isOptTrade ? optParse(path, dateFormat, pattern) : nonOptParse(path, dateFormat, pattern);
	}

	private RecordList nonOptParse(String path, DateFormat dateFormat, String pattern) {
	RecordList recordList = null;

		try {
		recordList = new RecordList(false);
		URL url = new URL(path);
		PDFTextStripper stripper = new PDFTextStripper();
		PDDocument doc = null;
		doc = PDDocument.load(url);
		if (doc != null) {
			stripper.setSortByPosition(true);
			long time1 = System.currentTimeMillis();
			String pdfText = stripper.getText(doc);
			long time2 = System.currentTimeMillis();
			logger.debug("stripper.getText took " + ((time2 - time1) / 1000) + " seconds");

			Pattern p = Pattern.compile(pattern);
			Matcher m;

			BufferedReader br = new BufferedReader(new StringReader(pdfText));
			String line = null;
			String dateString = "";
			while ((line = br.readLine()) != null) {
				m = p.matcher(line);
				if (m.matches()) {
					dateString = m.group(1);
					break;
				}
			}

			Date d = new SimpleDateFormat("MMM dd, yyyy").parse(dateString);
			StringBuffer sb = new StringBuffer();
			for (String s : contractMap.keys())
				sb.append(s + "|");

			p = Pattern.compile(sb.toString().substring(0, sb.toString().length() - 1));
			while ((line = br.readLine()) != null) {
				String[] data = line.split(" ");
				if (data.length > 0) {
					m = p.matcher(data[0]);
					if (m.matches() && data.length == 1) {
						logger.error("Invalid data found in options CME: " + line);
						continue;
					}
					if (m.matches() && data[1].equals("FUT")) {
						logger.debug(m.group(0));
						for (line = br.readLine(); !line.startsWith("TOTAL"); line = br.readLine()) {
							String[] cols = line.split(" ");
							int length = cols.length;
							if (length >= 11 && length <= 20 && !cols[0].equals("PG61")) {
								double openIntChg = 0;
								if (!cols[length - 1].equals(UNCHANGED)) {
									openIntChg = Double.parseDouble(cols[length - 1])
											* (cols[length - 2].equals("-") ? -1 : 1);
									length -= 1;
								}
								String vol = cols[length - 4];
								if (vol.equals("----"))
									vol = "0";
								String pnt = cols[length - 3];
								if (pnt.equals("----"))
									pnt = "0";
								String openInt = cols[length - 2];
								if (openInt.equals("----"))
									openInt = "0";
								double settleChg = 0;
								double settlePx = 0;
								try {
									if (!cols[length - 8].equals(""))
										settlePx = Double.parseDouble(cols[length - 8]);
									if (!cols[length - 5].equals(UNCHANGED) && !cols[length - 5].equals(NEW)) {
										if (cols[length - 5].startsWith("+") || cols[length - 5].startsWith("-")) {
											settleChg = Double.parseDouble(cols[length - 5]);
											settlePx = Double.parseDouble(cols[length - 7]);
										} else {
											settleChg = Double.parseDouble(cols[length - 5])
													* (cols[length - 6].equals("-") ? -1 : 1);
										}
									}
									if (settlePx == 0) {
										if (!cols[length - 7].equals(""))
											settlePx = Double.parseDouble(cols[length - 7]);
									}
									String contract_name = m.group(0);
									for (Integer contract_ID : contractMap.get(contract_name)) {
										Record record = new Record(dateFormat);
										record.updateRecord(d, contract_ID, contract_name, cols[0],
											settlePx, settleChg, pnt, vol, openInt, openIntChg, "CME");
										recordList.add(record);
									}
								} catch (ParseException ex) {
									continue;
								} catch (NumberFormatException ex) {
									logger.debug("nfe: " + line);
									continue;
								}
							}
						}
					}
				}
			}
		}
		}
		catch (Exception e) {
			logger.error("Encountered error prior to parsing CME: " + e);
		}
		return recordList;
	}

	private RecordList optParse (String path, DateFormat dateFormat, String pattern) {
		RecordList recordList = null;
		try {
			recordList = new RecordList(true);
			URL url = new URL(path);
			PDFTextStripper stripper = new PDFTextStripper();
			PDDocument doc = null;
			doc = PDDocument.load(url);
			if (doc != null) {
				stripper.setSortByPosition(true);
				long time1 = System.currentTimeMillis();
				String pdfText = stripper.getText(doc);
				long time2 = System.currentTimeMillis();
				logger.debug("stripper.getText took " + ((time2 - time1) / 1000) + " seconds");
				Pattern p = Pattern.compile(pattern);
				Matcher m;

				BufferedReader br = new BufferedReader(new StringReader(pdfText));
				String line = null;
				String dateString = "";
				while ((line = br.readLine()) != null) {
					m = p.matcher(line);
					if (m.matches()) {
						dateString = m.group(1);
						break;
					}
				}

				Date d = new SimpleDateFormat("MMM dd, yyyy").parse(dateString);

				StringBuffer sb = new StringBuffer();
				for (String s : contractMap.keys())
					sb.append(s + "|");

				p = Pattern.compile("(" + sb.toString().substring(0, sb.toString().length() - 1) + ") (CALL|PUT).+");
				Pattern tenorPattern = Pattern.compile("\\w{3}\\d{2}");
				String code = "";
				String strategy = "";
				int[] tenor = new int[] {};
				while ((line = br.readLine()) != null) {
					m = p.matcher(line);
					Matcher tenorMatcher = tenorPattern.matcher(line);
					if (m.matches()) {
						code = m.group(1);
						strategy = m.group(2).substring(0, 1);
						logger.debug(code + " " + strategy);
					} else if (!code.isEmpty() && tenorMatcher.matches()) {
						String tenorString = line;
						tenor = getMonthDayYear(tenorString);
						logger.debug(tenor[2] + " " + tenor[0]);
					} else {
						String[] cols = line.split(" ");
						int length = cols.length;
						if (!code.isEmpty() && length >= 12 && length <= 20 && !cols[0].equals("PG63")) {
							if (cols[0].isEmpty()) {
								cols = removeFirstColFromArray(cols);
								length = cols.length;
							}
							double strike = Double.parseDouble(cols[0]) / 1000;
							String vol = cols[length - 5];
							if (vol.equals("----"))
								vol = "0";
							String pnt = cols[length - 4];
							if (pnt.equals("----"))
								pnt = "0";
							String openInt = cols[length - 3];
							if (openInt.equals("----"))
								openInt = "0";
							double settleChg = 0;
							double settlePx = 0;
							try {
								if (!cols[7].equals(""))
									settlePx = Double.parseDouble(cols[7]);
								if (!cols[8].equals(UNCHANGED) && !cols[8].equals(NEW)) {
									if (cols[8].startsWith("+") || cols[8].startsWith("-")) {
										settleChg = Double.parseDouble(cols[9]);
										if (cols[8].equals("-"))
											settleChg *= -1;
									}
								}
								String contract_name = code;
								for (Integer contract_ID : contractMap.get(contract_name)) {
									Record record = new Record(dateFormat);
									record.updateOptRecord(d, contract_ID, contract_name, String.valueOf(tenor[0]),
										String.valueOf(tenor[2]), String.valueOf(settlePx), String.valueOf(settleChg),
										vol, openInt, "CME", strike, strategy, pnt);
									recordList.add(record);
								}
							} catch (Exception ex) {
								logger.error(ex);
								logger.error("Invalid data found in options CME: " +  line);
								continue;
							}
						} else {
							if (line.startsWith("TOTAL"))
								tenor = new int[] {};
							else {
								code = "";
								strategy = "";
							}
						}
					}
				}
				br.close();
				doc.close();
			}
		} catch (Exception e) {
			logger.error(e);
			e.printStackTrace();
		}

		return recordList;
	}


	private String[] removeFirstColFromArray(String[] oldArray) {
		String[] newArray = new String[oldArray.length - 1];
		for (int i = 0; i < newArray.length; i++)
			newArray[i] = oldArray[i + 1];
		return newArray;
	}
}
