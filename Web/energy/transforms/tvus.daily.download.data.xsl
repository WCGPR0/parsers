<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format" exclude-result-prefixes="fo">
<xsl:output method="xml" indent="yes"/>
<xsl:template match="/">
	<trades>
		<xsl:attribute name="source"><xsl:text>TVUS</xsl:text></xsl:attribute>
		<xsl:apply-templates select="list/list" mode="filter">
			<xsl:sort select="string-array[string[1]='TradeReportID']/string[2]/text()" data-type="number" order="ascending"/>
		</xsl:apply-templates>
	</trades>
</xsl:template>

<xsl:template match="list" mode="filter">
	<xsl:variable name="underlying" select="string-array[string[1]='TVProductMnemonic']/string[2]/text()"/>
	<!-- Filter for Nat Gas and Electricity -->
	<xsl:if test="substring($underlying,1,1) = 'N'"><xsl:apply-templates select="." mode="trade"/></xsl:if>
</xsl:template>

<xsl:template match="list" mode="trade">
	<xsl:variable name="price">
		<xsl:value-of select="string-array[string[1]='Price']/string[2]/text()" />
	</xsl:variable>
	<xsl:variable name="strike" select="translate(string-array[string[1]='OptionStrikePrice']/string[2]/text(), ',', '')"/>
	<xsl:variable name="ccy1.report" select="string-array[string[1]='BlockUnit']/string[2]/text()"/>
	<xsl:variable name="premium" select="string-array[string[1]='OptionPremium']/string[2]/text()"/>
	<xsl:variable name="ccy1">
		<xsl:value-of select="$ccy1.report" />
	</xsl:variable>
	<xsl:variable name="amount1" select="translate(string-array[string[1]='TotalQuantity']/string[2]/text(), ',+', '')"/>
	<xsl:variable name="underlying">
		<xsl:value-of select="string-array[string[1]='TVProductMnemonic']/string[2]/text()"/>
	</xsl:variable>
	<trade>
		<id>
			<xsl:value-of select="string-array[string[1]='TradeReportID']/string[2]/text()"/>
		</id>
		<id.original>
			<xsl:value-of select="string-array[string[1]='TradeReportRefID']/string[2]/text()"/>
		</id.original>
		<action>
			<xsl:value-of select="string-array[string[1]='TradeAction']/string[2]/text()"/>
		</action>
		<execution.timestamp>
			<xsl:choose>
				<xsl:when test="substring(string-array[string[1]='ExecutionTime']/string[2]/text(),string-length('2013-02-28T16:03:03Z'),1) = 'Z'">
					<xsl:value-of select="substring(string-array[string[1]='ExecutionTime']/string[2]/text(),1,string-length('2013-02-28T16:03:03Z')-1)" />
				</xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="string-array[string[1]='ExecutionTime']/string[2]/text()"/>
				</xsl:otherwise>
			</xsl:choose>
		</execution.timestamp>
		<ccy1>
			<xsl:value-of select="$ccy1"/>
		</ccy1>		
		<amount1>
			<xsl:value-of select="$amount1"/>
		</amount1>
		<underlying>
			<xsl:value-of select="$underlying"/>
		</underlying>
		<price>
			<xsl:value-of select="$price" />
		</price>
		<premium>
			<xsl:if test="boolean($premium)">
				<xsl:value-of select="$premium div $amount1" />			
			</xsl:if>
		</premium>
		<strike>
			<xsl:value-of select="$strike"/>
		</strike>
		<option>
			<xsl:variable name="option.original.format" select="string-array[string[1]='OptionType']/string[2]/text()"/>
			<xsl:choose>
				<xsl:when test="boolean($option.original.format) = false()">X</xsl:when>
				<xsl:when test="$option.original.format='C-'">CALL</xsl:when>
				<xsl:when test="$option.original.format='P-'">PUT</xsl:when>
				<xsl:otherwise><xsl:value-of select="translate($option.original.format,'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ')"/></xsl:otherwise>
			</xsl:choose>
		</option>
		<option.ccy>
			<xsl:value-of select="string-array[string[1]='NotionalCurrency1']/string[2]/text()"/>
		</option.ccy>		
		<style>
			<xsl:value-of select="string-array[string[1]='OptionStyle']/string[2]/text()"/>
		</style>
		<expiry.date>
				<xsl:value-of select="string-array[string[1]='EndDate']/string[2]/text()"/>
		</expiry.date>
		<trade.date>
			<xsl:value-of select="string-array[string[1]='StartDate']/string[2]/text()"/>
		</trade.date>
		<execution.venue>
			<xsl:value-of select="string-array[string[1]='ExecutionVenue']/string[2]/text()"/>
		</execution.venue>
	</trade>
</xsl:template>

</xsl:stylesheet>