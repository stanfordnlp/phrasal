package mt.translationtreebank;

import java.util.*;
import java.io.*;

interface Featurizer {
  public List<String> extractFeatures(int deIdxInSent, TreePair validSent, Properties props);
}
