  package edu.stanford.nlp.mt.decoder.util;

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
    private static final Logger logger = LogManager.getLogger(DiverseNbestDecoder.class.getName());
    private final FeatureExtractor<TK, FV> featurizer;
    private final Scorer<FV> scorer;
    
    public Reranker(FeatureExtractor<TK, FV> featurizer, Scorer<FV> scorer) {
      this.featurizer = featurizer;
      this.scorer = scorer;
    }
    
    public void rerank(List<RichTranslation<TK, FV>> nbestList) {
      List<FeatureValue<FV>> features;
      
      for(RichTranslation<TK, FV> entry : nbestList) {
        Featurizable<TK, FV> featurizable = entry.getFeaturizable();
        features = featurizer.rerankingFeaturize(featurizable);
        entry.score += scorer.getIncrementalScore(features);

        entry.features.addAll(features);
        
      }
      
      Collections.sort(nbestList);
      
      // todo: reranking
    }

}
