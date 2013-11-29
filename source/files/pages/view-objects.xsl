<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="context"/>

<xsl:template match="/index">
	<html>
		<head>
			<title><xsl:value-of select="@studyName"/></title>
			<link rel="stylesheet" href="/view-objects.css" type="text/css"/>
			<script language="JavaScript" type="text/javascript" src="/JSAJAX.js">;</script>
			<script language="JavaScript" type="text/javascript" src="/view-objects.js">;</script>
			<xsl:call-template name="data"/>
		</head>
		<body>
			<table border="0">
				<tr>
					<td>Patient name:</td>
					<td><xsl:value-of select="@patientName"/></td>
				</tr>
				<tr>
					<td>Patient ID:</td>
					<td><xsl:value-of select="@patientID"/></td>
				</tr>
				<tr>
					<td>Study date:</td>
					<td>
						<xsl:call-template name="fixDate">
							<xsl:with-param name="d" select="@date"/>
						</xsl:call-template>
					</td>
				</tr>
				<tr>
					<td>Study name:</td>
					<td><xsl:value-of select="@studyName"/></td>
				</tr>
			</table>

			<div>
				<input type="button" value="Tile" onclick="tile();"/>
				<input type="button" value="Stack" onclick="stack();"/>
				<input type="button" value="Series" onclick="series();"/>
			</div>

			<div id="main"/>

		</body>
	</html>
</xsl:template>

<xsl:template name="data">
<xsl:variable name="studyName" select="@studyName"/>
<script>
var currentImage = 0;
<xsl:text>var images = new Array(</xsl:text>
	<xsl:for-each select="DicomObject[@type='image']">
	<xsl:sort select="series" data-type="number"/>
	<xsl:sort select="acquisition" data-type="number"/>
	<xsl:sort select="instance" data-type="number"/>
new Image(<xsl:text>"</xsl:text>
<xsl:value-of select="$context"/>
<xsl:text>/</xsl:text>
<xsl:value-of select="$studyName"/>
<xsl:text>/</xsl:text>
<xsl:value-of select="file"/>
<xsl:text>",</xsl:text>
<xsl:value-of select="series"/>
<xsl:text>,</xsl:text>
<xsl:value-of select="acquisition"/>
<xsl:text>,</xsl:text>
<xsl:value-of select="instance"/>
<xsl:text>,</xsl:text>
<xsl:value-of select="rows"/>
<xsl:text>,</xsl:text>
<xsl:value-of select="columns"/>
<xsl:text>)</xsl:text>
<xsl:if test="position()!=last()">
<xsl:text>,</xsl:text>
</xsl:if>
</xsl:for-each>
);
</script>
</xsl:template>

<xsl:template name="fixDate">
	<xsl:param name="d"/>
	<xsl:choose>
		<xsl:when test="string-length($d)=8">
			<xsl:value-of select="substring($d,1,4)"/>
			<xsl:text>.</xsl:text>
			<xsl:value-of select="substring($d,5,2)"/>
			<xsl:text>.</xsl:text>
			<xsl:value-of select="substring($d,7,2)"/>
		</xsl:when>
		<xsl:otherwise>
			<xsl:value-of select="$d"/>
		</xsl:otherwise>
	</xsl:choose>
</xsl:template>

</xsl:stylesheet>