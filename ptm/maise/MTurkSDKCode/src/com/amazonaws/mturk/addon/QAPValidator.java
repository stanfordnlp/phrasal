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

//validating an XML document with an XSD schema

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.XMLConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.amazonaws.mturk.service.exception.ValidationException;

/**
 * Validate a Turk QAP file using the Turk XSD schema Requires a copy of the
 * Turk schema "QuestionForm.xsd" in the current directory available from:
 * http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2005-10-01/QuestionForm.xsd
 */
public class QAPValidator {
  public final static String QUESTION_FORM_XSD = "QuestionForm.xsd";
  public final static String FORMATTED_CONTENT_XSD = "FormattedContentXHTMLSubset.xsd";
  public final static String EXTERNAL_QUESTION_XSD = "ExternalQuestion.xsd";
  public final static String HTML_QUESTION_XSD = "HTMLQuestion.xsd";

  public static void validate(String question) throws ValidationException, IOException {
    validate(question, false, QUESTION_FORM_XSD, false);
  }

  public static void validateFile(String fileName)
    throws ValidationException, IOException {
    validate(fileName, true, QUESTION_FORM_XSD, false);
  }

  protected static void validate(String fileOrString, boolean isFile,
      String schema, boolean skipFormattedContent) throws ValidationException, IOException {
    FileReader fReader = null;
    StringReader sReader = null;

    try {
      // Get a parser capable of parsing vanilla XML into a DOM tree
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      DocumentBuilder parser = factory.newDocumentBuilder();

      // parse the JNLP file on the command line purely as XML and get a DOM
      // tree representation.
      Document document = null;
      if (isFile) {
        fReader = new FileReader(fileOrString);
        document = parser.parse(new File(fileOrString));

      } else {
        sReader = new StringReader(fileOrString);
        document = parser.parse(new InputSource(new StringReader(fileOrString)));
      }

      // Determine if root node is QuestionForm or ExternalQuestion,
      // then validate using the appropriate schema.  For
      // QuestionForm, also find and validate FormattedContent
      // elements.
      Element docElement = document.getDocumentElement();
      String docElemName = docElement.getTagName();

      // build an XSD-aware SchemaFactory
      SchemaFactory schemaFactory = 
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      System.setProperty("javax.xml.transform.TransformerFactory",
          "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");

      // hook up mindless org.xml.sax.ErrorHandler implementation.
      schemaFactory.setErrorHandler(new XSDErrorHandler());

      if (docElemName == "QuestionForm") {
        schema = QUESTION_FORM_XSD;
      } else if (docElemName == "ExternalQuestion") {
        schema = EXTERNAL_QUESTION_XSD;
        skipFormattedContent = true;
      } else if (docElemName == "HTMLQuestion") {
        schema = HTML_QUESTION_XSD;
        skipFormattedContent = true;
      } else {
        throw new SAXException("Root element is not valid Question "
            + "data (QuestionForm, ExternalQuestion)");
      }

      // get the custom xsd schema describing the required format for my XML files.
      Schema schemaXSD = schemaFactory.newSchema(QAPValidator.class.getResource(schema));

      // The line above doesn't work when run in the Eclipse debugger.
      // Use an explicit file URL as show below if running in eclipse debugger
      //Schema schemaXSD = schemaFactory.newSchema( new URL("file:/...path to SDK.../etc/schema/2005-10-01/QuestionForm.xsd") );

      // Create a Validator capable of validating JNLP files according to to the
      // Vampqh custom schema.
      Validator validator = schemaXSD.newValidator();

      // parse the JNLP tree againts the stricter XSD schema
      validator.validate(new DOMSource(document));

      if (!skipFormattedContent) {
        String xmlString = null;

        if (isFile) {
          // Read the raw XML into a string
          File file = new File(fileOrString);
          char[] buffer = new char[ (int) file.length() + 1 ];
          BufferedReader reader = new BufferedReader( new FileReader( file ) );
          try {
            reader.read( buffer );
          } finally {
            reader.close();
          }
          xmlString = new String( buffer );
        } else {
          xmlString = fileOrString;
        }

        XhtmlValidator.validateAndClean( xmlString );
      }

      closeStreams(isFile, fReader, sReader);

    } catch (SAXParseException e) {

      closeStreams(isFile, fReader, sReader);
      
      throw new ValidationException("[" + e.getLineNumber() + ","
          + e.getColumnNumber() + "] " + e.getMessage(), null);

    } catch (Exception e) {

      closeStreams(isFile, fReader, sReader);

      throw new ValidationException(e.getMessage(), e);
    }
  }

  private static void closeStreams(boolean isFile, FileReader fReader, StringReader sReader) 
    throws IOException {
    if (isFile && fReader != null)
      fReader.close();
    else if (sReader != null)
      sReader.close();
  }

} // end ValidateSchema

