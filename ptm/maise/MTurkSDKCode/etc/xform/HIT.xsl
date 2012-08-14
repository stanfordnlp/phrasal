<?xml version="1.0" encoding="UTF-8"?>

<!--
  == This file implements an XSLT for a multiple-choice question format for HITs.
  == The input is an XML file and the output is an HTML fragment.
  ==
  == QUESTIONS:
  ==
  == Here is a loose description of the output format:
  == * An optional overview is displayed
  ==    * The overview may consist of any number of the following in any order:
  ==        * Title
  ==        * Paragraph of text
  ==        * List of text
  ==        * Image (specified by a URL)
  == * Any number of questions are displayed
  ==    * Each question may have a content section containing any number of the
  ==            following in any order:
  ==        * Paragraph of text
  ==        * Image
  ==    * Each question has an HITAnswer field of one of the following formats:
  ==        * Free text entry
  ==        * Multiple choice
  ==        * File upload
  ==    * Each question is rendered based on a style suggestion for that question
  ==            in one of the following ways:
  ==        * dropdown - <select>...
  ==        * checkbox - <input type="checkbox">...
  ==        * radiobutton - <input type="radio">...
  ==        * combobox - <select size="n">...
  ==
  ==
  == ANSWERS:
  ==
  == A marker for each question is created as a hidden field, named:
  ==    QuestionMarker
  ==
  == Each question returns an HITAnswer in the following format:
  ==    N is the index of the Question/Answer
  ==    ID is the base64 representation of an Answer identifier
  ==    VALUE is the value of a free text input
  ==
  ==    * Any number of:
  ==        Answer_N=Selection_ID
  ==        Answer_N=OtherSelection
  ==
  ==    * The value of the other selection is given by (note that it is only valid
  ==            if a value of OtherSelection is given by one of the Answer_N's):
  ==        Answer_N_OtherSelection=VALUE
  ==
  ==    * For free text entries, the text value is given by:
  ==        Answer_N_FreeText=VALUE
  ==
  ==    * Validation data is serialized and stored in:
  ==        ValidationData_N=constraint1;constraint2;...
  ==        ValidationData_N_OtherSelection=constraint1;constraint2;...
  -->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:lxslt="http://xml.apache.org/xslt"
        xmlns:xalan="http://xml.apache.org/xalan"
        xmlns:java="xalan://com.amazon.mechanicalturk.common.domain"
        xmlns:sunJava="xalan://java.net"
        xmlns:qap="http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2005-10-01/QuestionForm.xsd"
        xmlns:eqa="http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2006-07-14/ExternalQuestion.xsd"
        extension-element-prefixes="java"
        version="1.0">
    <xsl:output method="html" encoding="UTF-8"/>
    	    	

    <!--
      == Java components are used to:
      ==   * Encode constraints
      ==   * Base64 encode identifiers
      ==   * Populate form fields with answers
      -->

    <xalan:component prefix="java" functions="">
        <xalan:script lang="javaclass"/>
    </xalan:component>

    <xalan:component prefix="sunJava" functions="">
        <xalan:script lang="javaclass"/>
    </xalan:component>

    <!--
      == This parameter contains the hitId.  This used in the Application templates.
      ==
      -->

    <xsl:param name="hitId"/>
    
	<!--
      == This parameter contains the assignmentId.  This is used in the Application templates.
      ==
      -->

    <xsl:param name="assignmentId"/>


    <!--
      == This parameter determines if the HIT has been accepted as an assignment.
      == If it has not been accepted, all form fields are disabled.
      ==
      -->

    <xsl:param name="accepted"/>

	<!-- 
	  == This parameter contains the answers domain object with which to 
	  == populate the generated form, or (alternately) the string "nil".
	  -->

	<xsl:param name="answers" />


    <xsl:variable name="externalParams">assignmentId&#61;<xsl:value-of select="$assignmentId"/>&amp;hitId&#61;<xsl:value-of select="$hitId"/></xsl:variable>


    <!-- Render the HTML in case of External hit -->
    <xsl:template match="/eqa:ExternalQuestion">
        <style type="text/css">
            iframe {                
                border: 1px black solid;
                margin: 5px 15px 10px 15px;
                width: 100%;
            }

            form>iframe {
                border: 1px black solid;
                margin: 5px 0 10px 15px;
                width: 97%;
            }


        </style>
        <iframe  name="ExternalQuestionIFrame" src="" align="center" frameborder="0" scrolling="auto" height="">
            <xsl:if test="contains(eqa:ExternalURL, '?')">
                <xsl:if test="string-length(substring-after(eqa:ExternalURL, '?')) >0">
                    <xsl:attribute name="src"><xsl:value-of select="eqa:ExternalURL"/>&amp;<xsl:value-of select="$externalParams"/></xsl:attribute>
                </xsl:if>
                <xsl:if test="string-length(substring-after(eqa:ExternalURL, '?')) =0">
                    <xsl:attribute name="src"><xsl:value-of select="eqa:ExternalURL"/><xsl:value-of select="$externalParams"/></xsl:attribute>
                </xsl:if>
            </xsl:if>
            <xsl:if test="not(contains(eqa:ExternalURL, '?'))">
                <xsl:attribute name="src"><xsl:value-of select="eqa:ExternalURL"/>?<xsl:value-of select="$externalParams"/></xsl:attribute>
            </xsl:if>
            <xsl:attribute name="height"><xsl:value-of select="eqa:FrameHeight"/></xsl:attribute>
        </iframe>
    </xsl:template>

    <!-- Render the entire HTML fragment -->
    <xsl:template match="/qap:QuestionForm">
        <!-- TODO: move this to a .css file -->
        <style type="text/css">
            #hit-wrapper {
                margin: 10px;
                border: 1px black solid;
                padding: 10px;
                line-height: 1.5em;
                background-color: white;
                width: 100%;
                max-width:97%;
            }
            
            form>#hit-wrapper {
            	border: 1px black solid;
            	margin: 5px 0px 10px 15px;
            	width: 96%;
            }

            p {
                padding-left: 5px;
                padding-right: 5px;
            }

            .overview-wrapper {
                border-bottom: 1px lightgrey solid;
            }

            .title {
                font-size: 150%;
                font-weight: bold;
            }

            .question-wrapper {
                border-bottom: 1px lightgrey solid;
            }
            
            .question-error-wrapper {
                border: 2px solid rgb(214, 0, 0);
                border-bottom: 2px solid rgb(214, 0, 0);
            }

            .fieldset {
                border: none;
            }

            .checkbox-wrapper {
                margin: 0;
                padding: 0;
		vertical-align: middle;
            }

            .radiobutton-wrapper {
                margin: 0;
                padding: 0;
                vertical-align: middle;
            }

            .free-text-wrapper {
                padding-left: 15px;
            }

            .answer-binary-wrapper {
                margin: 5px;
            }

            .dropdown-wrapper {
                padding-left: 15px;
            }
            
            .error-message-none-wrapper {
                color: rgb(214, 0, 0);
                display: none;
                visibility: hidden;
            }
            
            .error-message-error-wrapper {
                color: rgb(214, 0, 0);
                display: block;
                visibility: visible;
            }
        </style>


        <!-- HIT -->

        <div id="hit-wrapper">

            <xsl:apply-templates select="*"/>

        </div>
    </xsl:template>

    <xsl:template match="qap:TextEncoding"/>

    <xsl:template match="qap:Overview">
        <div class="overview-wrapper">
            <xsl:apply-templates select="*">
                <xsl:with-param name="type">overview</xsl:with-param>
            </xsl:apply-templates>
        </div>
    </xsl:template>

    <xsl:template match="qap:Question">
        <xsl:variable name="questionIndex"><xsl:number count="qap:Question"/></xsl:variable>
    
        <xsl:variable name="identifier">
            <xsl:if test="qap:QuestionIdentifier"><xsl:value-of select="java:XsltHelper.base64Encode(string(qap:QuestionIdentifier))"/></xsl:if>
            <xsl:if test="not(qap:QuestionIdentifier)"><xsl:value-of select="java:XsltHelper.base64Encode(string($questionIndex))"/></xsl:if>
        </xsl:variable>
    
    
        <div class="question-wrapper">

            <!-- Render a marker for this question -->

            <input name="QuestionMarker" type="hidden" value="-"/>

            <!-- Add mapping from Question Identifier to Question Index -->  
            <xsl:element name="input">
                <xsl:attribute name="value"><xsl:value-of select="$identifier"/></xsl:attribute>
                <xsl:attribute name="type">hidden</xsl:attribute>
                <xsl:attribute name="name">Question_<xsl:number count="qap:Question"/>_Id</xsl:attribute>
            </xsl:element>

            <!-- Add mapping from Question Identifier to Question Display Name if one exists. -->            
            <xsl:if test="qap:DisplayName">
                <xsl:element name="input">
                    <xsl:attribute name="value"><xsl:value-of select="java:XsltHelper.base64Encode(string(qap:DisplayName))"/></xsl:attribute>
                    <xsl:attribute name="type">hidden</xsl:attribute>
                    <xsl:attribute name="name">Identifier_<xsl:value-of select="$identifier"/>_Identifier</xsl:attribute>
                </xsl:element>
            </xsl:if>
            <xsl:if test="not(qap:DisplayName)">
                <xsl:element name="input">
                    <xsl:attribute name="value"><xsl:value-of select="$identifier"/></xsl:attribute>
                    <xsl:attribute name="type">hidden</xsl:attribute>
                    <xsl:attribute name="name">Identifier_<xsl:value-of select="$identifier"/>_Identifier</xsl:attribute>
                </xsl:element>
            </xsl:if>
            
           
            <xsl:apply-templates select="qap:IsRequired"/>

           <!-- Question Content -->

            <div class="question-content-wrapper">
                <xsl:apply-templates select="qap:QuestionContent">
                    <xsl:with-param name="type">question</xsl:with-param>
                </xsl:apply-templates>
            </div>

            <!-- Answer Form Fields -->

            <div class="HITAnswer-wrapper" name="HITAnswer-wrapper">
                <xsl:apply-templates select="qap:AnswerSpecification">
                    <xsl:with-param name="isRequired" select="qap:IsRequired[text()]" />
                </xsl:apply-templates>
