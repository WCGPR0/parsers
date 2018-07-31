import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/** CurrencyConversion
 * 
 * @version 0.01
 * Utilizes a standalone executable that will get the most current up to date currency conversions.
 * 
 * @param binary executable that fetches the data
 *
 */
public class CurrencyConversion {
	
	class currency {
		private float AUD; //< Australian dollars
		private float CAD; //< Canadian dollars
		private float CHF; //< Swiss Franc
		private float EUR; //< Euro
		private float NZD; //< New Zealand
		private float RUB; //< Russian Ruble
		private float JPY; //< Japanese Yen
		private float USD; //< US dollars
		/**
		 * Mutator method that sets the AUD to USD value
		 * @param float x
		 */
		protected void setAUD(float x) {AUD = x;}
		/**
		 * Mutator method that sets the CAD to USD value
		 * @param float x
		 */
		protected void setCAD(float x) {CAD = x;}
		/**
		 * Mutator method that sets the CHF to USD value
		 * @param float x
		 */
		protected void setCHF(float x) {CHF = x;}
		/**
		 * Mutator method that sets the EUR to USD value
		 * @param float x
		 */
		protected void setEUR(float x) {EUR = x;}
		/**
		 * Mutator method that sets the NZD to USD value
		 * @param float x
		 */
		protected void setNZD(float x) {NZD = x;}
		/**
		 * Mutator method that sets the RUB to USD value
		 * @param float x
		 */
		protected void setRUB(float x) {RUB = x;}
		/**
		 * Mutator method that sets the JPY to USD value
		 * @param float x
		 */
		protected void setJPY(float x) {JPY = x;}
		/**
		 * Mutator method that sets the USD value
		 * @param float x
		 */
		protected void setUSD(float x) {USD = x;}
		/**
		 * Accessor method that returns the AUD value
		 * @return float AUD
		 */
		public float getAUD() {return AUD; }
		/**
		 * Accessor method that returns the CAD value
		 * @return float CAD
		 */
		public float getCAD() {return CAD; }
		/**
		 * Accessor method that returns the CHF value
		 * @return float CHF
		 */
		public float getCHF() {return CHF; }
		/**
		 * Accessor method that returns the EUR value
		 * @return float EUR
		 */
		public float getEUR() {return EUR; }
		/**
		 * Accessor method that returns the NZD value
		 * @return float NZD
		 */
		public float getNZD() {return NZD; }
		/**
		 * Accessor method that returns the RUB value
		 * @return float RUB
		 */
		public float getRUB() {return RUB; }
		/**
		 * Accessor method that returns the JPY value
		 * @return float JPY
		 */
		public float getJPY() {return JPY; }
		/**
		 * Accessor method that returns the USD value
		 * @return float USD
		 */
		public float getUSD() {return USD; }
	};
	
	public static currency currency_; //< Main Instance of currency
	
	/** Initializes the rates by running the executable
	 * @param path to the executable
	 */
	CurrencyConversion(String path) {
		Process process = null;
		currency_ = new currency();
		try {
			process = new ProcessBuilder(path).start();
		} catch (IOException e1) {
			System.err.println("Error launching executable, please verify it's there: " + path); 
		}
		BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line = null;
		try {
			while ((line = br.readLine()) != null) {
				if (line.isEmpty()) continue;
				switch(line.substring(0,3)) {
				case "AUD":
					currency_.setAUD(Float.parseFloat(line.substring(5)));
					break;
				case "CAD":
					currency_.setCAD(Float.parseFloat(line.substring(5)));
					break;
				case "CHF":
					currency_.setCHF(Float.parseFloat(line.substring(5)));
					break;
				case "EUR":
					currency_.setEUR(Float.parseFloat(line.substring(5)));
					break;
				case "NZD":
					currency_.setNZD(Float.parseFloat(line.substring(5)));
					break;
				case "RUB":
					currency_.setRUB(Float.parseFloat(line.substring(5)));
					break;
				case "JPY":
					currency_.setJPY(Float.parseFloat(line.substring(5)));
					break;
				case "USD":
					currency_.setUSD(Float.parseFloat(line.substring(5)));
					break;
				default:
					//System.err.println("Unmatched currency! " + header);
				}
			}
		} catch (IOException e) {
			System.err.println("Error reading line: " + line);
		}
		process.destroy();
	}
	
	/**
	 * By default looks for the executable with the same name, CurrencyConversion
	 * @throws ClassNotFoundException
	 */
	CurrencyConversion() throws ClassNotFoundException {
		this(Class.forName("CurrencyConversion").getSimpleName());
	}
}
