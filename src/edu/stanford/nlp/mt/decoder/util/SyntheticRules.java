package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.decoder.AbstractInferer;
import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.stats.Distributions.Poisson;
import edu.stanford.nlp.mt.stats.SimilarityMeasures;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.tm.Rule;
import edu.stanford.nlp.mt.train.AlignmentSymmetrizer;
import edu.stanford.nlp.mt.train.GIZAWordAlignment;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.train.AlignmentSymmetrizer.SymmetrizationType;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * For constructing synthetic rules.
 * 
 * @author Spence Green
 *
 */
public final class SyntheticRules {

  private static final Logger logger = LogManager.getLogger(SyntheticRules.class.getName());

  private static final PhraseAlignment UNIGRAM_ALIGNMENT = PhraseAlignment.getPhraseAlignment("(0)");
//  private static final PhraseAlignment MONOTONE_ALIGNMENT = PhraseAlignment.getPhraseAlignment(PhraseAlignment.MONOTONE_ALIGNMENT);

  // This heuristic seems to maximize prefix BLEU relative to the other heuristics.
  private static final SymmetrizationType SYM_HEURISTIC = SymmetrizationType.intersection;
  private static final double SYNTHETIC_PROB = 1e-5;
  private static final double BACKOFF_PROB = 1e-9;
  private static final double POSITION_TERM_LAMBDA = 1.0;

  public static final String PHRASE_TABLE_NAME = "synthetic";

  private static final int MAX_SYNTHETIC_ORDER = 3;
  private static final int MAX_TARGET_ORDER = 4;

  private SyntheticRules() {}

  /**
   * Create a synthetic translation rule.
   * 
   * @param source
   * @param target
   * @param sourceIndex
   * @param phraseScoreNames
   * @return
   */
  public static <TK,FV> ConcreteRule<TK,FV> makeSyntheticUnigramRule(Sequence<TK> source, Sequence<TK> target, 
      CoverageSet sourceCoverage, String[] phraseScoreNames, Scorer<FV> scorer,
      FeatureExtractor<TK,FV> featurizer,
      double cnt_f_e, int cnt_e, int cnt_f, InputProperties inputProperties, Sequence<TK> sourceSequence,
      int sourceInputId) {
    if (source.size() != 1 || target.size() != 1) throw new IllegalArgumentException(String.format("Non-unigram arguments %d %d", source.size(), target.size()));
    return makeSyntheticRule(source, target, sourceCoverage, phraseScoreNames, scorer, featurizer,
        cnt_f_e, cnt_e, cnt_f, inputProperties, sourceSequence, sourceInputId, UNIGRAM_ALIGNMENT);
  }

  /**
   * Create a synthetic translation rule.
   * 
   * @param source
   * @param target
   * @param sourceCoverage
   * @param phraseScoreNames
   * @param scorer
   * @param featurizer
   * @param cnt_f_e
   * @param cnt_e
   * @param cnt_f
   * @param inputProperties
   * @param sourceSequence
   * @param sourceInputId
   * @param align
   * @return
   */
  public static <TK,FV> ConcreteRule<TK, FV> makeSyntheticRule(Sequence<TK> source, Sequence<TK> target, 
      CoverageSet sourceCoverage, String[] phraseScoreNames, Scorer<FV> scorer,
      FeatureExtractor<TK,FV> featurizer,
      double cnt_f_e, int cnt_e, int cnt_f, InputProperties inputProperties, Sequence<TK> sourceSequence,
      int sourceInputId, PhraseAlignment align) {
    // Baseline dense features
    float[] scores = new float[phraseScoreNames.length];
    scores[0] = (float) (Math.log(cnt_f_e) - Math.log(cnt_e));
    scores[1] = scores[0];
    scores[2] = (float) (Math.log(cnt_f_e) - Math.log(cnt_f));
    scores[3] = scores[2];
    if (scores.length == 6) {
      // Extended features
      scores[4] = cnt_f_e > 1 ? (float) Math.log(cnt_f_e) : 0.0f;
      scores[5] = cnt_f_e <= 1 ? -1.0f : 0.0f;
    }

    Rule<TK> abstractRule = new Rule<>(scores, phraseScoreNames, target, source, 
        align, PHRASE_TABLE_NAME);
    ConcreteRule<TK,FV> rule = new ConcreteRule<>(abstractRule, sourceCoverage, featurizer, 
        scorer, sourceSequence, sourceInputId, inputProperties);
    return rule;
  }

