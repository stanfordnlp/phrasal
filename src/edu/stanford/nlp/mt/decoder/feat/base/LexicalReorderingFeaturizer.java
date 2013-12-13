package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.LexicalReorderingTable;
import edu.stanford.nlp.mt.base.Rule;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.SourceClassMap;
import edu.stanford.nlp.mt.base.TargetClassMap;
import edu.stanford.nlp.mt.base.TokenUtils;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.util.Generics;

/**
 * Generative and discriminative lexicalized reordering models.
 * 
 * @author danielcer
 * @author Spence Green
 */
public class LexicalReorderingFeaturizer extends
    DerivationFeaturizer<IString, String> {

  private static final int LEXICAL_FEATURE_CUTOFF = 50;
  private static final boolean DETAILED_DEBUG = false;
  private static final Sequence<IString> INITIAL_PHRASE = new SimpleSequence<IString>(
      TokenUtils.START_TOKEN);
  
  private static final String DISCRIMINATIVE_PREFIX = "Disc";
  private static final String FEATURE_PREFIX = "LexR:";
  public final String[] featureTags;
  public final LexicalReorderingTable mlrt;
  private List<LexicalReorderingTable.ReorderingTypes> discriminativeSet;
  private final boolean useAlignmentConstellations;
  private boolean useClasses;
  private int countFeatureIndex = -1;
  private SourceClassMap sourceMap;
  private TargetClassMap targetMap;

  /**
   * Constructor for discriminative lexicalized reordering.
   */
  public LexicalReorderingFeaturizer() {
    // by default include everything
    discriminativeSet = Generics.newArrayList(Arrays.asList(LexicalReorderingTable.ReorderingTypes.values()));
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
  public LexicalReorderingFeaturizer(String...args) {
    discriminativeSet = Generics.newArrayList(Arrays.asList(LexicalReorderingTable.ReorderingTypes.values()));
    boolean useAlignmentConstellations = false;
    for (String argument : args) {
      // Condition the classes on constellations
      if (argument.equals("conditionOnConstellations")) {
        useAlignmentConstellations = true;
        System.err.printf("using constellations%n");
      
      } else if (argument.startsWith("classes")) {
        String[] toks = argument.trim().split(":");
        assert toks.length == 2;
        String[] typeStrings = toks[1].split("-");
        discriminativeSet = Generics.newArrayList();
        for (String type : typeStrings) {
          discriminativeSet.add(LexicalReorderingTable.ReorderingTypes.valueOf(type));
        }
      } else if (argument.equals("useClasses")) {
        useClasses = true;
        sourceMap = SourceClassMap.getInstance();
        targetMap = TargetClassMap.getInstance();
        
      } else if (argument.startsWith("countFeatureIndex")) {
        String[] toks = argument.trim().split(":");
        assert toks.length == 2;
        countFeatureIndex = Integer.parseInt(toks[1]);
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
    if (discriminativeSet != null && aboveThreshold(f.rule)) {
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
    
    // Create the state
    int rightEdge = lastOptionRightEdge(f.derivation);
    int leftEdge = lastOptionLeftEdge(f.derivation);
    f.setState(this, new MSDState(leftEdge, rightEdge, f.derivation.sourceCoverage));

    return values;
  }

  private boolean aboveThreshold(ConcreteRule<IString, String> rule) {
    if (countFeatureIndex < 0 || useClasses) return true;
    if (countFeatureIndex >= rule.abstractRule.scores.length) {
      // Generated by unknown word model...don't know count.
      return false;
    }
    double count = Math.exp(rule.abstractRule.scores[countFeatureIndex]);
    return count > LEXICAL_FEATURE_CUTOFF;
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
        String tokenClass = Sentence.listToString(sourceMap.get(token), true, "-");
        sb.append(tokenClass);
      }
      sb.append(">");
      boolean seenFirst = false;
      for (IString token : rule.target) {
        if (seenFirst) sb.append("-");
        String tokenClass = Sentence.listToString(targetMap.get(token), true, "-");
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
  
  private static int lastOptionLeftEdge(Derivation<IString, String> hyp) {
    if (hyp.rule == null)
      return -1;
    return hyp.rule.sourcePosition - 1;
  }

  private static int lastOptionRightEdge(Derivation<IString, String> hyp) {
    if (hyp.rule == null)
      return 0;
    return hyp.rule.sourceCoverage.length();
  }
  
  private static class MSDState extends FeaturizerState {

    private final int leftEdge;
    private final int rightEdge;
    private final CoverageSet sourceCoverage;

    public MSDState(int leftEdge, int rightEdge, CoverageSet sourceCoverage) {
      this.leftEdge = leftEdge;
      this.rightEdge = rightEdge;
      this.sourceCoverage = sourceCoverage;
    }
    
    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      } else if ( ! (other instanceof MSDState)) {
        return false;
      } else {
        MSDState o = (MSDState) other;
        return equals(this, o);
      }
    }
    
    private boolean equals(MSDState stateA, MSDState stateB) {
      if (stateA.rightEdge != stateB.rightEdge)
        // same as LinearDistortionRecombinationFilter:
        return false;
      int leftA = stateA.leftEdge;
      int leftB = stateB.leftEdge;
      if (leftA == leftB)
        return true;

      // Now hypA and hypB may look like this:
      // hypA: y y . . . . x x . . . z z
      // hypB: y y y . . x x x . . . z z
      // where y,z stand for coverage set of previous options (the number of y and
      // z may be zero),
      // and x stands for coverage set of the current option.
      // If the next option (represented with n's) is generated to the right of
      // x's,
      // hypA and hypB will always receive the same orientation, i.e.:
      // (A) Both monotone:
      // hypA: y y . . . . x x n n . z z
      // hypB: y y y . . x x x n n . z z
      // (B) Both discontinuous:
      // hypA: y y . . . . x x . n n z z
      // hypB: y y y . . x x x . n n z z
      // If the next option is generated to the left, we have two cases:
      // (C) Both discontinuous:
      // hypA: y y . n . . x x . . . z z
      // hypB: y y y n . x x x . . . z z
      // (D) One discontinuous, one swap:
      // hypA: y y . . n . x x . . . z z
      // hypB: y y y . n x x x . . . z z
      // So we only need to worry about case (D). The function should return false
      // if case (D) is possible. The condition that makes (D) impossible is:
      // the number of words between the last y and the first x is zero for either
      // hypA or hypB. In this condition is true, (D) is impossible, thus
      // the next option will always receive the same orientation (no matter where
      // it appears), thus hypA and hypB are combinable, thus return true.

      if (leftA < 0 || leftB < 0)
        // Nothing to the left of either hypA or hypB, so (D) is impossible:
        return true;

      if (!stateA.sourceCoverage.get(leftA) && !stateB.sourceCoverage.get(leftA)) {
        // (D) is possible as shown here:
        // hypA: y y . . n x x x . . . z z
        // hypB: y y y . n . x x . . . z z
        return false;
      }

      if (!stateA.sourceCoverage.get(leftB) && !stateB.sourceCoverage.get(leftB)) {
        // (D) is possible as shown here:
        // hypA: y y . . n . x x . . . z z
        // hypB: y y y . n x x x . . . z z
        return false;
      }
      return true;
    }

    @Override
    public int hashCode() {
      return rightEdge;
    }
  }
}
