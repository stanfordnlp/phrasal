package mt.translationtreebank;

import java.util.*;
import java.io.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.*;

public class DEinTextClassifier {
  public static void main(String[] args) throws IOException {
    Properties props = StringUtils.argsToProperties(args);

    boolean reducedCategory = true;
    boolean nonOracleTree = true;
    List<TreePair> treepairs = ExperimentUtils.readAnnotatedTreePairs(reducedCategory, nonOracleTree);
    int npid = 0;

    Featurizer featurizer = new FullInformationFeaturizer();

    // Build training set
    GeneralDataset trainDataset = new Dataset();

    for(TreePair validSent : treepairs) {
      for (int deIdxInSent : validSent.NPwithDEs_deIdx_set) {
        String label = validSent.NPwithDEs_categories.get(deIdxInSent);
        if (!ExperimentUtils.is5class(label)) {
          continue;
        }
        List<String> featureList = featurizer.extractFeatures(deIdxInSent, validSent, props);
        Datum<String, String> d = new BasicDatum(featureList, label);
        trainDataset.add(d);
      }
    }

    LinearClassifierFactory<String, String> factory = new LinearClassifierFactory<String, String>();
    LinearClassifier<String, String> classifier
      = (LinearClassifier<String, String>)factory.trainClassifier(trainDataset);

  }
}
