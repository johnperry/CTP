<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="context"/>
<xsl:param name="p"/>
<xsl:param name="s"/>

<xsl:template match="/Files">
	<xsl:if test="File">
		<TABLE id="FilesTable" class="FilesTable">
			<THEAD>
				<xsl:call-template name="FilesHeadings"/>
			</THEAD>
			<TBODY>
				<xsl:for-each select="File">
					<tr>
						<td class="left" style="cursor:default" onmouseenter="showImagePopup('{@filename}');">
							<xsl:value-of select="@type"/>
						</td>
						<td class="center" style="cursor:default">
							<xsl:value-of select="@lmdate"/>
						</td>
						<td class="right" style="cursor:default">
							<xsl:value-of select="@instanceNumber"/>
						</td>
						<td class="left">
							<a href="/{$context}/downloadFile?p={$p}&amp;s={$s}&amp;filename={@filename}" title="Download the file">
								<xsl:value-of select="@filename"/>
							</a>
						</td>
						<td class="button">
							<input type="button" value="Display"
								onclick="window.open('/{$context}/displayFile?p={$p}&amp;s={$s}&amp;filename={@filename}','Image');"/>
						</td>
						<td class="button">
							<input type="button" value="List"
								onclick="window.open('/{$context}/listFile?p={$p}&amp;s={$s}&amp;filename={@filename}','List');"/>
						</td>
						<td class="button">
							<input type="button" value="Queue"
								onclick="window.open('/{$context}/queueFile?p={$p}&amp;s={$s}&amp;filename={@filename}','_self');"/>
						</td>
						<td class="button">
							<input type="button" value="Delete"
								onclick="window.open('/{$context}/deleteFile?p={$p}&amp;s={$s}&amp;filename={@filename}','_self');"/>
						</td>
					</tr>
				</xsl:for-each>
			</TBODY>
		</TABLE>
	</xsl:if>
	<xsl:if test="not(File)">
		<p>The series has no files.</p>
	</xsl:if>
</xsl:template>

<xsl:template name="FilesHeadings">
	<tr>
		<th class="left">Type</th>
		<th class="center">Date</th>
		<th class="right">Instance</th>
		<th class="left">Name</th>
	</tr>
</xsl:template>

</xsl:stylesheet>
