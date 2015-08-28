package edu.stanford.nlp.mt.util;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit test for sequences.
 * 
 * @author Spence Green
 *
 */
public class SequenceTest {

  private static final String str = "Lorem ipsum sit dolor";
  
  @Test
  public void testPaddedSubsequence() {
    Sequence<IString> seq = IStrings.tokenize(str);
    assertTrue(seq.toString().equals(str));
    
    Sequence<IString> padStart = Sequences.wrapStart(seq, TokenUtils.START_TOKEN);
    assertTrue(TokenUtils.START_TOKEN.equals(padStart.subsequence(0, 1).get(0)));
    assertTrue(seq.equals(padStart.subsequence(1, padStart.size())));
   
    Sequence<IString> padEnd = Sequences.wrapEnd(seq, TokenUtils.END_TOKEN);
    assertTrue(TokenUtils.END_TOKEN.equals(padEnd.subsequence(seq.size(), padEnd.size()).get(0)));
    assertTrue(seq.equals(padEnd.subsequence(0, seq.size())));
    
    Sequence<IString> padStartEnd = Sequences.wrapStartEnd(seq, TokenUtils.START_TOKEN, TokenUtils.END_TOKEN);
    assertTrue(TokenUtils.START_TOKEN.equals(padStartEnd.subsequence(0, 1).get(0)));
    assertTrue(TokenUtils.END_TOKEN.equals(padStartEnd.subsequence(seq.size() + 1, padStartEnd.size()).get(0)));
    assertTrue(seq.equals(padStartEnd.subsequence(1, seq.size() + 1)));
  }
}
