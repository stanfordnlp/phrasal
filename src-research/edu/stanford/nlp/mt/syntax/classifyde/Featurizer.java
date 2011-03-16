package edu.stanford.nlp.mt.syntax.classifyde;

import java.util.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.trees.*;

interface Featurizer {
  public Counter<String> extractFeatures(int deIdxInSent,
      AnnotatedTreePair validSent, Properties props);

  public Counter<String> extractFeatures(int deIdxInSent,
      AnnotatedTreePair validSent, Properties props, Set<String> cachedWords);

  public Counter<String> extractFeatures(int deIdxInSent,
      Pair<Integer, Integer> chNPrange, Tree chTree, Properties props,
      Set<String> cachedWords);
}
