package edu.stanford.nlp.mt.tools;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.PhraseGeneratorFactory;
import edu.stanford.nlp.mt.pt.CombinedPhraseGenerator;
import edu.stanford.nlp.mt.pt.ConcreteRule;
import edu.stanford.nlp.mt.pt.FlatPhraseTable;
import edu.stanford.nlp.mt.pt.UnknownWordPhraseGenerator;

/**
 * Filter OOVs from an input file given a phrase table.c
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
  public static PhraseGenerator<IString,String> load(String filename) throws IOException {
    FlatPhraseTable.createIndex(false);
    String generatorName = PhraseGeneratorFactory.PSEUDO_PHARAOH_GENERATOR;

    PhraseGenerator<IString,String> phraseGenerator =  
        PhraseGeneratorFactory.<String>factory(false, generatorName, filename, String.valueOf(QUERY_LIMIT));
    phraseGenerator = new CombinedPhraseGenerator<IString,String>(
        Arrays.asList(phraseGenerator, new UnknownWordPhraseGenerator<IString, String>(true, FlatPhraseTable.sourceIndex)),
        CombinedPhraseGenerator.Type.STRICT_DOMINANCE, QUERY_LIMIT);

    FlatPhraseTable.lockIndex();

    return phraseGenerator;
  }

  /**
   * Filter a source input given a phrase generator.
   * 
   * @param source
   * @param phraseGenerator
   * @return
   */
  private static Sequence<IString> filterUnknownWords(String input, 
      PhraseGenerator<IString,String> phraseGenerator) {
    Sequence<IString> source = IStrings.tokenize(input);
    List<ConcreteRule<IString,String>> rules = phraseGenerator.getRules(source, null, null, -1, null);

    CoverageSet possibleCoverage = new CoverageSet();
    for (ConcreteRule<IString,String> rule : rules) {
      if (rule.abstractRule.target.size() > 0 && !"".equals(rule.abstractRule.target.toString())) {
        possibleCoverage.or(rule.sourceCoverage);
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

  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 1 || args[0].equals("-h") || args[0].equals("-help")) {
      System.err.printf("Usage: java %s phrase_table < file > file%n", 
          FilterOOVByPhraseTable.class.getName());
      System.exit(-1);
    }
    String filename = args[0];
    PhraseGenerator<IString,String> phraseGenerator = null;
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
        Sequence<IString> output = filterUnknownWords(line, phraseGenerator);
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