<!-- TODO: In the qual test creator, "AnswerSpecification" doesn't match SelectionAnswer/FreeTextAnswer/UploadAnswer here. -->
            </div>

        </div>
    </xsl:template>

    <!-- IsRequired block -->
    <xsl:template match="qap:IsRequired">
        <xsl:if test="text() = 'true'">
            <xsl:element name="input">
                <xsl:attribute name="type">hidden</xsl:attribute>
                <xsl:attribute name="name"><xsl:text>ValidationData_</xsl:text><xsl:number count="qap:Question"/></xsl:attribute>
                <xsl:attribute name="value">IsRequired;</xsl:attribute>
            </xsl:element>
        </xsl:if>      
    </xsl:template>
   
   <!-- huh? but I need isRequired below and this works -->
    <xsl:template match="qap:AnswerSpecification">
    	<xsl:param name="isRequired" />
    	<xsl:apply-templates select="*">
    		<xsl:with-param name="isRequired" select="$isRequired" />
    	</xsl:apply-templates>
    </xsl:template>

   
   
 <xsl:template match="qap:Flash">           
  <!-- object tag, ActiveX -->
      <xsl:element name="object">
          <xsl:attribute name="classid">clsid:d27cdb6e-ae6d-11cf-96b8-444553540000</xsl:attribute>
          <xsl:attribute name="codebase">http://fpdownload.macromedia.com/pub/shockwave/cabs/flash/swflash.cab#version=7,0,0,0</xsl:attribute>
          <xsl:attribute name="align">middle</xsl:attribute>                
          <xsl:attribute name="width"><xsl:value-of select="qap:Width[text()]"/> </xsl:attribute>
          <xsl:attribute name="height"><xsl:value-of select="qap:Height[text()]"/></xsl:attribute>  
          <xsl:variable name="FlashVars"><xsl:value-of select="$externalParams"/></xsl:variable>

          <xsl:for-each select="qap:ApplicationParameter">
          <!-- Each Parameter -->
          <!-- bgcolor is  fixed, skip it if passed on -->
           <xsl:variable name="parameterName">
              <xsl:value-of select="qap:Name[text()]"/>
           </xsl:variable>  
           <xsl:choose>
               <xsl:when test="contains($parameterName,'bgcolor')">
                   <param  name="bgcolor" value="#FFFFFF"/>
               </xsl:when>       
              <xsl:otherwise>
                  <param name="" value="">
                   <xsl:attribute name="value">
                       <xsl:value-of select="qap:Value[text()]"/>
                   </xsl:attribute>

                   <xsl:attribute name="name">
                      <xsl:value-of select="qap:Name[text()]"/>
                  </xsl:attribute>          
                  </param>  
             </xsl:otherwise>

           </xsl:choose>          
       </xsl:for-each>

       <!-- IE needs  this  parameter -->
        <param name="movie" value="">
           <xsl:attribute name="value">
            <xsl:value-of select="qap:FlashMovieURL[text()]"/>
           </xsl:attribute>
        </param>  

         <param name="FlashVars" value="">
           <xsl:attribute name="value">
            <xsl:value-of select="$FlashVars"/>
           </xsl:attribute>
        </param>

        <!-- embeded tag , Netscape based browsers-->
        <xsl:element name="embed">
           <xsl:attribute name="src"><xsl:value-of select="qap:FlashMovieURL[text()]"/></xsl:attribute>
           <xsl:attribute name="pluginspace">http://www.macromedia.com/shockwave/download/index.cgi?P1_Prod_Version=ShockwaveFlash</xsl:attribute>
           <xsl:attribute name="type">application/x-shockwave-flash</xsl:attribute>
           <xsl:attribute name="width"><xsl:value-of     select="qap:Width[text()]"/></xsl:attribute>
           <xsl:attribute name="height"><xsl:value-of    select="qap:Height[text()]"/></xsl:attribute>     
           <xsl:attribute name="FlashVars"><xsl:value-of select="$FlashVars"/></xsl:attribute>
           <xsl:for-each select="qap:ApplicationParameter">
          <!-- Each Parameter -->
          <!-- bgcolor is  fixed, skip it if passed on -->
              <xsl:variable name="parameterName">
                  <xsl:value-of select="qap:Name[text()]"/>
              </xsl:variable>      
               <xsl:choose>
                   <xsl:when test="contains($parameterName,'bgcolor')">            
                      <xsl:attribute name="bgcolor">#FFFFFF</xsl:attribute>
                  </xsl:when>          
              <xsl:otherwise> 
                  <xsl:attribute name="{$parameterName}"><xsl:value-of select="qap:Value[text()]"/></xsl:attribute>
             </xsl:otherwise>
           </xsl:choose>          
         </xsl:for-each>

 
         </xsl:element>

   
   </xsl:element>

 </xsl:template>
 


    <!-- Title overview/content block -->

    <xsl:template match="qap:Title">
        <xsl:param name="type"/>
        <!-- Title -->
        <p class="{$type} title">
            <!-- Title:content -->
              <xsl:call-template name="replace">
                    <xsl:with-param name="string"  select="text()" />
              </xsl:call-template>
        </p>
    </xsl:template>


    <!-- Text overview/content block -->

    <xsl:template match="qap:Text">
        <xsl:param name="type"/>

        <!-- Text -->

        <xsl:if test="$type='answer'">
            <span class="{$type} text">
                <xsl:call-template name="replace">
                    <xsl:with-param name="string" select="text()"/>
                </xsl:call-template>
            </span>
        </xsl:if>
        <xsl:if test="$type!='answer'">
            <p class="{$type} text">
                <xsl:call-template name="replace">
                    <xsl:with-param name="string" select="text()"/>
                </xsl:call-template>
            </p>
        </xsl:if>
    </xsl:template>
    
    <!-- List overview/content block -->

    <xsl:template match="qap:List">
        <xsl:param name="type"/>

        <!-- List -->

        <p class="{$type}-list-wrapper">
            <ul class="{$type}-list">
                <xsl:for-each select="qap:ListItem">

                    <!-- Each List item -->

                    <li class="{$type}-list-item">                      
                         <xsl:call-template name="replace">
                            <xsl:with-param name="string" select="text()"/>
                         </xsl:call-template>
                    </li>
                </xsl:for-each>
            </ul>
        </p>
    </xsl:template>

    <!-- XHTML content block -->

    <xsl:template match="qap:FormattedContent">
       <p class="text">
          <xsl:value-of select="text()" disable-output-escaping="yes"/>
       </p>
    </xsl:template> 

    <!--  Plug-in dependent content, use with caution -->
    <xsl:template match="qap:Binary">
        <xsl:param name="type"/>

        <xsl:variable name="selectionIndex">
            <xsl:number count="qap:Selection"/>
        </xsl:variable>

        <!-- Binary -->

        <xsl:if test="$type='answer'">
            <span class="{$type}-binary-wrapper">
                <xsl:if test="qap:MimeType/qap:Type[text()='image']">
                    <xsl:if test="qap:DataURL">

                        <!--
                          == Render an image specified by a URL.
                          == mime=="image/*" DataURL
                          -->

                        <img class="{$type} binary image" src="" alt="">
                           <xsl:attribute name="src">
                                <xsl:value-of select="qap:DataURL[text()]"/>
                            </xsl:attribute>
                            <xsl:attribute name="alt">
                                <xsl:value-of select="qap:AltText[text()]"/>
                            </xsl:attribute>
                        </img>
                    </xsl:if>
                </xsl:if>

                <!-- For future use -->

                <!-- mime=="image/*" Data -->
                <!-- ??? -->
                <!-- mime=="*" -->
                <!-- ??? -->
            </span>
        </xsl:if>
        <xsl:if test="$type!='answer'">
            <p class="{$type}-binary-wrapper">
                <xsl:if test="qap:MimeType/qap:Type[text()='image']">
                    <xsl:if test="qap:DataURL">

                        <!--
                          == Render an image specified by a URL.
                          == mime=="image/*" DataURL
                          -->

                        <img class="{$type} binary image" src="" alt="">
                            <xsl:attribute name="src">
                                <xsl:value-of select="qap:DataURL[text()]"/>
                            </xsl:attribute>
                            <xsl:attribute name="alt">
                                <xsl:value-of select="qap:AltText[text()]"/>
                            </xsl:attribute>
                        </img>
                    </xsl:if>
                </xsl:if>

            <xsl:if test="qap:MimeType/qap:Type[text()='audio']">
            <xsl:if test="qap:DataURL">

             <!--
                 == Render a link  to the audio  file  with an  audio  logo
              -->

              <table border="0" cellpadding="0" cellspacing="0">
               <tr>
                 <td><img src="/images/audio.gif" /></td>
                   <td valign="center">
                     <a>
                         <xsl:attribute name="href">
                             <xsl:value-of select="qap:DataURL[text()]"/>
                         </xsl:attribute>
                         <xsl:attribute name="target">
                             <xsl:text>_blank</xsl:text>
                         </xsl:attribute>                      
                        <xsl:value-of select="qap:AltText[text()]"/>                
                     </a>
                   </td>
                 </tr>
                </table>
               </xsl:if>
          </xsl:if> 
                
				<!--  mime = video -->
                <xsl:if test="qap:MimeType/qap:Type[text()='video']">
                    <xsl:if test="qap:DataURL">
                    	<xsl:element name="table">
                    		<xsl:attribute name="border">0</xsl:attribute>
                    		<xsl:element name="tr">
                    			<xsl:element name="td">
                    				<xsl:element name="img">
                    					<xsl:attribute name="src">../images/watch_video.gif</xsl:attribute>
                    				</xsl:element>
                    			</xsl:element>
                    			<xsl:element name="td">
                    				<xsl:attribute name="valign">center</xsl:attribute>
                    				<xsl:element name="a">
                    					<xsl:attribute name="target">_blank</xsl:attribute>
                    					<xsl:attribute name="href"><xsl:value-of select="qap:DataURL[text()]"/></xsl:attribute>
                    					<xsl:value-of select="qap:AltText[text()]"/>
                    				</xsl:element>
                    			</xsl:element>
                    		</xsl:element>
                    	</xsl:element>
                    </xsl:if>
                </xsl:if>

                <!-- mime=="image/*" Data -->
                <!-- ??? -->
                <!-- mime=="*" -->
                <!-- ??? -->
            </p>
        </xsl:if>

    </xsl:template>
    
    <!-- Plug-in dependent content, use with caution -->
    <xsl:template match="qap:EmbeddedBinary">
    <xsl:element name="object">
          <xsl:attribute name="type">
          		<xsl:value-of select="qap:EmbeddedMimeType/qap:Type[text()]"/>/<xsl:value-of select="qap:EmbeddedMimeType/qap:SubType[text()]"/>
          </xsl:attribute> 
          <xsl:attribute name="data"><xsl:value-of select="qap:DataURL[text()]"/>&#63;<xsl:value-of select="$externalParams"/></xsl:attribute> 
	  <xsl:attribute name="width"><xsl:value-of  select="qap:Width[text()]"/> </xsl:attribute>
          <xsl:attribute name="height"><xsl:value-of select="qap:Height[text()]"/></xsl:attribute> 
          <xsl:attribute name="style">border:gray 1px solid</xsl:attribute> 
           
	 <xsl:for-each select="qap:ApplicationParameter">

          <!-- Each Parameter -->      
           <param>
               	<xsl:attribute name="name">
                	  <xsl:value-of select="qap:Name[text()]"/>
            	</xsl:attribute> 
           		<xsl:attribute name="value">
                	 <xsl:value-of select="qap:Value[text()]"/>
           		</xsl:attribute>                
            </param>   
            </xsl:for-each> 
            <a><xsl:attribute name="href"><xsl:value-of select="qap:DataURL[text()]"/>&#63;<xsl:value-of select="$externalParams"/></xsl:attribute><xsl:value-of select="qap:AltText[text()]"/></a>
	
       </xsl:element>
      </xsl:template>
    
    
	<xsl:template match="qap:JavaApplet">
		<xsl:call-template name="generate_applet_object">
			<xsl:with-param name="applet_path" select="qap:AppletPath[text()]"/>
			<xsl:with-param name="applet_filename" select="qap:AppletFilename[text()]"/>
			<xsl:with-param name="applet_width" select="qap:Width[text()]"/>
			<xsl:with-param name="applet_height" select="qap:Height[text()]"/>
		</xsl:call-template>
    </xsl:template>
    
    
	<!-- this generates the object html for the java applet -->
	<xsl:template name="generate_applet_object">
		<xsl:param name="applet_path"/>
		<xsl:param name="applet_filename"/>
		<xsl:param name="applet_width"/>
		<xsl:param name="applet_height"/>
		<xsl:variable name="class_extension">.class</xsl:variable>

		<xsl:comment>[if !IE]&gt;</xsl:comment>
			<xsl:element name="object">
				<xsl:attribute name="type">application/x-java-applet</xsl:attribute>
				<xsl:attribute name="classid">
					<xsl:value-of select="concat('java:', $applet_filename)"/>
				</xsl:attribute>
				<xsl:attribute name="width">
					<xsl:value-of select="$applet_width"/>
				</xsl:attribute>
				<xsl:attribute name="height">
					<xsl:value-of select="$applet_height"/>
				</xsl:attribute>
				<xsl:for-each select="qap:ApplicationParameter">
					<!-- Each Parameter -->
					<param name="" value="">
						<xsl:attribute name="value">
							<xsl:value-of select="qap:Value[text()]"/>
                   		</xsl:attribute>
                   		<xsl:attribute name="name">
                      		<xsl:value-of select="qap:Name[text()]"/>
                  		</xsl:attribute>          
                  	</param>
				</xsl:for-each>
				<xsl:element name="param">
					<xsl:attribute name="name">codebase</xsl:attribute>
					<xsl:attribute name="value">
						<xsl:value-of select="$applet_path"/>
					</xsl:attribute>
				</xsl:element>
				<xsl:element name="param">
					<xsl:attribute name="name">assignmentId</xsl:attribute>
					<xsl:attribute name="value">
						<xsl:value-of select="$assignmentId"/>
					</xsl:attribute>
				</xsl:element>
				<xsl:element name="param">
					<xsl:attribute name="name">hitId</xsl:attribute>
					<xsl:attribute name="value">
						<xsl:value-of select="$hitId"/>
					</xsl:attribute>
				</xsl:element>
		<xsl:comment>&lt;![endif]</xsl:comment>
			<xsl:element name="object">
				<xsl:attribute name="classid">clsid:8AD9C840-044E-11D1-B3E9-00805F499D93</xsl:attribute>
				<xsl:attribute name="width">
					<xsl:value-of select="$applet_width"/>
				</xsl:attribute>
				<xsl:attribute name="height">
					<xsl:value-of select="$applet_height"/>
				</xsl:attribute>
				<xsl:for-each select="qap:ApplicationParameter">
					<!-- Each Parameter -->
					<param name="" value="">
						<xsl:attribute name="value">
							<xsl:value-of select="qap:Value[text()]"/>
                   		</xsl:attribute>
                   		<xsl:attribute name="name">
                      		<xsl:value-of select="qap:Name[text()]"/>
                  		</xsl:attribute>          
                  	</param>
				</xsl:for-each>
				<xsl:element name="param">
					<xsl:attribute name="name">codebase</xsl:attribute>
					<xsl:attribute name="value">
						<xsl:value-of select="$applet_path"/>
					</xsl:attribute>
				</xsl:element>
				<xsl:element name="param">
					<xsl:attribute name="name">code</xsl:attribute>
					<xsl:attribute name="value">
						<xsl:value-of select="substring-before($applet_filename, $class_extension)"/>
					</xsl:attribute>
				</xsl:element>
				<xsl:element name="param">
					<xsl:attribute name="name">assignmentId</xsl:attribute>
					<xsl:attribute name="value">
						<xsl:value-of select="$assignmentId"/>
					</xsl:attribute>
				</xsl:element>
				<xsl:element name="param">
					<xsl:attribute name="name">hitId</xsl:attribute>
					<xsl:attribute name="value">
						<xsl:value-of select="$hitId"/>
					</xsl:attribute>
				</xsl:element>
				<xsl:element name="strong">Your browser does not have a Java plugin.  Click 
					<xsl:element name="a">
						<xsl:attribute name="target">_blank</xsl:attribute>
						<xsl:attribute name="href">http://java.sun.com/products/plugin/downloads/index.html</xsl:attribute>here</xsl:element>
					to get the latest Java plugin.
				</xsl:element>
			</xsl:element>
		<xsl:comment>[if !IE]&gt;</xsl:comment>
			</xsl:element>
		<xsl:comment>&lt;![endif]</xsl:comment>

	</xsl:template>
	
	



 <xsl:template name="replaceLineBreak">
        <xsl:param name="string"/>
        <xsl:choose>
            <xsl:when test="contains($string,'&#10;')">
                <xsl:value-of select="substring-before($string,'&#10;')"/>
                <br/>
                <xsl:call-template name="replace">
                    <xsl:with-param name="string"
                        select="substring-after($string,'&#10;')"/>
                        </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$string"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>


 <!-- replaces  line  breaks  and linkifies -->

