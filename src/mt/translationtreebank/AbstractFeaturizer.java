package mt.translationtreebank;

import java.util.*;
import java.io.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;

abstract class AbstractFeaturizer implements Featurizer {
  public List<String> extractFeatures(int deIdxInSent, TreePair validSent, Properties props) {
    return extractFeatures(deIdxInSent, validSent, props, null);
  }
  public List<String> extractFeatures(int deIdxInSent, TreePair validSent, Properties props, Set<String> cachedWords) {
    Pair<Integer, Integer> chNPrange = validSent.parsedNPwithDEs_deIdx.get(deIdxInSent);
    Tree chTree = validSent.chParsedTrees.get(0);
    return extractFeatures(deIdxInSent, chNPrange, chTree, props, cachedWords);
  }
  abstract public List<String> extractFeatures(int deIdxInSent, Pair<Integer, Integer> chNPrange , Tree chTree, Properties props, Set<String> cachedWords);
}
