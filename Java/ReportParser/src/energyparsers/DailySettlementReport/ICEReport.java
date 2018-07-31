package reportparser.energyparsers.DailySettlementReport;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;


public class ICEReport extends DailySettlementReport  {
	private static final String numberWithComma = "(?<=\\d),(?=\\d)";

	protected void initialize() {
		source = SOURCE.ICE;
	}

	@Override
	public RecordList parse(String path, DateFormat dateFormat, String pattern, boolean isOptTrade) {
		return isOptTrade ? optParse(path, dateFormat, pattern) : nonOptParse(path, dateFormat, pattern);
	}


	private RecordList nonOptParse(String path, DateFormat dateFormat, String pattern) {
		PDFTextStripper stripper;
		RecordList recordList = null;
		try {
			recordList = new RecordList(false);
			stripper = new PDFTextStripper();
			PDDocument doc = null;
			doc = PDDocument.load(new URL(path));
			if (doc != null) {
				stripper.setSortByPosition(true);
				String pdfText = stripper.getText(doc);
				Matcher m;
				BufferedReader br = new BufferedReader(new StringReader(pdfText));
				String line = null;
				String dateString = "";
				Pattern _pattern = Pattern.compile(pattern);
				while ((line = br.readLine()) != null) {
					m = _pattern.matcher(line);
					if (m.matches()) {
						dateString = line;
						break;
					}
				}
				while ((line = br.readLine()) != null) {
					String[] data = line.split(" ");
					if (data.length > 0) {
						Pattern p = Pattern.compile(createRegex(contractMap.keySet()));
						m = p.matcher(data[0]);
						if (m.matches()) {
							try {
								String contract_name = data[0];
								for (Integer contract_ID : contractMap.get(contract_name)) {
									Record record = new Record(dateFormat);
									if (data.length == 15)
										record.updateRecord(dateString, contract_ID, contract_name, data[1], data[6],
											data[7], data[13].replaceAll(",", ""), data[8].replaceAll(",", ""),
											data[9].replaceAll(",", ""), "ICE");
									else if (data.length == 13)
										record.updateRecord(dateString, contract_ID, contract_name, data[1], data[6],
											data[7], data[11].replaceAll(",", ""), data[8].replaceAll(",", ""), "0",
											"ICE");
									else if (data.length == 11)
										record.updateRecord(dateString, contract_ID, contract_name, data[1], data[2],
											data[3], data[9].replaceAll(",", ""), data[4].replaceAll(",", ""),
											data[5].replaceAll(",", ""), "ICE");
									else if (data.length == 9)
										record.updateRecord(dateString, contract_ID, contract_name, data[1], data[2],
											data[3], data[7].replaceAll(",", ""), data[4].replaceAll(",", ""), "0",
											"ICE");
									recordList.add(record);
								}
							} catch (ParseException ex) {
								continue;
							} catch (NumberFormatException | SQLException e) {
								logger.error("Bad data while parsing ICE: " + e);
						}
					}
				}
				}
				br.close();
				doc.close();
			}
		} catch (IOException ex) {
			logger.error(ex);
		}
		return recordList;
	}

