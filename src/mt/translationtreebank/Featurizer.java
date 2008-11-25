package mt.translationtreebank;

import java.util.*;
import java.io.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;

interface Featurizer {
  public List<String> extractFeatures(int deIdxInSent, TreePair validSent, Properties props);
  public List<String> extractFeatures(int deIdxInSent, TreePair validSent, Properties props, Set<String> cachedWords);
  public List<String> extractFeatures(int deIdxInSent, Pair<Integer, Integer> chNPrange , Tree chTree, Properties props, Set<String> cachedWords);
}
