<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format">
	<xsl:output method="xml" indent="yes"/>
	<xsl:param name="date.current">09-12-2013 08:56:30</xsl:param>
	<xsl:param name="date.yesterday">08-12-2013 08:56:30</xsl:param>
	<xsl:param name="source">ALL</xsl:param>
	<xsl:template match="dummy">
		<xsl:element name="URLS">
		<xsl:if test="$source='ICE' or $source='ALL'">
				<xsl:element name="URL">
					<xsl:element name="url">
						<xsl:text>https://www.theice.com/publicdocs/dwreports/dmr/ifus-ed/futures/ALL_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 7, 4)"/>
						<xsl:text>_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 4, 2)"/>
						<xsl:text>_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 1, 2)"/>
						<xsl:text>.pdf</xsl:text>
					</xsl:element>
					<xsl:element name="dateFormat"><xsl:text>dd-MMM-yyyy</xsl:text></xsl:element>
					<xsl:element name="pattern"><xsl:text>\d{1,2}-\w{3}-\d{4}</xsl:text></xsl:element>
				</xsl:element>
				<xsl:element name="URL">
					<xsl:element name="url">
						<xsl:text>https://www.theice.com/publicdocs/dwreports/dmr/ifeu/futures/ALL_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 7, 4)"/>
						<xsl:text>_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 4, 2)"/>
						<xsl:text>_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 1, 2)"/>
						<xsl:text>.pdf</xsl:text>
					</xsl:element>
					<xsl:element name="dateFormat"><xsl:text>dd-MMM-yyyy</xsl:text></xsl:element>
					<xsl:element name="pattern"><xsl:text>\d{1,2}-\w{3}-\d{4}</xsl:text></xsl:element>
				</xsl:element>
				<xsl:element name="URL">
					<xsl:element name="url">
						<xsl:text>https://www.theice.com/publicdocs/dwreports/dmr/ifeu/futures/T_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 7, 4)"/>
						<xsl:text>_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 4, 2)"/>
						<xsl:text>_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 1, 2)"/>
						<xsl:text>.pdf</xsl:text>
					</xsl:element>
					<xsl:element name="dateFormat"><xsl:text>dd-MMM-yyyy</xsl:text></xsl:element>
					<xsl:element name="pattern"><xsl:text>\d{1,2}-\w{3}-\d{4}</xsl:text></xsl:element>
				</xsl:element>		
				<xsl:element name="URL">
					<xsl:element name="url">
						<xsl:text>https://www.theice.com/publicdocs/dwreports/dmr/ifes/futures/ALL_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 7, 4)"/>
						<xsl:text>_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 4, 2)"/>
						<xsl:text>_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 1, 2)"/>
						<xsl:text>.pdf</xsl:text>
					</xsl:element>
					<xsl:element name="dateFormat"><xsl:text>dd-MMM-yyyy</xsl:text></xsl:element>
					<xsl:element name="pattern"><xsl:text>\d{1,2}-\w{3}-\d{4}</xsl:text></xsl:element>
				</xsl:element>				
				<!-- Options URLs -->	
				<xsl:element name="URL">
					<xsl:element name="url">
						<xsl:attribute name="isOptTrade"><xsl:value-of select="true()" /></xsl:attribute>
						<xsl:text>https://www.theice.com/publicdocs/dwreports/dmr/ifes/options/ALL_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 7, 4)"/>
						<xsl:text>_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 4, 2)"/>
						<xsl:text>_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 1, 2)"/>
						<xsl:text>.pdf</xsl:text>						
					</xsl:element>
					<xsl:element name="dateFormat"><xsl:text>dd-MMM-yyyy</xsl:text></xsl:element>
					<xsl:element name="pattern"><xsl:text>\d{1,2}-\w{3}-\d{4}</xsl:text></xsl:element>
				</xsl:element>
				<xsl:element name="URL">
					<xsl:element name="url">
						<xsl:attribute name="isOptTrade"><xsl:value-of select="true()" /></xsl:attribute>
						<xsl:text>https://www.theice.com/publicdocs/dwreports/dmr/ifus-ed/options/ALL_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 7, 4)"/>
						<xsl:text>_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 4, 2)"/>
						<xsl:text>_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 1, 2)"/>
						<xsl:text>.pdf</xsl:text>						
					</xsl:element>
					<xsl:element name="dateFormat"><xsl:text>dd-MMM-yyyy</xsl:text></xsl:element>
					<xsl:element name="pattern"><xsl:text>\d{1,2}-\w{3}-\d{4}</xsl:text></xsl:element>
				</xsl:element>
				<xsl:element name="URL">
					<xsl:element name="url">
						<xsl:attribute name="isOptTrade"><xsl:value-of select="true()" /></xsl:attribute>
						<xsl:text>https://www.theice.com/publicdocs/dwreports/dmr/ifeu/options/ALL_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 7, 4)"/>
						<xsl:text>_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 4, 2)"/>
						<xsl:text>_</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 1, 2)"/>
						<xsl:text>.pdf</xsl:text>						
					</xsl:element>
					<xsl:element name="dateFormat"><xsl:text>dd-MMM-yyyy</xsl:text></xsl:element>
					<xsl:element name="pattern"><xsl:text>\d{1,2}-\w{3}-\d{4}</xsl:text></xsl:element>
				</xsl:element>				
		</xsl:if>
		<!-- /ICE Reports -->
		<!-- CME Reports -->
		<xsl:if test="$source='CME' or $source='ALL'">
				<xsl:element name="URL">
					<xsl:element name="url">
						<xsl:text>http://www.cmegroup.com/daily_bulletin/current/Section61_Energy_Futures_Products.pdf</xsl:text>
					</xsl:element>
					<xsl:element name="dateFormat">MM/dd/yyyy</xsl:element>
					<xsl:element name="pattern"><xsl:text>.+(\w{3} \d{1,2}, \d{4}).+</xsl:text></xsl:element>
				</xsl:element>
				<xsl:element name="URL">
					<xsl:element name="url">
						<xsl:attribute name="isOptTrade"><xsl:value-of select="true()" /></xsl:attribute>
						<xsl:text>http://www.cmegroup.com/daily_bulletin/current/Section63_Energy_Options_Products.pdf</xsl:text>
					</xsl:element>
					<xsl:element name="dateFormat">MM/dd/yyyy</xsl:element>
					<xsl:element name="pattern"><xsl:text>.+(\w{3} \d{1,2}, \d{4}).+</xsl:text></xsl:element>
				</xsl:element>
		</xsl:if>
		<!-- /CME Reports -->
		<!-- Nasdaq Reports -->
		<xsl:if test="$source='NFX' or $source='ALL'">
				<xsl:element name="URL">
						<xsl:element name="url">
						<xsl:text>ftp://ftp.nasdaqtrader.com/files/NFX/SettlementPriceData/SettlementPriceData</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 7, 4)"/>
						<xsl:value-of select="substring($date.yesterday, 4, 2)"/>
						<xsl:value-of select="substring($date.yesterday, 1, 2)"/>
						<xsl:text>.csv</xsl:text>
						</xsl:element>
						<xsl:element name="dateFormat">yyyyMMdd</xsl:element>
						<xsl:element name="pattern" />
				</xsl:element>
				<xsl:element name="URL">
						<xsl:element name="url">
						<xsl:attribute name="isOptTrade"><xsl:value-of select="true()" /></xsl:attribute>						
						<xsl:text>ftp://ftp.nasdaqtrader.com/files/NFX/SettlementPriceData/SettlementPriceData</xsl:text>
						<xsl:value-of select="substring($date.yesterday, 7, 4)"/>
						<xsl:value-of select="substring($date.yesterday, 4, 2)"/>
						<xsl:value-of select="substring($date.yesterday, 1, 2)"/>
						<xsl:text>.csv</xsl:text>
						</xsl:element>
						<xsl:element name="dateFormat">yyyyMMdd</xsl:element>
						<xsl:element name="pattern" />
				</xsl:element>				
		</xsl:if>
		<!-- /Nasdaq Reports -->
		</xsl:element>
	</xsl:template>
</xsl:stylesheet>