  /**
   * Augment the rule grid for a window relative to the current derivation.
   * 
   * @param ruleGrid
   * @param target
   * @param d
   * @param maxDistortion
   * @param sourceInputId
   * @param inferer
   * @param sourceInputProperties
   * @return
   */
  public static <TK,FV> int augmentRuleGrid(RuleGrid<TK, FV> ruleGrid, Sequence<TK> target, 
      Derivation<TK, FV> d, int maxDistortion, int sourceInputId, AbstractInferer<TK, FV> inferer,
      InputProperties sourceInputProperties) {
    int numRules = 0;

    // Baseline dense features
    final String[] featureNames = (String[]) inferer.phraseGenerator.getFeatureNames().toArray();
    float[] scores = new float[featureNames.length];
    scores[0] = (float) Math.log(SYNTHETIC_PROB);
    scores[1] = scores[0];
    scores[2] = scores[0];
    scores[3] = scores[0];
    if (scores.length == 6) {
      // Extended features
      scores[4] = 0.0f;
      scores[5] = -1.0f;
    }

    CoverageSet dCoverage = d.sourceCoverage;
    int firstClearBit = dCoverage.nextClearBit(0);
    for(int i = firstClearBit; 
        (i - firstClearBit) < maxDistortion &&
        i < d.sourceSequence.size();
        i = dCoverage.nextClearBit(i+1)) {
      Sequence<TK> source = d.sourceSequence.subsequence(i, i+1);
      CoverageSet sourceCoverage = new CoverageSet(d.sourceSequence.size());
      sourceCoverage.set(i);
      Rule<TK> abstractRule = new Rule<>(scores, featureNames, target, source, 
          UNIGRAM_ALIGNMENT, PHRASE_TABLE_NAME);
      ConcreteRule<TK,FV> rule = new ConcreteRule<>(abstractRule, sourceCoverage, inferer.featurizer, 
          inferer.scorer, d.sourceSequence, sourceInputId, sourceInputProperties);
      ruleGrid.addEntry(rule);
      ++numRules;
    }
    return numRules;
  }


  /**
   * Create a new rule from an existing rule by replacing the target side.
   * 
   * @param base
   * @param target
   * @param scorer
   * @param featurizer
   * @param sourceSequence
   * @param inputProperties
   * @param sourceInputId
   * @return
   */
  public static <TK,FV> ConcreteRule<TK,FV> makeSyntheticRule(ConcreteRule<TK,FV> base,
      Sequence<TK> target, PhraseAlignment align, Scorer<FV> scorer, FeatureExtractor<TK,FV> featurizer,
      Sequence<TK> sourceSequence, InputProperties inputProperties, int sourceInputId) {
    Rule<TK> baseRule = base.abstractRule;
    Rule<TK> newRule = new Rule<>(baseRule.scores, baseRule.phraseScoreNames, target, baseRule.source, 
        align, PHRASE_TABLE_NAME);
    newRule.reoderingScores = baseRule.reoderingScores;
    newRule.forwardOrientation = baseRule.forwardOrientation;
    newRule.backwardOrientation = baseRule.backwardOrientation;
    ConcreteRule<TK,FV> rule = new ConcreteRule<>(newRule, base.sourceCoverage, featurizer, 
        scorer, sourceSequence, sourceInputId, inputProperties);
    return rule;
  }

  /**
   * IBM Model 2 t() parameter.
   * 
   * @param k
   * @param max
   * @return
   */
  private static double distortionParam(int k, int max) {
    return Poisson.probOf(k, POSITION_TERM_LAMBDA);
//    return (0.99 * Poisson.probOf(k, POSITION_TERM_LAMBDA)) + (0.01 * Uniform.probOf(0, max));
  }
  
