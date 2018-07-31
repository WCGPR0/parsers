package DailySettlementDataParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;

public class DailySettlementDataParser {
	private static Logger logger = Logger.getLogger(MainExecutor.class);
	private Properties props = new Properties();
	protected String futUrl;
	protected String optUrl;
	protected String iceUrl;
	protected String headerFile;
	protected String optHeaderFile;
	protected int iceReports;
	protected String iceBasisFile;
	protected String iceBasisHeaderFile;
	private Pattern iceDatePattern;
	private static DateFormat iceDateFormat;
	private DateFormat cmeBulletinDateFormat;
	private static DateFormat cmeDateFormat;
	private static DateFormat nasdaqDateFormat;
	private static SimpleDateFormat iceFileSdf = new SimpleDateFormat("yyyy_MM_dd");

	public static enum MODETYPE_ {
		CME, CME_OPT, ICE, ICE_OPT, NASDAQ
	};

	private static final String UNCHANGED = "UNCH";
	private static final String NEW = "NEW";

	public DailySettlementDataParser(InputStream propsFile) throws SQLException {
		try {
			props.load(propsFile);
			futUrl = props.getProperty("url");
			optUrl = props.getProperty("optUrl");
			iceUrl = props.getProperty("iceUrl");
			headerFile = props.getProperty("headerFile");
			optHeaderFile = props.getProperty("optHeaderFile");
			iceBasisFile = props.getProperty("iceBasisFile");
			iceBasisHeaderFile = props.getProperty("iceBasisHeaderFile");
			iceDateFormat = new SimpleDateFormat(props.getProperty("iceDateFormat"));
			cmeBulletinDateFormat = new SimpleDateFormat(props.getProperty("cmeBulletinDateFormat"));
			cmeDateFormat = new SimpleDateFormat(props.getProperty("cmeDateFormat"));
			nasdaqDateFormat = new SimpleDateFormat(props.getProperty("nasdaqDateFormat"));

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Map.Entry<MODETYPE_, List<Record>> parse(MODETYPE_ mode_) {

		logger.debug("parse " + mode_);
		Map.Entry<MODETYPE_, List<Record>> recordList = null;
		iceDatePattern = Pattern.compile(props.getProperty("iceDatePattern"));
		if (mode_.equals(MODETYPE_.CME)) {
			recordList = new AbstractMap.SimpleEntry<MODETYPE_, List<Record>>(mode_, cmeParse());
		} else if (mode_.equals(MODETYPE_.CME_OPT)) {
			recordList = new AbstractMap.SimpleEntry<MODETYPE_, List<Record>>(mode_, cmeOptParse());
		} else if (mode_.equals(MODETYPE_.ICE)) {
			List<Record> iceRecords = iceParse();
			iceRecords.addAll(iceBasisParse());
			recordList = new AbstractMap.SimpleEntry<MODETYPE_, List<Record>>(mode_, iceRecords);
		} else if (mode_.equals(MODETYPE_.ICE_OPT)) {
			recordList = new AbstractMap.SimpleEntry<MODETYPE_, List<Record>>(mode_, iceOptParse());
		} else if (mode_.equals(MODETYPE_.NASDAQ)) {
			recordList = new AbstractMap.SimpleEntry<MODETYPE_, List<Record>>(mode_, nasdaqParse());
		}
		return recordList;

	}

	public List<Record> cmeParse() {
		List<Record> recordList = null;
		try {
			recordList = new ArrayList<Record>();
			// Parse File
			List<String> titleList = new ArrayList<String>();
			HashMap<String, Integer> contractMap = new HashMap<String, Integer>();
			try {
				BufferedReader in = new BufferedReader(new FileReader(headerFile));
				String str;
				while ((str = in.readLine()) != null) {
					String[] titleContractPair = str.split(",");
					titleList.add(titleContractPair[0]);
					contractMap.put(titleContractPair[0], Integer.parseInt(titleContractPair[1]));
				}
				in.close();
			} catch (IOException e) {
				logger.error(e);
				e.printStackTrace();
			}

			// create a url object
			URL url = new URL(futUrl);
			logger.debug("Parsing " + futUrl);
			PDFTextStripper stripper = new PDFTextStripper();
			PDDocument doc = null;
			doc = PDDocument.load(url);
			if (doc != null) {
				stripper.setSortByPosition(true);
				long time1 = System.currentTimeMillis();
				// System.setProperty("java.util.Arrays.useLegacyMergeSort",
				// "true");
				String pdfText = stripper.getText(doc);
				long time2 = System.currentTimeMillis();
				logger.debug("stripper.getText took " + ((time2 - time1) / 1000) + " seconds");
				Pattern p = Pattern.compile(props.getProperty("cmeDatePattern"));
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

				Date d = cmeBulletinDateFormat.parse(dateString);
				// logger.debug(dateString);
				StringBuffer sb = new StringBuffer();
				for (String s : titleList)
					sb.append(s + "|");

				p = Pattern.compile(sb.toString().substring(0, sb.toString().length() - 1));
				while ((line = br.readLine()) != null) {
					String[] data = line.split(" ");
					if (data.length > 0) {
						m = p.matcher(data[0]);
						if (m.matches() && data.length == 1) {
							logger.error(line);
							continue;
						}
						if (m.matches() && data[1].equals("FUT")) {
							logger.debug(m.group(0));
							for (line = br.readLine(); !line.startsWith("TOTAL"); line = br.readLine()) {
								String[] cols = line.split(" ");
								int length = cols.length;
								if (length >= 11 && length <= 20 && !cols[0].equals("PG61")) {
									// logger.debug(line);
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
										Record record = new Record();
										record.updateRecord(d, contractMap.get(m.group(0)), m.group(0), cols[0],
												settlePx, settleChg, pnt, vol, openInt, openIntChg, "CME");
										recordList.add(record);
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
				br.close();
				doc.close();
			}
		} catch (Exception e) {
			logger.error(e);
			e.printStackTrace();
		}
		return recordList;
	}

	public List<Record> cmeOptParse() {
		List<Record> recordList = null;
		try {
			recordList = new ArrayList<Record>();
			// Parse File
			List<String> titleList = new ArrayList<String>();
			HashMap<String, Integer> contractMap = new HashMap<String, Integer>();
			try {
				BufferedReader in = new BufferedReader(new FileReader(optHeaderFile));
				String str;
				while ((str = in.readLine()) != null) {
					String[] titleContractPair = str.split(",");
					titleList.add(titleContractPair[0]);
					contractMap.put(titleContractPair[0], Integer.parseInt(titleContractPair[1]));
				}
				in.close();
			} catch (IOException e) {
				logger.error(e);
				e.printStackTrace();
			}

			// create a url object
			URL url = new URL(optUrl);
			logger.debug("Parsing " + optUrl);
			PDFTextStripper stripper = new PDFTextStripper();
			PDDocument doc = null;
			doc = PDDocument.load(url);
			if (doc != null) {
				stripper.setSortByPosition(true);
				long time1 = System.currentTimeMillis();
				String pdfText = stripper.getText(doc);
				long time2 = System.currentTimeMillis();
				logger.debug("stripper.getText took " + ((time2 - time1) / 1000) + " seconds");
				Pattern p = Pattern.compile(props.getProperty("cmeDatePattern"));
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

				Date d = cmeBulletinDateFormat.parse(dateString);

				StringBuffer sb = new StringBuffer();
				for (String s : titleList)
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
								Record record = new Record();
								record.updateOptRecord(d, contractMap.get(code), code, String.valueOf(tenor[0]),
										String.valueOf(tenor[2]), String.valueOf(settlePx), String.valueOf(settleChg),
										vol, openInt, "CME", strike, strategy, pnt);
								recordList.add(record);
							} catch (Exception ex) {
								logger.error(ex);
								logger.error(line);
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

	private List<Record> iceParse() {
		List<Record> recordList = new ArrayList<Record>();
		iceReports = Integer.parseInt(props.getProperty("iceReports"));
		Calendar cal = Calendar.getInstance();
		if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY)
			cal.add(Calendar.DATE, -3);
		else
			cal.add(Calendar.DATE, -1);
		HashMap<String, Integer> contractMap = new HashMap<String, Integer>();
		for (int i = 1; i <= iceReports; i++) {
			contractMap.clear();
			try {
				BufferedReader in = new BufferedReader(new FileReader(props.getProperty("iceHeaderFile" + i)));
				String str;
				while ((str = in.readLine()) != null) {
					String[] titleContractPair = str.split(",");
					contractMap.put(titleContractPair[0], Integer.parseInt(titleContractPair[1]));
				}
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			String urlString = String.format(props.getProperty("iceUrl" + i), iceFileSdf.format(cal.getTime()));
			recordList.addAll(iceParse(urlString, contractMap));
		}
		return recordList;
	}

	private List<Record> iceParse(String urlString, HashMap<String, Integer> contractMap) {
		URL url = null;
		PDFTextStripper stripper;
		List<Record> recordList = null;
		try {
			recordList = new ArrayList<Record>();
			logger.debug("Parsing ICE " + urlString);
			url = new URL(urlString);
			stripper = new PDFTextStripper();
			PDDocument doc = null;
			doc = PDDocument.load(url);
			if (doc != null) {
				stripper.setSortByPosition(true);
				long time1 = System.currentTimeMillis();
				String pdfText = stripper.getText(doc);
				long time2 = System.currentTimeMillis();
				logger.debug("stripper.getText took " + ((time2 - time1) / 1000) + " seconds");
				Matcher m;
				BufferedReader br = new BufferedReader(new StringReader(pdfText));
				String line = null;
				String dateString = "";
				while ((line = br.readLine()) != null) {
					m = iceDatePattern.matcher(line);
					if (m.matches()) {
						// logger.debug("Date: "+line);
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
							Record record = new Record();
							try {
								if (data.length == 15)
									record.updateRecord(dateString, contractMap.get(data[0]), data[0], data[1], data[6],
											data[7], data[13].replaceAll(",", ""), data[8].replaceAll(",", ""),
											data[9].replaceAll(",", ""), "ICE");
								else if (data.length == 13)
									record.updateRecord(dateString, contractMap.get(data[0]), data[0], data[1], data[6],
											data[7], data[11].replaceAll(",", ""), data[8].replaceAll(",", ""), "0",
											"ICE");
								else if (data.length == 11)
									record.updateRecord(dateString, contractMap.get(data[0]), data[0], data[1], data[2],
											data[3], data[9].replaceAll(",", ""), data[4].replaceAll(",", ""),
											data[5].replaceAll(",", ""), "ICE");
								else if (data.length == 9)
									record.updateRecord(dateString, contractMap.get(data[0]), data[0], data[1], data[2],
											data[3], data[7].replaceAll(",", ""), data[4].replaceAll(",", ""), "0",
											"ICE");
							} catch (SQLException ex) {
								logger.debug(line);
								logger.error(ex);
								ex.printStackTrace();
							} catch (ParseException ex) {
								continue;
							}
							recordList.add(record);
						}
					}
				}
				br.close();
				doc.close();
				logger.debug("done parsing ICE");
			}
		} catch (IOException ex) {
			logger.error(ex);
		}
		return recordList;
	}

	private List<Record> iceBasisParse() {
		List<Record> recordList = null;
		try {
			recordList = new ArrayList<Record>();
			// Parse File
			List<String> titleList = new ArrayList<String>();
			HashMap<String, Integer> contractMap = new HashMap<String, Integer>();
			try {
				BufferedReader in = new BufferedReader(new FileReader(iceBasisHeaderFile));
				String str;
				while ((str = in.readLine()) != null) {
					String[] titleContractPair = str.split(",");
					titleList.add(titleContractPair[0]);
					contractMap.put(titleContractPair[0], Integer.parseInt(titleContractPair[1]));
				}
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			BufferedReader br = new BufferedReader(new FileReader(iceBasisFile));
			String line = br.readLine();
			String[] data = line.split(",");
			String dateString = data[data.length - 1];
			while ((line = br.readLine()) != null) {
				data = line.split(",");
				if (titleList.contains(data[0]) && (!data[3].equals("0") || !data[6].equals("0"))) {
					Record record = new Record();
					record.updateRecord(dateString, contractMap.get(data[0]), data[0], data[1], data[2], data[5],
							data[4], data[3], data[6], "ICE");
					recordList.add(record);
				}
			}
			br.close();
			logger.debug("done parsing ICE basis");
		} catch (Exception ex) {
			logger.error(ex);
			ex.printStackTrace();
		}
		return recordList;
	}

	private List<Record> iceOptParse() {
		List<Record> recordList = null;
		try {
			recordList = new ArrayList<Record>();
			int iceOptReports = Integer.parseInt(props.getProperty("iceOptReports"));
			List<String> titleList = new ArrayList<String>();
			HashMap<String, Integer> contractMap = new HashMap<String, Integer>();
			for (int i = 1; i <= iceOptReports; i++) {
				titleList.clear();
				contractMap.clear();
				try {
					// Parse File
					BufferedReader in = new BufferedReader(new FileReader(props.getProperty("iceOptHeaderFile" + i)));
					String str;
					while ((str = in.readLine()) != null) {
						String[] titleContractPair = str.split(",");
						titleList.add(titleContractPair[0]);
						contractMap.put(titleContractPair[0], Integer.parseInt(titleContractPair[1]));
					}
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				logger.debug("Parsing ICE " + props.getProperty("iceOptFile" + i));
				BufferedReader br = new BufferedReader(new FileReader(props.getProperty("iceOptFile" + i)));
				String line = br.readLine();
				if (line == null) continue;
				String[] data = line.split(",");
				String dateString = data[data.length - 1];
				while ((line = br.readLine()) != null) {
					data = line.split(",");
					if (titleList.contains(data[0]) && (!data[11].equals("0") || !data[12].equals("0"))) {
						Record record = new Record();
						record.updateOptRecord(dateString, contractMap.get(data[0]), data[0],
								String.valueOf(getMonthDayYear(data[1])[0]),
								String.valueOf(getMonthDayYear(data[1])[2]), data[9], data[10], data[11], data[12],
								"ICE", data[2], data[3], data[15]);
						recordList.add(record);
					}
				}
				br.close();
				logger.debug("done parsing ICE options");
			}
		} catch (Exception ex) {
			logger.error(ex);
			ex.printStackTrace();
		}
		return recordList;
	}

	private List<Record> nasdaqParse() {
		List<Record> recordList = null;
		try {
			recordList = new ArrayList<Record>();
			Calendar cal = Calendar.getInstance();
			if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY)
				cal.add(Calendar.DATE, -3);
			else
				cal.add(Calendar.DATE, -1);
			HashMap<String, Integer> contractMap = new HashMap<String, Integer>();
			HashMap<String, Integer> optionsContractMap = new HashMap<String, Integer>();
			try {
				BufferedReader in = new BufferedReader(new FileReader(props.getProperty("nasdaqHeaderFile")));
				String str;
				while ((str = in.readLine()) != null) {
					String[] titleContractPair = str.split(",");
					contractMap.put(titleContractPair[0], Integer.parseInt(titleContractPair[1]));
				}
				in.close();
				in = new BufferedReader(new FileReader(props.getProperty("nasdaqOptionsHeaderFile")));
				while ((str = in.readLine()) != null) {
					String[] titleContractPair = str.split(",");
					optionsContractMap.put(titleContractPair[0], Integer.parseInt(titleContractPair[1]));
				}
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			Pattern p = Pattern.compile(createNasdaqRegex(contractMap.keySet()));
			Pattern op = Pattern.compile(createNasdaqOptionsRegex(optionsContractMap.keySet()));
			String dateString = nasdaqDateFormat.format(cal.getTime());
			String urlString = String.format(props.getProperty("nasdaqUrl"), dateString);
			logger.debug(urlString);
			URL url = new URL(urlString);
			// create a urlconnection object
			URLConnection urlConnection = url.openConnection();
			// wrap the urlconnection in a bufferedreader
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));

			String line;
			String[] lineData;
			// read from the urlconnection via the bufferedreader
			Matcher m;
			while ((line = bufferedReader.readLine()) != null) {
				try {
				lineData = line.split(",");
				m = p.matcher(lineData[0]);
				if (m.matches() && lineData.length >= 12) {
					String code = m.group(0);
					Record record = new Record();
					record.updateRecord(dateString, contractMap.get(code.substring(0, code.length() - 2)),
							code.substring(0, code.length() - 2), code.substring(code.length() - 2), lineData[11], null,
							lineData[5], lineData[3], lineData[6], "NASDAQ");
					recordList.add(record);
				} else {
					m = op.matcher(lineData[0]);
					if (m.matches() && lineData.length >= 12 && (lineData[5].length() > 0 || lineData[3].length() > 0
							|| (lineData[6].length() > 0 && !lineData[6].equals("0")))) {
						String[] code = m.group(0).split("_");
						if (code.length != 2) {
							logger.debug("Bad data not added: " + line);
							continue;
						}
						int[] monthDayYear = getMonthDayYear(code[0].substring(code[0].length() - 2));
						Record record = new Record();
						record.updateOptRecord(cal.getTime(),
								optionsContractMap.get(code[0].substring(0, code[0].length() - 2)),
								code[0].substring(0, code[0].length() - 2), monthDayYear[0], monthDayYear[2],
								lineData[11], null, lineData[3], lineData[6], "NASDAQ",
								Double.parseDouble(code[1].substring(0, code[1].length() - 1)),
								code[1].substring(code[1].length() - 1), lineData[5]);
						recordList.add(record);
					}
				}
				}
				catch (Exception ex) {
					logger.error(ex);
				}
			}
			bufferedReader.close();
			logger.debug("done parsing Nasdaq");
		} catch (Exception ex) {
			logger.error(ex);
		}
		return recordList;
	}

	public static Connection getConnection(String dbUrl, String dbUser, String dbPassword) throws Exception {
		String driver = "oracle.jdbc.driver.OracleDriver";
		Class.forName(driver);
		Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
		return conn;
	}

	private static int[] getMonthDayYear(String input) throws ParseException {
		int month = -1;
		int day = -1;
		int year = -1;
		if (input.length() == 2 || input.length() == 3) {
			switch (input.charAt(0)) {
			case 'F':
				month = 1;
				break;
			case 'G':
				month = 2;
				break;
			case 'H':
				month = 3;
				break;
			case 'J':
				month = 4;
				break;
			case 'K':
				month = 5;
				break;
			case 'M':
				month = 6;
				break;
			case 'N':
				month = 7;
				break;
			case 'Q':
				month = 8;
				break;
			case 'U':
				month = 9;
				break;
			case 'V':
				month = 10;
				break;
			case 'X':
				month = 11;
				break;
			case 'Z':
				month = 12;
				break;
			}
			if (input.length() == 2) {
				int iYear = Integer.parseInt(input.substring(1));
				if (iYear >= 6)
					year = 2010 + iYear;
				else
					year = 2020 + iYear;
			} else
				year = 2000 + Integer.parseInt(input.substring(1));
		} else if (input.length() == 7) {
			// logger.warn("DDMMMYY: "+input);
			day = Integer.parseInt(input.substring(0, 2));
			month = getMonth(input.substring(2, 5));
			year = 2000 + Integer.parseInt(input.substring(5));
		} else if (input.length() == 9) {
			// logger.warn("MMM-DD-YY: "+input);
			day = Integer.parseInt(input.substring(4, 6));
			month = getMonth(input.substring(0, 3));
			year = 2000 + Integer.parseInt(input.substring(7));
		} else {
			String monthcode = input.substring(0, 3);
			month = getMonth(monthcode);
			year = 2000 + Integer.parseInt(input.substring(input.length() - 2));
		}
		return new int[] { month, day, year };
	}

	private static int getMonth(String monthcode) throws ParseException {
		int month = -1;
		if (monthcode.equalsIgnoreCase("JAN"))
			month = 1;
		else if (monthcode.equalsIgnoreCase("FEB"))
			month = 2;
		else if (monthcode.equalsIgnoreCase("MAR"))
			month = 3;
		else if (monthcode.equalsIgnoreCase("APR"))
			month = 4;
		else if (monthcode.equalsIgnoreCase("MAY"))
			month = 5;
		else if (monthcode.equalsIgnoreCase("JUN"))
			month = 6;
		else if (monthcode.equalsIgnoreCase("JUL"))
			month = 7;
		else if (monthcode.equalsIgnoreCase("AUG"))
			month = 8;
		else if (monthcode.equalsIgnoreCase("SEP"))
			month = 9;
		else if (monthcode.equalsIgnoreCase("OCT"))
			month = 10;
		else if (monthcode.equalsIgnoreCase("NOV"))
			month = 11;
		else if (monthcode.equalsIgnoreCase("DEC"))
			month = 12;
		else {
			logger.error("could not parse monthcode: " + monthcode);
			throw new ParseException("Invalid monthcode: " + monthcode, 0);
		}
		return month;
	}

	private String createRegex(Set<String> keys) {
		StringBuffer sb = new StringBuffer();
		for (String key : keys) {
			sb.append("|").append(key);
		}
		return sb.toString().substring(1);
	}

	private String createNasdaqRegex(Set<String> keys) {
		StringBuffer sb = new StringBuffer();
		for (String key : keys) {
			sb.append("|").append(key).append("\\w\\d");
		}
		return sb.toString().substring(1);
	}

	private String createNasdaqOptionsRegex(Set<String> keys) {
		StringBuffer sb = new StringBuffer();
		for (String key : keys) {
			sb.append("|").append(key).append("\\w\\d_.+");
		}
		return sb.toString().substring(1);
	}

	private String[] removeFirstColFromArray(String[] oldArray) {
		String[] newArray = new String[oldArray.length - 1];
		for (int i = 0; i < newArray.length; i++)
			newArray[i] = oldArray[i + 1];
		return newArray;
	}

	public static class Record {
		public String prd_code = "EN";
		public String source;
		public java.sql.Date report_date;
		public Integer contract_ID;
		public String contract_name;
		public Integer month;
		public Integer year;
		public Double settlement_price;
		public Integer volume;
		public Integer open_int;
		public Double block_volume;
		public Double change;
		public Double strike; //Options Only
		public String strategy; // Options Only
		public Double oi_change; //Non Options Only
		public Integer day = 0; //Non Options Only

		public Record(Record record) {
			this.prd_code = record.prd_code;
			this.source = record.source;
			this.report_date = record.report_date;
			this.contract_ID = record.contract_ID;
			this.contract_name = record.contract_name;
			this.month = record.month;
			this.year = record.year;
			this.settlement_price = record.settlement_price;
			this.volume = record.volume;
			this.open_int = record.open_int;
			this.block_volume = record.block_volume;
			this.change = record.change;
			this.strike = record.strike;
			this.strategy = record.strategy;
			this.oi_change = record.oi_change;
			this.day = record.day;
		}

		public Record() {}

		private void updateRecord(Date report_date, Integer contract_ID, String contract_name, String term, Double settlement_price,
				Double change, String block_volume, String volume, String open_int, double oi_change, String source)
				throws SQLException, ParseException {
			if (settlement_price != null)
				this.settlement_price = settlement_price;
			if (volume != null && volume.length() > 0)
				this.volume = Integer.parseInt(volume);
			if (open_int != null && open_int.length() > 0)
				this.open_int = Integer.parseInt(open_int);
			if (block_volume != null && block_volume.length() > 0)
				this.block_volume = Double.parseDouble(block_volume);
			if (change != null)
				this.change = change;
			this.oi_change = oi_change;
			this.report_date = new java.sql.Date(report_date.getTime());
			this.contract_ID = contract_ID;
			this.contract_name = contract_name;
			int[] monthDayYear = getMonthDayYear(term);
			if (monthDayYear[0] == -1 || monthDayYear[2] == -1)
				return;
			this.month = monthDayYear[0];
			this.year = monthDayYear[2];
			this.source = source;
			if (monthDayYear[1] != -1) {
				this.day = monthDayYear[1];
			}
		}

		public void updateRecord(String dateString, Integer contractId, String code, String term, String settle,
				String settleChg, String blockVol, String vol, String openInt, String source)
				throws NumberFormatException, SQLException, ParseException {
			Date d = null;
			try {
				if (source.equals("ICE"))
					d = iceDateFormat.parse(dateString);
				else if (source.equals("CME"))
					d = cmeDateFormat.parse(dateString);
				else
					d = nasdaqDateFormat.parse(dateString);
			} catch (ParseException ex) {
				logger.error("Error parsing " + dateString);
				return;
			}
			updateRecord(d, contractId, code, term, Double.parseDouble(settle),
					(settleChg != null) ? Double.parseDouble(settleChg) : null, blockVol, vol, openInt, 0, source);
		}

		private void updateOptRecord(String dateString, Integer contractId, String code, String month, String year,
				String settle, String settleChg, String vol, String openInt, String source, String strike,
				String strategy, String blockVol) throws SQLException {
			Date d = null;
			try {
				if (source.equals("ICE"))
					d = iceDateFormat.parse(dateString);
				else
					d = cmeDateFormat.parse(dateString);
			} catch (ParseException ex) {
				logger.error("Error parsing " + dateString);
				ex.printStackTrace();
			}
			updateOptRecord(d, contractId, code, month, year, settle, settleChg, vol, openInt, source,
					Double.parseDouble(strike), strategy, blockVol);
		}

		private void updateOptRecord(Date d, Integer contractId, String code, String month, String year, String settle,
				String settleChg, String vol, String openInt, String source, double strike, String strategy,
				String blockVol) throws SQLException {
			updateOptRecord(d, contractId, code, Integer.parseInt(month), Integer.parseInt(year), settle, settleChg,
					vol, openInt, source, strike, strategy, blockVol);
		}

		private void updateOptRecord(Date report_date, Integer contract_ID, String contract_name, int month, int year, String settlement_price,
				String settleChg, String volume, String open_int, String source, double strike, String strategy,
				String block_volume) throws SQLException {
			if (settlement_price != null && settlement_price.length() > 0)
				this.settlement_price = Double.parseDouble(settlement_price);
			if (volume != null && volume.length() > 0)
				this.volume = Integer.parseInt(volume);
			if (open_int != null && open_int.length() > 0)
				this.open_int = Integer.parseInt(open_int);
			if (block_volume != null && block_volume.length() > 0)
				this.block_volume = Double.parseDouble(block_volume);
			double change = 0;
			if (settleChg != null && !settleChg.equals(UNCHANGED))
				change = Double.parseDouble(settleChg);
			this.change = change;
			this.report_date = new java.sql.Date(report_date.getTime());
			this.contract_ID = contract_ID;
			this.contract_name = contract_name;
			this.month = month;
			this.year = year;
			this.strike = strike;
			this.strategy = strategy;
			this.source = source;
		}


		@Override
		public boolean equals(Object obj) {
			if (obj == null) return false;
			else {
				Record record = (Record) obj;
				return this.prd_code.equals(record.prd_code) &&
						((this.source == null && record.source == null) || (this.source != null && record.source != null && this.source.equals(record.source))) &&
						((this.report_date == null && record.report_date == null) || (this.report_date != null && record.report_date != null && this.report_date.equals(record.report_date))) &&
						((this.contract_ID == null && record.contract_ID == null) || (this.contract_ID != null && record.contract_ID != null && this.contract_ID.equals(record.contract_ID))) &&
						((this.contract_name == null && record.contract_name == null) || (this.contract_name != null && record.contract_name != null && this.contract_name.equals(record.contract_name))) &&
						((this.month == null && record.month == null) || (this.month != null && record.month != null && this.month.equals(record.month))) &&
						((this.year == null && record.year == null) || (this.year != null && record.year != null && this.year.equals(record.year))) &&
						((this.settlement_price == null && record.settlement_price == null) || (this.settlement_price != null && record.settlement_price != null && this.settlement_price.equals(record.settlement_price))) &&
						((this.volume == null && record.volume == null) || (this.volume != null && record.volume != null && this.volume.equals(record.volume))) &&
						((this.open_int == null && record.open_int == null) || (this.open_int != null && record.open_int != null && this.open_int.equals(record.open_int))) &&
						((this.block_volume == null && record.block_volume == null) || (this.block_volume != null && record.block_volume != null && this.block_volume.equals(record.block_volume))) &&
						((this.change == null && record.change == null) || (this.change != null && record.change != null && this.change.equals(record.change))) &&
						((this.strike == null && record.strike == null) || (this.strike != null && record.strike != null && this.strike.equals(record.strike))) &&
						((this.strategy == null && record.strategy == null) || (this.strategy != null && record.strategy != null && this.strategy.equals(record.strategy))) &&
						((this.oi_change == null && record.oi_change == null) || (this.oi_change != null && record.oi_change != null && this.oi_change.equals(record.oi_change))) &&
						((this.day == null && record.day == null) || (this.day != null && record.day != null && this.day.equals(record.day)));
			}
		}

		@Override
		public int hashCode() {
			int hash = this.contract_ID;
			hash = 31 * hash + (this.prd_code != null ? this.prd_code.hashCode() : 0);
			hash = 31 * hash + (this.source != null ? this.source.hashCode() : 0);
			hash = 31 * hash + (this.report_date != null ? this.report_date.toString().hashCode() : 0);
			hash = 31 * hash + (this.contract_name != null ? this.contract_name.hashCode() : 0);
			hash = 31 * hash + this.month;
			hash = 31 * hash + this.year;
			hash = 31 * hash + (this.settlement_price != null ? this.settlement_price.hashCode() : 0);
			hash = 31 * hash + this.volume;
			hash = 31 * hash + this.open_int;
			hash = 31 * hash + (this.block_volume != null ? this.block_volume.hashCode() : 0);
			hash = 31 * hash + (this.change != null ? this.change.hashCode() : 0);
			hash = 31 * hash + (this.strike != null ? this.strike.hashCode() : 0);
			hash = 31 * hash + (this.strategy != null ? this.strategy.hashCode() : 0);
			hash = 31 * hash + (this.oi_change != null ? this.oi_change.hashCode() : 0);
			hash = 31 * hash + this.day;
			return hash;
		}
	}
}
