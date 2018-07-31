<?xml version="1.0" encoding="UTF-8"?>
<!-- Attributes of "type": INT(N), VARCHAR(N), DATE, etc -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions"  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="schema.xsd">
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>

	<!-- Parameters from the Java Program; if not using, just ignore! If must change, change the Java program as well.
		 Currently is ONLY YYYY-MM-DD			-->
	<xsl:param name="REPORT_TIME">2017-05-04</xsl:param>
	<xsl:param name="PREPARE"><xsl:value-of select="false()" /></xsl:param> <!-- Will be set true initially to prepare the SQL Query, to get the headers -->
	<!-- Matches everything else, and ignores -->
	<xsl:template match="@* | node()" />
	<!-- Applies template to every trade, ignores first layer essentially -->
	<xsl:template match="trades">
		<xsl:element name="trades">
					<xsl:choose>
					<xsl:when test="$PREPARE = 'true'">
						<xsl:apply-templates select="./*/trade" />
					</xsl:when>
					<xsl:otherwise>
									<xsl:copy-of	select="document('')/*/@xsi:noNamespaceSchemaLocation"/>
									<xsl:apply-templates select="./*/trade" />
						</xsl:otherwise>
					</xsl:choose>
		</xsl:element>
	</xsl:template>
	
	<xsl:template match="trade">
	
		<!-- Variables -->
		<xsl:variable name="currency">
		<xsl:call-template name="default">
			<xsl:with-param name="node" select="SettlementCurrency"/>
			<xsl:with-param name="value" select="'USD'"/>
		</xsl:call-template>
	</xsl:variable>
		<xsl:variable name="volume">
		<xsl:call-template name="default-int">
			<xsl:with-param name="node" select="TotalQuantity"/>
			<xsl:with-param name="value" select="number(0)"/>
		</xsl:call-template>
	</xsl:variable>	
		<xsl:variable name="price">
		<xsl:call-template name="default-int">
			<xsl:with-param name="node" select="Price"/>
			<xsl:with-param name="value" select="number(0)"/>
		</xsl:call-template>
	</xsl:variable>
		<xsl:variable name="strike">
		<xsl:call-template name="default-int">
			<xsl:with-param name="node" select="OptionStrikePrice"/>
			<xsl:with-param name="value" select="number(0)"/>
		</xsl:call-template>
	</xsl:variable>		
			<xsl:variable name="strategy">
			<xsl:call-template name="default">
				<xsl:with-param name="node" select="OptionType"/>
				<xsl:with-param name="value" select="'X'"/>
			</xsl:call-template>
		</xsl:variable>
		<xsl:variable name="premium">
			<xsl:call-template name="default">
				<xsl:with-param name="node" select="OptionPremium"/>
				<xsl:with-param name="value" select="number(0)"/>
			</xsl:call-template>
		</xsl:variable>
		<xsl:variable name="unit">
			<xsl:call-template name="default">
				<xsl:with-param name="node" select="BlockUnit"/>
				<xsl:with-param name="value" select="'X'"/>
			</xsl:call-template>
		</xsl:variable>		
	
		<xsl:element name="trade">
			<xsl:element name="SELECT">
				<xsl:element name="REPORT_TIME">
					<xsl:attribute name="type">TIME</xsl:attribute>
					<xsl:value-of select="$REPORT_TIME"/>
				</xsl:element>	
				<xsl:element name="PRD_CODE">
					<xsl:attribute name="type">VARCHAR(5)</xsl:attribute>
					<xsl:text>EN</xsl:text>
				</xsl:element>
				<xsl:element name="CONTRACT_ID">
					<xsl:attribute name="type">INT(11)</xsl:attribute>
					<xsl:value-of select="ID"/>
				</xsl:element>
				<xsl:element name="INSTRUMENT_TYPE">
					<xsl:attribute name="type">VARCHAR(10)</xsl:attribute>
					<xsl:choose>
						<xsl:when test="$strategy = 'X'">FUTURES</xsl:when>
						<xsl:otherwise>
							<xsl:text>OPTIONS</xsl:text>
						</xsl:otherwise>
					</xsl:choose>					
				</xsl:element>								
				<xsl:element name="CONTRACT_NAME">
					<xsl:attribute name="type">VARCHAR(100)</xsl:attribute>
					<xsl:value-of select="name(..)"/>
				</xsl:element>
				<xsl:element name="START_DATE">
					<xsl:attribute name="type">DATE</xsl:attribute>
					<xsl:value-of select="StartDate"/>
				</xsl:element>
				<xsl:element name="END_DATE">
					<xsl:attribute name="type">DATE</xsl:attribute>
					<xsl:value-of select="EndDate"/>
				</xsl:element>											
				<xsl:element name="SOURCE">
					<xsl:attribute name="type">VARCHAR(10)</xsl:attribute>
					<xsl:text>ICE</xsl:text>
				</xsl:element>				
				<xsl:element name="STRIKE">
					<xsl:attribute name="type">NUMBER()</xsl:attribute>
					<xsl:value-of select="$strike" />
				</xsl:element>
			<xsl:element name="STRATEGY">
				<xsl:attribute name="type">VARCHAR(50)</xsl:attribute>
				<xsl:value-of select="translate($strategy,'aclptu','ACLPTU')"/>
			</xsl:element>							
			</xsl:element>
			<xsl:element name="UPDATE">
				<xsl:element name="SETTLEMENT_PRICE">
					<xsl:attribute name="type">NUMBER(5)</xsl:attribute>
					<xsl:value-of select="$price"/>
				</xsl:element>				
			<xsl:element name="VOLUME">
				<xsl:attribute name="type">NUMBER(11)</xsl:attribute>
				<xsl:value-of select="$volume"/>
			</xsl:element>
			<xsl:element name="PREMIUM">
				<xsl:attribute name="type">NUMBER()</xsl:attribute>
				<xsl:value-of select="$premium" />
			</xsl:element>							
			<xsl:element name="UNIT">
				<xsl:attribute name="type">VARCHAR(100)</xsl:attribute>
				<xsl:value-of select="translate($unit,'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ')"/>
			</xsl:element>
			<xsl:element name="EXEC_TIME">
				<xsl:attribute name="type">TIME</xsl:attribute>
				<xsl:value-of select="translate(translate(ExecutionTime,'T',' '),'Z','')"/>
			</xsl:element>								
		</xsl:element>
		</xsl:element>		
	</xsl:template>
	<xsl:template name="default">
		<xsl:param name="node"/>
		<xsl:param name="value"/>
		<xsl:choose>
			<xsl:when test="not(string($node))">
				<xsl:value-of select="$value"/>
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="$node"/>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
		<xsl:template name="default-int">
		<xsl:param name="node"/>
		<xsl:param name="value"/>
		<xsl:choose>
			<xsl:when test="not(string($node)) or string(number($node)) = 'NaN'">
				<xsl:value-of select="$value"/>
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="$node"/>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
</xsl:stylesheet>