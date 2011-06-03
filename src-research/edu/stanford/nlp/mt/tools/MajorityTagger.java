package edu.stanford.nlp.mt.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.util.StringUtils;

/**
 * Simple majority tagger. Choose the majority tag for each word.
 * 
 * @author Heeyoung Lee
 */
public class MajorityTagger {

  private static final String defaultDictionary = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tag_dict.txt";
  
  /** Map<word, major tag> */
  Map<String, String> tagDict = new HashMap<String, String>();
  
  
  public MajorityTagger() {
    loadDictionary(defaultDictionary);
  }

  public MajorityTagger(String filename){
    loadDictionary(filename);
  }

  /** load a pre-built dictionary for majority tagger */
  private void loadDictionary(String filename){
    for(String line : IOUtils.readLines(filename)){
      String[] split = line.split(" ");
      tagDict.put(split[0], split[1]);
    }
  }
  
  /** return the tag for a word */
  public String getTag(String word, boolean isFirstword){
    if(tagDict.containsKey(word)) return tagDict.get(word);
    boolean proper = isFirstword && Character.isUpperCase(word.charAt(0));
    boolean plural = word.endsWith("s");
    if(proper && plural) return "NNPS";
    else if(proper && !plural) return "NNP";
    else if(!proper && plural) return "NNS";
    else return "NN";
  }
  
  /** set PartOfSpeechAnnotation for a CoreLabel */
  public void tagWord(CoreLabel c, boolean isFirstWord){
    String tag = getTag(c.get(TextAnnotation.class), isFirstWord);
    c.set(PartOfSpeechAnnotation.class, tag);
  }
  
  /** 
   * return the list of tags for a sentence
   * @param sentence string
   * @return list of tags
   */
  public String[] tagSentence(String sentence){
    String[] words = sentence.split(" ");
    String[] tags = new String[words.length];
    int i = 0;
    for (String word : words) {
      tags[i++] = getTag(word, i==0);
    }    
    return tags;
  }
  
  public static void main(String args[]){
    Properties props = StringUtils.argsToProperties(args);
    String dict = props.getProperty("dictionary", defaultDictionary);
    MajorityTagger tagger = new MajorityTagger(dict);
    
    String[] tags = tagger.tagSentence("Stanford University is located in California");
    
    System.err.println();

  }
}