  /**
   * Augment the rule grid by aligning the source to the supplied prefix.
   * 
   * @param ruleGrid
   * @param prefix
   * @param sourceInputId
   * @param sourceSequence
   * @param inferer
   * @param inputProperties
   */
  @SuppressWarnings("unchecked")
  public static <TK,FV> void augmentRuleGrid(RuleGrid<TK,FV> ruleGrid, 
      Sequence<TK> prefix, int sourceInputId, Sequence<TK> sourceSequence, 
      AbstractInferer<TK, FV> inferer, InputProperties inputProperties) {

    if (! (inferer.phraseGenerator instanceof DynamicTranslationModel)) {
      throw new RuntimeException("Synthetic rule generation requires DynamicTranslationModel");
    }

    // WSGDEBUG
    boolean printDebug = false; // sourceInputId == 1022;
    if (printDebug) {
      System.err.printf("DEBUG %d%n", sourceInputId);
    }

    // Fetch translation models
    final List<DynamicTranslationModel<FV>> tmList = new ArrayList<>(2);
    tmList.add((DynamicTranslationModel<FV>) inferer.phraseGenerator);
    if (inputProperties.containsKey(InputProperty.ForegroundTM)) {
      tmList.add((DynamicTranslationModel<FV>) inputProperties.get(InputProperty.ForegroundTM));
    }
    final String[] featureNames = (String[]) inferer.phraseGenerator.getFeatureNames().toArray();

    final GIZAWordAlignment align = new GIZAWordAlignment((Sequence<IString>) sourceSequence, 
        (Sequence<IString>) prefix);

    // e2f align prefix to source with Cooc table and lexical similarity as backoff. This will
    // need to change for languages with different orthographies.
    alignInverse(tmList, align);

    // f2e align with Cooc table and lexical similarity. Includes deletion rules.
    align(tmList, align);

    // Symmetrization
    final SymmetricalWordAlignment sym = AlignmentSymmetrizer.symmetrize(align, SYM_HEURISTIC);

    // WSGDEBUG
    if (printDebug) {
      System.err.printf("src: %s%n", sourceSequence);
      System.err.printf("tgt: %s%n", prefix);
      System.err.printf("f2e: %s%n", align.toString(false));
      System.err.printf("e2f: %s%n", align.toString(true));
      System.err.printf("sym: %s%n", sym.toString());
    }

    // Extract phrases using the same heuristics as the DynamicTM
    CoverageSet targetCoverage = new CoverageSet(prefix.size());
    CoverageSet prefixSourceCoverage = new CoverageSet(sourceSequence.size());
    for (int order = 1; order <= MAX_SYNTHETIC_ORDER; ++order) {
      for (int i = 0, sz = sourceSequence.size() - order; i <= sz; ++i) {
        List<RuleBound> rules = extractRules(sym, i, order, MAX_TARGET_ORDER);
        for (RuleBound r : rules) {
          targetCoverage.set(r.ei, r.ej);
          prefixSourceCoverage.set(r.fi, r.fj);
          CoverageSet cov = new CoverageSet(sourceSequence.size());
          cov.set(r.fi, r.fj);
          Sequence<TK> src = sourceSequence.subsequence(r.fi, r.fj);
          Sequence<TK> tgt = prefix.subsequence(r.ei, r.ej);
          int[][] e2f = new int[tgt.size()][src.size()];
          for (int eIdx = r.ei; eIdx < r.ej; ++eIdx) {
            e2f[eIdx - r.ei] = sym.e2f(eIdx).stream().mapToInt(a -> a - r.fi).toArray();
          }
          PhraseAlignment alignment = new PhraseAlignment(e2f);

          int cnt_f = 0, cnt_e = 0;
          double cnt_fe = 0.0;
          if (src.size() == 1 && tgt.size() == 1) { // Unigram rule
            cnt_f = tmList.stream().mapToInt(tm -> tm.getSourceLexCount((IString) src.get(0))).sum();
            cnt_e = tmList.stream().mapToInt(tm -> tm.getTargetLexCount((IString) tgt.get(0))).sum();
            cnt_fe = tmList.stream().mapToInt(tm -> tm.getJointLexCount((IString) src.get(0), (IString) tgt.get(0))).sum();
            if (cnt_f == 0) cnt_f = 1;
            if (cnt_e == 0) cnt_e = 1;
            if (cnt_fe == 0) cnt_fe = SYNTHETIC_PROB;

          } else {
            cnt_f = 1;
            cnt_e = 1;
            cnt_fe = SYNTHETIC_PROB;
          }
          ConcreteRule<TK,FV> syntheticRule = SyntheticRules.makeSyntheticRule(src, tgt, 
              cov, featureNames, inferer.scorer, inferer.featurizer, cnt_fe, cnt_e, cnt_f, 
              inputProperties, sourceSequence, sourceInputId, alignment);
          ruleGrid.addEntry(syntheticRule);

          // WSGDEBUG
          if (printDebug) System.err.printf("Ext: %s%n", syntheticRule);
        }
      }
    }

    // Add backoff unigram rules.
    // TODO(spenceg) Cache cnt_f, which is expensive.
//    for (int j = 0, tgtLength = prefix.size(); j < tgtLength; ++j) {
//      TK targetQuery = prefix.get(j);
//      final int cnt_e = tmList.stream().mapToInt(tm -> tm.getTargetLexCount((IString) targetQuery)).sum();
//      boolean isTargetOOV = cnt_e == 0;
//      final Sequence<TK> target = prefix.subsequence(j, j+1);
//
//      for (int i = 0, srcLength = sourceSequence.size(); i < srcLength; ++i) {
//        TK sourceQuery = sourceSequence.get(i);
//        int cnt_f = tmList.stream().mapToInt(tm -> tm.getSourceLexCount((IString) sourceQuery)).sum();
//        final boolean isSourceOOV = cnt_f == 0;
//        final Sequence<TK> source = sourceSequence.subsequence(i,i+1);
//
//        CoverageSet ruleCoverage = new CoverageSet(srcLength);
//        ruleCoverage.set(i);
//        int cntE = isTargetOOV ? 1 : cnt_e;
//        int cntF = isSourceOOV ? 1 : cnt_f;
//        double cnt_joint = tmList.stream().mapToInt(tm -> tm.getJointLexCount((IString) sourceQuery, (IString) targetQuery)).sum();
//        if (cnt_joint != 0) continue;
//        ConcreteRule<TK,FV> syntheticRule = SyntheticRules.makeSyntheticUnigramRule(source, target, 
//            ruleCoverage, featureNames, inferer.scorer, inferer.featurizer, 
//            BACKOFF_PROB, cntE, cntF, inputProperties, sourceSequence, sourceInputId);
//        ruleGrid.addEntry(syntheticRule);
//        
////      WSGDEBUG
//        if (printDebug) System.err.printf("BackExt: %s%n", syntheticRule);
//      }
//    }
    
    // Get source coverage and add source deletions
    CoverageSet sourceCoverage = new CoverageSet(sourceSequence.size());
    final int maxSourceCoverage = Math.min(sourceSequence.size(), prefixSourceCoverage.cardinality() + tmList.get(0).maxLengthSource());
    for (int i = 0; i < maxSourceCoverage; ++i) {
      if ( ! sym.f2e(i).isEmpty()) {
        sourceCoverage.set(i);
      }
    }

    // Iterate over gaps in target coverage, aligning to source gaps along the diagonal
    if (targetCoverage.cardinality() != prefix.size()) {
      // Iterate over the target coverage
      CoverageSet finalTargetCoverage = targetCoverage.clone();
      for (int i = targetCoverage.nextClearBit(0); i < prefix.size(); 
          i = targetCoverage.nextClearBit(i+1)) {
        int ei = i;
        int ej = targetCoverage.nextSetBit(ei+1);
        if (ej < 0) ej = prefix.size();

        // Must be a valid index
        int mid = Math.max(0, Math.min((int) Math.round((ej + ei) / 2.0), sourceSequence.size() - 1));

        int rightQuery = sourceCoverage.nextClearBit(mid);
        int leftQuery = sourceCoverage.previousClearBit(mid);
        int sourceAnchor = -1;
        if (leftQuery >= 0 && rightQuery < sourceSequence.size()) {
          sourceAnchor = ((mid - leftQuery) < (rightQuery - mid)) ? leftQuery : rightQuery;
        } else if (leftQuery >= 0) {
          sourceAnchor = leftQuery;
        } else if (rightQuery < sourceSequence.size()) {
          sourceAnchor = rightQuery;
        }

        if (sourceAnchor >= 0) {
          int fi = Math.max(0, sourceCoverage.previousSetBit(sourceAnchor-1)+1);
          int fj = sourceCoverage.nextSetBit(sourceAnchor+1);
          if (fj < 0) fj = sourceSequence.size();
          Sequence<TK> src = sourceSequence.subsequence(fi, fj);
          Sequence<TK> tgt = prefix.subsequence(ei, ej);
          CoverageSet cov = new CoverageSet(sourceSequence.size());
          cov.set(fi, fj);   

          int[][] e2f = new int[tgt.size()][src.size()];
          for (int k = 0; k < tgt.size() && k < src.size(); ++k) {
            e2f[k] = new int[] { k } ;
          }
          PhraseAlignment alignment = new PhraseAlignment(e2f);

          final int cnt_f = 1, cnt_e = 1;
          ConcreteRule<TK,FV> syntheticRule = SyntheticRules.makeSyntheticRule(src, tgt, 
              cov, featureNames, inferer.scorer, inferer.featurizer, 
              SYNTHETIC_PROB, cnt_e, cnt_f, inputProperties, sourceSequence, sourceInputId, alignment);
          ruleGrid.addEntry(syntheticRule);
          finalTargetCoverage.set(ei, ej);

          // WSGDEBUG
          if (printDebug) System.err.printf("ExtUnk: %s%n", syntheticRule);
        }
      }
      if (finalTargetCoverage.cardinality() != prefix.size()) {
        logger.warn("input {}: Incomplete target coverage {}", sourceInputId, finalTargetCoverage);
      }
    }
  }

