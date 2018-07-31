package DailySettlementDataParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.log4j.Logger;

/** Main Executor for DailySettlementParser Revised
 * Uses DailySettlementDataParser as a library, and allows additional logic/checks before inserting to SQL database
 * @arg1 The properties file, with the following possible additional params:
 * @param Ticks, the amount of times it tries before executing anyways
 * @param Frequency, in terms of seconds of how often the program retries
 * @param OutputDir [opt], the output directory for the debugging log csv files
 * @param exec [opt], optional programs to run before main parse
 *
 *@arg2 The source: ICE, CME, NASDAQ
 *@arg3 OPT (OPTION)/NONOPT (NON OPTION)
 */
public class MainExecutor {

	//private static String initialQuery_sql = "INSERT INTO en.daily_settlement_data_t (%s) VALUES(%s) ON DUPLICATE KEY UPDATE %s"; //< Mold for SQL prepared statement (local testing)
	private static String initialQuery_oracle = "MERGE INTO en.daily_settlement_data_t a USING (select %s from dual) b on (%s) WHEN MATCHED THEN UPDATE SET %s WHEN NOT MATCHED THEN INSERT(%s) VALUES(%s)"; //< Mold for SQL prepared statement
	private static String initialQuery_opt_oracle = "MERGE INTO en.daily_settlement_opt_data_t a USING (select %s from dual) b on (%s) WHEN MATCHED THEN UPDATE SET %s WHEN NOT MATCHED THEN INSERT(%s) VALUES(%s)"; //< Mold for SQL prepared statement
	public static enum MODETYPE { DEBUG, PROD };
	public static DailySettlementDataParser.MODETYPE_ mode_;
	public static MODETYPE sql; //< DEBUG FOR MYSQL, PROD FOR ORACLE, ETC.
	private static Connection conn;
	private static PreparedStatement pstmt;
	private static PreparedStatement pstmt_opt;
	private int frequency;
	private static String DEFAULT_FREQUENCY = "60";
	private static String DEFAULT_TICKS = "0";
	private Map<DailySettlementDataParser.MODETYPE_, List<DailySettlementDataParser.Record> > recordList;


	private static Logger logger = Logger.getLogger(MainExecutor.class);


	public static void main(String[] args) {

		///Reading in Properties file, and initialization
		Properties props = new Properties();
		try {
			props.load(new FileInputStream(args[0]));

			///Configuration for Database Connection
			String DB_URL = props.getProperty("dbUrl");
			String DB_USER = props.getProperty("dbUser", null);
			String DB_PASSWORD = props.getProperty("dbPassword", null);

			if (DB_URL.contains("jdbc:mysql")) {
			//	Class.forName("com.mysql.jdbc.Driver");
				sql = MODETYPE.DEBUG;
			}
			else {
				if (DB_URL.contains("log4jdbc")) Class.forName("net.sf.log4jdbc.DriverSpy");
				else Class.forName("oracle.jdbc.driver.OracleDriver");
				oracle.jdbc.driver.OracleLog.setTrace(true);
				sql = MODETYPE.PROD;
			}

		if (sql == MODETYPE.PROD) conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

			MainExecutor main = new MainExecutor(props);
			String output = props.getProperty("outputDir", null);
			String source = args.length > 1 ? args[1] : "", opt = args.length > 2 ? args[2] : "";
			main.parse(args[0], source, opt, Integer.parseInt(props.getProperty("ticks",DEFAULT_TICKS)),output);

			logger.debug("Program terminated. Connections closed.");
		if (sql == MODETYPE.PROD) {
			pstmt.close();
			conn.close();
		}

		} catch (FileNotFoundException e) {
			logger.error("Properties file not found" + e);
		} catch (IOException e) {
			logger.error("I/O exception:");
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			logger.error("Class for SQL drivers not found");
		} catch (SQLException e) {
			logger.error("SQL Exception Error: " + e);
		} catch (NoSuchFieldException e) {
			logger.error("Record class missing fields:\n" + e);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
			logger.error("Number format Exception: " + e);
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			logger.error("Illegal Argument Exception: " + e);
		} catch (IllegalAccessException e) {
			logger.error("Illegal Access Exception: " + e);
			e.printStackTrace();
		};

	}

