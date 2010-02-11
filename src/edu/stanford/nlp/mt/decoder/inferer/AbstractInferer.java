package edu.stanford.nlp.mt.decoder.inferer;

import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.ConstrainedOutputSpace;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.Scorer;

abstract public class AbstractInferer<TK, FV> implements Inferer<TK,FV> {
	protected final CombinedFeaturizer<TK, FV> featurizer;
	protected final PhraseGenerator<TK> phraseGenerator;
	protected final Scorer<FV> scorer;
	protected final SearchHeuristic<TK,FV> heuristic;
	protected final RecombinationFilter<Hypothesis<TK,FV>> filter;


	abstract public List<RichTranslation<TK, FV>> nbest(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, List<Sequence<TK>> targets, int size);
	abstract public RichTranslation<TK, FV> translate(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, List<Sequence<TK>> targets);

	protected AbstractInferer(AbstractInfererBuilder<TK, FV> builder) {
		featurizer = builder.incrementalFeaturizer;
		phraseGenerator = builder.phraseGenerator;
		scorer = builder.scorer;
		heuristic = builder.heuristic;
		filter = builder.filter;
	}

	/**
	 *
	 */
	protected List<FeatureValue<FV>> collectFeatureValues(Hypothesis<TK,FV> hyp) {
		List<FeatureValue<FV>> features = new LinkedList<FeatureValue<FV>>();
		for ( ; hyp != null; hyp = hyp.preceedingHyp) {
			List<FeatureValue<FV>> localFeatures = hyp.localFeatures;
			if (localFeatures != null) {
				features.addAll(localFeatures);
			}
		}
		return features;
	}

  protected List<String> collectAlignments(Hypothesis<TK,FV> hyp) {
    LinkedList<String> alignments = new LinkedList<String>();
		for ( ; hyp != null; hyp = hyp.preceedingHyp) {
      ConcreteTranslationOption<TK> opt = hyp.translationOpt;
      if(opt == null)
        continue;
      int teIdx = hyp.length-1;
      int tsIdx = hyp.preceedingHyp == null ? 0 : hyp.length-1;
      CoverageSet cs = opt.foreignCoverage;
      int feIdx=-1;
      for (;;) {
        int fsIdx = cs.nextSetBit(feIdx+1);
        if(fsIdx < 0)
          break;
        feIdx = cs.nextClearBit(fsIdx)-1;
        StringBuilder sbuf = new StringBuilder();
        sbuf.append(fsIdx);
        if(fsIdx != feIdx)
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
