package edu.stanford.nlp.mt.tools;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.mt.tm.CombinedTranslationModel;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.TranslationModel;
import edu.stanford.nlp.mt.tm.TranslationModelFactory;
import edu.stanford.nlp.mt.tm.UnknownWordPhraseGenerator;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Filter OOVs from an input file given a phrase table.
 * 
 * @author Spence Green
 *
 */
public class FilterOOVByPhraseTable {

  // Only need one rule per span.
  private static final int QUERY_LIMIT = 1;

  /**
   * Load the phrase table, including the OOV model.
   * 
   * @param filename
   * @return
   * @throws IOException
   */
  public static TranslationModel<IString,String> load(String filename) throws IOException {
    TranslationModel<IString,String> translationModel =  
        TranslationModelFactory.<String>factory(filename);
    TranslationModel<IString,String> phraseGenerator = new CombinedTranslationModel<IString,String>(
        Arrays.asList(translationModel, new UnknownWordPhraseGenerator<IString, String>(true)),
        QUERY_LIMIT);
    return phraseGenerator;
  }

  /**
   * Filter a source input given a phrase generator.
   * 
   * @param source
   * @param phraseGenerator
   * @param keepASCII 
   * @return
   */
  private static Sequence<IString> filterUnknownWords(String input, 
      TranslationModel<IString,String> phraseGenerator, boolean keepASCII) {
    Sequence<IString> source = IStrings.tokenize(input);
    List<ConcreteRule<IString,String>> rules = phraseGenerator.getRules(source, null, null, -1, null);

    CoverageSet possibleCoverage = new CoverageSet();
    for (ConcreteRule<IString,String> rule : rules) {
      if (rule.abstractRule.target.size() > 0 && !"".equals(rule.abstractRule.target.toString())) {
        possibleCoverage.or(rule.sourceCoverage);
      }
    }
    
    if (keepASCII) {
      for (int i = 0, sz = source.size(); i < sz; ++i) {
        String token = source.get(i).toString();
        if (TokenUtils.isASCII(token)) {
          possibleCoverage.set(i);
        }
      }
    }

    if (possibleCoverage.cardinality() > 0) {
      IString[] filteredToks = new IString[possibleCoverage.cardinality()];
      for (int i = possibleCoverage.nextSetBit(0), j = 0; i >= 0; i = possibleCoverage.nextSetBit(i+1)) {
        filteredToks[j++] = source.get(i);
      }
      return new SimpleSequence<IString>(true, filteredToks);
    }
    return null;
  }

  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(FilterOOVByPhraseTable.class.getName()).append(" phrase_table < file").append(nl);
    sb.append(nl);
    sb.append(" Options:").append(nl);
    sb.append("   -a    : Do not filter ASCII tokens").append(nl);
    return sb.toString();
  }

  private static Map<String,Integer> argDefs() {
    Map<String,Integer> argDefs = new HashMap<>();
    argDefs.put("a", 0);
    return argDefs;
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length < 1 || args[0].equals("-h") || args[0].equals("-help")) {
      System.err.print(usage());
      System.exit(-1);
    }

    Properties options = StringUtils.argsToProperties(args, argDefs());
    boolean keepASCII = options.containsKey("a");
    String filename = options.getProperty("");
    TranslationModel<IString,String> phraseGenerator = null;
    try {
      System.err.println("Loading phrase table: " + filename);
      phraseGenerator = load(filename);
    } catch (IOException e) {
      System.err.println("Could not load: " + filename);
      System.exit(-1);
    }
    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        new BufferedInputStream(System.in)));
    try {
      for (String line; (line = reader.readLine()) != null;) {
        Sequence<IString> output = filterUnknownWords(line, phraseGenerator, keepASCII);
        if (output == null) {
          // Don't output blank lines
          System.out.println(line.trim());
        } else {
          System.out.println(output.toString());
        }
      }
    } catch (IOException e) {
      System.err.printf("Error reading from input file at line %d%n", reader.getLineNumber());
    }
    System.err.printf("Filtered %d input lines%n", reader.getLineNumber());
  }
}
