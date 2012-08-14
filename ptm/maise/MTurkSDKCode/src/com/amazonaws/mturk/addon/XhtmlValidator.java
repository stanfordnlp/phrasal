/*
 * Copyright 2007-2008 Amazon Technologies, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * 
 * http://aws.amazon.com/apache2.0
 * 
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and
 * limitations under the License.
 */ 


package com.amazonaws.mturk.addon;

import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.xerces.parsers.SAXParser;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.log4j.Logger;

import com.amazonaws.mturk.service.exception.ValidationException;

public class XhtmlValidator {
  // -------------------------------------------------------------
  // Constants - Private
  // -------------------------------------------------------------

  private static final String NS = "http://www.w3.org/1999/xhtml";
  private static final String CDATA_HEADER = "<![CDATA[";
  private static final String CDATA_FOOTER = "]]>";

  protected static Logger log = Logger.getLogger(XhtmlValidator.class);

  private static String XSD;
  static {
    XSD = "http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2006-07-14/FormattedContentXHTMLSubset.xsd";
  }

  /**
   * validateAndClean validates the content against the FormattedContentXHTMLSubset.xsd and removes comments
   * @param content
   * @param requester
   * @return
   * @throws XHTMLParseErrorException
   */
  public static String validateAndClean(String content) 
    throws ValidationException {

    SAXParser parser = new SAXParser();
    Validator handler = new Validator();
    parser.setErrorHandler(handler);

    Pattern p = Pattern.compile("<FormattedContent>.*?</FormattedContent>", Pattern.DOTALL);
    String htmlContent = null;

    try {	
      initializeParser(parser);
      Matcher matcher = p.matcher(content);
      while (matcher.find()) {
        htmlContent = matcher.group(0);

        content = removeComments(htmlContent, content);
        htmlContent = insertNamespaceAndRemoveCDataTags(htmlContent);

        try {
          parser.parse(new InputSource(new StringReader(htmlContent)));
          parser.reset(); //reset parser so we can use it for the next htmlContent
          initializeParser(parser); //resetting sets parser back to factory settings, so we need to initialize it again 
        } catch (java.io.IOException e) {
          String msg = "SAXParser exception: " + e + "\nhtmlContent: " + htmlContent;
          log.error(msg);
          throw new ValidationException(msg, e);
        }
        if (handler.validationError == true) {
          String msg = "Validator error: " + handler.validationError + handler.saxParseException.getMessage()
            + "\nhtmlContent: " + htmlContent;
          log.error(msg);
          throw new ValidationException(msg);
        }
      }    			
    } catch (SAXException e) {
      String msg = "SAXParser exception: " + e + "\nhtmlContent: " + htmlContent; 
      log.error(msg);
      throw new ValidationException(msg, e);
    }

    return content;
  }

  //-------------------------------------------------------------
  // Methods - Private
  //-------------------------------------------------------------
  private static void initializeParser(SAXParser parser) 
    throws SAXException {
    parser.setFeature("http://xml.org/sax/features/validation", true);
    parser.setFeature("http://apache.org/xml/features/validation/schema", true);
    parser.setFeature("http://apache.org/xml/features/validation/schema-full-checking", true);
    parser.setProperty("http://apache.org/xml/properties/schema/external-schemaLocation", NS + " " + XSD);
  }

  private static String insertNamespaceAndRemoveCDataTags(String text) throws ValidationException {
    String formattedContentHeader = "<FormattedContent>";
    if (text.indexOf(CDATA_HEADER) != text.indexOf(formattedContentHeader) + formattedContentHeader.length()) {
      String msg = "Missing or misplaced CDATA header: " + CDATA_HEADER + " in content :" + text; 
      log.error(msg);
      throw new ValidationException(msg);
    }
    String formattedContentFooter = "</FormattedContent>";
    if (text.indexOf(CDATA_FOOTER) + CDATA_FOOTER.length() != text.indexOf(formattedContentFooter) ) {
      String msg = "Missing or misplaced CDATA footer: " + CDATA_FOOTER + " in content :" + text; 
      log.error(msg);
      throw new ValidationException(msg);
    }
    return "<FormattedContent xmlns=\"" + NS + "\">" 
      + text.substring(text.indexOf(CDATA_HEADER) 
          + CDATA_HEADER.length(), text.indexOf(CDATA_FOOTER)) 
      + "</FormattedContent>";
  }

  private static String removeComments(String htmlContent, String question) {	
    int htmlContentStart = question.indexOf(htmlContent);
    int htmlContentStop = htmlContentStart + htmlContent.length();

    htmlContent = Pattern.compile("<!--.*?-->", Pattern.DOTALL).matcher(htmlContent).replaceAll("");
    return question.substring(0, htmlContentStart) + htmlContent + question.substring(htmlContentStop, question.length());
  }

  //-------------------------------------------------------------
  // Inner Classes
  //-------------------------------------------------------------
  /** Validator class for SaxParser **/
  private static class Validator extends DefaultHandler {
    public boolean validationError = false;  
    public SAXParseException saxParseException = null; 
    public void error(SAXParseException exception)
      throws SAXException {
      validationError = true;
      saxParseException = exception;
    }     
    public void fatalError(SAXParseException exception)
      throws SAXException {
      validationError = true;	    
      saxParseException=exception;	     
    }		    
    public void warning(SAXParseException exception)
      throws SAXException { }
  }

}
