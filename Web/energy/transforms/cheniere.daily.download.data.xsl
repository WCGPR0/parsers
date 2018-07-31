<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format" exclude-result-prefixes="fo">
	<xsl:output method="xml" indent="yes"/>
	<xsl:template match="/">
		<trades>
			<xsl:attribute name="source"><xsl:text>CHENIERE</xsl:text></xsl:attribute>
			<xsl:apply-templates select="list/list" mode="filter">
				<xsl:sort select="string-array[string[1]='TradeReportID']/string[2]/text()" data-type="number" order="ascending"/>
			</xsl:apply-templates>
		</trades>
	</xsl:template>
	<xsl:template match="list" mode="filter">
	<xsl:variable name="id" select="translate(string-array[string[1]='Loc']/string[2]/text(),',','')" />
		<xsl:apply-templates select="." mode="trade"/>	
	</xsl:template>
	<xsl:template match="list" mode="trade">
		<trade>
			<xsl:variable name="id" select="string-array[string[1]='loc']/string[2]/text()"/>
			<xsl:variable name="loc_name" select="string-array[string[1]='loC_NAME']/string[2]/text()" />
			<xsl:variable name="operating.capacity" select="string-array[string[1]='opeR_CAP']/string[2]/text()" />
			<xsl:variable name="total.scheduled.qty" select="string-array[string[1]='scheD_QTY']/string[2]/text()" />
			<xsl:variable name="operationally.avaliable.capacity" select="string-array[string[1]='qtY_AVAIL']/string[2]/text()" />
			<xsl:variable name="design.capacity" select="string-array[string[1]='desigN_OPER_CAP']/string[2]/text()" />
			<xsl:variable name="posting.date" select="string-array[string[1]='postinG_DT_TIME']/string[2]/text()" />
			<xsl:variable name="flow" select="string-array[string[1]='floW_IND']/string[2]/text()" />
			<id><xsl:value-of select="$id" /></id>
			<loc_name><xsl:value-of select="$loc_name" /></loc_name>
			<delivery_or_receipt><xsl:value-of select="$flow" /></delivery_or_receipt>
			<design_capacity><xsl:value-of select="round($design.capacity)" /></design_capacity>
			<operating_capacity><xsl:value-of select="round($operating.capacity)" /></operating_capacity>
			<operating_avail_capacity><xsl:value-of select="round($operationally.avaliable.capacity)" /></operating_avail_capacity>
			<block_volume><xsl:value-of select="round($total.scheduled.qty)" /></block_volume>
			<posting_date><xsl:value-of select="$posting.date" /></posting_date>
		</trade>
	</xsl:template>
</xsl:stylesheet>
