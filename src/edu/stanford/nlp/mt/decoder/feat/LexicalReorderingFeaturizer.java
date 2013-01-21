package edu.stanford.nlp.mt.decoder.feat;

import java.util.*;

import edu.stanford.nlp.mt.base.ARPALanguageModel;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.LexicalReorderingTable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.base.IString;

/**
 * 
 * @author danielcer
 */
public class LexicalReorderingFeaturizer implements
    MSDFeaturizer<IString, String> {

  static final String FEATURE_PREFIX = "LexR:";
  public final String[] featureTags;
  public final LexicalReorderingTable mlrt;
  final boolean DETAILED_DEBUG = false;
  final LexicalReorderingTable.ReorderingTypes[] discriminativeSet;
  public static final Sequence<IString> INITIAL_PHRASE = new SimpleSequence<IString>(
      ARPALanguageModel.START_TOKEN);
  final boolean useAlignmentConstellations;

  /**
   * Discriminative Lexical Reordering - using all reordering types
   * 
   */
  public LexicalReorderingFeaturizer() {
    // by default include everything
    discriminativeSet = LexicalReorderingTable.ReorderingTypes.values();
    mlrt = null;
    featureTags = null;
    useAlignmentConstellations = false;
  }

  /**
   * Discriminative lexical reordering - using selected reordering types
   * 
   */
  public LexicalReorderingFeaturizer(String... strTypes) {
    discriminativeSet = LexicalReorderingTable.ReorderingTypes.values();
    boolean useAlignmentConstellations = false;
    for (String strType : strTypes) {
      if (strType.equals("conditionOnConstellations")) {
        useAlignmentConstellations = true;
        System.err.printf("using constillations\n");
      }
    }
    this.useAlignmentConstellations = useAlignmentConstellations;
    mlrt = null;
    featureTags = null;
  }

  public LexicalReorderingFeaturizer(LexicalReorderingTable mlrt) {
    this.mlrt = mlrt;
    useAlignmentConstellations = false;
    featureTags = new String[mlrt.positionalMapping.length];
    for (int i = 0; i < mlrt.positionalMapping.length; i++)
      featureTags[i] = String.format("%s:%s", FEATURE_PREFIX,
          mlrt.positionalMapping[i]);
    discriminativeSet = null;
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {

    List<FeatureValue<String>> values = new LinkedList<FeatureValue<String>>();

    boolean monotone = f.linearDistortion == 0;
    boolean swap = (f.prior != null && f.foreignPosition
        + f.foreignPhrase.size() == f.prior.foreignPosition);

    if (discriminativeSet != null) {
      for (LexicalReorderingTable.ReorderingTypes mrt : discriminativeSet) {
        if (!featureFunction(monotone, swap, mrt))
          continue;
        if (usePrior(mrt)) {
          String condRep; // = null;
          if (!useAlignmentConstellations) {
            Sequence<IString> priorForeignPhrase = (f.prior != null ? f.prior.foreignPhrase
                : INITIAL_PHRASE);
            Sequence<IString> priorTranslatedPhrase = (f.prior != null ? f.prior.translatedPhrase
                : INITIAL_PHRASE);
            condRep = priorForeignPhrase.toString("_") + "=>"
                + priorTranslatedPhrase.toString("_");
          } else {
            IString priorAlignConst = (f.prior != null ? f.prior.option.abstractOption.alignment
                .toIString() : INITIAL_PHRASE.get(0));
            condRep = priorAlignConst.toString();
          }
          values.add(new FeatureValue<String>(FEATURE_PREFIX + ":" + mrt + ":"
              + condRep, 1.0, true));
        } else {
          String condRep; // = null;
          if (!useAlignmentConstellations) {
            condRep = f.foreignPhrase.toString("_") + "=>"
                + f.translatedPhrase.toString("_");
          } else {
            condRep = f.option.abstractOption.alignment.toString();
          }
          values.add(new FeatureValue<String>(FEATURE_PREFIX + ":" + mrt + ":"
              + condRep, 1.0, true));
        }
      }
    }

    if (mlrt != null) {
      double[] scores = mlrt.getReorderingScores(f.foreignPhrase,
          f.translatedPhrase);
      double[] priorScores = (f.prior == null ? null : mlrt
          .getReorderingScores(f.prior.foreignPhrase, f.prior.translatedPhrase));

      if (DETAILED_DEBUG) {
        System.err.printf("%s(%d) => %s(%d)\n", f.foreignPhrase,
            f.foreignPosition, f.translatedPhrase, f.translationPosition);
        if (f.prior == null)
          System.err.printf("Prior <s> => <s>\n");
        else
          System.err.printf("Prior %s(%d) => %s(%d)\n", f.foreignPhrase,
              f.foreignPosition, f.translatedPhrase, f.translationPosition);
        System.err.printf("Monotone: %s\nSwap: %s\n", monotone, swap);
        System.err.printf("PriorScores: %s\nScores: %s\n",
            (priorScores == null ? "null" : Arrays.toString(priorScores)),
            (scores == null ? "null" : Arrays.toString(scores)));
      }

      for (int i = 0; i < mlrt.positionalMapping.length; i++) {
        boolean ff = featureFunction(monotone, swap, mlrt.positionalMapping[i]);
        if (!usePrior(mlrt.positionalMapping[i])) {
          if (scores != null && ff)
            values.add(new FeatureValue<String>(featureTags[i], scores[i]));
        } else {
          if (priorScores != null && ff)
            values
                .add(new FeatureValue<String>(featureTags[i], priorScores[i]));
        }
      }
    }
    if (DETAILED_DEBUG) {
      System.err.printf("Feature values:\n");
      for (FeatureValue<String> value : values)
        System.err.printf("\t%s: %f\n", value.name, value.value);
    }

    return values;
  }

  private boolean usePrior(LexicalReorderingTable.ReorderingTypes type) {
    switch (type) {
    case monotoneWithNext:
    case swapWithNext:
    case discontinuousWithNext:
    case nonMonotoneWithNext:
      return true;
    default:
      break;
    }
    return false;
  }

  private boolean featureFunction(boolean monotone, boolean swap,
      LexicalReorderingTable.ReorderingTypes type) {
    switch (type) {
    case monotoneWithPrevious:
    case monotoneWithNext:
      return monotone;
    case swapWithPrevious:
    case swapWithNext:
      return swap;
    case discontinuousWithPrevious:
    case discontinuousWithNext:
      return !(monotone || swap);
    case nonMonotoneWithPrevious:
    case nonMonotoneWithNext:
      return !monotone;
    }
    return false;
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options,
      Sequence<IString> foreign) {
  }

  public void reset() {
  }

}
