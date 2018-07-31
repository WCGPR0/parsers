<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format">

<xsl:output method="text"/>

<xsl:param name="date.current">09-12-2013 08:56:30</xsl:param>
<xsl:param name="date.yesterday">08-12-2013 08:56:30</xsl:param>

<xsl:template match="/">
	<xsl:text>https://kgc0418-tdw-data-0.s3.amazonaws.com/slices/CUMULATIVE_COMMODITIES_</xsl:text>
	<xsl:value-of select="substring($date.yesterday, 7, 4)"/>
	<xsl:text>_</xsl:text>
	<xsl:value-of select="substring($date.yesterday, 4, 2)"/>
	<xsl:text>_</xsl:text>
	<xsl:value-of select="substring($date.yesterday, 1, 2)"/>
	<xsl:text>.zip</xsl:text>
</xsl:template>

</xsl:stylesheet>
