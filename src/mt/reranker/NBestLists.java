package mt.reranker;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.TreeMap;

/**
 * This class reads in the original N-best (or References) lists sentences.
 * Since the feature extraction is already done in the <code>LegacyFeatureExtractor</code> class,
 * these sentences are only used to output the predicted sentences,
 * or to calculate the BLEU scores.
 * TODO: Since this class is used for reading in N-best lists as well as reference lists, it should be renamed.
 *  
 * @author Pi-Chuan Chang
 */
public class NBestLists {
  public static final String N_THRESH_PROP = "nThresh";
  public static final String DEFAULT_N_THRESH= null;
  Map<Integer, Map<Integer,String>> nbestMap
          = new TreeMap<Integer, Map<Integer, String>>();

  public static NBestLists load(String file) throws IOException {
    String n = System.getProperty(N_THRESH_PROP, DEFAULT_N_THRESH);
    int nThresh = Integer.MAX_VALUE;
    if (n!=null)
      nThresh = Integer.parseInt(n);
    System.err.println("Max N = "+nThresh);

    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
    int lineNum = 1;
    NBestLists nbests = new NBestLists();
    for (String line; (line = br.readLine()) != null; lineNum++) {
      String[] fields = line.split("\\t");
      if (fields.length != 2) {
        throw new RuntimeException
                ("format of nbest lists file: sentId and hypId should be separated from the sentence using a TAB.\n");
      }
      String[] sIdPair = fields[0].split(",");
      int dataPt = Integer.parseInt(sIdPair[0]);
      int hypId = Integer.parseInt(sIdPair[1]);
      if (hypId >= nThresh) continue;
      Map<Integer, String> sents = nbests.nbestMap.get(dataPt);
      if (sents == null) sents = new TreeMap<Integer, String>();
      sents.put(hypId, fields[1]);
      nbests.nbestMap.put(dataPt, sents);
    }
    return nbests;
  }

  public static void main(String[] args) throws IOException{
    NBestLists nbests = NBestLists.load("nbests");
    System.err.println("blah");
  }
}
