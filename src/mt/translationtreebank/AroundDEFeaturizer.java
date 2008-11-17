package mt.translationtreebank;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.*;
import java.util.*;
import java.io.*;

class AroundDEFeaturizer implements Featurizer {
  public List<String> extractFeatures(int deIdxInSent, TreePair validSent, Properties props) {
    String dePosStr   = props.getProperty("dePos", "false");
    String revisedStr   = props.getProperty("revised", "false");
    Boolean dePos = Boolean.parseBoolean(dePosStr);
    Boolean revised = Boolean.parseBoolean(revisedStr);

    Pair<Integer, Integer> chNPrange = validSent.parsedNPwithDEs_deIdx.get(deIdxInSent);
    Tree chTree = validSent.chParsedTrees.get(0);
    Tree chNPTree = TranslationAlignment.getTreeWithEdges(chTree,chNPrange.first, chNPrange.second+1);
    int deIdxInPhrase = deIdxInSent-chNPrange.first;
    Tree maskedChNPTree = ExperimentUtils.maskIrrelevantDEs(chNPTree, deIdxInPhrase);
    Sentence<TaggedWord> npYield = maskedChNPTree.taggedYield();

    // make feature list
    List<String> featureList = new ArrayList<String>();
    StringBuilder sb = new StringBuilder();
    if (dePos) {
      sb = new StringBuilder();
      String deTag = npYield.get(deIdxInPhrase).tag();
      sb.append("dePos:").append(deTag);
      System.err.println("adding\t"+sb.toString());
      featureList.add(sb.toString());
    
      if (revised) {
        if ("DEC".equals(deTag)) {
          if (ExperimentUtils.hasVApattern(maskedChNPTree))
            featureList.add("hasVA");
        } else if ("DEG".equals(deTag)) {
          if (ExperimentUtils.hasADJPpattern(maskedChNPTree))
            featureList.add("hasADJP");
          if (ExperimentUtils.hasQPpattern(maskedChNPTree))
            featureList.add("hasQP");
          if (ExperimentUtils.hasNPPNpattern(maskedChNPTree))
            featureList.add("hasNPPN");
        }
      }
    }

    return featureList; 
  }
}