class XSDHandler extends DefaultHandler {
  private final String FORMATTED_CONTENT_TOKEN = "FormattedContent";

  boolean atFormattedContent = false;

  public void startDocument() throws SAXException {
    // System.out.println( "SAX Event: START DOCUMENT" );
  }

  public void endDocument() throws SAXException {
    // System.out.println( "SAX Event: END DOCUMENT" );
  }

  public void startElement(String namespaceURI, String localName, String qName,
      Attributes attr) throws SAXException {
    if (localName.equalsIgnoreCase(FORMATTED_CONTENT_TOKEN)) {
      atFormattedContent = true;
    }
  }

  public void endElement(String namespaceURI, String localName, String qName)
    throws SAXException {
    if (localName.equalsIgnoreCase(FORMATTED_CONTENT_TOKEN))
      atFormattedContent = false;
  }

  public void characters(char[] ch, int start, int length) throws SAXException {
    String thisString = new String(ch, start, length);
    if (atFormattedContent) {

      try {
        if (!thisString.trim().equals("")) {
          String xhtmlPre = "<?xml version=\"1.0\"?><FormattedContent xmlns=\"http://www.w3.org/1999/xhtml\">";
          String xhtmlPost = "</FormattedContent>";
          QAPValidator.validate(xhtmlPre + thisString + xhtmlPost, false,
              QAPValidator.FORMATTED_CONTENT_XSD, false);
        }
      } catch (Exception e) {
        throw new SAXException(e.getMessage(), e);
      }
    }
  }
}

class XSDErrorHandler implements ErrorHandler {
  /**
   * default contstructor
   */
  public XSDErrorHandler() {
  }

  /**
   * Receive notification of a warning.
   * 
   * <p>
   * SAX parsers will use this method to report conditions that are not errors
   * or fatal errors as defined by the XML recommendation. The default behaviour
   * is to take no action.
   * </p>
   * 
   * <p>
   * The SAX parser must continue to provide normal parsing events after
   * invoking this method: it should still be possible for the application to
   * process the document through to the end.
   * </p>
   * 
   * <p>
   * Filters may use this method to report other, non-XML warnings as well.
   * </p>
   * 
   * @param exception
   *          The warning information encapsulated in a SAX parse exception.
   * @exception org.xml.sax.SAXException
   *              Any SAX exception, possibly wrapping another exception.
   * @see org.xml.sax.SAXParseException
   */
  public void warning(SAXParseException exception) throws SAXException {
    System.err.println("[WARNING] " + exception.getMessage());
  }

  /**
   * Receive notification of a recoverable error.
   * 
   * <p>
   * This corresponds to the definition of "error" in section 1.2 of the W3C XML
   * 1.0 Recommendation. For example, a validating parser would use this
   * callback to report the violation of a validity constraint. The default
   * behaviour is to take no action.
   * </p>
   * 
   * <p>
   * The SAX parser must continue to provide normal parsing events after
   * invoking this method: it should still be possible for the application to
   * process the document through to the end. If the application cannot do so,
   * then the parser should report a fatal error even if the XML recommendation
   * does not require it to do so.
   * </p>
   * 
   * <p>
   * Filters may use this method to report other, non-XML errors as well.
   * </p>
   * 
   * @param exception
   *          The error information encapsulated in a SAX parse exception.
   * @exception org.xml.sax.SAXException
   *              Any SAX exception, possibly wrapping another exception.
   * @see org.xml.sax.SAXParseException
   */
  public void error(SAXParseException exception) throws SAXException {
    System.err.println("[ERROR] " + exception.getMessage());
  }

  /**
   * Receive notification of a non-recoverable error.
   * 
   * <p>
   * <strong>There is an apparent contradiction between the documentation for
   * this method and the documentation for {@link
   * org.xml.sax.ContentHandler#endDocument}. Until this ambiguity is resolved
   * in a future major release, clients should make no assumptions about whether
   * endDocument() will or will not be invoked when the parser has reported a
   * fatalError() or thrown an exception.</strong>
   * </p>
   * 
   * <p>
   * This corresponds to the definition of "fatal error" in section 1.2 of the
   * W3C XML 1.0 Recommendation. For example, a parser would use this callback
   * to report the violation of a well-formedness constraint.
   * </p>
   * 
   * <p>
   * The application must assume that the document is unusable after the parser
   * has invoked this method, and should continue (if at all) only for the sake
   * of collecting additional error messages: in fact, SAX parsers are free to
   * stop reporting any other events once this method has been invoked.
   * </p>
   * 
   * @param exception
   *          The error information encapsulated in a SAX parse exception.
   * @exception org.xml.sax.SAXException
   *              Any SAX exception, possibly wrapping another exception.
   * @see org.xml.sax.SAXParseException
   */
  public void fatalError(SAXParseException exception) throws SAXException {
    System.err.println("[FATAL ERROR] " + exception.getMessage());
  }

} // end JNLPErrorHandler
