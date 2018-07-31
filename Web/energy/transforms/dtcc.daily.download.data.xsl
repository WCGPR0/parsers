<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format" exclude-result-prefixes="fo">
<xsl:output method="xml" indent="yes"/>

<xsl:template match="/">
	<trades>
		<xsl:apply-templates select="list/list" mode="filter">
			<xsl:sort select="string-array[string[1]='DISSEMINATION_ID']/string[2]/text()" data-type="number" order="ascending"/>
		</xsl:apply-templates>
	</trades>
</xsl:template>

<xsl:template match="list" mode="filter">
	<xsl:variable name="taxonomy" select="string-array[string[1]='TAXONOMY']/string[2]/text()"/>
	<xsl:variable name="ccy1" select="string-array[string[1]='NOTIONAL_CURRENCY_1']/string[2]/text()"/>
	<xsl:variable name="ccy2" select="string-array[string[1]='NOTIONAL_CURRENCY_2']/string[2]/text()"/>
	<xsl:variable name="ccy3" select="string-array[string[1]='NOTIONAL_CURRENCY_3']/string[2]/text()"/>
	<xsl:variable name="strike" select="string-array[string[1]='OPTION_STRIKE_PRICE']/string[2]/text()"/>
	<xsl:variable name="smallcase" select="'abcdefghijklmnopqrstuvwxyz'" />
	<xsl:variable name="uppercase" select="'ABCDEFGHIJKLMNOPQRSTUVWXYZ'" />	
	<xsl:if test="(contains(translate($taxonomy, $smallcase, $uppercase), 'GAS') or contains(translate($taxonomy, $smallcase, $uppercase), 'ELEC') or contains(translate($taxonomy, $smallcase, $uppercase),'NG HH NMX')) and not(substring($ccy1,1,1)='' and substring($ccy2,1,1)='' and substring($ccy3,1,1)='')">
		<xsl:apply-templates select="." mode="trade"/>
	</xsl:if>
</xsl:template>

<xsl:template match="list" mode="trade">
	<xsl:variable name="taxonomy" select="string-array[string[1]='TAXONOMY']/string[2]/text()"/>
	<xsl:variable name="price">
		<xsl:choose>
			<xsl:when test="boolean(string-array[string[1]='PRICE_NOTATION']/string[2]/text())"><xsl:value-of select="string-array[string[1]='PRICE_NOTATION']/string[2]/text()" /></xsl:when>
			<xsl:when test="boolean(string-array[string[1]='PRICE_NOTATION2']/string[2]/text())"><xsl:value-of select="string-array[string[1]='PRICE_NOTATION2']/string[2]/text()" /></xsl:when>
			<xsl:otherwise><xsl:value-of select="string-array[string[1]='PRICE_NOTATION3']/string[2]/text()" /></xsl:otherwise>
		</xsl:choose>
	</xsl:variable>
	<xsl:variable name="strike" select="translate(string-array[string[1]='OPTION_STRIKE_PRICE']/string[2]/text(), ',', '')"/>
	<xsl:variable name="ccy1.report" select="string-array[string[1]='NOTIONAL_CURRENCY_1']/string[2]/text()"/>
	<xsl:variable name="ccy2.report" select="string-array[string[1]='NOTIONAL_CURRENCY_2']/string[2]/text()"/>
	<xsl:variable name="ccy3.report" select="string-array[string[1]='NOTIONAL_CURRENCY_3']/string[2]/text()"/>	
	<xsl:variable name="amount1" select="translate(string-array[string[1]='ROUNDED_NOTIONAL_AMOUNT_1']/string[2]/text(), ',+', '')"/>	
	<xsl:variable name="premium" select="string-array[string[1]='OPTION_PREMIUM']/string[2]/text()"/>
	<xsl:variable name="ccy1">
	<xsl:choose>
		<xsl:when test="boolean($ccy1.report)"><xsl:value-of select="$ccy1.report" /></xsl:when>
		<xsl:when test="boolean($ccy2.report)"><xsl:value-of select="$ccy2.report" /></xsl:when>
		<xsl:otherwise><xsl:value-of select="$ccy3.report" /></xsl:otherwise>
	</xsl:choose>
	</xsl:variable>
	<xsl:variable name="underlying">
		<xsl:choose>
			<xsl:when test="boolean(string-array[string[1]='UNDERLYING_ASSET_2']/string[2]/text()) and number($price) &lt; 1"><xsl:value-of select="string-array[string[1]='UNDERLYING_ASSET_2']/string[2]/text()" /></xsl:when>
			<xsl:when test="boolean(string-array[string[1]='UNDERLYING_ASSET_1']/string[2]/text())"><xsl:value-of select="string-array[string[1]='UNDERLYING_ASSET_1']/string[2]/text()" /></xsl:when>
			<xsl:otherwise><xsl:value-of select="$taxonomy" /></xsl:otherwise>
		</xsl:choose>
	</xsl:variable>
	<trade>
		<id>
			<xsl:value-of select="string-array[string[1]='DISSEMINATION_ID']/string[2]/text()"/>
		</id>
		<id.original>
			<xsl:value-of select="string-array[string[1]='ORIGINAL_DISSEMINATION_ID']/string[2]/text()"/>
		</id.original>
		<action>
			<xsl:value-of select="string-array[string[1]='ACTION']/string[2]/text()"/>
		</action>
		<execution.timestamp>
			<xsl:value-of select="string-array[string[1]='EXECUTION_TIMESTAMP']/string[2]/text()"/>
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
			<xsl:variable name="option.original.format" select="string-array[string[1]='OPTION_TYPE']/string[2]/text()"/>
			<xsl:choose>
				<xsl:when test="boolean($option.original.format) = false()">X</xsl:when>
				<xsl:when test="$option.original.format='C-'">CALL</xsl:when>
				<xsl:when test="$option.original.format='P-'">PUT</xsl:when>
				<xsl:otherwise><xsl:value-of select="$option.original.format"/></xsl:otherwise>
			</xsl:choose>
		</option>
		<option.ccy>
			<xsl:value-of select="string-array[string[1]='OPTION_CURRENCY']/string[2]/text()"/>
		</option.ccy>		
		<style>
			<xsl:value-of select="string-array[string[1]='OPTION_FAMILY']/string[2]/text()"/>
		</style>
		<expiry.date>
				<xsl:value-of select="string-array[string[1]='END_DATE']/string[2]/text()"/>
		</expiry.date>
		<trade.date>
			<xsl:value-of select="string-array[string[1]='EFFECTIVE_DATE']/string[2]/text()"/>
		</trade.date>
		<execution.venue>
			<xsl:value-of select="string-array[string[1]='EXECUTION_VENUE']/string[2]/text()"/>
		</execution.venue>
	</trade>
</xsl:template>
</xsl:stylesheet>