<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="context"/>
<xsl:param name="home"/>
<xsl:param name="pipelineName"/>
<xsl:param name="stageName"/>
<xsl:param name="lutFile"/>


<xsl:template match="/Terms">
	<html>
		<head>
			<title>Lookup Table Checker</title>
			<link rel="Stylesheet" type="text/css" media="all" href="/BaseStyles.css"></link>
			<link rel="Stylesheet" type="text/css" media="all" href="/LookupTableCheckerServlet.css"></link>
			<script language="JavaScript" type="text/javascript" src="/JSUtil.js">;</script>
			<script language="JavaScript" type="text/javascript" src="/LookupTableCheckerServlet.js">;</script>
		</head>
		<body>
			<xsl:if test="$home or Term">
				<div style="float:right;">
					<xsl:if test="$home">
						<img src="/icons/home.png"
								onclick="window.open('{$home}','_self');"
								style="margin-right:2px;"
								title="Return to the home page"/>
						<br/>
					</xsl:if>
					<xsl:if test="Term">
						<img src="/icons/save.png"
								onclick="submitForm();"
								style="margin-right:2px;"
								title="Save"/>
					</xsl:if>
				</div>
			</xsl:if>

			<h1>Lookup Table Checker</h1>
			<h2><xsl:value-of select="$pipelineName"/>: <xsl:value-of select="$stageName"/></h2>
			<h2><xsl:value-of select="$lutFile"/></h2>

			<center>

				<xsl:choose>
					<xsl:when test="Term">
						<p>Enter replacement values for the keys and click the Save button.</p>
						<form id="FormID" method="POST" accept-charset="UTF-8" action="/{$context}">
							<xsl:if test="not($home)">
								<input type="hidden" id="suppress" name="suppress" value=""/>
							</xsl:if>

							<table>
								<xsl:call-template name="headings"/>

								<xsl:for-each select="Term">
									<xsl:sort select="@key"/>
									<xsl:variable name="k">[<xsl:value-of select="position()"/>]</xsl:variable>
									<tr>
										<td>
											<xsl:value-of select="@keyType"/>
											<input type="hidden" id="keyType{$k}" name="keyType{$k}" value="{@keyType}"/>
										</td>
										<td>
											<xsl:value-of select="@key"/>
											<input type="hidden" id="key{$k}" name="key{$k}" value="{@key}"/>
										</td>
										<td>
											<input type="text" id="value{$k}" name="value{$k}"/>
										</td>
									</tr>
								</xsl:for-each>
							</table>
						</form>
					</xsl:when>
					<xsl:otherwise>
						<p>The LookupTableChecker database is empty.</p>
					</xsl:otherwise>
				</xsl:choose>
			</center>
		</body>
	</html>
</xsl:template>

<xsl:template name="headings">
	<tr>
		<th>Type</th>
		<th>Key</th>
		<th>Replacement value</th>
	</tr>
</xsl:template>

</xsl:stylesheet>
