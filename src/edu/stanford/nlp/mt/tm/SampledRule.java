package edu.stanford.nlp.mt.tm;

import java.util.Arrays;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.MurmurHash;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.QueryResult;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.SimpleSequence;

/**
 * A rule sampled from the bitext.
 * 
 * @author Spence Green
 *
 */
public class SampledRule {
  public final int srcStartInclusive;
  public final int srcEndExclusive;
  public final int tgtStartInclusive;
  public final int tgtEndExclusive;
  public final int[] src;
  public final int[] tgt;
  public final QueryResult saEntry;
  public float lex_e_f = 0.0f;
  public float lex_f_e = 0.0f;
  private final int hashCode;
  
  /**
   * Constructor.
   * 
   * @param srcStartInclusive
   * @param srcEndExclusive
   * @param tgtStartInclusive
   * @param tgtEndExclusive
   * @param s
   */
  public SampledRule(int srcStartInclusive, int srcEndExclusive, int tgtStartInclusive, int tgtEndExclusive, 
      QueryResult s) {
    assert srcEndExclusive - srcStartInclusive > 0;
    assert tgtEndExclusive - tgtStartInclusive > 0;
    this.srcStartInclusive = srcStartInclusive;
    this.srcEndExclusive = srcEndExclusive;
    this.tgtStartInclusive = tgtStartInclusive;
    this.tgtEndExclusive = tgtEndExclusive;
    this.saEntry = s;
    this.src = Arrays.copyOfRange(s.sentence.source, srcStartInclusive, srcEndExclusive);
    this.tgt = Arrays.copyOfRange(s.sentence.target, tgtStartInclusive, tgtEndExclusive);
//    int fID = MurmurHash.hash32(src, src.length, 1);
//    int eID = MurmurHash.hash32(tgt, tgt.length, 1);
    
    // Tie the sampled rule to a sentence so that we don't double count.
    int[] hashArr = new int[] {s.sentenceId, srcStartInclusive, srcEndExclusive, tgtStartInclusive, 
        tgtEndExclusive};
    this.hashCode = MurmurHash.hash32(hashArr, hashArr.length, 1);
  }
  
  /**
   * Convert the sampled rule to a Phrasal translation rule.
   * 
   * @param scores
   * @param featureNames
   * @param e2f
   * @param index
   * @return
   */
  public Rule<IString> getRule(float[] scores, String[] featureNames,
      Sequence<IString> sourceSpan, int[] tm2Sys) {
    PhraseAlignment alignment = new PhraseAlignment(e2f());
    Sequence<IString> tgtSeq = toSystemSequence(tgt, tm2Sys);
    return new Rule<IString>(scores, featureNames,
        tgtSeq, sourceSpan, alignment);
  }
  
  /**
   * Convert the target span from translation model ids to system ids.
   * 
   * @param tmTokens
   * @param tm2Sys
   * @return
   */
  public static Sequence<IString> toSystemSequence(int[] tmTokens, int[] tm2Sys) {
    IString[] tokens = new IString[tmTokens.length];
    for (int i = 0; i < tmTokens.length; ++i) {
      int systemId = tm2Sys[tmTokens[i]];
      tokens[i] = new IString(systemId);
    }
    return new SimpleSequence<IString>(true, tokens);
  }

  @Override
  public String toString() {
    return String.format("%s => %s", Arrays.toString(src),
        Arrays.toString(tgt));
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if ( ! (o instanceof SampledRule)) {
      return false;
    } else {
      return hashCode == ((SampledRule) o).hashCode;
    }
  }
  
  @Override
  public int hashCode() {
    return hashCode;
  }

  /**
   * Return the rule-internal target-source alignment grid.
   * 
   * @return
   */
  public int[][] e2f() {
    int eDim = tgtEndExclusive - tgtStartInclusive;
    int[][] e2f = new int[eDim][];
    for (int i = tgtStartInclusive; i < tgtEndExclusive; ++i) {
      int localIdx = i - tgtStartInclusive;
      int[] e2fI = saEntry.sentence.e2f(i);
      int srcAlignDim = e2fI.length;
      e2f[localIdx] = new int[srcAlignDim];
      if (srcAlignDim > 0) {
        System.arraycopy(e2fI, 0, e2f[localIdx], 0, srcAlignDim);
        for (int j = 0; j < srcAlignDim; ++j) {
          e2f[localIdx][j] -= srcStartInclusive;
        }
      }
    }
    return e2f;
  }
  
  /**
   * Return the rule-internal source-target alignment grid.
   * 
   * @return
   */
  public int[][] f2e() {
    int fDim = srcEndExclusive - srcStartInclusive;
    int[][] f2e = new int[fDim][];
    for (int i = srcStartInclusive; i < srcEndExclusive; ++i) {
      int localIdx = i - srcStartInclusive;
      int[] f2eI = saEntry.sentence.f2e(i);
      int tgtAlignDim = f2eI.length;
      f2e[localIdx] = new int[tgtAlignDim];
      if (tgtAlignDim > 0) {
        System.arraycopy(f2eI, 0, f2e[localIdx], 0, f2e[localIdx].length);
        for (int j = 0; j < f2e[localIdx].length; ++j) {
          f2e[localIdx][j] -= tgtStartInclusive;
        }
      }
    }
    return f2e;
  }
}