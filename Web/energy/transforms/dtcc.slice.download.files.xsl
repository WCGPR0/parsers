<?xml version="1.0" encoding="UTF-8"?>
<!--$LastChangedRevision: 79164 $-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format">
	<xsl:template match="table">
		<xsl:element name="Files">
			<xsl:apply-templates select="tr/td[a]"/>
		</xsl:element>
	</xsl:template>
	<xsl:template match="td">
		<xsl:element name="File">
			<xsl:element name="Key">
				<xsl:value-of select="a"/>
			</xsl:element>
			<xsl:element name="Link">
				<xsl:value-of select="a/@href"/>
			</xsl:element>
		</xsl:element>
	</xsl:template>
</xsl:stylesheet>
