package edu.stanford.nlp.mt.tune;

import java.util.*;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHash;
import edu.stanford.nlp.mt.decoder.util.MultiTranslationState;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;

/**
 * Windowed agenda based search
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class AgendaMultiTranslationMetricMax<TK, FV> implements
    MultiTranslationMetricMax<TK, FV> {
  public static final String DEBUG_PROPERTY = "AgendaMultiTranslationMetricMaxDebug";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));
  public static int DEFAULT_WINDOW_SIZE = 2;
  static final int windowSize = DEFAULT_WINDOW_SIZE;
  final EvaluationMetric<TK, FV> metric;

  /**
	 * 
	 */
  public AgendaMultiTranslationMetricMax(EvaluationMetric<TK, FV> metric) {
    this.metric = metric;
  }

  @Override
  public List<ScoredFeaturizedTranslation<TK, FV>> maximize(
      NBestListContainer<TK, FV> nbest) {

    RecombinationHash<MultiTranslationState<TK, FV>> recombinationHash = new RecombinationHash<MultiTranslationState<TK, FV>>(
        new MetricBasedRecombinationFilter<TK, FV>(metric));

    List<List<ScoredFeaturizedTranslation<TK, FV>>> nbestLists = nbest
        .nbestLists();

    PriorityQueue<MultiTranslationState<TK, FV>> agenda = new PriorityQueue<MultiTranslationState<TK, FV>>(
        1000, new Comparator<MultiTranslationState<TK, FV>>() {
          @Override
          public int compare(MultiTranslationState<TK, FV> o1,
              MultiTranslationState<TK, FV> o2) {
            return -o1.compareTo(o2);
          }
        });

    agenda.add(new MultiTranslationState<TK, FV>(nbest, metric));
    recombinationHash.put(agenda.peek());
    int recombinations = 0;
    int hypothesesGenerated = 0;
    int depthHead = -1;
    while (!agenda.isEmpty()) {
      MultiTranslationState<TK, FV> mts = agenda.remove();

      if (!recombinationHash.isBest(mts)) {
        continue;
      }

      if (mts.depth <= depthHead - windowSize) {
        continue;
      }

      if (mts.depth > depthHead) {
        depthHead = mts.depth;
      }

      if (mts.depth + 1 == nbestLists.size()) {
        if (DEBUG) {
          System.err.printf("Recombinations: %.3f %% (%d/%d)\n", recombinations
              * 100.0 / hypothesesGenerated, recombinations,
              hypothesesGenerated);
        }
        return mts.selected();
      }
      List<ScoredFeaturizedTranslation<TK, FV>> nbestList = nbestLists
          .get(mts.depth + 1);
      for (ScoredFeaturizedTranslation<TK, FV> tran : nbestList) {
        MultiTranslationState<TK, FV> newMts = mts.append(tran);
        RecombinationHash.Status status = recombinationHash.queryStatus(newMts,
            true);
        hypothesesGenerated++;
        if (status == RecombinationHash.Status.COMBINABLE) {
          recombinations++;
          continue;
        } else if (status == RecombinationHash.Status.BETTER) {
          recombinations++;
        }
        agenda.add(newMts);
      }
      System.out.printf("depth: %d, agenda size: %d\n", mts.depth,
          agenda.size());
    }

    throw new RuntimeException(
        "Search error - agenda exhaused without reaching goal");
  }

}
