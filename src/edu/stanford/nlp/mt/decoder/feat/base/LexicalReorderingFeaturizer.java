package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.LexicalReorderingTable;
import edu.stanford.nlp.mt.tm.LexicalReorderingTable.ReorderingTypes;
import edu.stanford.nlp.mt.tm.Rule;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.SourceClassMap;
import edu.stanford.nlp.mt.util.TargetClassMap;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Generative and discriminative lexicalized reordering models.
 * 
 * @author danielcer
 * @author Spence Green
 */
public class LexicalReorderingFeaturizer extends
    DerivationFeaturizer<IString, String> {

  private static final boolean DETAILED_DEBUG = false;
  
  private static final String DISCRIMINATIVE_PREFIX = "Disc";
  public static final String FEATURE_PREFIX = "LexR";
  public final String[] featureTags;
  public final LexicalReorderingTable mlrt;
  private List<LexicalReorderingTable.ReorderingTypes> discriminativeSet;
  private final boolean useAlignmentConstellations;
  private boolean useClasses;
  private final int countFeatureIndex;
  private final int lexicalCutoff;
  private SourceClassMap sourceMap;
  private TargetClassMap targetMap;

  /**
   * Dynamic translation model reordering features.
   */
  private final boolean dynamic;
  private final boolean dynamicDiscrim;
  
  /**
   * Constructor for discriminative lexicalized reordering.
   */
  public LexicalReorderingFeaturizer() {
    // by default include everything
    this.discriminativeSet = new ArrayList<>(Arrays.asList(LexicalReorderingTable.ReorderingTypes.values()));
    this.mlrt = null;
    this.featureTags = null;
    this.useAlignmentConstellations = false;
    this.useClasses = false;
    this.countFeatureIndex = -1;
    this.lexicalCutoff = 0;
    this.dynamic = false;
    this.dynamicDiscrim = false;
  }

  /**
   * Constructor for reflection loading discriminative lexicalized reordering.
   * 
   * @param args
   */
  public LexicalReorderingFeaturizer(String...args) {
    Properties options = FeatureUtils.argsToProperties(args);
    this.dynamic = PropertiesUtils.getBool(options, "dynamic", false);
    this.dynamicDiscrim = PropertiesUtils.getBool(options, "dynamicDiscrim", false);
    if (dynamic) {
      this.discriminativeSet = null;
      this.mlrt = null;
      this.featureTags = Arrays.stream(LexicalReorderingTable.msdBidirectionalPositionMapping).map(m -> 
      String.format("%s:%s", FEATURE_PREFIX, m)).toArray(String[]::new);
      this.useAlignmentConstellations = false;
      this.useClasses = false;
      this.countFeatureIndex = -1;
      this.lexicalCutoff = 0;

    } else {
      this.discriminativeSet = new ArrayList<>(Arrays.asList(LexicalReorderingTable.ReorderingTypes.values()));
      this.useAlignmentConstellations = options.containsKey("conditionOnConstellations");
      this.countFeatureIndex = PropertiesUtils.getInt(options, "countFeatureIndex", -1);
      // Which reordering classes to extract
      if (options.containsKey("classes")) {
        String[] typeStrings = options.getProperty("classes").split("-");
        discriminativeSet = new ArrayList<>();
        for (String type : typeStrings) {
          discriminativeSet.add(LexicalReorderingTable.ReorderingTypes.valueOf(type));
        }
      }
      // Use class-based feature representations
      this.useClasses = options.containsKey("useClasses");
      if (useClasses) {
        sourceMap = SourceClassMap.getInstance();
        targetMap = TargetClassMap.getInstance();
      }
      this.mlrt = null;
      this.featureTags = null;
      this.lexicalCutoff = PropertiesUtils.getInt(options, "lexicalCutoff", 0);
    }
  }

  /**
   * Constructor for the generative model.
   * 
   * @param mlrt
   */
  public LexicalReorderingFeaturizer(LexicalReorderingTable mlrt) {
    this.mlrt = mlrt;
    this.dynamic = false;
    this.dynamicDiscrim = false;
    this.useAlignmentConstellations = false;
    this.featureTags = new String[mlrt.positionalMapping.length];
    for (int i = 0; i < mlrt.positionalMapping.length; i++) {
      featureTags[i] = String.format("%s:%s", FEATURE_PREFIX,
          mlrt.positionalMapping[i]);
    }
    this.discriminativeSet = null;
    this.countFeatureIndex = -1;
    this.lexicalCutoff = 0;
  }
   
  @Override
  public void initialize(int sourceInputId,
      Sequence<IString> foreign) {
  }

  @Override
  public List<FeatureValue<String>> featurize(
      Featurizable<IString, String> f) {
    final List<FeatureValue<String>> features = new LinkedList<>();
    final boolean monotone = f.linearDistortion == 0;
    final boolean swap = (f.prior != null && f.sourcePosition
        + f.sourcePhrase.size() == f.prior.sourcePosition);

    // Discriminative model
    if (discriminativeSet != null && aboveThreshold(f.rule)) {
      for (LexicalReorderingTable.ReorderingTypes mrt : discriminativeSet) {
        if (!featureFunction(monotone, swap, mrt))
          continue;
        if (usePrior(mrt)) {
          String ruleRep;
          if (useAlignmentConstellations) {
            String priorAlignConst = (f.prior != null ? f.prior.rule.abstractRule.alignment
                .toString() : TokenUtils.START_TOKEN.toString());
            ruleRep = priorAlignConst;
          
          } else {
            ruleRep = getDiscriminativeRepresentation(f.prior);
          }
          String featureString = DISCRIMINATIVE_PREFIX + FEATURE_PREFIX + ":" + mrt + ":"
              + ruleRep;
          features.add(new FeatureValue<String>(featureString, 1.0, true));
        
        } else {
          String ruleRep = useAlignmentConstellations ? 
              f.rule.abstractRule.alignment.toString() :
                getDiscriminativeRepresentation(f);
          String featureString = DISCRIMINATIVE_PREFIX + FEATURE_PREFIX + ":" + mrt + ":"
              + ruleRep;
          features.add(new FeatureValue<String>(featureString, 1.0, true));
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
            features.add(new FeatureValue<String>(featureTags[i], scores[i], true));
        } else {
          if (priorScores != null && ff)
            features
                .add(new FeatureValue<String>(featureTags[i], priorScores[i], true));
        }
      }
    }
    
    if (dynamic) {
      float[] scores = f.rule.abstractRule.reoderingScores;
      float[] priorScores = f.prior == null ? null : f.prior.rule.abstractRule.reoderingScores;
      for (int i = 0; i < LexicalReorderingTable.msdBidirectionalPositionMapping.length; ++i) {
        ReorderingTypes type = LexicalReorderingTable.msdBidirectionalPositionMapping[i];
        boolean ff = featureFunction(monotone, swap, type);
        if (usePrior(type)) {
          // Forward scores
          assert i >= 3;
          if (priorScores != null && ff) {
            features.add(new FeatureValue<String>(featureTags[i], priorScores[i], true));
            if (dynamicDiscrim) {
              features.add(new FeatureValue<String>(
                  String.format("%s%s:%s-%s", DISCRIMINATIVE_PREFIX, FEATURE_PREFIX,
                      f.prior.rule.abstractRule.forwardOrientation.toString(),
                      type.toString()), 1.0));
            }
          }
        
        } else {
          // Backward scores
          assert i < 3;
          if (scores != null && ff) {
            features.add(new FeatureValue<String>(featureTags[i], scores[i], true));
            if (dynamicDiscrim) {
              features.add(new FeatureValue<String>(
                  String.format("%s%s:%s-%s", DISCRIMINATIVE_PREFIX, FEATURE_PREFIX,
                      f.rule.abstractRule.forwardOrientation.toString(),
                      type.toString()), 1.0));
            }
          }
        }
      }
    }
    
    if (DETAILED_DEBUG) {
      System.err.printf("Feature values:\n");
      for (FeatureValue<String> value : features)
        System.err.printf("\t%s: %f\n", value.name, value.value);
    }
    
    // Create the state
    int rightEdge = lastOptionRightEdge(f.derivation);
    int leftEdge = lastOptionLeftEdge(f.derivation);
    f.setState(this, new MSDState(leftEdge, rightEdge, f.derivation.sourceCoverage));

    return features;
  }

  private boolean aboveThreshold(ConcreteRule<IString, String> rule) {
    if (useClasses || countFeatureIndex < 0 || lexicalCutoff <= 0) return true;
    if (countFeatureIndex >= rule.abstractRule.scores.length) {
      // Generated by unknown word model...don't know count.
      return false;
    }
    double count = Math.exp(rule.abstractRule.scores[countFeatureIndex]);
    return count > lexicalCutoff;
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
      rep = String.format("%s>%s", TokenUtils.START_TOKEN, TokenUtils.START_TOKEN);
    
    } else if (useClasses) {
      // Class-based
      Rule<IString> rule = f.rule.abstractRule;
      StringBuilder sb = new StringBuilder();
      for (IString token : rule.source) {
        if (sb.length() > 0) sb.append("-");
        String tokenClass = sourceMap.get(token).toString();
        sb.append(tokenClass);
      }
      sb.append(">");
      boolean seenFirst = false;
      for (IString token : rule.target) {
        if (seenFirst) sb.append("-");
        String tokenClass = targetMap.get(token).toString();
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