<xsl:template name="replace"> 
   <xsl:param name="string"/>
   <xsl:variable name="hyper1">http</xsl:variable>
   <xsl:choose>
    <xsl:when test="contains($string,$hyper1)">
         <xsl:call-template name="linkify">
            <xsl:with-param name="string" select="$string"/>     
            <xsl:with-param name="hyper"  select="$hyper1"/>
        </xsl:call-template>
   </xsl:when>
      
    <xsl:otherwise>
        <xsl:call-template name="replaceLineBreak">
            <xsl:with-param name="string" select="$string"/>     
        </xsl:call-template>
    </xsl:otherwise> 
   </xsl:choose>
</xsl:template>



<!-- This  changes text that matches the  given  parameter  to  hyperlinks, 
    it looks  like  it also  needs to deal  with line  breaks since there is  no way, seems  like,
    to compound  the  transformation of two templates  -->

 <xsl:template name="linkify">
       <xsl:param    name="string"/>
       <xsl:param    name="hyper"/> 
      
       <xsl:variable name="textstart" select="substring-before($string, $hyper)"/>
       <xsl:variable name="rest"      select="concat(substring-after($string,$hyper), ' ')"/>   

       <!-- we might have  a line  break right after  the  link or a space, look for both
            of the  two url  bodys  below  we know  the right one  is  the shortest  one, unless is  empty -->

       <xsl:variable  name="urlbody1" select ="substring-before($rest, ' ')" />
       <xsl:variable  name="urlbody2" select ="substring-before($rest,'&#10;' )" />
         
   
       <xsl:variable  name="urlbody">

           <xsl:if test="  string-length($urlbody2) &gt; string-length($urlbody1)"> 
               <xsl:if test="string-length($urlbody1) &gt; 0">
                   <xsl:value-of select="$urlbody1"/>
                   
               </xsl:if>
               <xsl:if test="string-length($urlbody1) = 0">
                   <xsl:value-of select="$urlbody2"/>
               </xsl:if>
          </xsl:if>
          <xsl:if test=" string-length($urlbody1) &gt; string-length($urlbody2)"> 
               <xsl:if test="string-length($urlbody2) &gt; 0">
                  <xsl:value-of select="$urlbody2"/>
               </xsl:if>
              <xsl:if test="string-length($urlbody2) = 0">
                  <xsl:value-of select="$urlbody1"/>
             </xsl:if>
         </xsl:if>
           
       </xsl:variable>



       <xsl:variable name="link"      select="concat($hyper,$urlbody)"/> 
       <xsl:text>&#160;</xsl:text>   
       
       <xsl:variable name="textend"   select="substring-after($rest,$urlbody)"/>
      
       <xsl:call-template name="replaceLineBreak">
               <xsl:with-param name="string" select="$textstart"/>
              
       </xsl:call-template>
    
         <a>
         <xsl:attribute name="href">
            <xsl:value-of select="$link"/>
         </xsl:attribute>
          <xsl:attribute name="target">
            <xsl:text>_blank</xsl:text>
         </xsl:attribute>
         <xsl:value-of select="$link"/>
        

       </a>
       <xsl:text>&#160;</xsl:text>


     <xsl:choose>        
         <xsl:when test="contains($textend,$hyper)">
             <xsl:call-template name="linkify">
                 <xsl:with-param name="string" select="$textend" />
                 <xsl:with-param name="hyper" select="$hyper" />
             </xsl:call-template>
         </xsl:when>
         <xsl:otherwise>
             <xsl:call-template name="replaceLineBreak">
               <xsl:with-param name="string" select="$textend"/>
             </xsl:call-template>
         </xsl:otherwise>
     </xsl:choose>
    
    
