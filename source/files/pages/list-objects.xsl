<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="context"/>

<xsl:template match="/index">
	<html>
		<head>
			<title>Object List for <xsl:value-of select="@studyName"/></title>
			<link rel="Stylesheet" type="text/css" media="all" href="/JSPopup.css"></link>
			<script language="JavaScript" type="text/javascript" src="/JSUtil.js">;</script>
			<script language="JavaScript" type="text/javascript" src="/JSPopup.js">;</script>
			<script language="JavaScript" type="text/javascript" src="/list-objects.js">;</script>
			<style>
				th,td {padding-left:10px; padding-right:10px;}
				body {background-color:#b9d0ed;}
				td.filename {width:90%;}
				td {background-color:white;}
				td a {color:black;}
				td a:visited {color: black;}
			</style>
		</head>
		<body><center>
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
					<td>Accession:</td>
					<td><xsl:value-of select="@accessionNumber"/></td>
				</tr>
				<tr>
					<td>Study date:</td>
					<td><xsl:value-of select="@date"/></td>
				</tr>
				<tr>
					<td>Study name:</td>
					<td><xsl:value-of select="@studyName"/></td>
				</tr>
			</table>

			<br/>

			<xsl:variable name="studyName" select="@studyName"/>

			<table border="1">
				<thead>
					<tr>
						<th/>
						<th style="text-align:left">File</th>
						<th>Series</th>
						<th>Acquisition</th>
						<th>Instance</th>
					</tr>
				</thead>

				<xsl:for-each select="DicomObject[@type='image']">
					<xsl:sort select="series" data-type="number"/>
					<xsl:sort select="acquisition" data-type="number"/>
					<xsl:sort select="instance" data-type="number"/>

					<tr>
						<td style="text-align:right"><xsl:value-of select="position()"/></td>
						<td class="filename">
							<a href="{$context}/{$studyName}/{file}" 
							   onmouseenter="showImagePopup('{$context}/{$studyName}/{file}?format=jpeg', '{file}');">
								<xsl:value-of select="file"/>
							</a>
						</td>
						<td style="text-align:right"><xsl:value-of select="series"/></td>
						<td style="text-align:right"><xsl:value-of select="acquisition"/></td>
						<td style="text-align:right"><xsl:value-of select="instance"/></td>
						<td>
							<a href="{$context}/{$studyName}/{file}?format=list">
								<xsl:text>List</xsl:text>
							</a>
						</td>
						<td>
							<a href="{$context}/{$studyName}/{file}?format=jpeg">
								<xsl:text>Display</xsl:text>
							</a>
						</td>
					</tr>
				</xsl:for-each>

				<xsl:for-each select="DicomObject[not(@type) or (@type!='image')]">
					<tr>
						<td/>
						<td>
							<a href="{$context}/{$studyName}/{file}">
								<xsl:value-of select="file"/>
							</a>
						</td>
						<td/>
						<td/>
						<td/>
						<td>
							<a href="{$context}/{$studyName}/{file}?format=list">
								<xsl:text>List</xsl:text>
							</a>
						</td>
					</tr>
				</xsl:for-each>

				<xsl:for-each select="XmlObject">
					<tr>
						<td/>
						<td>
							<a href="{$context}/{$studyName}/{file}">
								<xsl:value-of select="file"/>
							</a>
						</td>
					</tr>
				</xsl:for-each>

				<xsl:for-each select="ZipObject">
					<tr>
						<td/>
						<td>
							<a href="{$context}/{$studyName}/{file}">
								<xsl:value-of select="file"/>
							</a>
						</td>
					</tr>
				</xsl:for-each>

				<xsl:for-each select="FileObject">
					<tr>
						<td/>
						<td>
							<a href="{$context}/{$studyName}/{file}">
								<xsl:value-of select="file"/>
							</a>
						</td>
					</tr>
				</xsl:for-each>
			</table>
		</center></body>
	</html>
</xsl:template>

</xsl:stylesheet>