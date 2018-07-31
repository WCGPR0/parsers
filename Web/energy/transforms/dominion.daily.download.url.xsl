<?xml version="1.0" encoding="UTF-8"?>
<!--$LastChangedRevision: 79164 $-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format">
	<xsl:template match="URLS">
		<xsl:element name="URLS">
			<xsl:apply-templates select="A/@HREF | a[1]/@href"/>
		</xsl:element>			
	</xsl:template>
	<xsl:template match="@HREF | @href">
		<xsl:element name="URL">
				<xsl:element name="url"><xsl:text>https://dekaflow.dominionenergy.com</xsl:text><xsl:value-of select="." /></xsl:element>
		</xsl:element>				
	</xsl:template>
</xsl:stylesheet>