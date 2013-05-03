package edu.stanford.nlp.mt.process.fr;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles detokenization for French output from WMT system.
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
public class Detokenizer {

  //singleton instance
  private static Detokenizer INSTANCE;

  //these patterns compile only once when INSTANCE is first loaded
  private final Pattern quotesPattern = Pattern.compile("(?:^| )\"( ).*? \" ");
  private final Pattern finalQuotePattern = Pattern.compile(" \" *$");
  private final Pattern rightPunctuation = Pattern.compile("(^| )(%|\\.\\.\\.|\\)|,|:|;|\\.|\\?|!)(?= )");
  private final Pattern leftPunctuation = Pattern.compile("(^| )\\((?= )");
  private final Pattern leftGrammatical = Pattern.compile("(^| )(l'|n'|d'|j'|s'|t'|c'|qu'|m')(?= )",Pattern.CASE_INSENSITIVE);
  private final Pattern rightGrammatical = Pattern.compile(" -[^ ]+(?= )",Pattern.CASE_INSENSITIVE);

  private final Pattern numberWordNumberGlom = Pattern.compile("(?:^| )\\d{1,2}( )[mh]( )\\d{1,2}(?: |$)");
  private final Pattern numberWordGlom = Pattern.compile("(?:^| )\\d{1,3}( )[mh](?: |$)");
  private final Pattern wordNumberGlom = Pattern.compile("(?:^| )[g]( )\\d{1,3}(?: |$)");

  /** get singleton instance */
  public static synchronized Detokenizer getInstance() {
    if (INSTANCE == null) {
      INSTANCE = new Detokenizer();
    }
    return INSTANCE;
  }

  //private constructor forces callers to use getInstance()
  private Detokenizer() {}

  /** Detokenize input string.
   *  True-cased and non-true-cased inputs are both acceptable.
   *  Output will preserve input casing.
   */
  public String detok(String input) {
    return detokImpl(input);
  }

  private String detokImpl(String input) {
    //Indexes of spaces that need deleting
    Set<Integer> toBeDeleted = new HashSet<Integer>();

    toBeDeleted.addAll(handleQuotes(input));
    toBeDeleted.addAll(handlePunctuation(input));
    toBeDeleted.addAll(handleGrammatical(input));
    toBeDeleted.addAll(handleNumberWordGloms(input));

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
    while(quotesMatcher.find()) {
      toBeDeleted.add(quotesMatcher.start(1));
      int endIdx = quotesMatcher.end(); //tracks end of
      toBeDeleted.add(endIdx - 3);
    }

    Matcher finalQuotesMatcher = finalQuotePattern.matcher(input);
    while(finalQuotesMatcher.find()) {
      toBeDeleted.add(finalQuotesMatcher.start());
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
    input = input + ' ';

    Set<Integer> toBeDeleted = new HashSet<Integer>();

    Matcher rightPuncMatcher = rightPunctuation.matcher(input);
    while(rightPuncMatcher.find()) {
      toBeDeleted.add(rightPuncMatcher.start());
    }

    Matcher leftPuncMatcher = leftPunctuation.matcher(input);
    while(leftPuncMatcher.find()) {
      toBeDeleted.add(leftPuncMatcher.end());
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
    input = input + ' ';

    Set<Integer> toBeDeleted = new HashSet<Integer>();

    Matcher leftGramMatcher = leftGrammatical.matcher(input);
    while(leftGramMatcher.find()) {
      toBeDeleted.add(leftGramMatcher.end());
    }

    Matcher rightGramMatcher = rightGrammatical.matcher(input);
    while(rightGramMatcher.find()) {
      toBeDeleted.add(rightGramMatcher.start());
    }

    return toBeDeleted;
  }

  private Set<Integer> handleNumberWordGloms(String input) {
    Set<Integer> toBeDeleted = new HashSet<Integer>();

    Matcher bothMatcher = numberWordNumberGlom.matcher(input);
    while (bothMatcher.find()) {
      toBeDeleted.add(bothMatcher.start(1));
      toBeDeleted.add(bothMatcher.start(2));
    }

    Matcher leftMatcher = numberWordGlom.matcher(input);
    while (leftMatcher.find()) {
      toBeDeleted.add(leftMatcher.start(1));
    }

    Matcher rightMatcher = wordNumberGlom.matcher(input);
    while (rightMatcher.find()) {
      toBeDeleted.add(rightMatcher.start(1));
    }

    return toBeDeleted;
  }

  /**
   * Detokenizes a file of sentences, one sentence per line.
   * Write output to new file, one sentence per line.
   *
   * Usage: java FrenchDetokenizer < infile > outfile
   */
  public static void main(String[] args) throws Exception {
    Detokenizer detokenizer = getInstance();
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    for (String line; (line = reader.readLine()) != null; ) {
      String detokenizedLine = detokenizer.detok(line.trim());
      System.out.println(detokenizedLine);
    }
  }

}
