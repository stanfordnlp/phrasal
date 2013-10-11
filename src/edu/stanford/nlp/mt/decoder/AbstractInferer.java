package edu.stanford.nlp.mt.decoder;

import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.decoder.annotators.Annotator;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.Scorer;

abstract public class AbstractInferer<TK, FV> implements Inferer<TK, FV> {
  protected final CombinedFeaturizer<TK, FV> featurizer;
  protected final PhraseGenerator<TK,FV> phraseGenerator;
  protected final Scorer<FV> scorer;
  protected final SearchHeuristic<TK, FV> heuristic;
  protected final RecombinationFilter<Derivation<TK, FV>> filter;
  protected final List<Annotator<TK,FV>> annotators;

  protected AbstractInferer(AbstractInfererBuilder<TK, FV> builder) {
    featurizer = builder.incrementalFeaturizer;
    phraseGenerator = builder.phraseGenerator;
    scorer = builder.scorer;
    heuristic = builder.heuristic;
    filter = builder.filter;
    annotators = builder.annotators;
  }

  protected AbstractInferer(AbstractInferer<TK, FV> inferer) {
    featurizer = inferer.featurizer;
    phraseGenerator = inferer.phraseGenerator;
    scorer = inferer.scorer;
    heuristic = inferer.heuristic;
    filter = inferer.filter;
    annotators = inferer.annotators;
  }

  protected FeatureValueCollection<FV> collectFeatureValues(
      Derivation<TK, FV> hyp) {
    class LinkedFeatureValues<FV2> extends LinkedList<FeatureValue<FV2>>
        implements FeatureValueCollection<FV2> {
      private static final long serialVersionUID = 1L;

      @Override
      public Object clone() {
        return super.clone();
      }
    }
    LinkedFeatureValues<FV> features = new LinkedFeatureValues<FV>();
    for (; hyp != null; hyp = hyp.preceedingDerivation) {
      List<FeatureValue<FV>> localFeatures = hyp.localFeatures;
      if (localFeatures != null) {
        features.addAll(localFeatures);
      }
    }
    return features;
  }

  protected List<String> collectAlignments(Derivation<TK, FV> hyp) {
    LinkedList<String> alignments = new LinkedList<String>();
    for (; hyp != null; hyp = hyp.preceedingDerivation) {
      ConcreteRule<TK,FV> opt = hyp.rule;
      if (opt == null)
        continue;
      int teIdx = hyp.length - 1;
      int tsIdx = hyp.preceedingDerivation == null ? 0 : hyp.length - 1;
      CoverageSet cs = opt.sourceCoverage;
      int feIdx = -1;
      while (true) {
        int fsIdx = cs.nextSetBit(feIdx + 1);
        if (fsIdx < 0)
          break;
        feIdx = cs.nextClearBit(fsIdx) - 1;
        StringBuilder sbuf = new StringBuilder();
        sbuf.append(fsIdx);
        if (fsIdx != feIdx)
          sbuf.append("-").append(feIdx);
        sbuf.append("=").append(tsIdx);
        if (tsIdx != teIdx)
          sbuf.append("-").append(teIdx);
        alignments.addFirst(sbuf.toString());
      }
    }
    return alignments;
  }
}
