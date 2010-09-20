package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.base.NBestListContainer;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.IncrementalEvaluationMetric;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class MultiTranslationState<TK, FV> implements
    State<MultiTranslationState<TK, FV>> {
  public final IncrementalEvaluationMetric<TK, FV> incMetric;
  final ScoredFeaturizedTranslation<TK, FV> selected;
  final MultiTranslationState<TK, FV> parent;
  public final int depth;

  /**
	 * 
	 */
  public MultiTranslationState(NBestListContainer<TK, FV> nbest,
      EvaluationMetric<TK, FV> metric) {
    this.incMetric = metric.getIncrementalMetric(nbest);
    this.selected = null;
    this.parent = null;
    this.depth = -1;
  }

  /**
	 * 
	 */
  @SuppressWarnings("unchecked")
  private MultiTranslationState(MultiTranslationState<TK, FV> parent,
      ScoredFeaturizedTranslation<TK, FV> selected) {
    this.parent = parent;
    try {
      this.incMetric = (IncrementalEvaluationMetric<TK, FV>) parent.incMetric
          .clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
    incMetric.add(selected);
    this.selected = selected;
    this.depth = parent.depth + 1;
  }

  public MultiTranslationState<TK, FV> append(
      ScoredFeaturizedTranslation<TK, FV> selected) {
    /*
     * System.out.printf("score: %f ", mts.incMetric.score()); for (double p :
     * ((BLEUIncrementalMetric)mts.incMetric).ngramPrecisions()) {
     * System.out.printf(" %.3f", p); } System.out.println();
     */
    return new MultiTranslationState<TK, FV>(this, selected);
  }

  @Override
  public int compareTo(MultiTranslationState<TK, FV> o) {
    return incMetric.compareTo(o.incMetric);
  }

  @Override
  public double score() {
    return incMetric.score();
  }

  public List<ScoredFeaturizedTranslation<TK, FV>> selected() {
    List<ScoredFeaturizedTranslation<TK, FV>> selected = new ArrayList<ScoredFeaturizedTranslation<TK, FV>>(
        depth + 1);
    for (MultiTranslationState<TK, FV> mts = this; mts.selected != null; mts = mts.parent) {
      selected.add(mts.selected);
    }
    Collections.reverse(selected);
    return selected;
  }

  @Override
  public State<MultiTranslationState<TK, FV>> parent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double partialScore() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int depth() {
    return depth;
  }
}