	/**
	 * Parses ICE Options. Not optimized for UK.
	 */
	private RecordList optParse(String path, DateFormat dateFormat, String pattern) {
		PDFTextStripper stripper;
		RecordList recordList = null;
		try {
			recordList = new RecordList(true);
			stripper = new PDFTextStripper();
			PDDocument doc = null;
			doc = PDDocument.load(new URL(path));
			if (doc != null) {
				stripper.setSortByPosition(true);
				String pdfText = stripper.getText(doc);
				Matcher m;
				BufferedReader br = new BufferedReader(new StringReader(pdfText));
				String line = null;
				String dateString = "";
				Pattern datePattern = Pattern.compile(pattern);
				while ((line = br.readLine()) != null) {
					m = datePattern.matcher(line);
					if (m.matches()) {
						dateString=line;
						break;
					}
				}
				while ((line = br.readLine()) != null) {
					String[] data = line.split(" ");
					if (data.length>0) {
						Pattern p = Pattern.compile(createRegex(contractMap.keySet()));
						m = p.matcher(data[0]);
						if (m.matches()) {
							try {
								String contract_name = data[0];
								for (Integer contract_ID : contractMap.get(contract_name)) {

								StringBuilder sb = new StringBuilder(); //< Contract,Month,Strike,P/C,Delta,Open,High,Low,Close,Settlement,Change,Volume,Open Int,OI Change,Exer,Block Vol,EOO Volume,Spread Vol,%s
								//Vol or OI
								if (data.length==18 && (!data[11].equals("0") || !data[12].equals("0")))
									writeLine(sb, data);
								else if (data.length==17 && (!data[10].equals("0") || !data[11].equals("0")))
									writeLineNoDelta(sb, data);
								//else if (data.length==16 && !data[11].equals("0"))
									//writeLineNoOI(bufferedWriter, data);
								else if (data.length==15 && !data[11].equals("0"))
									writeLineNoOINoChangeNoExer(sb, data);
								else if (data.length==14 && (!data[7].equals("0") || !data[8].equals("0")))
									writeLineNoOHLC(sb, data);
								else if (data.length==13 && (!data[6].equals("0") || !data[7].equals("0")))
									writeLineNoOHLCNoChange(sb, data);
								else if (data.length==11 && !data[7].equals("0"))
									writeLineNoOHLCNoOINoChangeNoExer(sb, data);
								else logger.error("Unaccounted data length: "+data.length+"  data: "+data);

								String[] data_ = sb.toString().split(",");

								if (!data_[11].equals("0") || !data_[12].equals("0")) {
									Record record = new Record(dateFormat);
									record.updateOptRecord(dateString, contract_ID, data_[0],
											String.valueOf(getMonthDayYear(data_[1])[0]),
											String.valueOf(getMonthDayYear(data_[1])[2]), data_[9], data_[10], data_[11], data_[12],
											"ICE", data_[2], data_[3], data_[15]);
									recordList.add(record);
								}

								}
							}
							catch (ParseException ex) {
								continue;
							} catch (NumberFormatException | SQLException e) {
								logger.error("Bad data while parsing ICE Options: " + e);
							}
						}
					}
				}
			}
		} catch (IOException ex) {
			logger.error(ex);
		}

		return recordList;
	}

	/** <Helper functions for ICE Options> */
    private void writeLine(StringBuilder sb, String[] line) throws IOException {
		for (int i=0; i<line.length; i++) {
			sb.append(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				sb.append(",");
		}
	}

    private void writeLineNoDelta(StringBuilder sb, String[] line) throws IOException {
		for (int i=0; i<line.length; i++) {
			sb.append(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				sb.append(",");
			if (i==3)
				sb.append(",");
		}
	}

    private void writeLineNoOHLC(StringBuilder sb, String[] line) throws IOException {
		for (int i=0; i<line.length; i++) {
			sb.append(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				sb.append(",");
			if (i==4)
				sb.append(",,,,");
		}
	}

    private void writeLineNoOHLCNoChange(StringBuilder sb, String[] line) throws IOException {
		for (int i=0; i<line.length; i++) {
			sb.append(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				sb.append(",");
			if (i==4)
				sb.append(",,,,");
			if (i==5)
				sb.append(",");
		}
	}

    private void writeLineNoOINoChangeNoExer(StringBuilder sb, String[] line) throws IOException {
		for (int i=0; i<line.length; i++) {
			sb.append(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				sb.append(",");
			if (i==11)
				sb.append(",,,");
		}
	}

    private void writeLineNoOHLCNoOINoChangeNoExer(StringBuilder sb, String[] line) throws IOException {
		for (int i=0; i<line.length; i++) {
			sb.append(line[i].replaceAll(numberWithComma, ""));
			if (i!=line.length-1)
				sb.append(",");
			if (i==4)
				sb.append(",,,,");
			if (i==7)
				sb.append(",,,");
		}
	}


    /** </Helper functions for ICE Options> */

}