</xsl:template>


    <xsl:template match="qap:FreeTextAnswer|qap:OtherSelection">
        <xsl:param name="name"/>
        <xsl:param name="onblur"/>
        <xsl:param name="isRequired"/>

        <xsl:if test="$name">
            <xsl:call-template name="RenderTextInput">
                <xsl:with-param name="name" select="$name"/>
				<xsl:with-param name="onblur" select="$onblur"/>
                <xsl:with-param name="isRequired" select="$isRequired"/>
            </xsl:call-template>
        </xsl:if>
        <xsl:if test="not($name)">
            <input name="" value="FreeTextAnswer" type="hidden">
                <xsl:attribute name="name">
                    <xsl:text>Answer_</xsl:text>
                    <xsl:number count="qap:Question"/>
                    <xsl:text>_Type</xsl:text>
                </xsl:attribute>
            </input>

            <div class="free-text-answer-wrapper">
                <xsl:call-template name="RenderTextInput">
				  <xsl:with-param name="onblur" select="$onblur"/>
                  <xsl:with-param name="isRequired" select="$isRequired"/>
                </xsl:call-template>
            </div>
            
        </xsl:if>
        
        <!-- Add in the constraint error messages if we're not in preview mode. -->
        
        <xsl:if test="starts-with($accepted, 'true')">
            <xsl:variable name="answerName">
                <xsl:text>Answer_</xsl:text>
                <xsl:if test="string-length($name) > 0">
                    <xsl:value-of select="$name" />
                </xsl:if>
                <xsl:if test="string-length($name) = 0">
                    <xsl:number count="qap:Question" />
                    <xsl:text>_FreeText</xsl:text>
                </xsl:if>
            </xsl:variable>
                <xsl:apply-templates select="qap:Constraints">
                    <xsl:with-param name="name" select="$name" />
                    <xsl:with-param name="answerName" select="$answerName" />
                </xsl:apply-templates>

            <xsl:if test="$isRequired = 'true'">
                <xsl:call-template name="IsRequiredConstraint">
                    <xsl:with-param name="name" select="$name" />
                    <xsl:with-param name="answerName" select="$answerName" />
                </xsl:call-template>                
            </xsl:if>
        </xsl:if>
        
        <xsl:if test="not($name)">
            <!-- The <p class="free-text-answer-wrapper"> was changed to <div class="free-text-answer-wrapper">
                 above. This is because the rendering mechanism didn't allow a div to nest in a p for
                 firefox (but it did for MSIE.) At least that was all I could come up with. So to keep
                 the same appearance and consistent across browsers, I added a <p/> here so that there
                 is space between the input field and the grey line separating questions.
              -->
            <p/> 
        </xsl:if>
    </xsl:template>

	<!-- 
	  == Render text input field (single- or multi-line).
	  == @param name - 
	  -->
    <xsl:template name="RenderTextInput">
        <xsl:param name="name"/>
		<xsl:param name="onblur"/>
        <xsl:param name="isRequired"/>

        <input type="hidden" name="" value="">
            <xsl:attribute name="name">
                <xsl:text>ValidationData_</xsl:text>
                <xsl:if test="string-length($name) > 0">
                    <xsl:value-of select="$name"/>
                </xsl:if>
                <xsl:if test="string-length($name) = 0">
                    <xsl:number count="qap:Question"/>
                </xsl:if>
            </xsl:attribute>
            <xsl:attribute name="value">
 			<xsl:value-of select="java:XsltHelper.encodeConstraints(qap:Constraints)"/>
            </xsl:attribute>
        </input>

        <!-- Render a single-line text field or a multi-line textarea -->
		<xsl:variable name="index"><xsl:number count="qap:Question"/></xsl:variable>
		<xsl:variable name="text" select="java:XsltHelper.getAnswerText($answers, string($index), string(qap:DefaultText[text()]))" />

         <xsl:call-template name="NumberOfLinesSuggestion">
            <xsl:with-param name="name"     select="$name"/>
            <xsl:with-param name="onblur">
              <xsl:choose>
                <xsl:when test="$isRequired = 'true'">
                  globalFieldList.validateField(this); <xsl:value-of select="$onblur" />
                </xsl:when>
                <xsl:when test="qap:Constraints">
                  globalFieldList.validateField(this); <xsl:value-of select="$onblur" />
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="$onblur" />
                </xsl:otherwise>
              </xsl:choose>
            </xsl:with-param>
            <xsl:with-param name="text"     select="$text"/>      
        </xsl:call-template>
        
    </xsl:template>

    <!-- Multiple-choice selection form field -->
    <xsl:template match="qap:SelectionAnswer">
        <!-- Selection -->
        <p class="question-selection-wrapper">
            <input name="" value="SelectionAnswer" type="hidden">
                <xsl:attribute name="name">
                    <xsl:text>Answer_</xsl:text>
                    <xsl:number count="qap:Question"/>
                    <xsl:text>_Type</xsl:text>
                </xsl:attribute>
            </input>
            <input type="hidden">
                <xsl:attribute name="name">
                    <xsl:text>ValidationData_</xsl:text>
                    <xsl:number count="qap:Question"/>
                </xsl:attribute>
                <xsl:attribute name="value">
	    			<xsl:value-of select="java:XsltHelper.encodeConstraints(.)"/>
                </xsl:attribute>
            </input>
            <input type="hidden">
                <xsl:attribute name="name">
                    <xsl:text>ValidChoices_</xsl:text>
                    <xsl:number count="qap:Question"/>
                </xsl:attribute>
                <xsl:attribute name="value">
    				<xsl:value-of select="java:XsltHelper.encodeValidChoices(.)"/>
                </xsl:attribute>
            </input>

            <!-- Use the style suggestion to pick the type of form field to render -->
            <xsl:choose>
                <!-- Dropdown -->
                <xsl:when test="qap:StyleSuggestion[text()]='dropdown'">
                    <fieldset class="fieldset">
                        <xsl:call-template name="DropdownSelections"/>
                    </fieldset>
                </xsl:when>

                <!-- Checkbox -->
                <xsl:when test="qap:StyleSuggestion[text()]='checkbox'  or  qap:StyleSuggestion[text()]='multichooser'">
                    <fieldset class="fieldset">
                        <xsl:call-template name="CheckboxSelections"/>
                    </fieldset>
                </xsl:when>

                <!-- Radiobutton -->
                <xsl:when test="qap:StyleSuggestion[text()]='radiobutton'  or  qap:StyleSuggestion[text()]='list'">
                    <fieldset class="fieldset">
                        <xsl:call-template name="RadiobuttonSelections"/>
                    </fieldset>
                </xsl:when>

                <!-- Combobox -->

                <xsl:when test="qap:StyleSuggestion[text()]='combobox'">
                    <fieldset class="fieldset">
                        <xsl:call-template name="ComboboxSelections"/>
                    </fieldset>
                </xsl:when>

                <xsl:otherwise>
                    <fieldset class="fieldset">
                        <xsl:call-template name="RadiobuttonSelections"/>
                    </fieldset>
                </xsl:otherwise>

            </xsl:choose>

        </p>

    </xsl:template>

    <!-- File upload form field -->
    <xsl:template match="qap:FileUploadAnswer">
        <!-- Selection -->
        <p class="file-upload-answer-wrapper">
            <input value="FileUploadAnswer" type="hidden">
                <xsl:attribute name="name">
                    <xsl:text>Answer_</xsl:text>
                    <xsl:number count="qap:Question"/>
                    <xsl:text>_Type</xsl:text>
                </xsl:attribute>
            </input>
            <input type="hidden">
                <xsl:attribute name="name">
                    <xsl:text>ValidationData_</xsl:text>
                    <xsl:number count="qap:Question"/>
                </xsl:attribute>
                <xsl:attribute name="value">
	    	    <xsl:value-of select="java:XsltHelper.encodeConstraints(.)"/>
                </xsl:attribute>
            </input>

            <input type="file" size="40">
                <xsl:attribute name="name">
                    <xsl:text>FileUploadAnswer(</xsl:text>
                    <xsl:number count="qap:Question"/>
                    <xsl:text>)</xsl:text>
                </xsl:attribute>
                <xsl:if test="starts-with($accepted, 'false')">
                    <xsl:attribute name="disabled"/>
                </xsl:if>
            </input>
        </p>
    </xsl:template>

    <!-- Dropdown selections -->

    <xsl:template name="DropdownSelections">

        <p class="dropdown-wrapper">

			<xsl:variable name="index">
				<xsl:number count="qap:Question"/>
			</xsl:variable>

			<xsl:variable name="selectname">
				<xsl:text>Answer_</xsl:text>
				<xsl:number count="qap:Question"/>
			</xsl:variable>

            <!-- Render a <select> form field -->

            <select name="Answer_[! Question*:index /]">
                <xsl:attribute name="name">
                	<xsl:value-of select="$selectname"/>
                </xsl:attribute>
                <xsl:if test="starts-with($accepted, 'false')">
                    <xsl:attribute name="disabled"/>
                </xsl:if>

                <!-- Render the selections for the <select> -->

                <xsl:call-template name="SelectSelections">
                	<xsl:with-param name="index" select="$index"/>
                </xsl:call-template>

				<!--  If the "Other" selection exists, render an additional dropdown option -->
				<xsl:if test="qap:Selections/qap:OtherSelection">
					<xsl:call-template name="SelectOtherSelection">
						<xsl:with-param name="index" select="$index"/>
					</xsl:call-template>
				</xsl:if>
            </select>

            <!-- Now render the "Other" text field, if it exists -->

            <xsl:if test="qap:Selections/qap:OtherSelection">
				<xsl:variable name="key">
					<xsl:text>OtherSelection</xsl:text>
				</xsl:variable>				
				<xsl:variable name="questionNumber">
					<xsl:number count="qap:Question"/>
				</xsl:variable>

                <br />
                
                <!-- Label for other selection -->
                <xsl:text>Other: </xsl:text>
                
                <!-- Text input for other selection -->
	            <xsl:apply-templates select="qap:Selections/*[name()='OtherSelection']">
	                <xsl:with-param name="name">
	                    <xsl:value-of select="$questionNumber"/>
	                    <xsl:text>_OtherSelection</xsl:text>
	                </xsl:with-param>
					<xsl:with-param name="onblur">
						<xsl:text>
