package edu.stanford.nlp.mt.decoder.feat;

import java.util.*;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.ARPALanguageModel;
import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.LexicalReorderingTable;
import edu.stanford.nlp.mt.base.Rule;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.SourceClassMap;
import edu.stanford.nlp.mt.base.TargetClassMap;
import edu.stanford.nlp.util.Generics;

/**
 * 
 * @author danielcer
 */
public class LexicalReorderingFeaturizer2 implements
    NeedsReorderingRecombination<IString, String> {

  public static final String DISCRIMINATIVE_PREFIX = "Disc";
  static final String FEATURE_PREFIX = "LexR:";
  public final String[] featureTags;
  public final LexicalReorderingTable mlrt;
  final boolean DETAILED_DEBUG = false;
  private List<LexicalReorderingTable.ReorderingTypes> discriminativeSet;
  public static final Sequence<IString> INITIAL_PHRASE = new SimpleSequence<IString>(
      ARPALanguageModel.START_TOKEN);
  final boolean useAlignmentConstellations;
  private boolean useClasses;

  /**
   * Constructor for discriminative lexicalized reordering.
   */
  public LexicalReorderingFeaturizer2() {
    // by default include everything
    discriminativeSet = new ArrayList<LexicalReorderingTable.ReorderingTypes>(Arrays.asList(LexicalReorderingTable.ReorderingTypes.values()));
    mlrt = null;
    featureTags = null;
    useAlignmentConstellations = false;
    useClasses = false;
  }

  /**
   * Constructor for discriminative lexicalized reordering.
   * 
   * @param args
   */
  public LexicalReorderingFeaturizer2(String...args) {
    discriminativeSet = new ArrayList<LexicalReorderingTable.ReorderingTypes>(Arrays.asList(LexicalReorderingTable.ReorderingTypes.values()));
    boolean useAlignmentConstellations = false;
    for (String argument : args) {
      
      // Condition the classes on constellations
      if (argument.equals("conditionOnConstellations")) {
        useAlignmentConstellations = true;
        System.err.printf("using constellations\n");
      
      } else if (argument.startsWith("classes")) {
        String[] toks = argument.trim().split(":");
        assert toks.length == 2;
        String[] typeStrings = toks[1].split("-");
        discriminativeSet = new ArrayList<LexicalReorderingTable.ReorderingTypes>();
        for (String type : typeStrings) {
          discriminativeSet.add(LexicalReorderingTable.ReorderingTypes.valueOf(type));
        }
      } else if (argument.equals("useClasses")) {
        useClasses = true;
      }
    }
    if (useClasses) {
      if (! SourceClassMap.isLoaded()) {
        throw new RuntimeException("You must enable the " + Phrasal.SOURCE_CLASS_MAP + " decoder option");
      }
      if (! TargetClassMap.isLoaded()) {
        throw new RuntimeException("You must enable the " + Phrasal.TARGET_CLASS_MAP + " decoder option");
      }
    }
    this.useAlignmentConstellations = useAlignmentConstellations;
    mlrt = null;
    featureTags = null;
  }

  /**
   * Constructor for the generative model.
   * 
   * @param mlrt
   */
  public LexicalReorderingFeaturizer2(LexicalReorderingTable mlrt) {
    this.mlrt = mlrt;
    useAlignmentConstellations = false;
    featureTags = new String[mlrt.positionalMapping.length];
    for (int i = 0; i < mlrt.positionalMapping.length; i++)
      featureTags[i] = String.format("%s:%s", FEATURE_PREFIX,
          mlrt.positionalMapping[i]);
    discriminativeSet = null;
  }
  
  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString,String>> options, Sequence<IString> foreign) {
  }

  @Override
  public List<FeatureValue<String>> featurize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> values = Generics.newLinkedList();

    boolean monotone = f.linearDistortion == 0;
    boolean swap = (f.prior != null && f.sourcePosition
        + f.sourcePhrase.size() == f.prior.sourcePosition);

    // Discriminative model
    if (discriminativeSet != null) {
      for (LexicalReorderingTable.ReorderingTypes mrt : discriminativeSet) {
        if (!featureFunction(monotone, swap, mrt))
          continue;
        if (usePrior(mrt)) {
          String ruleRep;
          if (useAlignmentConstellations) {
            IString priorAlignConst = (f.prior != null ? f.prior.rule.abstractRule.alignment
                .toIString() : INITIAL_PHRASE.get(0));
            ruleRep = priorAlignConst.toString();
          
          } else {
            ruleRep = getDiscriminativeRepresentation(f.prior);
          }
          values.add(new FeatureValue<String>(DISCRIMINATIVE_PREFIX + FEATURE_PREFIX + ":" + mrt + ":"
              + ruleRep, 1.0));
        
        } else {
          String ruleRep = useAlignmentConstellations ? 
              f.rule.abstractRule.alignment.toString() :
                getDiscriminativeRepresentation(f);
          values.add(new FeatureValue<String>(DISCRIMINATIVE_PREFIX + FEATURE_PREFIX + ":" + mrt + ":"
              + ruleRep, 1.0));
        }
      }
    }

    // Generative model
    if (mlrt != null) {
      float[] scores = mlrt
          .getReorderingScores(f.derivation.rule.abstractRule);
      float[] priorScores = (f.prior == null ? null : mlrt
          .getReorderingScores(f.prior.derivation.rule.abstractRule));

      if (DETAILED_DEBUG) {
        System.err.printf("%s(%d) => %s(%d)\n", f.sourcePhrase,
            f.sourcePosition, f.targetPhrase, f.targetPosition);
        if (f.prior == null)
          System.err.printf("Prior <s> => <s>\n");
        else
          System.err.printf("Prior %s(%d) => %s(%d)\n", f.sourcePhrase,
              f.sourcePosition, f.targetPhrase, f.targetPosition);
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

  /**
   * Return a representation of the rule contained by the <code>Featurizable</code>.
   * 
   * @param f
   * @return
   */
  private String getDiscriminativeRepresentation(Featurizable<IString, String> f) {
    String rep = "";
    if (f == null) {
      rep = String.format("%s>%s", INITIAL_PHRASE, INITIAL_PHRASE);
    
    } else if (useClasses) {
      // Class-based
      Rule<IString> rule = f.rule.abstractRule;
      StringBuilder sb = new StringBuilder();
      for (IString token : rule.source) {
        if (sb.length() > 0) sb.append("-");
        String tokenClass = SourceClassMap.get(token).toString();
        sb.append(tokenClass);
      }
      sb.append(">");
      boolean seenFirst = false;
      for (IString token : rule.target) {
        if (seenFirst) sb.append("-");
        String tokenClass = TargetClassMap.get(token).toString();
        sb.append(tokenClass);
        seenFirst = true;
      }
      rep = sb.toString();
      
    } else {
      // Lexicalized
      String sourcePhrase = f.sourcePhrase.toString("-");
      String targetPhrase = f.targetPhrase.toString("-");
      rep = String.format("%s>%s", sourcePhrase, targetPhrase);
    }
    return rep;
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
}
