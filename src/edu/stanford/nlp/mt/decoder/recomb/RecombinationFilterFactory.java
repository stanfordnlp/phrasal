package edu.stanford.nlp.mt.decoder.recomb;

import java.util.List;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.Featurizer;
import edu.stanford.nlp.mt.decoder.feat.base.HierarchicalReorderingFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.LexicalReorderingFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.util.Generics;

/**
 * 
 * @author danielcer
 * 
 */
public final class RecombinationFilterFactory {
  public static final String CLASSIC_RECOMBINATION = "classic";
  public static final String DTU_RECOMBINATION = "dtu";
  public static final String EXACT_RECOMBINATION = "exact";
  
  private RecombinationFilterFactory() {}

  /**
   * Create a recombination filter.
   * 
   * @param recombinationMode one of the modes specified in <code>RecombinationFilterFactory</code>.
   * @param featurizers the list of featurizers to consider
   * @return
   */
  public static RecombinationFilter<Derivation<IString, String>> factory(
      String recombinationMode, List<Featurizer<IString, String>> featurizers) {

    boolean msdRecombination = false;
    for (Featurizer<IString, String> featurizer : featurizers) {
      if (featurizer instanceof HierarchicalReorderingFeaturizer ||
          featurizer instanceof LexicalReorderingFeaturizer)
        msdRecombination = true;
    }

    if (recombinationMode.equals(CLASSIC_RECOMBINATION)) {
      List<RecombinationFilter<Derivation<IString, String>>> filters = Generics.newLinkedList();
      // maintain uniqueness of hypotheses that will result in different linear
      // distortion scores when extended
      // with future translation options.
      filters.add(new LinearDistortionRecombinationFilter<IString, String>(featurizers));

      // maintain uniqueness of hypotheses that differ by the last N-tokens,
      // this being relevant to lg model scoring
      filters.add(new TranslationNgramRecombinationFilter(featurizers));

      // maintain uniqueness of hypotheses that differ in terms of foreign
      // sequence coverage
      filters.add(new ForeignCoverageRecombinationFilter<IString, String>());

      if (msdRecombination) {
        filters.add(new MSDRecombinationFilter(featurizers));
      }
      return new CombinedRecombinationFilter<Derivation<IString, String>>(
          filters);

    } else if (recombinationMode.equals(DTU_RECOMBINATION)) {
      List<RecombinationFilter<Derivation<IString, String>>> filters = Generics.newLinkedList();
      filters.add(new LinearDistortionRecombinationFilter<IString, String>(featurizers));
      filters.add(new TranslationNgramRecombinationFilter(featurizers));
      filters.add(new ForeignCoverageRecombinationFilter<IString, String>());
      filters.add(new DTURecombinationFilter<IString, String>());
      if (msdRecombination) {
        filters.add(new MSDRecombinationFilter(featurizers));
      }
      return new CombinedRecombinationFilter<Derivation<IString, String>>(
          filters);
    
    } else if (recombinationMode.equals(EXACT_RECOMBINATION)) {
      return new ExactRecombinationFilter<Derivation<IString, String>>(featurizers);
    }
    
    throw new RuntimeException("Unrecognized recombination filter: " + recombinationMode);
  }
}
