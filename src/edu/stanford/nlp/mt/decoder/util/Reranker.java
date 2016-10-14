  package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.RichTranslation;

public class Reranker<TK, FV> {
    private final FeatureExtractor<TK, FV> featurizer;
    private final Scorer<FV> scorer;
    
    public Reranker(FeatureExtractor<TK, FV> featurizer, Scorer<FV> scorer) {
      this.featurizer = featurizer;
      this.scorer = scorer;
    }
    
    public void rerank(List<RichTranslation<TK, FV>> nbestList) {
      
      List<Featurizable<TK, FV>> featurizables  = new ArrayList<>();
      
      for(RichTranslation<TK, FV> entry : nbestList) {
        featurizables.add(entry.getFeaturizable());
      }
      
      List<List<FeatureValue<FV>>> featuresList = featurizer.rerankingFeaturize(featurizables);
      
      for(int i = 0; i < featuresList.size(); ++i) {
        List<FeatureValue<FV>> features = featuresList.get(i);
        RichTranslation<TK, FV> entry = nbestList.get(i);
        entry.score += scorer.getIncrementalScore(features);
        entry.features.addAll(features);
      }
      Collections.sort(nbestList);
    }

}
