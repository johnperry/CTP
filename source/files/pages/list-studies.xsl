<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="context"/>
<xsl:param name="dir"/>
<xsl:param name="delete"/>
<xsl:param name="key">studyDate</xsl:param>

<xsl:template match="/index">
	<html>
		<head>
			<title>Study List for <xsl:value-of select="@fileSystemName"/></title>
			<style>
				th,td {padding-left:10px; padding-right:10px; cursor:target;}
				body {background-color:#b9d0ed;}
				td {background-color:white;}
				th a {text-decoration: none; color:black;}
				th a:visited {color: black;}
				td a {color:black;}
				td a:visited {color: black;}
			</style>
			<xsl:call-template name="script"/>
		</head>
		<body><center>
			<h1>Study List for <xsl:value-of select="@fileSystemName"/></h1>
			<table border="1">
				<thead>
					<tr>
						<th><a href="{$context}?key=name">Patient Name</a></th>
						<th><a href="{$context}?key=id">Patient ID</a></th>
						<th><a href="{$context}?key=accession">Accession</a></th>
						<th><a href="{$context}?key=studyDate">Study Date</a></th>
						<th><a href="{$context}?key=storageDate">Storage Date</a></th>
					</tr>
				</thead>
				<xsl:choose>
					<xsl:when test="$key='name'">
						<xsl:apply-templates select="study">
							<xsl:sort select="patientName"/>
						</xsl:apply-templates>
					</xsl:when>
					<xsl:when test="$key='id'">
						<xsl:apply-templates select="study">
							<xsl:sort select="patientID"/>
						</xsl:apply-templates>
					</xsl:when>
					<xsl:when test="$key='accession'">
						<xsl:apply-templates select="study">
							<xsl:sort select="accessionNumber"/>
						</xsl:apply-templates>
					</xsl:when>
					<xsl:when test="$key='studyDate'">
						<xsl:apply-templates select="study">
							<xsl:sort select="studyDate" data-type="number" order="descending"/>
						</xsl:apply-templates>
					</xsl:when>
					<xsl:otherwise>
						<xsl:apply-templates select="study">
							<xsl:sort select="storageDate" data-type="number" order="descending"/>
						</xsl:apply-templates>
					</xsl:otherwise>
				</xsl:choose>
			</table>
		</center></body>
	</html>
</xsl:template>

<xsl:template match="study">

	<xsl:variable name="studyDate">
		<xsl:call-template name="fixDate">
			<xsl:with-param name="d" select="studyDate"/>
		</xsl:call-template>
	</xsl:variable>
	<xsl:variable name="storageDate">
		<xsl:call-template name="fixDate">
			<xsl:with-param name="d" select="storageDate"/>
		</xsl:call-template>
	</xsl:variable>

	<tr>
		<td><xsl:value-of select="patientName"/></td>
		<td><xsl:value-of select="patientID"/></td>
		<td><xsl:value-of select="accessionNumber"/></td>
		<td><xsl:value-of select="$studyDate"/></td>
		<td><xsl:value-of select="$storageDate"/></td>
		<td>
			<a href="{$context}/{studyName}?format=list">
				<xsl:text>List</xsl:text>
			</a>
		</td>
		<td>
			<a href="{$context}/{studyName}?format=viewer">
				<xsl:text>Display</xsl:text>
			</a>
		</td>
		<xsl:if test="$dir='yes'">
			<td>
				<a href="{$context}/{studyName}?format=dir"
					title="Export the study to a directory">
					<xsl:text>Dir</xsl:text>
				</a>
			</td>
		</xsl:if>
		<td>
			<a href="{$context}/{studyName}?format=zip"
				title="Export the study as a zip file">
				<xsl:text>Exp</xsl:text>
			</a>
		</td>
		<xsl:if test="$delete='yes'">
			<td>
				<a href="{$context}/{studyName}?format=zip&amp;delete=yes"
					onclick="hideRow(event);"
					title="Export the study as a zip file and then delete the study">
					<xsl:text>Exp&amp;Del</xsl:text>
				</a>
			</td>
			<td>
				<a href="{$context}/{studyName}?format=delete"
					title="Delete the study">
					<xsl:text>Del</xsl:text>
				</a>
			</td>
		</xsl:if>
	</tr>
</xsl:template>

<xsl:template name="script">
	<script>
<![CDATA[
function hideRow(theEvent) {
	var tr = ((document.all) ? theEvent.srcElement : theEvent.target);
	while (tr.tagName != "TR") tr = tr.parentNode;
	tr.parentNode.removeChild(tr);
}
]]>
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