if (document.getElementById('</xsl:text><xsl:value-of select="$selectname"/><xsl:text>_OtherSelection').value.length > 0) {
  var z = document.getElementById('</xsl:text><xsl:value-of select="$selectname"/><xsl:text>').options ;  
  for (iter=0 ; iter &lt; z.length ; iter++) { 
    if (z[iter].value == '</xsl:text><xsl:value-of select="$key"/><xsl:text>') { 
      z[iter].selected = true;
      return;
    }
  }
}</xsl:text>
					</xsl:with-param>
	            </xsl:apply-templates>

            </xsl:if>
        </p>
    </xsl:template>


    <!-- Checkbox selections -->

    <xsl:template name="CheckboxSelections">

        <!-- All selections -->

		<xsl:variable name="index">
			<xsl:number count="qap:Question"/>
		</xsl:variable>
		
        <xsl:for-each select="qap:Selections/qap:Selection">

			<xsl:variable name="key">
                <xsl:if test="qap:SelectionIdentifier">
                    <xsl:text>Selection_</xsl:text>
                    <xsl:value-of select="java:XsltHelper.base64Encode(string(qap:SelectionIdentifier))"/>
                </xsl:if>
                <xsl:if test="not(qap:SelectionIdentifier)">
                    <xsl:text>Selection_</xsl:text>
                    <xsl:value-of select="java:XsltHelper.base64Encode(string(qap:Identifier))"/>
                </xsl:if>
			</xsl:variable>

            <p class="checkbox-wrapper">

                <!-- Render a checkbox -->

                <input class="question selection" type="checkbox">
                    <xsl:attribute name="name">
                        <xsl:text>Answer_</xsl:text>
                        <xsl:number count="qap:Question"/>
                    </xsl:attribute>
                    <xsl:attribute name="id">
                        <xsl:text>Answer_</xsl:text>
                        <xsl:number count="qap:Question"/>
                    </xsl:attribute>
                    <xsl:attribute name="value">
                    	<xsl:value-of select="$key"/>
                    </xsl:attribute>
                    <xsl:if test="starts-with($accepted, 'false')">
                        <xsl:attribute name="disabled"/>
                    </xsl:if>
                    <xsl:if test="java:XsltHelper.isAnswerChoice($answers,string($index),string($key))">
                    	<xsl:attribute name="checked">
                    		<xsl:value-of select="string('true')"/>
                    	</xsl:attribute>
                    </xsl:if>
                </input>

                <!-- Render a label for a checkbox -->
                <xsl:if test="qap:SelectionIdentifier">
                    <xsl:apply-templates select="*[name()!='SelectionIdentifier']">
                        <xsl:with-param name="type">answer</xsl:with-param>
                    </xsl:apply-templates>
                </xsl:if>
                <xsl:if test="not(qap:SelectionIdentifier)">
                    <xsl:apply-templates select="*[name()!='Identifier']">
                        <xsl:with-param name="type">answer</xsl:with-param>
                    </xsl:apply-templates>
                </xsl:if>
            </p>

        </xsl:for-each>

        <!-- Other selection -->

        <xsl:if test="qap:Selections/qap:OtherSelection">
        
        	<xsl:variable name="key">
        		<xsl:text>OtherSelection</xsl:text>
        	</xsl:variable>
        	
            <p class="checkbox-wrapper">

                <input class="question selection" type="checkbox">
                    <xsl:attribute name="name">
                        <xsl:text>Answer_</xsl:text>
                        <xsl:number count="qap:Question"/>
                    </xsl:attribute>
                    <xsl:attribute name="id">
                        <xsl:text>Answer_</xsl:text>
                        <xsl:number count="qap:Question"/>
                    </xsl:attribute>
					<xsl:attribute name="value">
						<xsl:value-of select="$key"/>
					</xsl:attribute>
                    <xsl:if test="starts-with($accepted, 'false')">
                        <xsl:attribute name="disabled"/>
                    </xsl:if>
                    <xsl:if test="java:XsltHelper.isAnswerChoice($answers, string($index), string($key))">
                    	<xsl:attribute name="checked">
                    		<xsl:value-of select="string('true')"/>
                    	</xsl:attribute>
					</xsl:if>
                </input>

                <!-- Label for other selection -->
                <xsl:text>Other: </xsl:text>

                <!-- Text input for other selection -->
                <xsl:apply-templates select="qap:Selections/*[name()='OtherSelection']">
                    <xsl:with-param name="name">
                        <xsl:number count="qap:Question"/>
                        <xsl:text>_OtherSelection</xsl:text>
                    </xsl:with-param>
                </xsl:apply-templates>
            </p>
        </xsl:if>
    </xsl:template>


    <!-- Radiobutton selections -->

    <xsl:template name="RadiobuttonSelections">

        <!-- All selections -->

		<xsl:variable name="index">
			<xsl:number count="qap:Question"/>
		</xsl:variable>

        <xsl:for-each select="qap:Selections/qap:Selection">

			<xsl:variable name="key">
				<xsl:choose>
                    <xsl:when test="qap:SelectionIdentifier">
						<xsl:text>Selection_</xsl:text>
						<xsl:value-of select="java:XsltHelper.base64Encode(string(qap:SelectionIdentifier))"/>
                    </xsl:when>
					<xsl:when test="qap:Identifier">
						<xsl:text>Selection_</xsl:text>
						<xsl:value-of select="java:XsltHelper.base64Encode(string(qap:Identifier))"/>
					</xsl:when>
					<xsl:otherwise>
						<xsl:text>OtherSelection</xsl:text>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>

            <p class="radiobutton-wrapper">

                <!-- Radiobutton for a selection -->

				<table border="0" cellpadding="0" cellspacing="4"><tr><td valign="center">

                <input class="question selection" type="radio">
                    <xsl:attribute name="name">
                        <xsl:text>Answer_</xsl:text>
                        <xsl:number count="qap:Question"/>
                    </xsl:attribute>
                    <xsl:attribute name="id">
                        <xsl:text>Answer_</xsl:text>
                        <xsl:number count="qap:Question"/>
                    </xsl:attribute>
                    <xsl:attribute name="value">
						<xsl:value-of select="$key"/>
					</xsl:attribute>
                    <xsl:if test="starts-with($accepted, 'false')">
                        <xsl:attribute name="disabled"/>
                    </xsl:if>
					<xsl:if test="java:XsltHelper.isAnswerChoice($answers,string($index),string($key))">
                    	<xsl:attribute name="checked">
                    		<xsl:value-of select="string('true')"/>
                    	</xsl:attribute>
					</xsl:if>
                </input>

				</td>
				<td valign="center">
								
                <!-- Label for a selection -->
                <xsl:if test="qap:SelectionIdentifier">
                    <xsl:apply-templates select="*[name()!='SelectionIdentifier']">
                        <xsl:with-param name="type">answer</xsl:with-param>
                    </xsl:apply-templates>
                </xsl:if>
                <xsl:if test="not(qap:SelectionIdentifier)">
                    <xsl:apply-templates select="*[name()!='Identifier']">
                        <xsl:with-param name="type">answer</xsl:with-param>
                    </xsl:apply-templates>
                </xsl:if>

                
                </td></tr></table>

            </p>

        </xsl:for-each>

        <!-- Other selection -->

        <xsl:if test="qap:Selections/qap:OtherSelection">

			<xsl:variable name="key">
				<xsl:text>OtherSelection</xsl:text>
			</xsl:variable>
			 
            <p class="radiobutton-wrapper">

                <!-- Render a radiobutton -->

				<table border="0" cellpadding="0" cellspacing="4"><tr><td valign="center">

                <input class="question selection" type="radio">
                    <xsl:attribute name="name">
                        <xsl:text>Answer_</xsl:text>
                        <xsl:number count="qap:Question"/>
                    </xsl:attribute>
                    <xsl:attribute name="id">
                        <xsl:text>Answer_</xsl:text>
                        <xsl:number count="qap:Question"/>
                    </xsl:attribute>
					<xsl:attribute name="value">
						<xsl:value-of select="$key"/>
					</xsl:attribute>
                    <xsl:if test="starts-with($accepted, 'false')">
                        <xsl:attribute name="disabled"/>
                    </xsl:if>
 					<xsl:if test="java:XsltHelper.isAnswerChoice($answers,string($index),string($key))">
                    	<xsl:attribute name="checked">
                    		<xsl:value-of select="string('true')"/>
                    	</xsl:attribute>
					</xsl:if>
                </input>

				</td>
                <!-- Render a label -->

				<td valign="center">
                <xsl:text>Other: </xsl:text>

                <!-- Render a text input -->

                <xsl:apply-templates select="qap:Selections/*[name()='OtherSelection']">
                    <xsl:with-param name="name">
                        <xsl:number count="qap:Question"/>
                        <xsl:text>_OtherSelection</xsl:text>
                    </xsl:with-param>
                    <xsl:with-param name="id">
                        <xsl:number count="qap:Question"/>
                        <xsl:text>_OtherSelection</xsl:text>
                    </xsl:with-param>
                </xsl:apply-templates>

				</td></tr></table>
            </p>
        </xsl:if>
    </xsl:template>

    <!-- Combobox selections -->
    <xsl:template name="ComboboxSelections">
		<xsl:variable name="index">
			<xsl:number count="qap:Question"/>
		</xsl:variable>
		<xsl:variable name="selectname">
			<xsl:text>Answer_</xsl:text>
			<xsl:number count="qap:Question"/>
		</xsl:variable>
		
        <!-- Render a <select> form field -->
        <select class="question selection">
            <xsl:attribute name="name">
				<xsl:value-of select="$selectname"/>
            </xsl:attribute>
            <xsl:attribute name="id">
				<xsl:value-of select="$selectname"/>
            </xsl:attribute>
            <xsl:if test="qap:MaxSelectionCount[text()] &gt; 1">
                <xsl:attribute name="multiple"/>
            </xsl:if>
            <xsl:attribute name="size">
                <xsl:choose>
                    <xsl:when test="qap:MaxSelectionCount[text()] &lt; 10">
                        <xsl:text>10</xsl:text>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:text>5</xsl:text>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:attribute>
            <xsl:if test="starts-with($accepted, 'false')">
                <xsl:attribute name="disabled"/>
            </xsl:if>
            <!-- Render the <option>s for the <select> -->
            <xsl:call-template name="SelectSelections">
            	<xsl:with-param name="index" select="$index"/>
            </xsl:call-template>
	        <!-- If the "Other" selection exists, render an additional combo option -->
			<xsl:if test="qap:Selections/qap:OtherSelection">
				<xsl:call-template name="SelectOtherSelection">
					<xsl:with-param name="index" select="$index"/>
				</xsl:call-template>
			</xsl:if>
        </select>
        <!-- Other selection -->
        <xsl:if test="qap:Selections/qap:OtherSelection">
			<xsl:variable name="key">
				<xsl:text>OtherSelection</xsl:text>
			</xsl:variable>				
			<xsl:variable name="questionNumber">
				<xsl:number count="qap:Question"/>
			</xsl:variable>
            <!-- Render a text input form field -->
			<br />
            <xsl:apply-templates select="qap:Selections/*[name()='OtherSelection']">
                <xsl:with-param name="name">
                    <xsl:value-of select="$questionNumber"/>
                    <xsl:text>_OtherSelection</xsl:text>
                </xsl:with-param>
                <xsl:with-param name="id">
                    <xsl:value-of select="$questionNumber"/>
                    <xsl:text>_OtherSelection</xsl:text>
                </xsl:with-param>
                <xsl:with-param name="onblur">
					<xsl:text>
