import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;

public class CMEBlockTradeParser {
	//private static Logger logger = Logger.getLogger(CMEBlockTradeParser.class);
	private Properties props = new Properties();
	public String dir;
	public String urlString;
	public String transformUrl;
	public String codesFile;
	private HashMap<String, Matrix> contractMatrixMap;
	private SimpleDateFormat sdf;

	public CMEBlockTradeParser(String propsFile) {
		try {
			props.load(new FileInputStream(propsFile));
			contractMatrixMap = new HashMap<String, Matrix>();
			dir = props.getProperty("dir");
			urlString = props.getProperty("url");
			sdf = new SimpleDateFormat(props.getProperty("dateFormat"));
			transformUrl = props.getProperty("transform");
			codesFile = props.getProperty("codesFile");
		}
		catch (Exception ex) {
			//logger.error(ex);
		}
	}

	public Matrix getMatrix(String contract) {
		return contractMatrixMap.get(contract);
	}

	public void parse() {
		try {
		//	logger.debug("Begin parse");
			try { FileUtils.cleanDirectory(new File(dir)); }
			catch (Exception e) { System.out.println(e); }

			Date today = new Date();
			String dateString = sdf.format(today);
			//logger.debug("Date string "+dateString);
			URL url = new URL(String.format(urlString, dateString));
			Document doc = Util.documentFromInputStream(url.openStream());
			CachedTransform transform = new CachedTransform(transformUrl, true);
			String output = Util.performTransformToString(transform, doc, true);
			//logger.debug("Transformed output: "+output);
			BufferedReader br = new BufferedReader(new StringReader(output));
			br.readLine();
			String line;
			while ((line = br.readLine())!=null) {
				String[] data = line.split(",");
				System.out.print(data);
				String contract = data[0].substring(0, data[0].indexOf("|")-2);
				String tenorString = data[0].substring(data[0].indexOf("|")-2, data[0].length()-1);
				String strategy = data[0].substring(data[0].length()-1);
				Matrix matrix = contractMatrixMap.get(contract);
				if (matrix == null) {
				//	logger.debug("Create new matrix: "+contract);
					matrix = new Matrix(contract);
					contractMatrixMap.put(contract, matrix);
				}
				matrix.addBlockTrade(tenorString, strategy, Integer.parseInt(data[1]));
			}
		//	logger.debug("Done parsing");
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private TreeSet<String> generateCodeList() {
		TreeSet<String> codes = new TreeSet<String> ();
		for (String code : contractMatrixMap.keySet()) {
			codes.add(code);
		}
		try {
			FileWriter fw = new FileWriter(dir+codesFile);
			BufferedWriter bw = new BufferedWriter(fw);
			for (String code : codes) {
				bw.write(code);
				bw.newLine();
			}
			bw.close();
			fw.close();
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}
		return codes;
	}


	public static void main(String[] args) {
		CMEBlockTradeParser parser = new CMEBlockTradeParser(args[0]);
		parser.parse();
		TreeSet<String> codes = parser.generateCodeList();
		for (String code : codes) {
			Matrix m = parser.getMatrix(code);
			m.generateCsv();
		}

	}

	public class Matrix {
		private String contract;
		private HashMap<String, Integer> putStrikeTenorQtyMap;
		private HashMap<String, Integer> callStrikeTenorQtyMap;
		private HashMap<String, Integer> totalStrikeTenorQtyMap;
		private TreeSet<Integer> putStrikeSet;
		private TreeSet<Integer> callStrikeSet;
		private TreeSet<Integer> totalStrikeSet;
		private TreeMap<Integer,String> tenorMap;

		public Matrix(String contract) {
			this.contract = contract;
			this.putStrikeTenorQtyMap = new HashMap<String, Integer>();
			this.callStrikeTenorQtyMap = new HashMap<String, Integer>();
			this.totalStrikeTenorQtyMap = new HashMap<String, Integer>();
			putStrikeSet = new TreeSet<Integer>(Collections.reverseOrder());
			callStrikeSet = new TreeSet<Integer>(Collections.reverseOrder());
			totalStrikeSet = new TreeSet<Integer>(Collections.reverseOrder());
			tenorMap = new TreeMap<Integer,String>();
		}

		public void addBlockTrade(String code, String strategy, int qty) {
			HashMap<String, Integer> strikeTenorQtyMap;
			TreeSet<Integer> strikeSet;
			if (strategy.equals("P")) {
				strikeTenorQtyMap = putStrikeTenorQtyMap;
				strikeSet = putStrikeSet;
			}
			else {
				strikeTenorQtyMap = callStrikeTenorQtyMap;
				strikeSet = callStrikeSet;
			}
			Integer total = strikeTenorQtyMap.get(code);
			if (total != null)
				strikeTenorQtyMap.put(code,total+qty);
			else
				strikeTenorQtyMap.put(code, qty);
			total = totalStrikeTenorQtyMap.get(code);
			if (total != null)
				totalStrikeTenorQtyMap.put(code,total+qty);
			else
				totalStrikeTenorQtyMap.put(code, qty);
			tenorMap.put(getTenor(code.substring(0, 2)), code.substring(0, 2));
			strikeSet.add(Integer.parseInt(code.substring(3)));
			totalStrikeSet.add(Integer.parseInt(code.substring(3)));
		}

		private int getTenor(String input) {
			int month = -1;
			int year = -1;
			if (input.length()==2) {
				switch (input.charAt(0)) {
				case 'F':
					month=1;
					break;
				case 'G':
					month=2;
					break;
				case 'H':
					month=3;
					break;
				case 'J':
					month=4;
					break;
				case 'K':
					month=5;
					break;
				case 'M':
					month=6;
					break;
				case 'N':
					month=7;
					break;
				case 'Q':
					month=8;
					break;
				case 'U':
					month=9;
					break;
				case 'V':
					month=10;
					break;
				case 'X':
					month=11;
					break;
				case 'Z':
					month=12;
					break;
				}
				year = 2010 + Integer.parseInt(input.substring(1));
			}
			return year*100+month;
		}

		public void generateCsv() {
			generateCsv("C");
			generateCsv("P");
			generateCsv("Total");
		}

		private void generateCsv(String strategy) {
			try {
				String fileName = dir+contract+"_"+strategy+".csv";
				FileWriter fw = new FileWriter(fileName);
				BufferedWriter bw = new BufferedWriter(fw);
				TreeSet<Integer> strikeSet;
				HashMap<String, Integer> strikeTenorQtyMap;
				HashMap<Integer, Integer> tenorTotalMap = new HashMap<Integer, Integer>();
				if (strategy.equals("C")) {
					bw.write("Call");
					strikeSet = callStrikeSet;
					strikeTenorQtyMap = callStrikeTenorQtyMap;
				}
				else if (strategy.equals("P")) {
					bw.write("Put");
					strikeSet = putStrikeSet;
					strikeTenorQtyMap = putStrikeTenorQtyMap;
				}
				else {
					bw.write("Total");
					strikeSet = totalStrikeSet;
					strikeTenorQtyMap = totalStrikeTenorQtyMap;
				}
				for (String tenor : tenorMap.values()) {
					bw.write(",");
					bw.write(tenor);
				}
				bw.write(",");
				bw.write("Total");
				bw.newLine();
				for (Integer strike : strikeSet) {
					bw.write(strike.toString());
					int rowTotal = 0;
					for (Integer tenor : tenorMap.keySet()) {
						bw.write(",");
						Integer val = strikeTenorQtyMap.get(tenorMap.get(tenor)+"|"+strike);
						bw.write((val==null)?"":val.toString());
						if (val!=null) {
							rowTotal+=val;
							Integer tenorTotal = tenorTotalMap.get(tenor);
							if (tenorTotal==null)
								tenorTotalMap.put(tenor, val);
							else
								tenorTotalMap.put(tenor, tenorTotal+val);
						}
					}
					bw.write(",");
					bw.write(String.valueOf(rowTotal));
					bw.newLine();
				}
				bw.write("Total");
				for (Integer tenor : tenorMap.keySet()) {
					bw.write(",");
					Integer tenorTotal = tenorTotalMap.get(tenor);
					bw.write((tenorTotal!=null)?tenorTotal.toString():"0");
				}
				bw.newLine();
				bw.close();
				fw.close();
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
}
