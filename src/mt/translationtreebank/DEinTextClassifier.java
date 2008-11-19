package mt.translationtreebank;

import java.util.*;
import java.io.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.*;

public class DEinTextClassifier {
  public static void main(String[] args) throws IOException {
    Properties props = StringUtils.argsToProperties(args);

    // (1) read in the trained classifier!
    LinearClassifier<String, String> classifier
      = LinearClassifier.readClassifier("projects/mt/src/mt/translationtreebank/report/nonoracle/1st.ser.gz");

    // (2) setting up the tree & sentence files
    String sentFile = props.getProperty("sentFile", null);
    String treeFile = props.getProperty("treeFile", null);

    BufferedReader sentBR = new BufferedReader(new FileReader(sentFile));
    BufferedReader treeBR = new BufferedReader(new FileReader(treeFile));
  }
}
