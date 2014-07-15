package edu.stanford.nlp.mt.metrics;

import java.util.*;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.util.NBestListContainer;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public interface EvaluationMetric<TK, FV> {

  /**
	 * 
	 */
  
  public double score(List<ScoredFeaturizedTranslation<TK,FV>> translations);
  // Java has a bug with erasure.
  public double scoreSeq(List<Sequence<TK>> translations);

  /**
	 * 
	 */
  IncrementalEvaluationMetric<TK, FV> getIncrementalMetric();

  /**
	 * 
	 */
  public IncrementalEvaluationMetric<TK, FV> getIncrementalMetric(
      NBestListContainer<TK, FV> nbestList);

  /**
	 * 
	 */
  public RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> getIncrementalMetricRecombinationFilter();

  /**
	 * 
	 */
  double maxScore();
}
