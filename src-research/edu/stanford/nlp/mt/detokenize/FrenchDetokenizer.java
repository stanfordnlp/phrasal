package edu.stanford.nlp.mt.detokenize;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Properties;
import java.util.Stack;
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
    String output = input;
    
    //double quotes
    // " abc "  ->  "abc"
    output = handleQuotes(output);
    
    //punctuation
    output = handlePunctuation(output);
    
    //grammatical detokenizations
    output = handleGrammatical(output);
    
    return output;
  }
  
  //double quotes
  // " abc "  ->  "abc"
  // " abc" abc " -> "abc" abc"
  // " abc "abc " abc " -> "abc"abc "abc"
  // " abc " abc " abc -> "abc" abc " abc
  //TODO see line 402 of reference.  It turns out that even a single " should
  //  glom onto nearest word
  private String handleQuotes(String input) {
    Matcher quotesMatcher = quotesPattern.matcher(input);
    Stack<Integer> toBeDeleted = new Stack<Integer>();
    while(quotesMatcher.find()) {
      toBeDeleted.push(quotesMatcher.start(0) + 1);
      toBeDeleted.push(quotesMatcher.end(0) - 2);
    }
    StringBuilder sb = new StringBuilder(input);
    while(!toBeDeleted.isEmpty()) {
      sb.deleteCharAt(toBeDeleted.pop());
    }
    return sb.toString();
  }
    
  //punctation
  // a ,  ->  a,
  // a .  ->  a.
  // a :  ->  a:
  // a ;  ->  a;
  // a ?  ->  a?
  // a !  ->  a!
  // a %  ->  a%
  // a ... -> a...
  // ( a   -> (a
  // a )   -> a)
  private String handlePunctuation(String input) {
    //append space for simpler regex
    String output = input + " ";

    //TODO wrap into single regex
    //TODO order matters.  With wrong order, we may get
    //  a % .   ->  a %.   Can we fix this?
    //TODO fix bug where a . "  ->  a ."
    output = output.replace(" % ", "% ");
    output = output.replace(" ... ", "... ");

    output = output.replace(" ( ", " (");
    output = output.replace(" ) ", ") ");
    
    output = output.replace(" , ", ", ");
    output = output.replace(" : ", ": ");
    output = output.replace(" ; ", "; ");
    output = output.replace(" . ", ". ");
    output = output.replace(" ? ", "? ");
    output = output.replace(" ! ", "! ");
    
        
    //remove the appended space
    if(output.charAt(output.length() - 1) == ' ')
      output = output.substring(0, output.length() - 1);
    
    return output;
  }
  
  //a closed class of grammatical detokenizations
  // l' a  ->  l'a
  // n' a  ->  n'a
  // d' a  ->  d'a
  // j' a  ->  j'a
  // s' a  ->  s'a
  // t' a  ->  t'a
  // c' a  ->  c'a
  // qu' a  -> qu'a
  // a -il ->  a-il  (as in question formation)
  // a -ils -> a-ils
  // a -elle -> a-elle
  // a -elles -> a-elles
  // a -on   -> a-on
  // est -ce  -> est-ce
  //Note: we also allow capitalized forms
  private String handleGrammatical(String input) {
    //append space for simpler regex
    String output = input + " ";
    
    //TODO use a single regex
    output = output.replace("l' ", "l'");
    output = output.replace("L' ", "L'");
    output = output.replace("n' ", "n'");
    output = output.replace("N' ", "N'");
    output = output.replace("d' ","d'");
    output = output.replace("D' ","D'");
    output = output.replace("j' ","j'");
    output = output.replace("J' ","j'");
    output = output.replace("s' ","s'");
    output = output.replace("S' ","S'");
    output = output.replace("t' ","t'");
    output = output.replace("T' ","T'");
    output = output.replace("c' ","c'");
    output = output.replace("C' ","C'");
    output = output.replace("qu' ","qu'");
    output = output.replace("Qu' ","Qu'");
    output = output.replace(" -il ","-il ");
    output = output.replace(" -ils ","-ils ");
    output = output.replace(" -elle ","-elle ");
    output = output.replace(" -elles ","-elles ");
    output = output.replace(" -on ","-on ");
    output = output.replace("est -ce ","est-ce ");
    output = output.replace("Est -ce ","Est-ce ");
    
    //remove the appended space
    if(output.charAt(output.length() - 1) == ' ')
      output = output.substring(0, output.length() - 1);
    
    return output;
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
