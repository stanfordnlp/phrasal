package edu.stanford.nlp.mt.decoder.inferer;

import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.decoder.annotators.Annotator;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.ConstrainedOutputSpace;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.Scorer;

abstract public class AbstractInferer<TK, FV> implements Inferer<TK, FV> {
  protected final CombinedFeaturizer<TK, FV> featurizer;
  protected final PhraseGenerator<TK,FV> phraseGenerator;
  protected final Scorer<FV> scorer;
  protected final SearchHeuristic<TK, FV> heuristic;
  protected final RecombinationFilter<Hypothesis<TK, FV>> filter;
  protected final List<Annotator<TK,FV>> annotators;

  
  @Override
  abstract public List<RichTranslation<TK, FV>> nbest(Sequence<TK> source,
      int sourceInputId, ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets, int size);

  @Override
  abstract public RichTranslation<TK, FV> translate(Sequence<TK> source,
      int sourceInputId, ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets);
  
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

  /**
   * TODO(spenceg): I'm not really sure what this does. It looks like a phrasal
   * alignment....
   * 
   * @param hyp
   * @return
   */
  protected List<String> collectAlignments(Hypothesis<TK, FV> hyp) {
    LinkedList<String> alignments = new LinkedList<String>();
    for (; hyp != null; hyp = hyp.preceedingHyp) {
      ConcreteTranslationOption<TK,FV> opt = hyp.translationOpt;
      if (opt == null)
        continue;
      int teIdx = hyp.length - 1;
      int tsIdx = hyp.preceedingHyp == null ? 0 : hyp.length - 1;
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
