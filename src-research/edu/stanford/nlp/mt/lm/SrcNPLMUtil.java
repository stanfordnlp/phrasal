/**
 * 
 */
package edu.stanford.nlp.mt.lm;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TokenUtils;

/**
 * @author Thang Luong
 *
 */
public class SrcNPLMUtil {
	public final static int startId = TokenUtils.START_TOKEN.id; // index of <s>
	
	public static double getNPLMScore(NPLMLanguageModel nplm, Featurizable<IString, String> f){
		return 0.0;
	}
	
	/**
   * Find average of source positions that correspond to the current tgtPos 
   * w.r.t to the given phrase alignment.
   * The input target position and the output source position are relative
   * positions within the current phrase.
   * 
   * @param tgtPos
   * @param alignment
   * @return
   */
  public static int findSrcAvgPos(int tgtPos, PhraseAlignment alignment){
    int tgtLength = alignment.size();
    int srcAvgPos = -1;
    int distance = 0;
    
    // System.err.println("findSrcAvgPos tgtPos=" + tgtPos + ", alignment=" + alignment);
    int[] alignments;
    while(true){
      // look right
      int rightPos = tgtPos + distance;
      boolean isStop = true;
      if(rightPos<tgtLength){
        alignments = alignment.t2s(rightPos);
        if (alignments != null) {
          // System.err.print("right " + rightPos + ": " + Util.intArrayToString(alignments));
          srcAvgPos = ArrayMath.mean(alignments);
          break;
        }
        
        isStop = false;
      }
      
      // look left
      int leftPos = tgtPos - distance;
      if(leftPos>=0 && leftPos!=rightPos){
        alignments = alignment.t2s(leftPos);
        if (alignments != null) {
          // System.err.print("left " + leftPos + ": " + Util.intArrayToString(alignments));
          srcAvgPos = ArrayMath.mean(alignments);
          break;
        }
        
        isStop = false;
      }
      
      distance++;
      if (isStop) break;
    }
    
    return srcAvgPos;
  }
}