  public static List<RuleBound> extractRules(SymmetricalWordAlignment align, int sourcePosition, 
      int length, int maxTargetPhrase) {
    // Find the target span
    int minTarget = Integer.MAX_VALUE;
    int maxTarget = -1;
    final int startSource = sourcePosition;
    final int endSource = startSource + length;
    for(int sourcePos = startSource; sourcePos < endSource; sourcePos++) {
      assert sourcePos < align.fSize() : String.format("[%d,%d) %d %d ", startSource, endSource, sourcePos, align.fSize());
      if ( align.f2e(sourcePos).size() > 0) {
        for(int targetPos : align.f2e(sourcePos)) {
          if (targetPos < minTarget) {
            minTarget = targetPos;
          }
          if (targetPos > maxTarget) {
            maxTarget = targetPos;
          }
        }
      }
    }

    if (maxTarget < 0 || maxTarget-minTarget >= maxTargetPhrase) return Collections.emptyList();

    // Admissibility check
    for (int i = minTarget; i <= maxTarget; ++i) {
      if ( align.e2f(i).size() > 0) {
        for (int sourcePos : align.e2f(i)) {
          if (sourcePos < startSource || sourcePos >= endSource) {
            // Failed check
            return Collections.emptyList();
          }
        }
      }
    }

    // "Loose" heuristic to grow the target
    // Try to grow the left bound of the target
    List<RuleBound> ruleList = new ArrayList<>();
    for(int startTarget = minTarget; (startTarget >= 0 &&
        startTarget > maxTarget-maxTargetPhrase &&
        (startTarget == minTarget || align.e2f(startTarget).size() == 0)); startTarget--) {

      // Try to grow the right bound of the target
      for (int endTarget=maxTarget; (endTarget < align.eSize() &&
          endTarget < startTarget+maxTargetPhrase && 
          (endTarget==maxTarget || align.e2f(endTarget).size() == 0)); endTarget++) {
        RuleBound r = new RuleBound(startSource, endSource, startTarget, endTarget + 1);
        ruleList.add(r);
      }
    }
    return ruleList;
  }

