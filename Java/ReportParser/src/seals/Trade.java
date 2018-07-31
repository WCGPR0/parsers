package reportparser.seals;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Node;

public class Trade {

	private final String business_state;
	private final String trade_id;
	private final String exchange_id;
	private final String buy_sell;
	private final Double volume;
	//private final DateTime market_expiry;
	private final String market_product;
	private final String option_type;
	private final String strike;
	private final Double price;

	//isin
	private String isin;
	//bloomberg data
	//TODO

	public Trade(Node tradexml) throws InvalidTradeException
	{
		if (tradexml!=null)
		{
			business_state = Util.getNodeValueFromNode(tradexml, "Data[@column='BusinessStateName']/text()").trim();
			trade_id = Util.getNodeValueFromNode(tradexml, "Data[@column='TradeId']/text()");
			exchange_id = Util.getNodeValueFromNode(tradexml, "Data[@column='ExchangeId']/text()");
			buy_sell = Util.getNodeValueFromNode(tradexml, "Data[@column='BuySell']/text()");
			String szvolume = Util.getNodeValueFromNode(tradexml, "Data[@column='Volume']/text()");
			if (StringUtils.isNotBlank(szvolume))
			{
				volume = Double.valueOf(szvolume);
			}
			else
			{
				volume = null;
			}
			//String szmarket_expiry = Util.getNodeValueFromNode(rowxml, "Data[@column='Market_Expiry']/text()");
			market_product = Util.getNodeValueFromNode(tradexml, "Data[@column='Market_Product']/text()");
			option_type = Util.getNodeValueFromNode(tradexml, "Data[@column='OptionType']/text()");
			strike = Util.getNodeValueFromNode(tradexml, "Data[@column='Strike']/text()");
			String szprice = Util.getNodeValueFromNode(tradexml, "Data[@column='Price']/text()");
			if (StringUtils.isNotBlank(szprice))
			{
				price = Double.valueOf(szprice);
			}
			else
			{
				price = null;
			}
		}
		else
		{
			throw new InvalidTradeException("Cannot instatiate trade from null xml");
		}
	}

	//TODO change to the check for the correct status
	//!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!DO NOT CHECK THIS IN!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
	public boolean isRiskTrade()
	{
		return "DELETED".equals(business_state);
	}

	public boolean getProductISIN()
	{
		return true;

	}

	public boolean getBloombergData()
	{
		return true;
	}

	public boolean save()
	{
		return true;
	}

	public static class InvalidTradeException extends Exception
	{
		private static final long serialVersionUID = 8342399517645831386L;

		public InvalidTradeException(String szmessage) {
			super(szmessage);
		}
	}


}
