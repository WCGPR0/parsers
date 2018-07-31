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
						<xsl:text>https://www.icetradevault.com/tvus-ticker/exportticks</xsl:text>
					</xsl:element>
					<xsl:element name="dateFormat"><xsl:text>YYYY-MM-dd</xsl:text></xsl:element>
					<xsl:element name="group"><xsl:text>TVProductMnemonic</xsl:text></xsl:element>
					<xsl:element name="cookie_url"><xsl:text>https://www.icetradevault.com/tvus-ticker</xsl:text></xsl:element>
					<xsl:element name="ticks"><xsl:text>1</xsl:text></xsl:element>
					<xsl:element name="frequency"><xsl:text>5</xsl:text></xsl:element>
					<xsl:element name="schema"><xsl:text>TVProductMnemonic,TradeAction,ContinuationEvent,TradeReportID,TradeReportRefID,ExecutionTime,ClearedOrBilateral,Collateralization,EndUserException,BespokeSwap,Block/LargeNotional,BlockUnit,FuturesContract,ExecutionVenue,StartDate,EndDate,Price,TotalQuantity,SettlementCurrency,SettlementFrequency,AssetClass,ResetFrequency,NotionalAmount,NotionalUnit,OptionStrikePrice,OptionType,OptionStyle,OptionPremium,OptionExpirationFrequency,OptionExpirationDate,PriceType,OtherPriceTerm,MultiAssetClassSwap,MacsPrimaryAssetClass,MacsSecondaryAssetClass,MixedSwap,mixedSwapOtherReportedSDR,DayCountConvention,PaymentFrequency1,PaymentFrequency2,ResetFrequency2,OptionLockoutPeriod,EmbeddedOption,NotionalAmount1,NotionalAmount2,NotionalCurrency1,NotionalCurrency2,Capped</xsl:text>
					</xsl:element>
				<xsl:element name="params">
					<xsl:element name="tickStartDate">
						<xsl:value-of select="substring($date.yesterday,7,4)"/><xsl:text>-</xsl:text><xsl:value-of select="substring($date.yesterday,4,2)"/><xsl:text>-</xsl:text><xsl:value-of select="substring($date.yesterday,1,2)"/>
					</xsl:element>
					<xsl:element name="tickEndDate">
						<xsl:value-of select="substring($date.current,7,4)"/><xsl:text>-</xsl:text><xsl:value-of select="substring($date.current,4,2)"/><xsl:text>-</xsl:text><xsl:value-of select="substring($date.current,1,2)"/>
					</xsl:element>
					<xsl:element name="marketType">All</xsl:element>
					<xsl:element name="productMnemonic" />
				</xsl:element>
			</xsl:element>
			</xsl:if>
		</xsl:element>
	</xsl:template>
</xsl:stylesheet>
