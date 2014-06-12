<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="context"/>
<xsl:param name="p"/>
<xsl:param name="s"/>

<xsl:template match="/Study">
	<xsl:if test="Series">
		<TABLE id="SeriesTable" class="SeriesTable">
			<THEAD>
				<xsl:call-template name="SeriesHeadings"/>
			</THEAD>
			<TBODY>
				<xsl:for-each select="Series">
					<tr>
						<td class="right" onclick="getFiles(event,'{@seriesUID}');">
							<xsl:value-of select="@seriesNumber"/>
						</td>
						<td class="right" onclick="getFiles(event,'{@seriesUID}');">
							<xsl:value-of select="@nFiles"/>
						</td>
						<td class="left" onclick="getFiles(event,'{@seriesUID}');">
							<xsl:value-of select="@seriesUID"/>
						</td>
						<td class="button">
							<input type="button" value="Queue"
								onclick="window.open('/{$context}/queueSeries?p={$p}&amp;s={$s}&amp;seriesUID={@seriesUID}','_self');"/>
						</td>
						<td class="button">
							<input type="button" value="Delete"
								onclick="window.open('/{$context}/deleteSeries?p={$p}&amp;s={$s}&amp;seriesUID={@seriesUID}','_self');"/>
						</td>
					</tr>
				</xsl:for-each>
			</TBODY>
		</TABLE>
		<br/>
	</xsl:if>
	<xsl:if test="not(Series)">
		<p>The study has no series.</p>
	</xsl:if>
</xsl:template>

<xsl:template name="SeriesHeadings">
	<tr>
		<th class="right">Series</th>
		<th class="right">Files</th>
		<th class="left">Series UID</th>
	</tr>
</xsl:template>

</xsl:stylesheet>
