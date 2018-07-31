<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="text"/>
	<xsl:variable name="newline" select="'&#xa;'"/>
	<xsl:template match="//div/style"/>
	<xsl:template match="//div/span"/>
	<xsl:template match="tr">
		<xsl:if test="@id='resultsHeadings'">Contract,Qty<xsl:value-of select="$newline"/>
		</xsl:if>
		<xsl:if test="@class='dataRow'">
			<xsl:value-of select="td[1]"/>,<xsl:value-of select="td[7]"/>
			<xsl:value-of select="$newline"/>
		</xsl:if>
	</xsl:template>
</xsl:stylesheet>