<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="context"/>
<xsl:param name="pipeline"/>
<xsl:param name="stage"/>
<xsl:param name="p"/>
<xsl:param name="s"/>

<xsl:template match="/Studies">
	<html>
		<head>
			<title>Quarantine</title>
			<link rel="Stylesheet" type="text/css" media="all" href="/BaseStyles.css"></link>
			<link rel="Stylesheet" type="text/css" media="all" href="/JSPopup.css"></link>
			<link rel="Stylesheet" type="text/css" media="all" href="/QuarantineServlet.css"></link>
			<script language="JavaScript" type="text/javascript" src="/JSUtil.js">;</script>
			<script language="JavaScript" type="text/javascript" src="/JSAJAX.js">;</script>
			<script language="JavaScript" type="text/javascript" src="/JSPopup.js">;</script>
			<script language="JavaScript" type="text/javascript" src="/QuarantineServlet.js">;</script>
			<script>
				var context = '<xsl:value-of select="$context"/>';
				var p = '<xsl:value-of select="$p"/>';
				var s = '<xsl:value-of select="$s"/>';
			</script>
		</head>
		<body>
			<center>
				<h1>
					<xsl:value-of select="$pipeline"/>
					<br/>
					<xsl:value-of select="$stage"/> Quarantine
				</h1>

				<xsl:if test="Study">
					<input type="button" value="Rebuild Index"
						onclick="window.open('/{$context}/rebuildIndex?p={$p}&amp;s={$s}','_self');"/>
					<xsl:text>&#160;&#160;&#160;</xsl:text>
					<input type="button" value="Queue All"
						onclick="window.open('/{$context}/queueAll?p={$p}&amp;s={$s}','_self');"/>
					<xsl:text>&#160;&#160;&#160;</xsl:text>
					<input type="button" value="Delete All"
						   onclick="window.open('/{$context}/deleteAll?p={$p}&amp;s={$s}','_self');"/>
					<br/>
					<div id="StudiesDiv">
						<table id="StudiesTable" class="StudiesTable">
							<xsl:call-template name="StudyHeadings"/>
							<xsl:for-each select="Study">
								<tr>
									<td class="left" onclick="getSeries(event,'{@studyUID}');">
										<xsl:value-of select="@patientID"/>
									</td>
									<td class="left" onclick="getSeries(event,'{@studyUID}');">
										<xsl:value-of select="@patientName"/>
									</td>
									<td class="center" onclick="getSeries(event,'{@studyUID}');">
										<xsl:value-of select="substring(@studyDate,1,4)"/>
										<xsl:text>.</xsl:text>
										<xsl:value-of select="substring(@studyDate,5,2)"/>
										<xsl:text>.</xsl:text>
										<xsl:value-of select="substring(@studyDate,7,2)"/>
									</td>
									<td class="right" onclick="getSeries(event,'{@studyUID}');">
										<xsl:value-of select="@nSeries"/>
									</td>
									<td class="left" onclick="getSeries(event,'{@studyUID}');">
										<xsl:value-of select="@studyUID"/>
									</td>
									<td class="button">
										<input type="button" value="Queue"
						   					onclick="window.open('/{$context}/queueStudy?p={$p}&amp;s={$s}&amp;studyUID={@studyUID}','_self');"/>
						   			</td>
									<td class="button">
										<input type="button" value="Delete"
						   					onclick="window.open('/{$context}/deleteStudy?p={$p}&amp;s={$s}&amp;studyUID={@studyUID}','_self');"/>
						   			</td>
								</tr>
							</xsl:for-each>
						</table>
						<br/>
					</div>
					<div id="SeriesDiv">
						&#160;
					</div>
					<div id="FilesDiv">
						&#160;
					</div>
				</xsl:if>
				<xsl:if test="not(Study)">
					<p>The quarantine is empty.</p>
				</xsl:if>
			</center>
		</body>
	</html>

</xsl:template>

<xsl:template name="StudyHeadings">
	<tr>
		<th class="left">Patient ID</th>
		<th class="left">Patient Name</th>
		<th class="center">Study Date</th>
		<th class="right">Series</th>
		<th class="left">Study UID</th>
	</tr>
</xsl:template>

</xsl:stylesheet>
