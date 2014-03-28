<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="context"/>
<xsl:param name="home"/>

<xsl:template match="/Stages">
	<html>
		<head>
			<title>Select the Lookup Table File to Edit</title>
			<link rel="Stylesheet" type="text/css" media="all" href="/BaseStyles.css"></link>
			<link rel="Stylesheet" type="text/css" media="all" href="/LookupServlet.css"></link>
		</head>
		<body>
			<xsl:if test="$home">
				<div style="float:right;">
					<img src="/icons/home.png"
							onclick="window.open('{$home}','_self');"
							style="margin-right:2px;"
							title="Return to the home page"/>
				</div>
			</xsl:if>

			<h1>Select the Lookup Table File to Edit</h1>

			<center>
				<xsl:choose>
					<xsl:when test="Stage">
						<table border="1" width="100%">
							<xsl:apply-templates select="Stage"/>
						</table>
					</xsl:when>
					<xsl:otherwise>
						<p>The configuration contains no lookup tables.</p>
					</xsl:otherwise>
				</xsl:choose>
			</center>
		</body>
	</html>
</xsl:template>

<xsl:template match="Stage">
	<xsl:variable name="url">
		<xsl:text>/</xsl:text>
		<xsl:value-of select="$context"/>
		<xsl:text>?p=</xsl:text><xsl:value-of select="@p"/>
		<xsl:text>&amp;s=</xsl:text><xsl:value-of select="@s"/>
		<xsl:if test="$home=''">
			<xsl:text>&amp;suppress</xsl:text>
		</xsl:if>
	</xsl:variable>
	<tr>
		<td class="list">
			<xsl:value-of select="@pipelineName"/>
		</td>
		<td class="list">
			<xsl:value-of select="@stageName"/>
		</td>
		<td class="list">
			<a href="{$url}"><xsl:value-of select="@file"/></a>
		</td>
	</tr>
</xsl:template>

</xsl:stylesheet>
