package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.decoder.AbstractInferer;
import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.stats.SimilarityMeasures;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.tm.Rule;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;

/**
 * For constructing synthetic rules.
 * 
 * @author Spence Green
 *
 */
public final class SyntheticRules {

  private static final Logger logger = LogManager.getLogger(SyntheticRules.class.getName());
  
  private static final PhraseAlignment UNIGRAM_ALIGNMENT = PhraseAlignment.getPhraseAlignment("(0)");
  private static final PhraseAlignment MONOTONE_ALIGNMENT = PhraseAlignment.getPhraseAlignment(PhraseAlignment.MONOTONE_ALIGNMENT);
  
  public static final String PHRASE_TABLE_NAME = "synthetic";
  
  private static final int MAX_SYNTHETIC_ORDER = 3;
  private static final int MAX_TARGET_ORDER = 5;
  
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
  
  @SuppressWarnings("unchecked")
  public static <TK,FV> void augmentRuleGrid(RuleGrid<TK,FV> ruleGrid, 
      Sequence<TK> prefix, int sourceInputId, Sequence<TK> sourceSequence, 
      AbstractInferer<TK, FV> inferer, InputProperties inputProperties) {
    
    // Fetch translation models
    final List<DynamicTranslationModel<FV>> tmList = new ArrayList<>(2);
    tmList.add((DynamicTranslationModel<FV>) inferer.phraseGenerator);
    if (inputProperties.containsKey(InputProperty.ForegroundTM)) {
      tmList.add((DynamicTranslationModel<FV>) inputProperties.get(InputProperty.ForegroundTM));
    }
    final String[] featureNames = (String[]) inferer.phraseGenerator.getFeatureNames().toArray();
    
    // e2f align prefix to source with Cooc table and lexical similarity as backoff. This will
    // need to change for languages with different orthographies.
    SymmetricalWordAlignment alignInverse = alignInverse(sourceSequence, prefix, tmList);
    
    // f2e align with Cooc table and lexical similarity. Includes deletion rules.
    SymmetricalWordAlignment align = align(sourceSequence, prefix, tmList);
    
    // Symmetrize Apply. Start with intersection. Then try grow.
    SymmetricalWordAlignment sym = intersect(align, alignInverse);
    
    // WSGDEBUG
//    System.err.println(sym.toString());
    
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
            if (cnt_fe == 0) cnt_fe = 1e-9;
            
          } else {
            cnt_f = 1;
            cnt_e = 1;
            cnt_fe = 1e-9;
          }
          ConcreteRule<TK,FV> syntheticRule = SyntheticRules.makeSyntheticRule(src, tgt, 
              cov, featureNames, inferer.scorer, inferer.featurizer, cnt_fe, cnt_e, cnt_f, 
              inputProperties, sourceSequence, sourceInputId, alignment);
          ruleGrid.addEntry(syntheticRule);