	private void parse(String props, String source, String opt, int counter, String outputDir) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, IOException {
		Map<DailySettlementDataParser.MODETYPE_, List<DailySettlementDataParser.Record> > recordList_ = null;
		boolean initial = true;
		try {
			do {
				execDependencies(props);
				recordList_ = new HashMap<DailySettlementDataParser.MODETYPE_, List<DailySettlementDataParser.Record> >();
				if (!source.equals("CME") && !source.equals("ICE") && !source.equals("NASDAQ")) source = "";
				// Starting up Daily Settlement Data Parser
				logger.error("Starting DailySettlementDataParser...");
				DailySettlementDataParser parser;
				parser = new DailySettlementDataParser(new FileInputStream(props));
				if (source.equals("CME") || source.isEmpty()) {
					if (!opt.equals("OPT") || opt.isEmpty()) {
						Map.Entry<DailySettlementDataParser.MODETYPE_, List<DailySettlementDataParser.Record> > entry = parser.parse(DailySettlementDataParser.MODETYPE_.CME);
						recordList_.put(entry.getKey(),entry.getValue());
					}
					if (!opt.equals("NONOPT") || opt.isEmpty()) {
						Map.Entry<DailySettlementDataParser.MODETYPE_, List<DailySettlementDataParser.Record> > entry = parser.parse(DailySettlementDataParser.MODETYPE_.CME_OPT);
						recordList_.put(entry.getKey(), entry.getValue());
					}
				}
				if (source.equals("ICE") || source.isEmpty()) {
					if (!opt.equals("OPT") || opt.isEmpty()) {
						Map.Entry<DailySettlementDataParser.MODETYPE_, List<DailySettlementDataParser.Record> > entry = parser.parse(DailySettlementDataParser.MODETYPE_.ICE);
						recordList_.put(entry.getKey(),entry.getValue());
					}
					if (!opt.equals("NONOPT") || opt.isEmpty()) {
						Map.Entry<DailySettlementDataParser.MODETYPE_, List<DailySettlementDataParser.Record> > entry = parser.parse(DailySettlementDataParser.MODETYPE_.ICE_OPT);
						recordList_.put(entry.getKey(),entry.getValue());
					}
				}
				if (source.equals("NASDAQ") || source.isEmpty()) {
					Map.Entry<DailySettlementDataParser.MODETYPE_, List<DailySettlementDataParser.Record> > entry = parser.parse(DailySettlementDataParser.MODETYPE_.NASDAQ);
					recordList_.put(entry.getKey(),entry.getValue());
				}
				if (outputDir != null) output(recordList_, outputDir);

				if (!validate(recordList_) && counter != 0) {
					logger.debug("DailySettlementDataParser has been determined to be invalid; going to continue polling at interval of " + frequency + " seconds for another " + counter + "attempts");
					Thread.sleep(frequency * 1000);
				}
				else {
					logger.debug("DailySettlementDataParser has been determined to be valid; finished polling");
					break;
				}
			--counter;

			if (sql == MODETYPE.PROD) {
				if (initial) {
					initial = false;
					for (Map.Entry<DailySettlementDataParser.MODETYPE_, List<DailySettlementDataParser.Record> > entry : recordList_.entrySet())
						updateDB_SMALL(entry);
					recordList = recordList_.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().stream().map(e_ -> new DailySettlementDataParser.Record(e_)).collect(Collectors.toList()) ));
				}
				else {
					for (Map.Entry<DailySettlementDataParser.MODETYPE_, List<DailySettlementDataParser.Record> > entry : recordList_.entrySet()) {
						DailySettlementDataParser.MODETYPE_ mode = entry.getKey();
						List<DailySettlementDataParser.Record> minus = entry.getValue().stream().filter(recordList.get(mode)::contains).collect(Collectors.toList());
						if (!minus.isEmpty())
							updateDB_SMALL(new AbstractMap.SimpleEntry<DailySettlementDataParser.MODETYPE_, List<DailySettlementDataParser.Record> >(mode,minus));
					}

				}
			}

			}
			while (counter > 0);
		}
		 catch (SQLException e) {
		logger.error("Error during initialization" + e);
		e.printStackTrace();
		} catch (FileNotFoundException e) {
		logger.error("Properties file not found" + e);
		} catch (InterruptedException e) {
		logger.error("Interrupted while sleeping" + e);
		}
	}


	private void execDependencies(String props) throws IOException {
		Files.readAllLines(Paths.get(props)).stream().filter(item -> item.contains("exec=")).forEach( s-> {
			try {
				String path = s.substring(5);
				logger.debug("Starting external dependency program: " + path);
				File file = new File(path);
				ProcessBuilder pb;
				if (s.endsWith(".bat"))
					pb = new ProcessBuilder("cmd.exe", "/C", file.getName()).redirectErrorStream(true);
				else
					pb = new ProcessBuilder(file.getName()).redirectErrorStream(true);
				pb.directory(file.getParentFile());
				Process process = pb.start();
				BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				while (reader.readLine() != null); //Consumes large error streams to prevent deadlocks
				process.waitFor();
				logger.debug("Completed external dependency program: " + path);
			} catch (IOException e) {
				logger.error("Error executing program" + s);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
				);
	}

	private void output(Map<DailySettlementDataParser.MODETYPE_, List<DailySettlementDataParser.Record> > map, String outputPath) throws IOException {
		Field[] fields = DailySettlementDataParser.Record.class.getFields();
		String headers = "TIME," + Arrays.stream(fields).map(s -> s.getName()).collect(Collectors.joining(",")) + "\n";
		map.entrySet().forEach(e -> {
			String outputPath_ = outputPath + "/" + LocalDate.now() + "_" + e.getKey().toString() + ".csv";
			Path path = Paths.get(outputPath_);
			try {
				try { Files.createFile(path); Files.write(path, headers.getBytes()); } catch(FileAlreadyExistsException ignored) { }
				e.getValue().forEach( e_ -> {
					try {
						String line = LocalDateTime.now() + ",";
						for (int i = 0; i < fields.length; i++) {
							Object field = fields[i].get(e_);
							line +=  i == 0 ? field == null ? "NULL" : field.toString() : "," + (field == null ? "NULL" : field.toString());
						}
						line += "\n";
						Files.write(path, line.getBytes(), StandardOpenOption.APPEND);
					}
					catch (Exception e1) {
						logger.error("Error outputting file: " + e1);
					}
				});
			} catch (Exception e1) {
				logger.error("Error outputting file: " + e1 + e.getKey().toString());
			}
		});

	}

	private synchronized static void updateDB_SMALL(Map.Entry<DailySettlementDataParser.MODETYPE_, List<DailySettlementDataParser.Record> > map) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		DailySettlementDataParser.MODETYPE_ modetype = map.getKey();
		List<DailySettlementDataParser.Record> val = map.getValue();
		if (val.size() == 0) {logger.debug("NO RECORDS FOUND FOR SOURCE: " + modetype.toString()); return;}
		logger.debug("Attempting to update/insert " + val.size() + " " + modetype.toString() + " records. Of which, " + val.stream().map(s -> s.contract_ID).distinct().count() + " are unique contract IDs, and " + val.stream().map(s -> s.contract_name).distinct().count() + " unique products");
		int errors = 0;
		if (modetype == DailySettlementDataParser.MODETYPE_.CME_OPT || modetype == DailySettlementDataParser.MODETYPE_.ICE_OPT) {
			for (DailySettlementDataParser.Record record : val) {
					Field headers[] = Stream.concat(Stream.concat(Arrays.stream(getHeaders(false)), Arrays.stream(getHeaders(true))), Stream.concat(Arrays.stream(getHeaders(false,true)), Arrays.stream(getHeaders(true,true)))).toArray(Field[]::new);
					for (int i = 0; i < headers.length; i++) {
						try {
							pstmt_opt.setObject(i+1, headers[i].get(record));
						} catch (SQLException e) {
							logger.error(String.format("ERROR filling in params of SQL; param: %s, statement:%s", headers[i].get(record), pstmt_opt.toString()));
							++errors;
							continue;
						}
					}
					try {
						pstmt_opt.execute();
					} catch (SQLException e) {
						logger.error(String.format("ERROR executing SQL; please run with log4jdbc in dbUrl for indepth debugging"));
						logger.error(e);
						++errors;
					}
				}
		}
	else {
		for (DailySettlementDataParser.Record record : val) {
			Field headers[] = Stream.concat(Stream.concat(Arrays.stream(getHeaders(false)), Arrays.stream(getHeaders(true))), Stream.concat(Arrays.stream(getHeaders(false,false)), Arrays.stream(getHeaders(true,false)))).toArray(Field[]::new);
			for (int i = 0; i < headers.length; i++) {
				try {
					pstmt.setObject(i+1, headers[i].get(record));
				} catch (SQLException e) {
					logger.error(String.format("ERROR filling in params of SQL; param: %s in field: %s", headers[i].get(record), headers[i].getName()));
					++errors;
					continue;
				}
			}
			try {
				pstmt.execute();
			} catch (SQLException e) {
				logger.error(String.format("ERROR executing SQL; please run with log4jdbc in dbUrl for indepth debugging"));
				++errors;
			}
		}
	}
	if (errors > 0) logger.debug(String.format("There was %d failed SQL queries.", errors));
}


	//!* VALIDATION FOR DATA BEING GOOD
	private boolean validate(Map<DailySettlementDataParser.MODETYPE_, List<DailySettlementDataParser.Record> > map) {
		boolean flag = true;
		/*List<DailySettlementDataParser.Record> iceList = map.get(DailySettlementDataParser.MODETYPE_.ICE);
		if (iceList != null) {
			flag = iceList.stream().filter(s -> s.open_int != null && s.open_int > 0).count() > 0;
		}
		iceList = map.get(DailySettlementDataParser.MODETYPE_.ICE_OPT);
		if (iceList != null) {
			flag = iceList.stream().filter(s -> s.open_int != null && s.open_int > 0).count() > 0;
		}*/
		return !flag;
	}


	/**
	 * Helper function that gets the field array of headers.
	 * @param update, true if update, false for insert headers
	 * @return Array of Fields that can be used as SQL headers
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 */
	private static Field[] getHeaders(boolean update) throws NoSuchFieldException, SecurityException {
		if (!update)
			return 	new Field[]{
					DailySettlementDataParser.Record.class.getDeclaredField("prd_code"),
					DailySettlementDataParser.Record.class.getDeclaredField("source"),
					DailySettlementDataParser.Record.class.getDeclaredField("report_date"),
					DailySettlementDataParser.Record.class.getDeclaredField("contract_ID"),
					DailySettlementDataParser.Record.class.getDeclaredField("contract_name"),
					DailySettlementDataParser.Record.class.getDeclaredField("month"),
					DailySettlementDataParser.Record.class.getDeclaredField("year")
					};
		else
			return 	new Field[]{
				DailySettlementDataParser.Record.class.getDeclaredField("settlement_price"),
				DailySettlementDataParser.Record.class.getDeclaredField("volume"),
				DailySettlementDataParser.Record.class.getDeclaredField("open_int"),
				DailySettlementDataParser.Record.class.getDeclaredField("block_volume"),
				DailySettlementDataParser.Record.class.getDeclaredField("change")
				};
	}

	/**
	 * Helper function that gets the field array of headers specific for option or non options
	 * @param update, true if update, false for insert headers
	 * @param options, true for options, false for non options
	 * @return Array of Fields that can be used as SQL headers
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 */
	private static Field[] getHeaders(boolean update, boolean options) throws NoSuchFieldException, SecurityException {
		if (!update)
			if (options)
				return new Field[] {};
			else return new Field[]{
					DailySettlementDataParser.Record.class.getDeclaredField("day") };
		else
			if (options)
				return 	new Field[]{
					DailySettlementDataParser.Record.class.getDeclaredField("strike"),
					DailySettlementDataParser.Record.class.getDeclaredField("strategy")
				};
			else return new Field[]{
					DailySettlementDataParser.Record.class.getDeclaredField("oi_change")
			};

	}

	MainExecutor(Properties props) throws SQLException, NoSuchFieldException, SecurityException {
		frequency = Integer.parseInt(props.getProperty("frequency",DEFAULT_FREQUENCY)) * 1000;

		String string1, string2, string3, string4;
		//Non options
		string1 = Stream.concat(Stream.concat(Arrays.stream(getHeaders(false)), Arrays.stream(getHeaders(true))), Stream.concat(Arrays.stream(getHeaders(false,false)), Arrays.stream(getHeaders(true,false)))).map(s -> "? as " + s.getName()).collect(Collectors.joining(","));
		string2 = Stream.concat(Arrays.stream(getHeaders(false)),Arrays.stream(getHeaders(false,false))).map(s -> "a." + s.getName() + " = b." + s.getName()).collect(Collectors.joining(" and "));
		string3 = Stream.concat(Arrays.stream(getHeaders(true)),Arrays.stream(getHeaders(true,false))).map(s -> "a." + s.getName() + " = b." + s.getName() ).collect(Collectors.joining(","));
		string4 = Stream.concat(Stream.concat(Arrays.stream(getHeaders(false)), Arrays.stream(getHeaders(true))), Stream.concat(Arrays.stream(getHeaders(false,false)), Arrays.stream(getHeaders(true,false)))).map(s -> "b." + s.getName()).collect(Collectors.joining(","));
		String query = String.format(initialQuery_oracle, string1, string2, string3, string4.replace("b.",""), string4);
		if (sql == MODETYPE.PROD) pstmt = conn.prepareStatement(query);

		string1 = null; string2 = null; string3 = null; string4 = null;
		//Options
		string1 = Stream.concat(Stream.concat(Arrays.stream(getHeaders(false)), Arrays.stream(getHeaders(true))), Stream.concat(Arrays.stream(getHeaders(false,true)), Arrays.stream(getHeaders(true,true)))).map(s -> "? as " + s.getName()).collect(Collectors.joining(","));
		string2 = Stream.concat(Arrays.stream(getHeaders(false)),Arrays.stream(getHeaders(false,true))).map(s -> "a." + s.getName() + " = b." + s.getName()).collect(Collectors.joining(" and "));
		string3 = Stream.concat(Arrays.stream(getHeaders(true)),Arrays.stream(getHeaders(true,true))).map(s -> "a." + s.getName() + " = b." + s.getName() ).collect(Collectors.joining(","));
		string4 = Stream.concat(Stream.concat(Arrays.stream(getHeaders(false)), Arrays.stream(getHeaders(true))), Stream.concat(Arrays.stream(getHeaders(false,true)), Arrays.stream(getHeaders(true,true)))).map(s -> "b." + s.getName()).collect(Collectors.joining(","));
		String query_ = String.format(initialQuery_opt_oracle, string1, string2, string3, string4.replace("b.",""), string4);
		if (sql == MODETYPE.PROD) pstmt_opt = conn.prepareStatement(query_);
	}

}