if (document.getElementById('</xsl:text><xsl:value-of select="$selectname"/><xsl:text>_OtherSelection').value.length > 0) {
  var z = document.getElementById('</xsl:text><xsl:value-of select="$selectname"/><xsl:text>').options ;
  for (iter=0 ; iter &lt; z.length ; iter++) { 
    if (z[iter].value == '</xsl:text><xsl:value-of select="$key"/><xsl:text>') { 
      z[iter].selected = true;
      return;
    }
  } 
}</xsl:text>
				</xsl:with-param>
            </xsl:apply-templates>
        </xsl:if>
    </xsl:template>

    <!-- Select selections (used by Dropdown selections and Combobox selections) -->

	<xsl:template name="SelectSelections">
		<xsl:param name="index"/>

        <!-- All selections -->

        <xsl:for-each select="qap:Selections/qap:Selection">

			<xsl:variable name="key">
                <xsl:if test="qap:SelectionIdentifier">
				    <xsl:text>Selection_</xsl:text>
				    <xsl:value-of select="java:XsltHelper.base64Encode(string(qap:SelectionIdentifier))"/>
                </xsl:if>
                <xsl:if test="not(qap:SelectionIdentifier)">
				    <xsl:text>Selection_</xsl:text>
				    <xsl:value-of select="java:XsltHelper.base64Encode(string(qap:Identifier))"/>
                </xsl:if>
			</xsl:variable>

			<option>
				<xsl:attribute name="value">
					<xsl:value-of select="$key"/>
				</xsl:attribute>
				<xsl:if test="java:XsltHelper.isAnswerChoice($answers,string($index),string($key))">
					<xsl:attribute name="selected">
						<xsl:value-of select="string('true')"/>
	               	</xsl:attribute>
				</xsl:if>
                
                <xsl:if test="qap:SelectionIdentifier">
				    <xsl:apply-templates select="*[name()!='SelectionIdentifier']">
					    <xsl:with-param name="type">answer</xsl:with-param>
				    </xsl:apply-templates>
                </xsl:if>
                <xsl:if test="not(qap:SelectionIdentifier)">
				    <xsl:apply-templates select="*[name()!='Identifier']">
					    <xsl:with-param name="type">answer</xsl:with-param>
				    </xsl:apply-templates>
                </xsl:if>

			</option>

        </xsl:for-each>

    </xsl:template>

    <xsl:template name="SelectOtherSelection">
		<xsl:param name="index"/>

        <!-- All selections -->

        <xsl:for-each select="qap:Selections/qap:OtherSelection">

			<xsl:variable name="key">
				<xsl:text>OtherSelection</xsl:text>
			</xsl:variable>

			<option>
				<xsl:attribute name="value">
					<xsl:value-of select="$key"/>
				</xsl:attribute>
				<xsl:if test="java:XsltHelper.isAnswerChoice($answers,string($index),string($key))">
					<xsl:attribute name="selected">
						<xsl:value-of select="string('true')"/>
	               	</xsl:attribute>
				</xsl:if>
				Other (see below)
			</option>

        </xsl:for-each>

    </xsl:template>

    <xsl:template name="NumberOfLinesSuggestion">
        <xsl:param name="name"/>
		<xsl:param name="onblur"/>
		<xsl:param name="text"/>

		<xsl:variable name="answerName">
		    <xsl:text>Answer_</xsl:text>
		    <xsl:if test="string-length($name) > 0">
		        <xsl:value-of select="$name" />
		    </xsl:if>
		    <xsl:if test="string-length($name) = 0">
		        <xsl:number count="qap:Question" />
		        <xsl:text>_FreeText</xsl:text>
		    </xsl:if>
		</xsl:variable>

        <xsl:choose>
            <xsl:when test="number(qap:NumberOfLinesSuggestion) = 1">
            
                <input class="question free-text" type="text" size="60">
                    <xsl:attribute name="name">
                        <xsl:value-of select="$answerName" />
                    </xsl:attribute>
                    <xsl:attribute name="id">
                        <xsl:value-of select="$answerName" />
                    </xsl:attribute>
                    <xsl:if test="string($onblur)!=''">
					    <xsl:attribute name="onblur">
						    <xsl:value-of select="$onblur"/>
					    </xsl:attribute>
				    </xsl:if>
                    <xsl:attribute name="value">
					    <xsl:value-of select="$text" />
                    </xsl:attribute>
                    <xsl:if test="starts-with($accepted, 'false')">
                        <xsl:attribute name="disabled"/>
                    </xsl:if>
                </input>
            
            </xsl:when>
            <xsl:otherwise>

                <textarea class="question free-text" name="Answer_[! Question*:index /]" cols="80">
                    <xsl:attribute name="name">
                        <xsl:value-of select="$answerName" />
                    </xsl:attribute>
                    <xsl:attribute name="id">
                        <xsl:value-of select="$answerName" />
                    </xsl:attribute>
                    <xsl:attribute name="rows">
                        <xsl:if test="not(qap:NumberOfLinesSuggestion)">
                            <xsl:value-of select="ceiling(string-length($text) * 0.01) + 4"/>
                        </xsl:if>
                        <xsl:if test="number(qap:NumberOfLinesSuggestion) &gt; 49">50</xsl:if>
                        <xsl:if test="number(qap:NumberOfLinesSuggestion) &lt; 50"><xsl:value-of select="qap:NumberOfLinesSuggestion"/></xsl:if>
                    </xsl:attribute>
                    <xsl:if test="starts-with($accepted, 'false')">
                        <xsl:attribute name="disabled"/>
                    </xsl:if>
                    <xsl:if test="string($onblur)!=''">
		                <xsl:attribute name="onblur">
			                <xsl:value-of select="$onblur"/>
		                </xsl:attribute>
	                </xsl:if>
	                <xsl:value-of select="$text" />
                </textarea>
        
            </xsl:otherwise>
        </xsl:choose>

    </xsl:template>
    
    <xsl:template match="qap:Constraints">
    	<xsl:param name="name" />
        <xsl:param name="answerName" />
    	<xsl:apply-templates select="*">
    		<xsl:with-param name="name" select="$name" />
            <xsl:with-param name="answerName" select="$answerName" />
    	</xsl:apply-templates>
    </xsl:template>

    <xsl:template name="IsRequiredConstraint">
        <xsl:param name="name" />
        <xsl:param name="answerName" />

        <xsl:variable name="errName">Error_Required_1_<xsl:value-of select="$answerName" /></xsl:variable>

        <div>
            <xsl:attribute name="class">error-message-none-wrapper</xsl:attribute>
            <xsl:attribute name="id"><xsl:value-of select="$errName" /></xsl:attribute>

            This field is required.
        </div>

        <!-- This script sets up the validation handler -->
        <script type="text/javascript">
        globalFieldList.addField(new TextIsRequiredValidator("<xsl:value-of select="$answerName" />", "<xsl:value-of select="$errName" />"));
        </script>
    </xsl:template>

    <xsl:template match="qap:Length">
        <xsl:param name="name" />
        <xsl:param name="answerName" />
        
        <xsl:variable name="min">
        <xsl:if test="string(@minLength)=''">0</xsl:if>
        <xsl:if test="string(@minLength)!=''"><xsl:value-of select="@minLength" /></xsl:if>
        </xsl:variable>
        
        <xsl:variable name="max">
        <xsl:if test="string(@maxLength)=''">0</xsl:if>
        <xsl:if test="string(@maxLength)!=''"><xsl:value-of select="@maxLength" /></xsl:if>
        </xsl:variable>
           
        <xsl:variable name="errName">Error_Length_<xsl:number count="qap:Length" />_<xsl:value-of select="$answerName" /></xsl:variable>
        
        <div>
            <xsl:attribute name="class">error-message-none-wrapper</xsl:attribute>
            <xsl:attribute name="id"><xsl:value-of select="$errName" /></xsl:attribute>

            Invalid entry length. 
            <xsl:if test="string(@minLength)!=''">
                Must be at least <xsl:value-of select="@minLength" /> characters.
            </xsl:if>
            <xsl:if test="string(@maxLength)!=''">
                Must be at most <xsl:value-of select="@maxLength" /> characters.
            </xsl:if>
        </div>
        
        <!-- This script sets up the validation handler -->
        <script type="text/javascript">
        globalFieldList.addField(new LengthValidator("<xsl:value-of select="$answerName" />", "<xsl:value-of select="$errName" />", <xsl:value-of select="$min" />, <xsl:value-of select="$max" />));
        </script>
    </xsl:template>

    <xsl:template match="qap:AnswerFormatRegex">
        <xsl:param name="name" />
        <xsl:param name="answerName" />

        <xsl:variable name="errName">Error_AnswerFormatRegex_<xsl:number count="qap:AnswerFormatRegex" />_<xsl:value-of select="$answerName" /></xsl:variable>
        <div>
            <!--  <xsl:attribute name="style">visibility: hidden;</xsl:attribute> -->
            <xsl:attribute name="class">error-message-none-wrapper</xsl:attribute>
            <xsl:attribute name="id"><xsl:value-of select="$errName" /></xsl:attribute>
            <xsl:if test="string(@errorText)=''">
                Invalid input supplied.
            </xsl:if>
            <xsl:value-of select="@errorText" />
        </div>
        
        <!-- This script sets up the validation handler -->
        <script type="text/javascript">
        globalFieldList.addField(new RegexValidator('<xsl:value-of select="$answerName" />', '<xsl:value-of select="$errName" />', "<xsl:value-of select="java:XsltHelper.escapeJavaScript(string(@regex))"/>", '<xsl:value-of select="java:XsltHelper.escapeJavaScript(string(@flags))" />'));
        </script>
    </xsl:template>

    <xsl:template match="qap:IsNumeric">
        <xsl:param name="name" />
        <xsl:param name="answerName" />

        <xsl:variable name="errName">Error_IsNumeric_<xsl:number count="qap:IsNumeric" />_<xsl:value-of select="$answerName" /></xsl:variable>
        <div>
            <xsl:attribute name="class">error-message-none-wrapper</xsl:attribute>
            <xsl:attribute name="id"><xsl:value-of select="$errName" /></xsl:attribute>

            Invalid numeric value. 
            <xsl:if test="string(@minValue)!=''">
                Must be at least <xsl:value-of select="@minValue" />.
            </xsl:if>
            <xsl:if test="string(@maxValue)!=''">
                Must be at most <xsl:value-of select="@maxValue" />.
            </xsl:if>
        </div>
        
        <!-- This script sets up the validation handler -->
        <xsl:variable name="minString"><xsl:if test="@minValue"><xsl:value-of select="@minValue" /></xsl:if><xsl:if test="not(@minValue)">''</xsl:if></xsl:variable>
        <xsl:variable name="maxString"><xsl:if test="@maxValue"><xsl:value-of select="@maxValue" /></xsl:if><xsl:if test="not(@maxValue)">''</xsl:if></xsl:variable>
        <script type="text/javascript">
        globalFieldList.addField(new NumericValidator("<xsl:value-of select="$answerName" />", "<xsl:value-of select="$errName" />", <xsl:value-of select="$minString" />, <xsl:value-of select="$maxString" />));
        </script>
    </xsl:template>
    
</xsl:stylesheet>
