<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="context"/>
<xsl:param name="home"/>
<xsl:param name="pipeline"/>
<xsl:param name="stage"/>
<xsl:param name="pipelineName"/>
<xsl:param name="stageName"/>

<xsl:variable name="KeyTypeCount" select="count(/LookupTable/KeyType)"/>
<xsl:variable name="lutFile" select="/LookupTable/@lutFile"/>
<xsl:variable name="scriptFile" select="/LookupTable/@scriptFile"/>

<xsl:template match="/LookupTable">
	<html>
		<head>
			<title>Lookup Table Editor</title>
			<link rel="Stylesheet" type="text/css" media="all" href="/BaseStyles.css"></link>
			<link rel="Stylesheet" type="text/css" media="all" href="/JSPopup.css"></link>
			<link rel="Stylesheet" type="text/css" media="all" href="/LookupServlet.css"></link>
			<script language="JavaScript" type="text/javascript" src="/JSUtil.js">;</script>
			<script language="JavaScript" type="text/javascript" src="/JSAJAX.js">;</script>
			<script language="JavaScript" type="text/javascript" src="/JSPopup.js">;</script>
			<script language="JavaScript" type="text/javascript" src="/LookupServlet.js">;</script>
		</head>
		<body>
			<div style="float:right;">
				<xsl:if test="$home">
					<img src="/icons/home.png"
							onclick="window.open('{$home}','_self');"
							style="margin-right:2px;"
							title="Return to the home page"/>
					<br/>
				</xsl:if>
				<img src="/icons/save.png"
						onclick="submitURLEncodedForm();"
						style="margin-right:2px;"
						title="Update the file"/>
				<br/>
				<br/>
				<img src="/icons/arrow-up.png"
						onclick="uploadCSV();"
						style="margin-right:2px;"
						title="Upload CSV Lookup Table File"/>
				<br/>
				<img src="icons/arrow-down.png"
						onclick="downloadCSV();"
						style="margin-right:2px"
						title="Download CSV Lookup Table File"/>
			</div>

			<h1>Lookup Table Editor</h1>
			<h2><xsl:value-of select="$pipelineName"/>: <xsl:value-of select="$stageName"/></h2>
			<h2><xsl:value-of select="$lutFile"/></h2>
			<xsl:if test="$scriptFile">
				<h2><xsl:value-of select="$scriptFile"/></h2>
			</xsl:if>

			<center>
				<p>
					<xsl:choose>
						<xsl:when test="$KeyTypeCount != 0">
							<xsl:text>KeyTypes used in this DicomAnonymizer script: </xsl:text>
							<xsl:for-each select="KeyType">
								<xsl:if test="position()!=1">
									<xsl:text>, </xsl:text>
								</xsl:if>
								<tt><b><xsl:value-of select="@type"/></b></tt>
							</xsl:for-each>
							<xsl:text>. </xsl:text>
						</xsl:when>
						<xsl:otherwise>
							There are no KeyTypes specified in this DicomAnonymizer script.
						</xsl:otherwise>
					</xsl:choose>
					For instructions, see <a href="http://mircwiki.rsna.org/index.php?title=The_CTP_Lookup_Table_Editor" target="wiki">this article</a>.
				</p>

				<form id="URLEncodedFormID" method="POST" accept-charset="UTF-8" action="/{$context}">
					<input type="hidden" id="p" name="p" value="{$pipeline}"/>
					<input type="hidden" id="s" name="s" value="{$stage}"/>
					<xsl:if test="not($home)">
						<input type="hidden" id="suppress" name="suppress" value=""/>
					</xsl:if>
					<xsl:if test="$KeyTypeCount = 1">
						<input type="hidden" id="defaultKeyType" name="defaultKeyType" value="{KeyType/@type}"/>
					</xsl:if>

					<table class="newEntries">
						<xsl:call-template name="headings"/>

						<xsl:for-each select="Preset">
							<xsl:sort select="@key"/>
							<xsl:variable name="k">[<xsl:value-of select="position()"/>]</xsl:variable>
							<tr>
								<td>
									<input type="text" id="phi{$k}" name="phi{$k}"/>
									<input type="hidden" id="phikey{$k}" name="phikey{$k}" value="{@key}"/>
								</td>
								<td>&#160;=&#160;</td>
								<td><xsl:value-of select="@key"/></td>
							</tr>
						</xsl:for-each>

						<tr>
							<td><input type="text" id="phi" name="phi"/></td>
							<td>&#160;=&#160;</td>
							<td><input type="text" name="replacement"/></td>
						</tr>
					</table>

					<xsl:if test="Entry">
						<div class="currentEntries">
							<p>Current Lookup Table Entries</p>
							<table class="currentEntries">
								<xsl:call-template name="headings"/>

								<xsl:for-each select="Entry">
									<xsl:sort select="@key"/>
									<tr>
										<td><xsl:value-of select="@key"/></td>
										<td>&#160;=&#160;</td>
										<td><xsl:value-of select="@value"/></td>
									</tr>
								</xsl:for-each>
							</table>
						</div>
					</xsl:if>
				</form>
			</center>
		</body>
	</html>
</xsl:template>

<xsl:template name="headings">
	<tr>
		<th>
			<xsl:if test="$KeyTypeCount != 1">
				KeyType/PHI value
			</xsl:if>
			<xsl:if test="$KeyTypeCount = 1">
				PHI value
			</xsl:if>
		</th>
		<th/>
		<th>Replacement value</th>
	</tr>
</xsl:template>

</xsl:stylesheet>
