<xsl:stylesheet
    version="1.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  
  <xsl:output method="xml" encoding="utf-8" indent="yes"
	      doctype-public="-//W3C//DTD SMIL 1.0//EN"
              doctype-system="http://www.w3.org/TR/REC-smil/SMIL10.dtd" />
  
  <xsl:template match="@* | node()">
    <xsl:copy>
      <xsl:apply-templates select="@* | node()"/>
    </xsl:copy>
  </xsl:template>
  
</xsl:stylesheet>
