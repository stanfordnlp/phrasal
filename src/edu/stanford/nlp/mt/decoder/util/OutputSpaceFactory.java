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
   * @param sequences
   * @param longestPhrase
   * @param sequencesArePrefixes
   * @return
   */
  public static OutputSpace<IString,String> getOutputSpace(Sequence<IString> source, 
      List<Sequence<IString>> sequences, int longestPhrase, boolean sequencesArePrefixes) {
    if (sequences == null) {
      return new UnconstrainedOutputSpace<IString,String>();
    
    } 
//    else if (sequencesArePrefixes) {
//      return new SoftPrefixOutputSpace<IString,String>(source, sequences);
//    
//    } 
    else {
      return new EnumeratedConstrainedOutputSpace<IString,String>(sequences, longestPhrase);
    }
  }
}
