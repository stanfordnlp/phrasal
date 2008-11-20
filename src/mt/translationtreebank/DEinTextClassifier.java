package mt.translationtreebank;

import java.util.*;
import java.io.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.ling.*;

public class DEinTextClassifier {
  public static void main(String[] args) throws IOException {
    Properties props = StringUtils.argsToProperties(args);

    // (1) read in the trained classifier!
    LinearClassifier<String, String> classifier
      = LinearClassifier.readClassifier("projects/mt/src/mt/translationtreebank/report/nonoracle/1st.ser.gz");

    // this is the set for "1st"
    String[] featArgs = {"-2feat", "true",
                         "-revised", "true",
                         "-ngram", "true",
                         "-lastcharN", "true",
                         "-lastcharNgram", "true",
                         "-1st", "true",};
    Properties featProps = StringUtils.argsToProperties(featArgs);
    Featurizer feat = new FullInformationFeaturizer();

    // (2) setting up the tree & sentence files
    String sentFile = props.getProperty("sentFile", null);
    String treeFile = props.getProperty("treeFile", null);
    SentTreeFileReader reader = new SentTreeFileReader(sentFile, treeFile);
    Tree parsedSent = null;
    while((parsedSent=reader.next())!=null) {
      // Get the index of the DEs
      List<Integer> deIdxs = ExperimentUtils.getDEIndices(parsedSent.yield());
      List<String>  labels = new ArrayList<String>();
      Map<Integer, String> deIdxWithPredictedClass = new TreeMap<Integer, String>();
      for (int deIdx : deIdxs) {
        Pair<Integer,Integer> range = ExperimentUtils.getNPwithDERangeFromIdx(parsedSent, deIdx);
        if (range.first == -1) {
          throw new RuntimeException("Warning: skip for now");
          //continue;
        }
        Tree chNPTree = TranslationAlignment.getTreeWithEdges(parsedSent,range.first, range.second+1);
        chNPTree.pennPrint(System.err);
        System.err.println("==================================");
        List<String> features = feat.extractFeatures(deIdx, range, parsedSent, featProps);
        Datum d = new BasicDatum(features);
        String predictedClass = classifier.classOf(d);
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
    }
  }
}

class SentTreeFileReader {
  BufferedReader sentBR;
  BufferedReader treeBR;
  
  public SentTreeFileReader(String sentFile, String treeFile) throws IOException {
    sentBR = new BufferedReader(new FileReader(sentFile));
    treeBR = new BufferedReader(new FileReader(treeFile));
  }

  public Tree next() throws IOException {
    String sent = sentBR.readLine();
    String tree = treeBR.readLine();
    if (sent==null && tree==null) return null;
    if (sent==null && tree!=null) 
      throw new RuntimeException("sentFile is shorter than treeFile");
    if (sent!=null && tree==null) 
      throw new RuntimeException("sentFile is longer than treeFile");
    
    Tree t = Tree.valueOf(tree);
    List<Tree> leaves = t.getLeaves();
    String[] words = sent.trim().split("\\s+");

    if (leaves.size() != words.length) {
      System.err.println("sent size & tree size doesn't match:");
      System.err.println("SENT="+sent);
      System.err.println("TREE=");
      t.pennPrint(System.err);
      throw new RuntimeException();
    }

    // sanity check -- at least one of the words should match..
    boolean matched = false;
    for(int i = 0; i < leaves.size(); i++) {
      if (leaves.get(i).value().equals(words[i])) { 
        matched = true;
      } else
        leaves.get(i).setValue(words[i]);
    }
    
    if (!matched) {
      System.err.println("sent & tree doesn't even have one match in word");
      System.err.println("SENT="+sent);
      System.err.println("TREE="+tree);
      throw new RuntimeException();
    }

    return t;
  }
}
