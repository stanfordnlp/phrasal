package edu.stanford.nlp.mt.base;

import java.util.*;

import edu.stanford.nlp.mt.tune.MultiTranslationMetricMax;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class ListTopMultiTranslationMetricMax<TK, FV> implements
    MultiTranslationMetricMax<TK, FV> {
  @Override
  public List<ScoredFeaturizedTranslation<TK, FV>> maximize(
      NBestListContainer<TK, FV> nbest) {
    List<ScoredFeaturizedTranslation<TK, FV>> selected = new LinkedList<ScoredFeaturizedTranslation<TK, FV>>();
    List<List<ScoredFeaturizedTranslation<TK, FV>>> nbestLists = nbest
        .nbestLists();

    for (List<ScoredFeaturizedTranslation<TK, FV>> nbestList : nbestLists) {
      selected.add(nbestList.get(0));
    }
    return selected;
  }
}
