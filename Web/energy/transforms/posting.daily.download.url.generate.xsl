<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format">
	<xsl:output method="xml" indent="yes"/>
	<xsl:param name="date.current">09-12-2013 08:56:30</xsl:param>
	<xsl:param name="date.yesterday">08-12-2013 08:56:30</xsl:param>
	<xsl:param name="source">ALL</xsl:param>
	<xsl:template match="dummy">
		<xsl:element name="URLS">
		<xsl:if test="$source='DOMINION' or $source='ALL'">
				<xsl:element name="URL">
					<xsl:element name="url">
						<xsl:text><![CDATA[https://dekaflow.dominionenergy.com/servlet/ContentServlet?method=operGrid&company=cpt]]></xsl:text>
					</xsl:element>
					<xsl:element name="dateFormat"><xsl:text>MM/dd/yyyy HH:mm</xsl:text></xsl:element>					
					<xsl:element name="schema"><xsl:text>Receipt point(s),OIA,Loc,Loc Name,Operating,Total Scheduled,Operationally,Design,All Quantities,Quantity Not,Flow</xsl:text></xsl:element>
					<xsl:element name="pattern"><xsl:text><![CDATA[^(?:([A-Z*&,. ]*) )?(X) (0|[1-9][0-9]*) ([A-Z*&() \/-]+ ?[A-Z*&() \/]-*) (\d{1,3}(?:[\,]\d{1,3})*) (\d{1,3}(?:[\,]\d{1,3})*) (\d{1,3}(?:[\,]\d{1,3})*) (\d{1,3}(?:[\,]\d{1,3})*) ([YN]) ([RD]|SW) ([YN])$]]></xsl:text></xsl:element>
					<xsl:element name="urlPattern"><xsl:text><![CDATA[<td>(<a[\s\S]*?<\/a>)<\/td>]]></xsl:text></xsl:element>
			</xsl:element>
			</xsl:if>
		<!-- Historical Data
		<xsl:if test="$source='DOMINION' or $source='ALL'">
				<xsl:element name="URL">
					<xsl:element name="url">
						<xsl:text><![CDATA[https://dekaflow.dominionenergy.com/servlet/InfoPostServlet?region=null&company=cpt&method=headers&category=Capacity&subcategory=Operationally+Available]]></xsl:text>
					</xsl:element>
					<xsl:element name="dateFormat"><xsl:text>MM/dd/yyyy HH:mm</xsl:text></xsl:element>					
					<xsl:element name="schema"><xsl:text>Receipt point(s),OIA,Loc,Loc Name,Operating,Total Scheduled,Operationally,Design,All Quantities,Quantity Not,Flow</xsl:text></xsl:element>
					<xsl:element name="pattern"><xsl:text><![CDATA[^(?:([A-Z*&,. ]*) )?(X) (0|[1-9][0-9]*) ([A-Z*&() \/-]+ ?[A-Z*&() \/]-*) (\d{1,3}(?:[\,]\d{1,3})*) (\d{1,3}(?:[\,]\d{1,3})*) (\d{1,3}(?:[\,]\d{1,3})*) (\d{1,3}(?:[\,]\d{1,3})*) ([YN]) ([RD]|SW) ([YN])$]]></xsl:text></xsl:element>
					<xsl:element name="urlPattern"><xsl:text><![CDATA[<TD.*?>(<A.*? HREF=".*?">.*?Intraday 1<\/A>)<\/TD>)]]></xsl:text></xsl:element>
			</xsl:element>
			</xsl:if>		
		<xsl:if test="$source='CHENIERE' or $source='ALL'">
				<xsl:element name="URL">
					<xsl:element name="url">
						<xsl:text><![CDATA[http://lngconnectionapi.cheniere.com/api/Capacity/GetCapacity?tspNo=200&cycleId=null&locationId=0]]></xsl:text>
					</xsl:element>
					<xsl:element name="dateFormat"><xsl:text>MM/dd/yyyy</xsl:text></xsl:element>									
					<xsl:element name="schema"><xsl:text>tsp,tsP_NAME,cycle,postinG_DT_TIME,avaiL_CAP_EFF_DT_TIME,caP_TYPE_DESC,loc,loC_NAME,loC_QTI,meaS_BASIS,it,alL_QTY_AVAIL,desigN_OPER_CAP,opeR_CAP,scheD_QTY,qtY_AVAIL,floW_IND,row_Number</xsl:text></xsl:element>
			</xsl:element>
			</xsl:if>
Historical Data -->
		<xsl:if test="$source='CHENIERE' or $source='ALL'">
				<xsl:element name="URL">
					<xsl:element name="url">
						<xsl:text><![CDATA[http://lngconnectionapi.cheniere.com/api/Capacity/GetCapacity?tspNo=200&cycleId=132&locationId=0]]></xsl:text>
					</xsl:element>
					<xsl:element name="dateFormat"><xsl:text>MM/dd/yyyy</xsl:text></xsl:element>									
					<xsl:element name="schema"><xsl:text>tsp,tsP_NAME,cycle,postinG_DT_TIME,avaiL_CAP_EFF_DT_TIME,caP_TYPE_DESC,loc,loC_NAME,loC_QTI,meaS_BASIS,it,alL_QTY_AVAIL,desigN_OPER_CAP,opeR_CAP,scheD_QTY,qtY_AVAIL,floW_IND,row_Number</xsl:text></xsl:element>
					<xsl:element name="params">
						<xsl:element name="beginDate">01/08/2018</xsl:element>
						<xsl:element name="endDate"><xsl:value-of select="concat(substring($date.current,4,2), '/', substring($date.current,1,2), '/' , substring($date.current,7,4))" /></xsl:element>
					</xsl:element>
			</xsl:element>
			</xsl:if>
		</xsl:element>
	</xsl:template>
</xsl:stylesheet>
