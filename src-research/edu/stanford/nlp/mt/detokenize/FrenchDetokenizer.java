package edu.stanford.nlp.mt.detokenize;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.StringUtils;

/**
 * Handles detokenization for french output from
 *  wmt system
 *  
 * Usage:
 *  i) Use the main method to convert an input file to a detokenized output file
 *  -or-
 *  ii) put the following in your java code:
 *       FrenchDetokenizer fd = FrenchDetokenizer.getInstance();
 *       String detokenizedString = fd.detok(inputString)
 *  
 *  
 * @author kevinreschke
 *
 */
public class FrenchDetokenizer {

  //singleton instance
  private static FrenchDetokenizer INSTANCE;
  
  //these patterns compile only once when INSTANCE is first loaded
  private Pattern quotesPattern = Pattern.compile("\" .*? \"");
  private Pattern rightPunctuation = Pattern.compile("(^| )(%|\\.\\.\\.|\\(|,|:|;|\\.|\\?|!) ");
  private Pattern leftPunctuation = Pattern.compile("(^| )\\) ");
  private Pattern leftGrammatical = Pattern.compile("(^| )(l'|n'|d'|j'|s'|t'|c'|qu') ",Pattern.CASE_INSENSITIVE);
  private Pattern rightGrammatical = Pattern.compile(" -[^ ]+ ",Pattern.CASE_INSENSITIVE);
  
  /** get singleton instance */
  public static FrenchDetokenizer getInstance() {
    if(INSTANCE == null) {
      INSTANCE = new FrenchDetokenizer();
    }
    return INSTANCE;
  }
  
  //private constructor forces callers to use getInstance()
  private FrenchDetokenizer() {}
  
  /** detokenize input string 
   *   true-cased and non-true-cased inputs are both acceptable
   *   output will preserve input casing.
   * */
  public String detok(String input) {
    return detokImpl(input);
  }
 
  private String detokImpl(String input) {
    //Indexes of spaces that need deleting
    Set<Integer> toBeDeleted = new HashSet<Integer>(); 
    
    toBeDeleted.addAll(handleQuotes(input));
    toBeDeleted.addAll(handlePunctuation(input));
    toBeDeleted.addAll(handleGrammatical(input));

    //sort
    List<Integer> sorted = new ArrayList<Integer>(toBeDeleted);
    Collections.sort(sorted);
    
    //delete spaces
    StringBuilder output = new StringBuilder(input);
    for(int i = sorted.size() - 1; i >= 0; i--) {
      output.deleteCharAt(sorted.get(i));
    }
    
    return output.toString().trim();
  }
  
  //double quotes
  // " abc "  ->  "abc"
  // " abc" abc " -> "abc" abc"
  // " abc "abc " abc " -> "abc"abc "abc"
  //
  //Note: see line 402 of 2012 reference; a single "
  // is glommed onto next word
  // TODO decide what to do with extra/singleton instances
  //
  //returns indexes of spaces to be deleted
  private Set<Integer> handleQuotes(String input) {
    Set<Integer> toBeDeleted = new HashSet<Integer>();

    Matcher quotesMatcher = quotesPattern.matcher(input);
    int endIdx = 0; //tracks end of 
    while(quotesMatcher.find()) {
      toBeDeleted.add(quotesMatcher.start() + 1);
      endIdx = quotesMatcher.end();
      toBeDeleted.add(endIdx - 2);
    }
        
    return toBeDeleted;
  }
    
  //punctuation
  //
  //right punctuation:
  // a ,  ->  a,
  // a .  ->  a.
  // a :  ->  a:
  // a ;  ->  a;
  // a ?  ->  a?
  // a !  ->  a!
  // a %  ->  a%
  // a ... -> a...
  // a )   -> a)
  //
  //left punctuation:
  // ( a   -> (a
  //
  //returns indexes of spaces to be deleted
  private Set<Integer> handlePunctuation(String input) {
    //append space for simpler regex
    input = input + " ";
    
    Set<Integer> toBeDeleted = new HashSet<Integer>();
    
    Matcher rightPuncMatcher = rightPunctuation.matcher(input);
    while(rightPuncMatcher.find()) {
      toBeDeleted.add(rightPuncMatcher.start());
    }
    
    Matcher leftPuncMatcher = leftPunctuation.matcher(input);
    while(leftPuncMatcher.find()) {
      toBeDeleted.add(leftPuncMatcher.end() - 1);
    }
    
    //remove appended space from remove set
    toBeDeleted.remove(input.length() - 1);

    return toBeDeleted;
  }
  
  //a closed class of grammatical detokenizations
  //
  //left grammaticals:
  // l' a  ->  l'a
  // n' a  ->  n'a
  // d' a  ->  d'a
  // j' a  ->  j'a
  // s' a  ->  s'a
  // t' a  ->  t'a
  // c' a  ->  c'a
  // qu' a  -> qu'a
  //
  //right grammaticals:
  // a -il ->  a-il  (as in question formation)
  // a -ils -> a-ils
  // a -elle -> a-elle
  // a -elles -> a-elles
  // a -on   -> a-on
  // a -toi -> a-toi  (as in imperative + pronoun)
  // a -moi -> a-moi
  //   etc.
  // a -ce  -> a-ce  (as in est -ce -> est-ce)
  //
  //Note: we also allow capitalized forms
  //
  //returns indexes of spaces to be deleted
  private Set<Integer> handleGrammatical(String input) {
    //append space for simpler regex
    input = input + " ";
    
    Set<Integer> toBeDeleted = new HashSet<Integer>();

    Matcher leftGramMatcher = leftGrammatical.matcher(input);
    while(leftGramMatcher.find()) {
      toBeDeleted.add(leftGramMatcher.end() - 1);
    }

    Matcher rightGramMatcher = rightGrammatical.matcher(input);
    while(rightGramMatcher.find()) {
      toBeDeleted.add(rightGramMatcher.start());
    }
    
    return toBeDeleted;
  }
  
  /**
   * Detokenizes a file of sentences, one sentence per line.
   * Write output to new file, one sentence per line.
   * 
   * Usage: java FrenchDetonenizer -input inputFile -output outputFile
   */
  public static void main(String args[]) throws Exception{
  Properties props = StringUtils.argsToProperties(args);
  String inputFile = props.getProperty("input");
  String outputFile = props.getProperty("output");
  
    FrenchDetokenizer fd = getInstance();
    
    BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
    
    for(String input : ObjectBank.getLineIterator(inputFile)) {
      String output = fd.detok(input);
      bw.write(output);
      bw.write("\n");
    }
    
    bw.close();
  }
}
