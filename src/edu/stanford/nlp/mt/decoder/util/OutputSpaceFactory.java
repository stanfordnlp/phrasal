package edu.stanford.nlp.mt.decoder.util;

import java.util.List;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;

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
   * @param source
   * @param sourceInputId 
   * @param sequences if null, don't constrain the output space
   * @param longestPhrase
   * @param sequencesArePrefixes
   * @return
   */
  public static OutputSpace<IString,String> getOutputSpace(Sequence<IString> source, 
      int sourceInputId, List<Sequence<IString>> sequences, int longestPhrase, boolean sequencesArePrefixes) {
    if (sequences == null || sequences.size() == 0) {
      return new UnconstrainedOutputSpace<IString,String>();
    
    } else if (sequencesArePrefixes) {
      return new SoftPrefixOutputSpace<IString,String>(source, sequences.get(0), sourceInputId);
    
    } else {
      return new ConstrainedOutputSpace<IString,String>(sequences, longestPhrase);
    }
  }
}
