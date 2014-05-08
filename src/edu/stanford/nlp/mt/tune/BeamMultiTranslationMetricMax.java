package edu.stanford.nlp.mt.tune;

import java.util.*;

import edu.stanford.nlp.mt.base.NBestListContainer;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.decoder.recomb.MetricBasedRecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.Beam;
import edu.stanford.nlp.mt.decoder.util.MultiTranslationState;
import edu.stanford.nlp.mt.decoder.util.TreeBeam;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class BeamMultiTranslationMetricMax<TK, FV> implements
    MultiTranslationMetricMax<TK, FV> {
  public static final String DEBUG_PROPERTY = "BeamMultiTranslationMetricMaxDebug";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  static public final int DEFAULT_BEAM_SIZE = 200;
  final EvaluationMetric<TK, FV> metric;
  final int beamSize;

  public BeamMultiTranslationMetricMax(EvaluationMetric<TK, FV> metric) {
    this.metric = metric;
    this.beamSize = DEFAULT_BEAM_SIZE;
  }

  /**
	 * 
	 */
  public BeamMultiTranslationMetricMax(EvaluationMetric<TK, FV> metric,
      int beamSize) {
    this.metric = metric;
    this.beamSize = beamSize;
  }

  @Override
  public List<ScoredFeaturizedTranslation<TK, FV>> maximize(
      NBestListContainer<TK, FV> nbest) {

    if (DEBUG) {
      System.err.printf("...maximize()\n");
    }

    List<List<ScoredFeaturizedTranslation<TK, FV>>> nbestLists = nbest
        .nbestLists();

    List<MultiTranslationState<TK, FV>> stateList = new ArrayList<MultiTranslationState<TK, FV>>();
    stateList.add(new MultiTranslationState<TK, FV>(nbest, metric));
    Beam<MultiTranslationState<TK, FV>> beam = null;
    double bestScore = 0;
    for (int translationId = 0; translationId < nbestLists.size(); translationId++) {
      if (DEBUG) {
        System.err.printf(
            "Doing translation id: %d Incoming States: %d Best Score: %.5f\n",
            translationId, stateList.size(), bestScore);
      }
      beam = new TreeBeam<MultiTranslationState<TK, FV>>(beamSize,
          new MetricBasedRecombinationFilter<TK, FV>(metric));
      for (MultiTranslationState<TK, FV> mts : stateList) {
        for (ScoredFeaturizedTranslation<TK, FV> featurizedTrans : nbestLists
            .get(translationId)) {
          beam.put(mts.append(featurizedTrans));
        }
      }
      bestScore = beam.bestScore();
      stateList = new ArrayList<MultiTranslationState<TK, FV>>(beam.size());
      for (MultiTranslationState<TK, FV> mts : beam) {
        stateList.add(mts);
      }
    }

    if (DEBUG) {
      System.err.printf("Done Best Score: %.5f\n", bestScore);
    }

    assert (beam != null);
    MultiTranslationState<TK, FV> best = beam.remove();

    return best.selected();
  }

}
