package edu.stanford.nlp.mt.process.fr;

import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

/**
 * This class handles source side (English) coref for 
 *  use in WMT2013 en-fr translation.
 *  
 * Main Idea: Use alignments and source-side coref
 *  to get french coref.  Then correct errors in pronoun
 *  gender.
 * 
 * @author kevinreschke
 *
 */
public class SourceSideCoref {
    
    private StanfordCoreNLP pipeline;
  
  /**
   * Create a new instance of SourceSideCoref, loading
   *  all necessary corenlp models.
   */
  public SourceSideCoref() {
    Properties props = new Properties();
    props.put("tokenize.whitespace","true");
    props.put("ssplit.isOneSentence","true");
      props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
      pipeline = new StanfordCoreNLP(props);
  }
  
  
  /**
   * Get the coref graph for this sentence.
   * 
   * We take the string to be exactly one white-space tokenized sentence
   * 
   */
  public Map<Integer,CorefChain> getCorefGraph(String sentence) {
    Annotation document = new Annotation(sentence);
    pipeline.annotate(document);
    return document.get(CorefChainAnnotation.class);
  }
  
  //Test main
  public static void main(String args[]) {
    SourceSideCoref ssc = new SourceSideCoref();
    
//    String gummy = "1-4=5 6=3 9=3";
//    String[] g = gummy.split(" ");
//    for(String gs : g) {
//      String[] a = gs.split("=");
//      System.err.println(a[0] + "..." + a[1]);
//    }
    
//    String text = "To the wide public , IMB is a symbol of technological revolution ; the company is seen as a highly temporary one , yet it was founded over a hundred years ago .";
    String text = "IBM survived and flourished for a hundred years , because it remained true to its original values , while it was not afraid to change everything around itself .";
    
    int lineNo = 0;
//    for(String text : ObjectBank.getLineIterator("/user/kreschke/scr/wmt/Apr29/source.tok")) {

      Annotation document = new Annotation(text);
      ssc.pipeline.annotate(document);

      Map<Integer, CorefChain> graph = 
          document.get(CorefChainAnnotation.class);

      System.err.println(lineNo+": graph: "+graph);
 
      //      for(CorefChain cc : graph.values()) {
      //        for(CorefMention cm : cc.getMentionsInTextualOrder()) {
      //          
      //        }
      //      }

      lineNo++;
//    }
    
  }
  
}
