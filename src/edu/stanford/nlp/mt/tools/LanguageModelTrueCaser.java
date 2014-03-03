package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.List;

import edu.stanford.nlp.mt.base.AbstractPhraseGenerator;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.base.Rule;
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

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;


/**
 * Language Model-based TrueCasing.
 *
 * This class implements n-gram language model based truecasing, an approach
 * similar to that seen Lita et al 2003's paper tRuEcasIng.
 *
 * @author danielcer
 */
public class LanguageModelTrueCaser {

  static final int MAX_ACRONYM_LIMIT = 7;

  private Inferer<IString, String> inferer;

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err
      .println("Usage:\n\tjava ... TrueCaser lm_file uncased_input > cased_output");
      System.exit(-1);
    }

    LanguageModelTrueCaser trueCaser = new LanguageModelTrueCaser();
    trueCaser.init(args[0]);

    // enter main truecasing loop
    LineNumberReader reader = IOTools.getReaderFromFile(args[1]);
    for (String line; (line = reader.readLine()) != null;) {
      String[] tokens = line.split("\\s+");
      int inputId = reader.getLineNumber() - 1;
      String[] trg = trueCaser.trueCase(tokens, inputId);
      System.out.println(StringUtils.join(trg, " "));
    }
    reader.close();
  }

  public void init(String lmFilename) {

    CubePruningDecoder.CubePruningDecoderBuilder<IString, String> infererBuilder = (CubePruningDecoder.CubePruningDecoderBuilder<IString, String>) InfererBuilderFactory
        .factory(InfererBuilderFactory.CUBE_PRUNING_DECODER);

    // Read in LM & create LM featurizer
    try {
      NGramLanguageModelFeaturizer lmFeaturizer = new NGramLanguageModelFeaturizer
          (lmFilename, NGramLanguageModelFeaturizer.DEFAULT_FEATURE_NAME);
      List<Featurizer<IString, String>> listFeaturizers = Generics.newLinkedList();
      listFeaturizers.add(lmFeaturizer);
      CombinedFeaturizer<IString, String> combinedFeaturizer = new CombinedFeaturizer<IString, String>(
          listFeaturizers);

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
//      infererBuilder.setBeamCapacity(BEAM_SIZE);
//      infererBuilder.setBeamType(BeamFactory.BeamType.sloppybeam);

      // builder decoder
      inferer = infererBuilder.build();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public String[] trueCase(String[] tokens, int inputId) {
    Sequence<IString> source = new SimpleSequence<IString>(true,
        IStrings.toIStringArray(tokens));
    RichTranslation<IString, String> translation = inferer.translate(source,
        inputId, null, new UnconstrainedOutputSpace<IString,String>(), null);

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
  
  private static class IdentityRecombinationFilter implements RecombinationFilter<Derivation<IString, String>> {

    @Override
    public boolean combinable(Derivation<IString, String> hypA,
        Derivation<IString, String> hypB) {
      if (hypA.featurizable == null && hypB.featurizable == null) {
        // null hypothesis
        return true;
      } else if (hypA.featurizable == null || hypB.featurizable == null) {
        // one or the other is the null hypothesis
        return false;
      }
      return hypA.targetSequence.equals(hypB.targetSequence);
    }

    @Override
    public long recombinationHashCode(Derivation<IString, String> hyp) {
      return hyp.targetSequence.hashCode();
    }
    
    @Override
    public Object clone() throws CloneNotSupportedException {
      return super.clone();
    }
  }
  
  private static class AllCasePhraseGenerator extends AbstractPhraseGenerator<IString, String> {

    private static final String NAME = "AllCasePhrGen";

    public AllCasePhraseGenerator(
        RuleFeaturizer<IString, String> phraseFeaturizer) {
      super(phraseFeaturizer);
    }

    private static List<String> getCasings(String token) {
      List<String> casings = Generics.newLinkedList();
      casings.add(token);
      if (token.length() == 0) return casings;
      
      // Add the lowercased version if necessary
      if ( ! token.equals(token.toLowerCase())) {
        casings.add(token.toLowerCase());
      }
      
      // add all caps token
      if (token.length() <= LanguageModelTrueCaser.MAX_ACRONYM_LIMIT) {
        casings.add(token.toUpperCase());
      }

      // add first letter capitalized version of token
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
        Sequence<IString> sequence) {
      if (sequence.size() != 1) {
        throw new RuntimeException("Subsequence length != 1");
      }
      List<Rule<IString>> list = Generics.newLinkedList();
      String token = sequence.get(0).toString().toLowerCase();
      List<String> casings = getCasings(token);
      for (String casing : casings) {
        Sequence<IString> target = IStrings.tokenize(casing);
        list.add(new Rule<IString>(new float[0], new String[0], target,
            sequence, PhraseAlignment.getPhraseAlignment(PhraseAlignment.PHRASE_ALIGNMENT)));
      }
      return list;
    }

    @Override
    public int longestSourcePhrase() {
      return 1;
    }

    @Override
    public int longestTargetPhrase() {
      return 1;
    }

    @Override
    public List<String> getFeatureNames() {
      return Generics.newArrayList(1);
    }
  }
}
