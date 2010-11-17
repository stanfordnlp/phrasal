package edu.stanford.nlp.mt.syntax.classifyde;

// Java imports
import java.io.*;
import java.util.*;

// JavaNLP imports
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;

// RA related imports
import edu.stanford.cs.ra.arguments.Argument;
import edu.stanford.cs.ra.arguments.ArgumentPolicy;
import edu.stanford.cs.ra.arguments.Flag;
import edu.stanford.cs.ra.RA;

public class DEinTextClassifier {
  @Argument("The trained DE classifier file (serialized)")
  @Argument.Switch("-classifier")
  @Argument.Policy(ArgumentPolicy.REQUIRED)
  static String classifierFile;

  @Argument("Use 6 class definition (experimental, not used in the WMT09 paper)")
  @Argument.Switch("-6class")
  static Flag sixclass;

  public static void main(String[] args) throws IOException {
    RA.begin(args, DEinTextClassifier.class);

    Properties props = StringUtils.argsToProperties(args);

    // (1) read in the trained classifier!
    LinearClassifier<String, String> classifier = LinearClassifier
        .readClassifier(classifierFile);

    // this is the set for "1st"
    String[] featArgs = { "-2feat", "true", "-revised", "true", "-ngram",
        "true", "-lastcharN", "true", "-lastcharNgram", "true", "-1st", "true",
        "-pword", "true", "-path", "true", "-percent", "true", "-ciLin",
        "true", "-topicality", "true", "-length", "true", };
    Properties featProps = StringUtils.argsToProperties(featArgs);
    Featurizer feat = new FullInformationFeaturizer();

    // (2) setting up the tree & sentence files
    String sentFile = props.getProperty("sentFile", null);
    String treeFile = props.getProperty("treeFile", null);
    SentTreeFileReader reader = new SentTreeFileReader(sentFile, treeFile);
    Tree parsedSent = null;

    Queue<Set<String>> cachedWordsBySent = new LinkedList<Set<String>>();

    while ((parsedSent = reader.next()) != null) {
      // Get the index of the DEs
      List<Integer> deIdxs = ExperimentUtils.getDEIndices(parsedSent.yield());
      Map<Integer, String> deIdxWithPredictedClass = new TreeMap<Integer, String>();
      /*
       * parsedSent.pennPrint(System.err); for(Set<String> s :
       * cachedWordsBySent) { System.err.println("------------------");
       * System.err.println(StringUtils.join(s, " ")); }
       * System.err.println("==================================");
       */
      for (int deIdx : deIdxs) {
        MutablePair<Integer, Integer> range = ExperimentUtils.getNPwithDERangeFromIdx(
            parsedSent, deIdx);
        if (range.first == -1) {
          System.err.println("WARNING: Flat tree. Don't mark");
          continue;
        }
        Set<String> cachedWords = ExperimentUtils
            .mergeAllSets(cachedWordsBySent);

        Counter<String> features = feat.extractFeatures(deIdx, range,
            parsedSent, featProps, cachedWords);
        Datum<String, String> d = new RVFDatum<String, String>(features);
        String predictedClass = classifier.classOf(d);
        // deIdxWithPredictedClass.put(deIdx,
        // ExperimentUtils.short5class(predictedClass));
        if (sixclass.isSet)
          deIdxWithPredictedClass.put(deIdx,
              ExperimentUtils.short6class(predictedClass));
        else
          deIdxWithPredictedClass.put(deIdx,
              ExperimentUtils.short5class(predictedClass));
      }
      List<String> newSentence = new ArrayList<String>();
      for (int i = 0; i < parsedSent.yield().size(); i++) {
        String pClass = deIdxWithPredictedClass.get(i);
        // TODO FIXME: yield() is not cached. is this not crazy slow?
        String currW = parsedSent.yield().get(i).value();
        if (pClass == null) {
          newSentence.add(currW);
        } else {
          StringBuilder sb = new StringBuilder();
          sb.append(currW).append("_").append(pClass);
          newSentence.add(sb.toString());
        }
      }
      System.out.println(StringUtils.join(newSentence, " "));

      // update cachedWordsBySent
      if (cachedWordsBySent.size() == ExperimentUtils.TOPICALITY_SENT_WINDOW_SIZE) {
        // pop one push current
        cachedWordsBySent.remove();
        cachedWordsBySent.add(ExperimentUtils.treeToSetWords(parsedSent));
      } else if (cachedWordsBySent.size() < ExperimentUtils.TOPICALITY_SENT_WINDOW_SIZE) {
        // push current
        cachedWordsBySent.add(ExperimentUtils.treeToSetWords(parsedSent));
      } else {
        throw new RuntimeException(
            "Queue can't be bigger than ExperimentUtils.TOPICALITY_SENT_WINDOW_SIZE");
      }
    }
  }
}
