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
    <xsl:variable name="current" select="."/>
    <xsl:variable name="sublevels" select="./following-sibling::xhtml:h2[preceding-sibling::xhtml:h1[1] = $current]"/>
    <level1>
      <h1><xsl:value-of select="text()"/></h1>
      <xsl:apply-templates select="$sublevels"/>
      <xsl:if test="not($sublevels)"><p/></xsl:if>
    </level1>
  </xsl:template>

  <xsl:template match="xhtml:h2">
    <xsl:variable name="current" select="."/>
    <xsl:variable name="sublevels" select="./following-sibling::xhtml:h3[preceding-sibling::xhtml:h2[1] = $current]"/>
    <level2>
      <h2><xsl:value-of select="text()"/></h2>
      <xsl:apply-templates select="$sublevels"/>
      <xsl:if test="not($sublevels)"><p/></xsl:if>
    </level2>
  </xsl:template>

  <xsl:template match="xhtml:h3">
    <xsl:variable name="current" select="."/>
    <xsl:variable name="sublevels" select="./following-sibling::xhtml:h4[preceding-sibling::xhtml:h3[1] = $current]"/>
    <level3>
      <h3><xsl:value-of select="text()"/></h3>
      <xsl:apply-templates select="$sublevels"/>
      <xsl:if test="not($sublevels)"><p/></xsl:if>
    </level3>
  </xsl:template>

  <xsl:template match="xhtml:h4">
    <xsl:variable name="current" select="."/>
    <xsl:variable name="sublevels" select="./following-sibling::xhtml:h5[preceding-sibling::xhtml:h4[1] = $current]"/>
    <level4>
      <h4><xsl:value-of select="text()"/></h4>
      <xsl:apply-templates select="$sublevels"/>
      <xsl:if test="not($sublevels)"><p/></xsl:if>
    </level4>
  </xsl:template>

  <xsl:template match="xhtml:h5">
    <xsl:variable name="current" select="."/>
    <xsl:variable name="sublevels" select="./following-sibling::xhtml:h6[preceding-sibling::xhtml:h5[1] = $current]"/>
    <level5>
      <h5><xsl:value-of select="text()"/></h5>
      <xsl:apply-templates select="$sublevels"/>
      <xsl:if test="not($sublevels)"><p/></xsl:if>
    </level5>
  </xsl:template>

  <xsl:template match="xhtml:h6">
    <level6>
      <h6><xsl:value-of select="text()"/></h6>
      <p/>
    </level6>
  </xsl:template>

  <xsl:template match="@* | node()">
    <xsl:copy>
      <xsl:apply-templates select="@* | node()"/>
    </xsl:copy>
  </xsl:template>
  
</xsl:stylesheet>
