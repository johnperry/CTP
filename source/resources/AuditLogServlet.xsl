<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="context"/>
<xsl:param name="suppress"/>

<xsl:template match="/Plugin">
	<html>
		<head>
			<title>Audit Log Service - (<xsl:value-of select="$context"/>)</title>
			<link rel="Stylesheet" type="text/css" media="all" href="/BaseStyles.css"></link>
			<link rel="Stylesheet" type="text/css" media="all" href="/AuditLogServlet.css"></link>
			<script language="JavaScript" type="text/javascript" src="/JSUtil.js">;</script>
			<script language="JavaScript" type="text/javascript" src="/JSAJAX.js">;</script>
			<script language="JavaScript" type="text/javascript" src="/AuditLogServlet.js">;</script>
			<script>var context = "<xsl:value-of select="$context"/>";</script>
		</head>
		<body>
			<div class="closebox">
			<xsl:if test="$suppress=''">
					<img src="/icons/home.png"
						 onclick="window.open('/','_self');"
						 title="Return to the home page"/>
					<br/>
				</xsl:if>
				<img src="/icons/refresh.png"
					 onclick="search();"
					 title="Search"/>
				<br/>
				<img src="icons/arrow-down.png"
						onclick="exportAuditLog();"
						style="margin-right:2px"
						title="Export the AuditLog"/>
			</div>

			<h1>Audit Log Service</h1>
			<h2><xsl:value-of select="@name"/></h2>

			<p class="instruction">Select a search field, enter a value, and click the search button:</p>
			<p class="center">
			<table border="1">
				<tr>
					<td class="text-label">
						<input id="ptid" type="radio" name="searchfield" value="ptid">Patient ID:</input>
					</td>
					<td class="text-field">
						<input type="text" class="fullwidth"/>
					</td>
				</tr>
				<tr>
					<td class="text-label">
						<input id="study" type="radio" name="searchfield" value="study">Study UID:</input>
					</td>
					<td class="text-field">
						<input type="text" class="fullwidth"/>
					</td>
				</tr>
				<tr>
					<td class="text-label">
						<input id="object" type="radio" name="searchfield" value="object">Object UID:</input>
					</td>
					<td class="text-field">
						<input type="text" class="fullwidth"/>
					</td>
				</tr>
				<tr>
					<td class="text-label">
						<input id="object" type="radio" name="searchfield" value="scan" checked="true">Text search:</input>
					</td>
					<td class="text-field">
						<input type="text" class="fullwidth"/>
					</td>
				</tr>
				<tr>
					<td class="text-label">
						<input id="object" type="radio" name="searchfield" value="entry">Entry ID:</input>
					</td>
					<td class="text-field">
						<input type="text" class="fullwidth"/>
					</td>
				</tr>
			</table>
			</p>

			<p class="instruction">Select an Audit Log entry:</p>
			<div id="entryselect" class="entryselect">&#160;</div>

			<p class="instruction">Audit Log Entry:</p>
			<div id="entrydisplay" class="entrydisplay">&#160;</div>

		</body>
	</html>
</xsl:template>

</xsl:stylesheet>
