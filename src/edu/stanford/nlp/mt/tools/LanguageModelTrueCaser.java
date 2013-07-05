package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.AbstractPhraseGenerator;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.LanguageModel;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.base.Rule;
import edu.stanford.nlp.mt.decoder.h.IsolatedPhraseForeignCoverageHeuristic;
import edu.stanford.nlp.mt.decoder.inferer.Inferer;
import edu.stanford.nlp.mt.decoder.inferer.InfererBuilderFactory;
import edu.stanford.nlp.mt.decoder.inferer.impl.MultiBeamDecoder;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.recomb.TranslationNgramRecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.BeamFactory;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.decoder.util.UniformScorer;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.Featurizer;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.NGramLanguageModelFeaturizer;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.process.TrueCaser;


/**
 * Language Model based TrueCasing
 * 
 * This class implements n-gram language model based truecasing, an approach
 * similar to that seen Lita et al 2003's paper tRuEcasIng.
 * 
 * @author danielcer
 * 
 */

public class LanguageModelTrueCaser implements TrueCaser {

  private static final int BEAM_SIZE = 50;
  
  static final int MAX_ACRONYM_LIMIT = 4;

  private Inferer<IString, String> inferer;

  public static void main(String args[]) throws Exception {
    if (args.length != 1) {
      System.err
          .println("Usage:\n\tjava ... TrueCaser (language model) < uncased_input > cased_output");
      System.exit(-1);
    }

    LanguageModelTrueCaser tc = new LanguageModelTrueCaser();
    tc.init(args[0]);

    // enter main truecasing loop
    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in, "UTF-8"));
    for (String line; (line = reader.readLine()) != null;) {
      String[] tokens = line.split("\\s+");
      int lineNumber = reader.getLineNumber();
      String[] trg = tc.trueCase(tokens, lineNumber);
      System.out.printf("%s \n", StringUtils.join(trg, " "));
    }

    System.exit(0);
  }

  public void init(String lmFilename) {

    MultiBeamDecoder.MultiBeamDecoderBuilder<IString, String> infererBuilder = (MultiBeamDecoder.MultiBeamDecoderBuilder<IString, String>) InfererBuilderFactory
        .factory(InfererBuilderFactory.MULTIBEAM_DECODER);

    // Read in LM & create LM featurizer
    try {
      NGramLanguageModelFeaturizer lmFeaturizer = NGramLanguageModelFeaturizer
          .fromFile(lmFilename, NGramLanguageModelFeaturizer.FEATURE_NAME);
      List<Featurizer<IString, String>> listFeaturizers = Generics.newLinkedList();
      listFeaturizers.add(lmFeaturizer);
      CombinedFeaturizer<IString, String> combinedFeaturizer = new CombinedFeaturizer<IString, String>(
          listFeaturizers);

      infererBuilder.setIncrementalFeaturizer(combinedFeaturizer);
      Scorer<String> scorer = new UniformScorer<String>(false, null);
      infererBuilder.setScorer(scorer);

      // Create truecasing phrase generator
      infererBuilder.setPhraseGenerator(new AllCasePhraseGenerator(
          combinedFeaturizer));
      infererBuilder
          .setSearchHeuristic(new IsolatedPhraseForeignCoverageHeuristic<IString, String>(
              combinedFeaturizer));
      List<LanguageModel<IString>> lgModels = new LinkedList<LanguageModel<IString>>();
      lgModels.add(lmFeaturizer.lm);

      // misc. decoder configuration
      RecombinationFilter<Derivation<IString, String>> recombinationFilter = new TranslationNgramRecombinationFilter<IString, String>(
          lgModels, Integer.MAX_VALUE);
      infererBuilder.setRecombinationFilter(recombinationFilter);
      infererBuilder.setMaxDistortion(0);
      infererBuilder.setBeamCapacity(BEAM_SIZE);
      infererBuilder.setBeamType(BeamFactory.BeamType.sloppybeam);

      // builder decoder
      inferer = infererBuilder.build();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public String[] trueCase(String[] tokens, int id) {

    Sequence<IString> source = new SimpleSequence<IString>(true,
        IStrings.toIStringArray(tokens));
    RichTranslation<IString, String> translation = inferer.translate(source,
        id - 1, null, null);

    // manual fix up(s)
    // capitalize the first letter
    String[] trg = translation.translation.toString().split("\\s+");
    if (trg.length > 0 && trg[0].length() > 0) {
      String firstLetter = trg[0].substring(0, 1);
      String rest = trg[0].substring(1, trg[0].length());
      String capTrg = firstLetter.toUpperCase() + rest;
      trg[0] = capTrg;
    }

    return trg;
  }
}

class AllCasePhraseGenerator extends AbstractPhraseGenerator<IString, String> {

  static final String NAME = "AllCasePhrGen";

  public AllCasePhraseGenerator(
      RuleFeaturizer<IString, String> phraseFeaturizer) {
    super(phraseFeaturizer);
  }

  List<String> caseMapGet(String token) {
    List<String> casings = new LinkedList<String>();
    if (token.length() == 0) return casings;

    // add token as is
    casings.add(token);

    // add all caps token
    if (token.length() <= LanguageModelTrueCaser.MAX_ACRONYM_LIMIT) {
      casings.add(token.toUpperCase());
    }

    // add all lower case version of token
    casings.add(token.toLowerCase());

    // add first letter capitalized version of token
    String firstLetter = token.substring(0, 1);
    String rest = token.substring(1, token.length());
    String capToken = firstLetter.toUpperCase() + rest;
    casings.add(capToken);
    return casings;
  }
  
  public String getName() {
    return NAME;
  }

  public List<Rule<IString>> query(
      Sequence<IString> sequence) {
    if (sequence.size() != 1) {
      throw new RuntimeException("Subsequence length != 1");
    }
    List<Rule<IString>> list = new LinkedList<Rule<IString>>();
    String token = sequence.get(0).toString().toLowerCase();
    List<String> casings = caseMapGet(token);
    if (casings == null) {
      casings = new LinkedList<String>();
      casings.add(sequence.get(0).toString());
    }
    RawSequence<IString> rawSource = new RawSequence<IString>(sequence);

    for (String casing : casings) {
      IString[] trgArr = IStrings.toIStringArray(new String[] { casing });
      RawSequence<IString> trg = new RawSequence<IString>(trgArr);
      list.add(new Rule<IString>(new float[0], new String[0], trg,
          rawSource, PhraseAlignment.getPhraseAlignment(PhraseAlignment.PHRASE_ALIGNMENT)));
    }
    return list;
  }

  public int longestSourcePhrase() {
    return 1;
  }

  public void setCurrentSequence(Sequence<IString> foreign,
      List<Sequence<IString>> tranList) {
    // no op
  }
}