          // WSGDEBUG
//          System.err.printf("Ext: %s%n", syntheticRule);
        }
      }
    }

    // Get source coverage and add source deletions
    CoverageSet sourceCoverage = new CoverageSet(sourceSequence.size());
    final int maxSourceCoverage = Math.min(sourceSequence.size(), prefixSourceCoverage.cardinality() + tmList.get(0).maxLengthSource());
    for (int i = 0; i < maxSourceCoverage; ++i) {
      if (sym.f2e(i).isEmpty()) {
        // Source deletion
        CoverageSet cov = new CoverageSet(sourceSequence.size());
        cov.set(i);
        Sequence<TK> src = sourceSequence.subsequence(i, i+1);
        Sequence<TK> tgt = Sequences.emptySequence();
        int cnt_f = 1;
        int cnt_e = 1;
        double cnt_fe = 1e-15; // Really discourage this! Should always be a last resort since the LM will prefer deleting words
        ConcreteRule<TK,FV> syntheticRule = SyntheticRules.makeSyntheticRule(src, tgt, 
            cov, featureNames, inferer.scorer, inferer.featurizer, cnt_fe, cnt_e, cnt_f, 
            inputProperties, sourceSequence, sourceInputId, MONOTONE_ALIGNMENT);
        ruleGrid.addEntry(syntheticRule);

        // WSGDEBUG
//        System.err.printf("ExtDel: %s%n", syntheticRule);

      } else {
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
          
          int cnt_f = 1, cnt_e = 1;
          double cnt_fe = 1e-9;
          ConcreteRule<TK,FV> syntheticRule = SyntheticRules.makeSyntheticRule(src, tgt, 
              cov, featureNames, inferer.scorer, inferer.featurizer, 
              cnt_fe, cnt_e, cnt_f, inputProperties, sourceSequence, sourceInputId, MONOTONE_ALIGNMENT);
          ruleGrid.addEntry(syntheticRule);
          finalTargetCoverage.set(ei, ej);

          // WSGDEBUG
//          System.err.printf("ExtUnk: %s%n", syntheticRule);
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
  
//  protected static boolean addPhrasesToIndex(WordAlignment sent, int maxPhraseLenE, int maxPhraseLenF) {
//
//    int fsize = sent.f().size();
//    int esize = sent.e().size();
//
//    // For each English phrase:
//    for (int e1 = 0; e1 < esize; ++e1) {
//
//      int f1 = Integer.MAX_VALUE;
//      int f2 = Integer.MIN_VALUE;
//      int lastE = Math.min(esize, e1 + maxPhraseLenE) - 1;
//
//      for (int e2 = e1; e2 <= lastE; ++e2) {
//
//        // Find range of f aligning to e1...e2:
//        SortedSet<Integer> fss = sent.e2f(e2);
//        if (!fss.isEmpty()) {
//          int fmin = fss.first();
//          int fmax = fss.last();
//          if (fmin < f1)
//            f1 = fmin;
//          if (fmax > f2)
//            f2 = fmax;
//        }
//
//        // Phrase too long:
//        if (f2 - f1 >= maxPhraseLenF)
//          continue;
//
//        // No word alignment within that range, or phrase too long?
//        if (f1 > f2)
//          continue;
//
//        // Check if range [e1-e2] [f1-f2] is admissible:
//        boolean admissible = true;
//        for (int fi = f1; fi <= f2; ++fi) {
//          SortedSet<Integer> ess = sent.f2e(fi);
//          if (!ess.isEmpty())
//            if (ess.first() < e1 || ess.last() > e2) {
//              admissible = false;
//              break;
//            }
//        }
//        if (!admissible)
//          continue;
//
//        // See how much we can expand the phrase to cover unaligned words:
//        int F1 = f1, F2 = f2;
//        int lastF1 = Math.max(0, f2 - maxPhraseLenF + 1);
//        while (F1 > lastF1 && sent.f2e(F1 - 1).isEmpty()) {
//          --F1;
//        }
//        int lastF2 = Math.min(fsize - 1, f1 + maxPhraseLenF - 1);
//        while (F2 < lastF2 && sent.f2e(F2 + 1).isEmpty()) {
//          ++F2;
//        }
//
//        for (int i = F1; i <= f1; ++i) {
//          int lasti = Math.min(F2, i + maxPhraseLenF - 1);
//          for (int j = f2; j <= lasti; ++j) {
//            assert (j - i < maxPhraseLenF);
//            addPhraseToIndex(sent, i, j, e1, e2, true, 1.0f);
//          }
//        }
//      }
//    }
//
//    return true;
//  }
  
  
  private static SymmetricalWordAlignment intersect(SymmetricalWordAlignment align,
      SymmetricalWordAlignment alignInverse) {
    SymmetricalWordAlignment a = new SymmetricalWordAlignment(align.f(), align.e());
    for (int fi = 0; fi < align.fSize(); ++fi) {
      for (int ei : align.f2e(fi)) {
        if (alignInverse.e2f(ei).contains(fi)) {
          a.addAlign(fi, ei);
        }
      }
    }
    return a;
  }

  @SuppressWarnings("unchecked")
  private static <TK,FV> SymmetricalWordAlignment align(Sequence<TK> source,
      Sequence<TK> target, List<DynamicTranslationModel<FV>> tmList) {
    SymmetricalWordAlignment a = new SymmetricalWordAlignment((Sequence<IString>) source, 
        (Sequence<IString>) target);
    
    int[] cnt_f = new int[source.size()];
    Arrays.fill(cnt_f, -1);
//    cnt_f[cnt_f.length-1] = tmList.stream().mapToInt(tm -> tm.bitextSize()).sum();
    
    for (int i = 0; i < target.size(); ++i) {
      double max = -10000.0;
      int argmax = -1;
      final IString tgtToken = (IString) target.get(i);
//      int cnt_e = tmList.stream().mapToInt(tm -> tm.getTargetLexCount(tgtToken)).sum();
      for (int j = 0; j < source.size(); ++j) {
        final IString srcToken = (IString) source.get(j);
        if (cnt_f[j] < 0) cnt_f[j] = tmList.stream().mapToInt(tm -> tm.getSourceLexCount(srcToken)).sum();
        int cnt_ef = tmList.stream().mapToInt(tm -> tm.getJointLexCount(srcToken, tgtToken)).sum();
        double tEF = Math.log(cnt_ef) - Math.log(cnt_f[j]);
        if (tEF > max) {
          max = tEF;
          argmax = j;
        }
      }
      
      if (argmax < 0) {
        // Backoff to lexical similarity
        // TODO(spenceg) Only works for languages with similar orthography.
        String tgt = target.get(i).toString();
        for (int j = 0; j < source.size(); ++j) {
          String src = source.get(j).toString();
          double q = SimilarityMeasures.jaccard(tgt, src);
          if (q > max) {
            max = q;
            argmax = j;
          }
        }
      }
      
      // Populate alignment
      if (argmax >= 0) {
        a.addAlign(argmax, i);
      }
    }
    return a;
  }
  
  @SuppressWarnings("unchecked")
  private static <TK,FV> SymmetricalWordAlignment alignInverse(Sequence<TK> source,
      Sequence<TK> target, List<DynamicTranslationModel<FV>> tmList) {
    SymmetricalWordAlignment a = new SymmetricalWordAlignment((Sequence<IString>) source, 
        (Sequence<IString>) target);
    
    int[] cnt_e = new int[target.size()];
    Arrays.fill(cnt_e, -1);
//    cnt_e[cnt_e.length-1] = tmList.stream().mapToInt(tm -> tm.bitextSize()).sum();
    
    for (int i = 0; i < source.size(); ++i) {
      double max = -10000.0;
      int argmax = -1;
      final IString srcToken = (IString) source.get(i);
//      int cnt_e = tmList.stream().mapToInt(tm -> tm.getTargetLexCount(tgtToken)).sum();
      for (int j = 0; j < target.size(); ++j) {
        final IString tgtToken = (IString) target.get(j);
        if (cnt_e[j] < 0) cnt_e[j] = tmList.stream().mapToInt(tm -> tm.getTargetLexCount(tgtToken)).sum();
        int cnt_ef = tmList.stream().mapToInt(tm -> tm.getJointLexCount(srcToken, tgtToken)).sum();
        double tEF = Math.log(cnt_ef) - Math.log(cnt_e[j]);
        if (tEF > max) {
          max = tEF;
          argmax = j;
        }
      }
      
      if (argmax < 0) {
        // Backoff to lexical similarity
        // TODO(spenceg) Only works for languages with similar orthography.
        String src = source.get(i).toString();
        for (int j = 0; j < target.size(); ++j) {
          String tgt = target.get(j).toString();
          double q = SimilarityMeasures.jaccard(tgt, src);
          if (q > max) {
            max = q;
            argmax = j;
          }
        }
      }
      
      // Populate alignment
      if (argmax >= 0) {
        a.addAlign(i, argmax);
      }
    }
    return a;
  }
}
