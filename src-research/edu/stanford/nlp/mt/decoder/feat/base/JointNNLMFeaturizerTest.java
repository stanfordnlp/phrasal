/**
 * 
 */
package edu.stanford.nlp.mt.decoder.feat.base;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

import edu.stanford.nlp.mt.lm.NNLMState;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * @author Thang Luong
 *
 */
public class JointNNLMFeaturizerTest {
  public static final String PREFIX = ""; // "projects/mt/"; //
  public static final String nplmFile = PREFIX + "test/inputs/src5.tgt3.nplm";
  
  
  @Test
  public void test() throws IOException {
    JointNNLMFeaturizer nplmFeat = new JointNNLMFeaturizer("nnlm="+nplmFile, "cache=0", "id=srcNPLM");
    String srcStr = "建设 法治 政府 , 就 是 为了 使 行政 权力 授予 有 据 , 行使 有规 , 监督 有效 , 做到 依法 治官 , 依法 治权 , 防止 行政 权力 的 缺失 和 滥用 , 带动 全 社会 尊重 法律 , 遵守 法律 , 维护 法律 \" 。";
    Sequence<IString> srcSent = IString.getIStringSequence(srcStr.split("\\s+"));
    String tgtStr = "<s> construction if so law government ,";
    Sequence<IString> tgtSent = IString.getIStringSequence(tgtStr.split("\\s+"));
    
    // f=政府 , ||| government , ||| (0) (1)
    int srcStartPos = 2, tgtStartPos = 5; // 
    PhraseAlignment alignment = PhraseAlignment.getPhraseAlignment("(0) (1)");
    
    NNLMState state = nplmFeat.getScoreMulti(tgtStartPos, tgtSent, srcStartPos, srcSent, alignment);
    double score = state.getScore();
    assertEquals("[378, 44]", state.toString());
    assertEquals(-9.93789005279541, score, 1e-6);
    
    state = nplmFeat.getScore(tgtStartPos, tgtSent, srcStartPos, srcSent, alignment);
    score = state.getScore();
    assertEquals("[378, 44]", state.toString());
    assertEquals(-9.93789005279541, score, 1e-6);
  }

}
