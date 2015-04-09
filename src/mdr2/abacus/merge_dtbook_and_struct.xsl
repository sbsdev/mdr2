<xsl:stylesheet 
    version="1.0" 
    xmlns="http://www.daisy.org/z3986/2005/dtbook/"	
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:dtb="http://www.daisy.org/z3986/2005/dtbook/"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    exclude-result-prefixes="dtb xhtml">
  
  <xsl:output method="xml" indent="yes"/>

  <xsl:param name="filename"/>
  <xsl:param name="struct" select="document($filename)" />
  
  <xsl:variable name="tocItems" select="$struct/xhtml:html/xhtml:body/xhtml:h1" />
  
  <xsl:template match="dtb:bodymatter/dtb:level1">
    <xsl:apply-templates select="$tocItems"/>
  </xsl:template>

  <xsl:template match="xhtml:h1">
    <level1>
      <h1><xsl:value-of select="text()"/></h1>
      <p/>
      <xsl:apply-templates select="xhtml:h2"/>
    </level1>
  </xsl:template>

  <xsl:template match="xhtml:h2">
    <level2>
      <h2><xsl:value-of select="text()"/></h2>
      <p/>
      <xsl:apply-templates select="xhtml:h3"/>
    </level2>
  </xsl:template>

  <xsl:template match="xhtml:h3">
    <level3>
      <h3><xsl:value-of select="text()"/></h3>
      <p/>
      <xsl:apply-templates select="xhtml:h4"/>
    </level3>
  </xsl:template>

  <xsl:template match="@* | node()">
    <xsl:copy>
      <xsl:apply-templates select="@* | node()"/>
    </xsl:copy>
  </xsl:template>
  
</xsl:stylesheet>
