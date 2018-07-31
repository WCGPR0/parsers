<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format">
	<xsl:output method="xml" indent="yes"/>
	<xsl:param name="date.current">09-12-2013 08:56:30</xsl:param>
	<xsl:param name="date.yesterday">08-12-2013 08:56:30</xsl:param>
	<xsl:param name="source">ALL</xsl:param>
	<xsl:template match="dummy">
		<xsl:element name="URLS">
		<xsl:if test="$source='TVUS' or $source='ALL'">
				<xsl:element name="URL">
					<xsl:element name="url">
						<xsl:text>https://www.icetradevault.com/tvus-ticker/resources/getTickers.json</xsl:text>
					</xsl:element>
					<xsl:element name="group"><xsl:text>TVProductMnemonic</xsl:text></xsl:element>
					<xsl:element name="cookie_url"><xsl:text>https://www.icetradevault.com/tvus-ticker</xsl:text></xsl:element>
					<xsl:element name="schema">
						<xsl:text>NULL,TVProductMnemonic,TradeAction,ContinuationEvent,TradeReportID,TradeReportRefID,ExecutionTime,ClearedOrBilateral,Collateralization,EndUserException,BespokeSwap,Block/LargeNotional,BlockUnit,FuturesContract,ExecutionVenue,StartDate,EndDate,Price,TotalQuantity,SettlementCurrency,SettlementFrequency,AssetClass,ResetFrequency,NotionalAmount,NotionalUnit,OptionStrikePrice,OptionType,OptionStyle,OptionPremium,OptionExpirationFrequency,OptionExpirationDate,PriceType,OtherPriceTerm,MultiAssetClassSwap,MacsPrimaryAssetClass,MacsSecondaryAssetClass,MixedSwap,mixedSwapOtherReportedSDR,DayCountConvention,PaymentFrequency1,PaymentFrequency2,ResetFrequency2,OptionLockoutPeriod,EmbeddedOption,NotionalAmount1,NotionalAmount2,NotionalCurrency1,NotionalCurrency2,Capped</xsl:text>
					</xsl:element>
					<xsl:element name="params">
						<xsl:element name="latestTickId">%c</xsl:element>
					</xsl:element>					
			</xsl:element>
			</xsl:if>
		<xsl:if test="$source='DTCC' or $source='ALL'">
				<xsl:element name="URL">
					<xsl:element name="url">
						<xsl:text>https://kgc0418-tdw-data-0.s3.amazonaws.com/slices/COMMODITIES_RSS_FEED.rss</xsl:text>
					</xsl:element>
					<xsl:element name="group"><xsl:text>TAXONOMY</xsl:text></xsl:element>
					<xsl:element name="schema"><xsl:text>DISSEMINATION_ID,ORIGINAL_DISSEMINATION_ID,ACTION,EXECUTION_TIMESTAMP,CLEARED,INDICATION_OF_COLLATERALIZATION,INDICATION_OF_END_USER_EXCEPTION,INDICATION_OF_OTHER_PRICE_AFFECTING_TERM,BLOCK_TRADES_AND_LARGE_NOTIONAL_OFF-FACILITY_SWAPS,EXECUTION_VENUE,EFFECTIVE_DATE,END_DATE,DAY_COUNT_CONVENTION,SETTLEMENT_CURRENCY,ASSET_CLASS,SUB-ASSET_CLASS_FOR_OTHER_COMMODITY,TAXONOMY,PRICE_FORMING_CONTINUATION_DATA,UNDERLYING_ASSET_1,UNDERLYING_ASSET_2,PRICE_NOTATION_TYPE,PRICE_NOTATION,ADDITIONAL_PRICE_NOTATION_TYPE,ADDITIONAL_PRICE_NOTATION,NOTIONAL_CURRENCY_1,NOTIONAL_CURRENCY_2,ROUNDED_NOTIONAL_AMOUNT_1,ROUNDED_NOTIONAL_AMOUNT_2,PAYMENT_FREQUENCY_1,PAYMENT_FREQUENCY_2,RESET_FREQUENCY_1,RESET_FREQUENCY_2,EMBEDED_OPTION,OPTION_STRIKE_PRICE,OPTION_TYPE,OPTION_FAMILY,OPTION_CURRENCY,OPTION_PREMIUM,OPTION_LOCK_PERIOD,OPTION_EXPIRATION_DATE,PRICE_NOTATION2_TYPE,PRICE_NOTATION2,PRICE_NOTATION3_TYPE,PRICE_NOTATION3</xsl:text>
					</xsl:element>			
			</xsl:element>
			</xsl:if>			
		</xsl:element>
	</xsl:template>
</xsl:stylesheet>
