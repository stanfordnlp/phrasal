package edu.stanford.nlp.mt.decoder.util;

import java.util.List;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Factory for OutputSpace objects, which define the decoder output space.
 * 
 * @author Spence Green
 *
 */
public class OutputSpaceFactory {

  private OutputSpaceFactory() {}

  /**
   * Create an OutputSpace instance for a source input.
   * 
   * @param sourceInputId 
   * @param targets if null, don't constrain the output space
   * @param targetsArePrefixes
   * @param longestSourcePhrase
   * @param longestTargetPhrase 
   *    * @param wrapBoundary
   * @return
   */
  public static OutputSpace<IString,String> getOutputSpace(int sourceInputId, List<Sequence<IString>> targets, 
      boolean targetsArePrefixes, int longestSourcePhrase, int longestTargetPhrase, boolean wrapBoundary) {
    return getOutputSpace(sourceInputId, targets, targetsArePrefixes, longestSourcePhrase, longestTargetPhrase,
        wrapBoundary, false);
  }
  
  /**
   * Create an OutputSpace instance for a source input.
   * 
   * @param sourceInputId 
   * @param targets if null, don't constrain the output space
   * @param targetsArePrefixes
   * @param longestSourcePhrase
   * @param longestTargetPhrase 
   * @param wrapBoundary
   * @param allowIncompletePrefix
   * @return
   */
  public static OutputSpace<IString,String> getOutputSpace(int sourceInputId, List<Sequence<IString>> targets, 
      boolean targetsArePrefixes, int longestSourcePhrase, int longestTargetPhrase, boolean wrapBoundary,
      boolean allowIncompletePrefix) {
    if (wrapBoundary) {
      return new WrapBoundaryOutputSpace<>();
    
    } else if (targets == null || targets.size() == 0) {
      return new UnconstrainedOutputSpace<IString,String>();
    
    } else if (targetsArePrefixes) {
//      return new SoftPrefixOutputSpace(targets.get(0), sourceInputId);
      return new PrefixOutputSpace(targets.get(0), sourceInputId, allowIncompletePrefix);
    
    } else {
    //else if (targets != null && targets.size() > 0) {
      return new ConstrainedOutputSpace<IString,String>(targets, longestSourcePhrase, longestTargetPhrase);
    } 
    //else {
      //List<Integer> permutation = PreorderingAgreement.parsePermutation(permutationString);
      //return new PermutationConstrainedOutputSpace<IString, String>(permutation);
    //}
  }
}
