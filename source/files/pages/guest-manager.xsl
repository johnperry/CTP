<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.1">
<xsl:output method="xml" encoding="utf-8" omit-xml-declaration="yes" />

<xsl:param name="context"/>
<xsl:param name="all-users"/>
<xsl:param name="guest-users"/>
<xsl:param name="user"/>
<xsl:param name="proxy"/>

<xsl:template match="/guests">
	<html>
		<head>
			<title><xsl:value-of select="$user"/> Guest List</title>
			<style>
				body {background-color:#b9d0ed;; margin-right:0px; margin-top:0px;}
				td {padding-left:10px; padding-right:10px; background-color:white;}
				.username {width:150px;}
				.box {border:thin solid gray; width:500px; padding:20px; margin:10px; margin-bottom:30px;}
				h1 {margin-top:15px;}
			</style>
			<script>
				var context = "<xsl:value-of select="$context"/>";
				function switchUser() {
					var x = document.getElementById("proxyuser");
					if (x) {
						if (x.value) window.open("/"+context+"/"+x.value,"_self");
					}
				}
			</script>
		</head>
		<body>
			<div style="float:right;">
				<img src="/icons/home.png"
					onclick="window.open('/','_self');"
					title="Return to the main page"
					style="margin:2"/>
			</div>

		<center>
			<h1><xsl:value-of select="$user"/> Guest List</h1>

			<form action="" method="POST" accept-charset="UTF-8">

			<div class="box">
				<xsl:if test="guest">
					<p>To remove a guest from this list, uncheck the box next to the guest's name.</p>
					<table border="1">
						<xsl:apply-templates select="guest"/>
					</table>
				</xsl:if>

				<xsl:if test="not(guest)">
					<p>The guest list is empty.</p>
				</xsl:if>

				<xsl:if test="$guest-users">
					<p>To add a guest to this list, select the guest's name.</p>
					<p>
						<select class="username" name="addguest">
							<option value=""/>
							<xsl:call-template name="make-options">
								<xsl:with-param name="list" select="$guest-users"/>
							</xsl:call-template>
						</select>
					</p>
				</xsl:if>

				<xsl:if test="not($guest-users)">
					<p>To add a guest, enter the guest's name in this box:</p>
					<p><input class="username" name="addguest" type="text"/></p>
				</xsl:if>

				<p><input type="submit" value="Submit Changes"/></p>
			</div>

			<xsl:if test="$proxy='yes'">
				<div class="box">
					<p>
						To manage the guest list for another user, select<br/>
						the user's name and click the "Switch User" button.
					</p>
					<p>
						<select class="username" id="proxyuser">
							<option value=""/>
							<xsl:call-template name="make-options">
								<xsl:with-param name="list" select="$all-users"/>
							</xsl:call-template>
						</select>
					<xsl:text> </xsl:text>
					<input type="button" value="Switch User" onclick="switchUser();"/>
					</p>
				</div>
			</xsl:if>

			</form>
		</center>
		</body>
	</html>
</xsl:template>

<xsl:template match="guest">
	<xsl:variable name="id"><xsl:number/></xsl:variable>
	<tr>
		<td><input type="checkbox" name="cb{$id}" value="yes" checked="true"/></td>
		<td>
			<xsl:value-of select="@username"/>
			<input type="hidden" name="un{$id}" value="{@username}"/>
		</td>
	</tr>
</xsl:template>

<xsl:template name="make-options">
	<xsl:param name="list"/>
	<xsl:variable name="first" select="substring-before($list, ';')"/>
	<xsl:variable name="rest" select="substring-after($list, ';')"/>
	<option value="{$first}"><xsl:value-of select="$first"/></option>
	<xsl:if test="$rest">
		<xsl:call-template name="make-options">
			<xsl:with-param name="list" select="$rest"/>
		</xsl:call-template>
	</xsl:if>
</xsl:template>

</xsl:stylesheet>