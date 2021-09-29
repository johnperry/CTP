<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:template match="/dictionary">
	<html>
		<head>
			<title>Select the Lookup Table File to Edit</title>
			<link rel="Stylesheet" type="text/css" media="all" href="/BaseStyles.css"></link>
			<style>
				h3 {margin-bottom: 0px; padding-bottom: 0px; font-size: 18pt;}
				table {margin-left: 50px;}
				th {padding-left:15px; padding-right:15px; 
					text-align: left;
				    font-family: monospace, monospace; }
				td {padding-left:15px; padding-right:15px; 
				    background-color:white; 
				    font-family: monospace, monospace; }
				a { padding-left: 50px;
					font-weight: bold;
					text-decoration: none;
				    font-family: monospace, monospace; }
				a:link {color: black;}
				a:visited {color: black;}
			</style>
				
		</head>
		<body>
			<h1 id="top">DICOM Dictionary</h1>
			<xsl:call-template name="index"/>			
			<xsl:apply-templates/>
		</body>
	</html>
</xsl:template>

<xsl:template name="index">
	<xsl:for-each select="elements">
		<a href="#{@type}-elements"><xsl:value-of select="@type"/> Elements</a>
		<br/>
	</xsl:for-each>
	<xsl:for-each select="uids">
		<a href="#{@type}-uids"><xsl:value-of select="@type"/> UIDs</a>
		<br/>
	</xsl:for-each>
	<xsl:for-each select="statuses">
		<a href="#{@service}-statuses"><xsl:value-of select="@service"/> Statuses</a>
		<br/>
	</xsl:for-each>
</xsl:template>

<xsl:template match="elements">
	<h3 id="{@type}-elements"><xsl:value-of select="@type"/> Elements</h3>
	<table>
		<tr>
			<th>Tag</th>
			<th>Keyword</th>
			<th>VR</th>
			<th>VM</th>
		</tr>			
		<xsl:apply-templates select="element"/>
	</table>
	<a href="#top">Index</a>
</xsl:template>

<xsl:template match="element">
	<tr>
		<td><xsl:value-of select="@tag"/></td>
		<td><xsl:value-of select="@key"/></td>
		<td><xsl:value-of select="@vr"/></td>
		<td><xsl:value-of select="@vm"/></td>
	</tr>
</xsl:template>

<xsl:template match="uids">
	<h3 id="{@type}-uids"><xsl:value-of select="@type"/> UIDs</h3>
	<table>
		<tr>
			<th>UID</th>
			<th>Keyword</th>
		</tr>			
		<xsl:apply-templates select="uid"/>
	</table>
	<a href="#top">Index</a>
</xsl:template>

<xsl:template match="uid">
	<tr>
		<td><xsl:value-of select="@value"/></td>
		<td><xsl:value-of select="@key"/></td>
	</tr>
</xsl:template>

<xsl:template match="statuses">
	<h3 id="{@service}-statuses"><xsl:value-of select="@service"/> Statuses</h3>
	<table>
		<tr>
			<th>Code</th>
			<th>Keyword</th>
		</tr>			
		<xsl:apply-templates select="status"/>
	</table>
	<a href="#top">Index</a>
</xsl:template>

<xsl:template match="status">
	<tr>
		<td><xsl:value-of select="@code"/></td>
		<td><xsl:value-of select="@key"/></td>
	</tr>
</xsl:template>


</xsl:stylesheet>
