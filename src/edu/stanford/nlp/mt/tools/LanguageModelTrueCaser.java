package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.List;

import edu.stanford.nlp.mt.decoder.CubePruningDecoder;
import edu.stanford.nlp.mt.decoder.Inferer;
import edu.stanford.nlp.mt.decoder.InfererBuilderFactory;
import edu.stanford.nlp.mt.decoder.h.IsolatedPhraseForeignCoverageHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.recomb.TranslationNgramRecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.UnconstrainedOutputSpace;
import edu.stanford.nlp.mt.decoder.util.UniformScorer;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.Featurizer;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.NGramLanguageModelFeaturizer;
import edu.stanford.nlp.mt.pt.AbstractPhraseGenerator;
import edu.stanford.nlp.mt.pt.Rule;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.RichTranslation;
import edu.stanford.nlp.mt.util.Sequence;

import edu.stanford.nlp.util.Generics;

/**
 * Language Model-based TrueCasing.
 *
 * This class implements n-gram language model based truecasing, an approach
 * similar to that seen Lita et al 2003's paper tRuEcasIng.
 *
 * @author danielcer
 */
public class LanguageModelTrueCaser {

  private static final int MAX_ACRONYM_LIMIT = 7;

  private final Inferer<IString, String> inferer;

  /**
   * Constructor.
   * 
   * @param lmFilename
   * @throws IOException 
   */
  public LanguageModelTrueCaser(String lmFilename) throws IOException {
    // Read in LM & create LM featurizer
    NGramLanguageModelFeaturizer lmFeaturizer = new NGramLanguageModelFeaturizer(lmFilename, 
        NGramLanguageModelFeaturizer.DEFAULT_FEATURE_NAME);
    List<Featurizer<IString, String>> listFeaturizers = Generics.newLinkedList();
    listFeaturizers.add(lmFeaturizer);
    CombinedFeaturizer<IString, String> combinedFeaturizer = new CombinedFeaturizer<IString, String>(
        listFeaturizers);

    CubePruningDecoder.CubePruningDecoderBuilder<IString, String> infererBuilder = 
        (CubePruningDecoder.CubePruningDecoderBuilder<IString, String>) InfererBuilderFactory.factory(InfererBuilderFactory.CUBE_PRUNING_DECODER);
    infererBuilder.setIncrementalFeaturizer(combinedFeaturizer);
    infererBuilder.setScorer(new UniformScorer<String>());

    // Create truecasing phrase generator
    infererBuilder.setPhraseGenerator(new AllCasePhraseGenerator(
        combinedFeaturizer));
    infererBuilder
    .setSearchHeuristic(new IsolatedPhraseForeignCoverageHeuristic<IString, String>(
        combinedFeaturizer));

    // misc. decoder configuration
    RecombinationFilter<Derivation<IString, String>> recombinationFilter =
        new TranslationNgramRecombinationFilter(listFeaturizers);
    infererBuilder.setRecombinationFilter(recombinationFilter);
    infererBuilder.setMaxDistortion(0);

    // builder decoder
    inferer = infererBuilder.build();    
  }

  /**
   * Apply casing to the input.
   * 
   * @param input
   * @param inputId
   * @return
   */
  public String trueCase(String input, int inputId) {
    Sequence<IString> source = IStrings.tokenize(input);
    RichTranslation<IString, String> translation = inferer.translate(source,
        inputId, null, new UnconstrainedOutputSpace<IString,String>(), null);
    return translation.translation.toString();
  }

  /**
   * Generate casing options.
   * 
   * @author Spence Green
   *
   */
  private static class AllCasePhraseGenerator extends AbstractPhraseGenerator<IString, String> {

    private static final String NAME = "AllCasePhrGen";

    public AllCasePhraseGenerator(
        RuleFeaturizer<IString, String> phraseFeaturizer) {
      super(phraseFeaturizer);
    }

    private static List<String> getCasings(String token) {
      List<String> casings = Generics.newLinkedList();
      // Identity
      casings.add(token);
      if (token.length() == 0) return casings;

      // Add the lowercased version if necessary
      if ( ! token.equals(token.toLowerCase())) {
        casings.add(token.toLowerCase());
      }

      // Add all caps token
      if (token.length() <= LanguageModelTrueCaser.MAX_ACRONYM_LIMIT) {
        casings.add(token.toUpperCase());
      }

      // Add first letter capitalized version of token
      String firstLetter = token.substring(0, 1);
      String rest = token.substring(1, token.length());
      String capToken = firstLetter.toUpperCase() + rest;
      casings.add(capToken);

      return casings;
    }

    @Override
    public String getName() {
      return NAME;
    }

    @Override
    public List<Rule<IString>> query(
        Sequence<IString> sourceSequence) {
      if (sourceSequence.size() != longestSourcePhrase()) {
        throw new RuntimeException("Subsequence length != " + String.valueOf(longestSourcePhrase()));
      }
      List<Rule<IString>> list = Generics.newLinkedList();
      String token = sourceSequence.get(0).toString();
      List<String> casings = getCasings(token);
      for (String casing : casings) {
        Sequence<IString> target = IStrings.tokenize(casing);
        list.add(new Rule<IString>(new float[0], new String[0], target,
            sourceSequence, PhraseAlignment.getPhraseAlignment(PhraseAlignment.PHRASE_ALIGNMENT)));
      }
      return list;
    }

    @Override
    public int longestSourcePhrase() {
      // DO NOT CHANGE THIS!
      return 1;
    }

    @Override
    public int longestTargetPhrase() {
      // DO NOT CHANGE THIS!
      return 1;
    }

    @Override
    public List<String> getFeatureNames() {
      return Generics.newArrayList(1);
    }
  }

  /**
   * 
   * @param args
   * @throws IOException 
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.err.printf("Usage: java %s lm_file uncased_input > cased_output%n",
          LanguageModelTrueCaser.class.getName());
      System.exit(-1);
    }

    LanguageModelTrueCaser trueCaser = new LanguageModelTrueCaser(args[0]);

    // enter main truecasing loop
    LineNumberReader reader = IOTools.getReaderFromFile(args[1]);
    for (String line; (line = reader.readLine()) != null;) {
      final int inputId = reader.getLineNumber() - 1;
      String output = trueCaser.trueCase(line, inputId);
      System.out.println(output);
    }
    reader.close();
  }
}
