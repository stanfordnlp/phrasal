package mt.translationtreebank;

import java.util.*;
import java.io.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.ling.*;

public class DEinTextClassifier {
  public static void main(String[] args) throws IOException {
    Properties props = StringUtils.argsToProperties(args);
    String classifierFile = props.getProperty("classifier", "/user/pichuan/javanlp/projects/mt/src/mt/translationtreebank/report/nonoracle/1st.ser.gz");
    String sixclassStr = props.getProperty("6class", "false");
    Boolean sixclass = Boolean.parseBoolean(sixclassStr);

    // (1) read in the trained classifier!
    LinearClassifier<String, String> classifier
      = LinearClassifier.readClassifier(classifierFile);

    // this is the set for "1st"
    String[] featArgs = {"-2feat", "true",
                         "-revised", "true",
                         "-ngram", "true",
                         "-lastcharN", "true",
                         "-lastcharNgram", "true",
                         "-1st", "true",
                         "-pword", "true",
                         "-path", "true",
                         "-percent", "true",
                         "-ciLin", "true",
                         "-topicality", "true",
                         "-length", "true",};
    Properties featProps = StringUtils.argsToProperties(featArgs);
    Featurizer feat = new FullInformationFeaturizer();

    // (2) setting up the tree & sentence files
    String sentFile = props.getProperty("sentFile", null);
    String treeFile = props.getProperty("treeFile", null);
    SentTreeFileReader reader = new SentTreeFileReader(sentFile, treeFile);
    Tree parsedSent = null;

    Queue<Set<String>> cachedWordsBySent = new LinkedList<Set<String>>();

    while((parsedSent=reader.next())!=null) {
      // Get the index of the DEs
      List<Integer> deIdxs = ExperimentUtils.getDEIndices(parsedSent.yield());
      List<String>  labels = new ArrayList<String>();
      Map<Integer, String> deIdxWithPredictedClass = new TreeMap<Integer, String>();
      /*
      parsedSent.pennPrint(System.err);
      for(Set<String> s : cachedWordsBySent) {
        System.err.println("------------------");
        System.err.println(StringUtils.join(s, " "));
      }
      System.err.println("==================================");
      */
      for (int deIdx : deIdxs) {
        Pair<Integer,Integer> range = ExperimentUtils.getNPwithDERangeFromIdx(parsedSent, deIdx);
        if (range.first == -1) {
          System.err.println("WARNING: Flat tree. Don't mark");
          continue;
        }
        Tree chNPTree = TranslationAlignment.getTreeWithEdges(parsedSent,range.first, range.second+1);
        Set<String> cachedWords = ExperimentUtils.mergeAllSets(cachedWordsBySent);
        
        Counter<String> features = feat.extractFeatures(deIdx, range, parsedSent, featProps, cachedWords);
        Datum d = new RVFDatum(features);
        String predictedClass = classifier.classOf(d);
        //deIdxWithPredictedClass.put(deIdx, ExperimentUtils.short5class(predictedClass));
        if (sixclass)
          deIdxWithPredictedClass.put(deIdx, ExperimentUtils.short6class(predictedClass));
        else
          deIdxWithPredictedClass.put(deIdx, ExperimentUtils.short5class(predictedClass));
      }
      List<String> newSentence = new ArrayList<String>();
      for (int i = 0; i < parsedSent.yield().size(); i++) {
        String pClass = deIdxWithPredictedClass.get(i);
        String currW = parsedSent.yield().get(i).word();
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
        throw new RuntimeException("Queue can't be bigger than ExperimentUtils.TOPICALITY_SENT_WINDOW_SIZE");
      }
    }
  }
}