  private static class RuleBound {
    public final int fi;
    public final int fj; // exclusive
    public final int ei;
    public final int ej; // exclusive
    public RuleBound(int fi, int fj, int ei, int ej) {
      this.fi = fi;
      this.fj = fj;
      this.ei = ei;
      this.ej = ej;
    }
  }
  
  private static <TK,FV> void align(List<DynamicTranslationModel<FV>> tmList, GIZAWordAlignment a) {

    int[] cnt_f = new int[a.fSize()];
    Arrays.fill(cnt_f, -1);

    for (int i = 0, tSz = a.e().size(); i < tSz; ++i) {
      double max = -10000.0;
      int argmax = -1;
      final IString tgtToken = (IString) a.e().get(i);
      for (int j = 0, sz = a.fSize(); j < sz; ++j) {
        final IString srcToken = (IString) a.f().get(j);
        if (srcToken.equals(tgtToken)) {
          // Exact match
          max = 0.0;
          argmax = j;
        } else {
          if (cnt_f[j] < 0) cnt_f[j] = tmList.stream().mapToInt(tm -> tm.getSourceLexCount(srcToken)).sum();
          int cnt_ef = tmList.stream().mapToInt(tm -> tm.getJointLexCount(srcToken, tgtToken)).sum();
          if (cnt_ef == 0) continue;
          double tEF = Math.log(cnt_ef) - Math.log(cnt_f[j]);
          int posDiff = Math.abs(i - j);
          tEF += Math.log(distortionParam(posDiff, sz-1));
          if (tEF > max) {
            max = tEF;
            argmax = j;
          }
        }
      }

      if (argmax < 0) {
        // Backoff to lexical similarity
        // TODO(spenceg) Only works for orthographically similar languages. 
        String tgt = a.e().get(i).toString();
        for (int j = 0, sz = a.fSize(); j < sz; ++j) {
          String src = a.f().get(j).toString();
          double q = SimilarityMeasures.jaccard(tgt, src);
          int posDiff = Math.abs(i - j);
          q *= distortionParam(posDiff, sz-1);
          if (q > max) {
            max = q;
            argmax = j;
          }

          // TODO(spenceg) This results in lower prefix BLEU right now.
          //          List<ConcreteRule<TK,FV>> ruleList = ruleGrid.get(j, j);
          //          if (ruleList == null) ruleList = Collections.emptyList();
          //          for (ConcreteRule<TK,FV> r : ruleList) {
          //            if (r.abstractRule.target.size() != 1) continue;
          //            double qq = SimilarityMeasures.jaccard(tgt, r.abstractRule.target.toString());
          //            if (qq > max) {
          //              max = qq;
          //              argmax = j;
          //            }
          //          }
        }
      }

      // Populate alignment
      if (argmax >= 0) {
        a.addf2e(argmax, i);
      }
    }
  }

