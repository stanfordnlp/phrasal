package edu.stanford.nlp.mt.tools;

import static java.lang.System.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * MTEvalXMLExtractor is a utility for extracting source segments and 
 * references from the LDC releases of NIST OpenMT data. 
 * 
 * Extracted segments can be optional filtered by genre. 
 * 
 * @author daniel cer (danielcer@stanford.edu)
 *
 */
public class MTEvalXMLExtractor {
  
  static class MTEvalContentHandler extends DefaultHandler {
     final String outputFnOrPrefix;
     final String filterGenre; 
     private BufferedWriter output = null;
     private String genre = null;
     private boolean inSeg = false;
     
     public MTEvalContentHandler(String outputFn, String filterGenre) {
        this.outputFnOrPrefix = outputFn;
        this.filterGenre = filterGenre;
     }
     
     @Override
     public void characters (char ch[], int start, int length)
         throws SAXException
     {
       try {
         if (inSeg) {
           if (filterGenre == null || filterGenre.equals(genre)) {
             output.write(ch, start, length);
           }
         } 
       } catch (IOException e) {
         throw new RuntimeException(e);
       }
     }
     
     @Override
     public void startElement (String uri, String localName,
         String qName, Attributes attributes) throws SAXException
     {
       if ("seg".equals(qName)) {
         inSeg = true;
       } else if ("doc".equals(qName)) {
         genre = attributes.getValue("genre");
       } else if ("srcset".equals(qName)) {
         try {
           output = new BufferedWriter(new FileWriter(outputFnOrPrefix));
         } catch (IOException e) {
           throw new RuntimeException(e);
         }
       } else if ("refset".equals(qName)) {
         try {
           String refId = attributes.getValue("refid");
           String fn = String.format("%s.%s", outputFnOrPrefix, refId);
           output = new BufferedWriter(new FileWriter(fn));
         } catch (IOException e) {
           throw new RuntimeException(e);
         }
       }
     }
     
     @Override
     public void endElement (String uri, String localName,
         String qName) throws SAXException
     {
       try {         
         if ("seg".equals(qName)) {
           inSeg = false;           
           if (filterGenre == null || filterGenre.equals(genre)) {             
             output.write("\n");
           }
         } else if ("doc".equals(qName)) {
           genre = null;
         } else if ("srcset".equals(qName) || "refset".equals(qName)) {
           output.close();
         }
       } catch (IOException e) {
         throw new RuntimeException(e);
       }
     }     
  }
  
  static void usage() {
     err.printf("Usage:\n\tjava %s \\\n"+
       "\t   (mteval.xml) \\\n"+
       "\t   (source output name / ref output prefix) \\\n"+
       "\t   [genre]\n", MTEvalXMLExtractor.class.getName());    
  }
  
  public static void  main(String[] args) throws Exception {
    if (args.length != 2 && args.length != 3) {
      usage();
      exit(-1);
    }
    String mtEvalFn = args[0];
    String outputFn = args[1];
    String genre = args.length == 3 ? args[2] : null;
    
    SAXParserFactory saxpf = SAXParserFactory.newInstance();
    SAXParser saxParser = saxpf.newSAXParser();
    saxParser.parse(new File(mtEvalFn), new MTEvalContentHandler(outputFn, genre));
  }
}
