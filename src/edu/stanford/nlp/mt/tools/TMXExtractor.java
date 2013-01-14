package edu.stanford.nlp.mt.tools;

import static java.lang.System.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * TMXExtractor is a utility for extracting plain text parallel data from 
 * Translation Memory eXchange (TMX) formatted files. 
 * 
 * @author daniel cer (danielcer@stanford.edu)
 *
 */
public class TMXExtractor extends DefaultHandler {
  private final Set<String> langs;
  private final Map<String,BufferedWriter> langToFh = 
      new HashMap<String, BufferedWriter>();
  private final Map<String,String> langToCurrentSeg = 
      new HashMap<String, String>();
  private String inTUVLang = null;
  private boolean inSeg = false;
  
  public TMXExtractor(String outputPrefixFn, Set<String> langs) {
    this.langs = langs;
    try {
      for (String lang : langs) {
        langToFh.put(lang, new BufferedWriter(
            new FileWriter(
                String.format("%s.%s",  outputPrefixFn, lang.toLowerCase()))));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }    
  }
  
  @Override
  public void startElement (String uril, String localName, 
      String qName, Attributes attributes) throws SAXException {
    if ("tuv".equals(qName)) {
      inTUVLang = attributes.getValue("xml:lang");
    } else if ("seg".equals(qName)) {
      inSeg = true;
    }
  }
  
  @Override
  public void endElement(String uri, String localName, 
      String qName) throws SAXException {
    if ("tu".equals(qName)) {
      Set<String> tuLangSet = langToCurrentSeg.keySet();
      tuLangSet.retainAll(langs);
      if (tuLangSet.equals(langs)) {
        try {
          for (Map.Entry<String, String> langSeg : 
            langToCurrentSeg.entrySet()) {
             String lang = langSeg.getKey();
             String seg = langSeg.getValue();
             langToFh.get(lang).write(
                 seg.replaceAll("[\\s+\r\n]+", " ").
                 replaceFirst("^ ", "").
                 replaceFirst(" $", ""));
             langToFh.get(lang).write("\n");
          }
        } catch (IOException e) {
          throw new RuntimeException(e);          
        }
      }
      langToCurrentSeg.clear();  
    } else if ("tuv".equals(qName)) {
      inTUVLang = null;
    } else if ("seg".equals(qName)) {
      inSeg = false;
    }
  }
  
  @Override
  public void characters (char ch[], int start, int length) 
      throws SAXException {
    if (inSeg && inTUVLang != null) {
      String segString = new String(ch, start, length);
      if (!langToCurrentSeg.containsKey(inTUVLang)) {
        langToCurrentSeg.put(inTUVLang, segString);
      } else {
        langToCurrentSeg.put(inTUVLang, 
            langToCurrentSeg.get(inTUVLang) + segString);
      }
    }
  }
  
  @Override
  public void endDocument() throws SAXException {
    try {
      for (BufferedWriter fh : langToFh.values()) {      
        fh.close();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  static public void usage() {
    err.printf("Usage:\n\t%s (TMX file) (output prefix) \\\n" + 
      "\t  (lang 1) [lang 2] [lang 3] ...\n", TMXExtractor.class.getName());
  }
  
  static public void main(String[] args) throws Exception {
    if (args.length < 3) {
      usage();
      exit(-1);
    }
    
    String tmxFn = args[0];
    String outputPrefixFn = args[1];
    Set<String> langs = new HashSet<String>();
    for (int i = 2; i < args.length; i++) {
      langs.add(args[i]);
    }
    SAXParserFactory saxpf = SAXParserFactory.newInstance();
    SAXParser saxParser = saxpf.newSAXParser();
    saxParser.parse(new File(tmxFn), new TMXExtractor(outputPrefixFn, langs));        
  }
}