  private static <TK,FV> void alignInverse(List<DynamicTranslationModel<FV>> tmList, GIZAWordAlignment a) {

    int[] cnt_e = new int[a.eSize()];
    Arrays.fill(cnt_e, -1);

    for (int i = 0, sSz = a.fSize(); i < sSz; ++i) {
      double max = -10000.0;
      int argmax = -1;
      final IString srcToken = (IString) a.f().get(i);
      for (int j = 0, sz = a.eSize(); j < sz; ++j) {
        final IString tgtToken = (IString) a.e().get(j);
        if (srcToken.equals(tgtToken)) {
          max = 0.0;
          argmax = j;
        } else {
          if (cnt_e[j] < 0) cnt_e[j] = tmList.stream().mapToInt(tm -> tm.getTargetLexCount(tgtToken)).sum();
          int cnt_ef = tmList.stream().mapToInt(tm -> tm.getJointLexCount(srcToken, tgtToken)).sum();
          if (cnt_ef == 0) continue;
          double tFE = Math.log(cnt_ef) - Math.log(cnt_e[j]);
          int posDiff = Math.abs(i - j);
          tFE += Math.log(distortionParam(posDiff, sz-1));
          if (tFE > max) {
            max = tFE;
            argmax = j;
          }
        }
      }

      if (argmax < 0) {
        // Backoff to lexical similarity
        String src = a.f().get(i).toString();

        // Iterate over everything in the prefix
        // TODO(spenceg) Only works for orthographically similar languages.
        for (int j = 0, sz = a.eSize(); j < sz; ++j) {
          // Check for similarity with the source item
          String tgt = a.e().get(j).toString();
          double q = SimilarityMeasures.jaccard(tgt, src);
          int posDiff = Math.abs(i - j);
          q *= distortionParam(posDiff, sz-1);
          if (q > max) {
            max = q;
            argmax = j;
          }

          // TODO(spenceg) This results in lower prefix-bleu right now.
          // Check for similarity with known translations of this
          // source token.
          //          for (ConcreteRule<TK,FV> r : ruleList) {
          //            if (r.abstractRule.target.size() != 1) continue;
          //            double qq = SimilarityMeasures.jaccard(tgt, r.abstractRule.target.toString());
          //            if (qq > max) {
          //              max = qq;
          //              argmax = j;
          //            }
          //          }
        }
      }

      // Populate alignment
      if (argmax >= 0) {
        a.adde2f(i, argmax);
      }
    }
  }
}
