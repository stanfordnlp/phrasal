/**
 * 
 */
package edu.stanford.nlp.mt.decoder.feat.base;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.lm.NNLMState;

/**
 * @author Thang Luong
 *
 */
public class TargetNNLMFeaturizerTest {
  public static final String PREFIX = ""; // "projects/mt/"; //
  public static final String nplmFile = PREFIX + "test/inputs/tgt3.nplm";
  
  
  @Test
  public void test() throws IOException {
    TargetNNLMFeaturizer nplmFeat = new TargetNNLMFeaturizer("nnlm="+nplmFile, "cache=0", "id=srcNPLM");
    String tgtStr = "<s> construction if so law government ,";
    Sequence<IString> tgtSent = IString.getIStringSequence(tgtStr.split("\\s+"));
    int tgtStartPos = 5; // 
    
    NNLMState state = nplmFeat.getScoreMulti(tgtStartPos, tgtSent);
    double score = state.getScore();
    assertEquals("[378, 44]", state.toString());
    System.err.println(score);
    assertEquals(-9.937389999731584, score, 1e-5);
    
    state = nplmFeat.getScore(tgtStartPos, tgtSent);
    score = state.getScore();
    assertEquals("[378, 44]", state.toString());
    System.err.println(score);
    assertEquals(-9.937389999731584, score, 1e-5);
  }

}
