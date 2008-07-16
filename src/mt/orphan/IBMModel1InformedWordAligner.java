package mt.orphan;

import java.util.Set;

import mt.base.IBMModel1;
import edu.stanford.nlp.util.IString;
import mt.base.Sequence;
import mt.train.WordAligner;
import mt.train.WordAlignment;

/**
 * This class makes use of the GIZA++ style alignment information in SymmetricalWordAlignment to limit the
 * possible English word alignment choice, and then use IBM Model1 score to pick the best one
 * Notice it's not an "IBMModel1Aligner", because it first constrains the possibilities to only the
 * indices provided in the SymmetricalWordAlignment information
 * 
 * @author Pi-Chuan Chang
 */

public class IBMModel1InformedWordAligner implements WordAligner{

  IBMModel1 model1 = null;

  private static final boolean DETAILED_DEBUG = false;

  /**
   * @param filename the model 1 file
   **/
  public IBMModel1InformedWordAligner(String filename) {
    try {
      long startTimeMillis = System.currentTimeMillis();
      System.err.println("loading IBM Model 1 for IBMModel1InformedWordAligner from file: "+filename);
      model1 = IBMModel1.load(filename);
      double totalSecs = (System.currentTimeMillis() - startTimeMillis)/1000.0;
      System.err.printf("done loading IBM Model. Time = %.3f secs\n", totalSecs);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("IBMModel1 generated an exception");
    }
  }

  /**
   * @param model1 a pre-loaded IBMModel1
   **/
  public IBMModel1InformedWordAligner(IBMModel1 model1) {
    this.model1 = model1;
  }

  /**
   * constructor with a hard-coded IBM model 1:
   * "/scr/nlp/data/gale2/acl08dd/resources/model1/zh_en.model.actual.t1"
   */
  public IBMModel1InformedWordAligner() {
    this("/scr/nlp/data/gale2/acl08dd/resources/model1/zh_en.model.actual.t1");
  }

  public int getAlignedEnglishIndex(WordAlignment sp, int foreignIdx) {
    //private int getAlignedEnglishWordIndexFromSet(IString chWord, Set<Integer> englishWordsIndices, Sequence<IString> e) {
    IString chWord = sp.f().get(foreignIdx);
    Set<Integer> englishWordsIndices = sp.f2e(foreignIdx);
    Sequence<IString> e = sp.e();
    
    double max = Double.MIN_VALUE;
    int max_idx = -1;
    if (DETAILED_DEBUG) System.err.println("chWord="+chWord);
    for(int eidx : englishWordsIndices) {
      IString eWord= e.get(eidx);
      double score = model1.score(chWord, eWord);
      if (max < score) {
        max_idx = eidx;
        if (DETAILED_DEBUG) System.err.println("max_idx="+max_idx);
        max = score;
        if (DETAILED_DEBUG) System.err.println("max="+max);
      }
    }
    if (DETAILED_DEBUG) {
      if (max_idx >=0) System.err.println("enWord="+e.get(max_idx));
    }
    return max_idx;
  }
}
