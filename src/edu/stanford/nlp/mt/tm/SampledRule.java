package edu.stanford.nlp.mt.tm;

import java.util.Arrays;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.MurmurHash;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.SentencePair;
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
  public final SentencePair saEntry;
  public double lex_e_f = 0.0;
  public double lex_f_e = 0.0;
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
      SentencePair s) {
    assert srcEndExclusive - srcStartInclusive > 0;
    assert tgtEndExclusive - tgtStartInclusive > 0;
    this.srcStartInclusive = srcStartInclusive;
    this.srcEndExclusive = srcEndExclusive;
    this.tgtStartInclusive = tgtStartInclusive;
    this.tgtEndExclusive = tgtEndExclusive;
    this.saEntry = s;
    this.src = new int[srcEndExclusive - srcStartInclusive];
    for (int i = 0; i < src.length; ++i) {
      src[i] = s.source(srcStartInclusive + i);
    }
    this.tgt = new int[tgtEndExclusive - tgtStartInclusive];
    for (int i = 0; i < tgt.length; ++i) {
      tgt[i] = s.target(tgtStartInclusive + i);
    }
    
    // Tie the SampledRule to a sentence so that we don't double count.
    int[] hashArr = new int[] {s.srcStartInclusive, srcStartInclusive, srcEndExclusive, tgtStartInclusive, 
        tgtEndExclusive};
    this.hashCode = MurmurHash.hash32(hashArr, hashArr.length, 1);
  }
  
  public int sourceLength() { return src.length; }
  
  public int targetLength() { return tgt.length; }
  
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
      int[] e2fI = saEntry.e2f(i);
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
      int[] f2eI = saEntry.f2e(i);
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
  
  public int[] f2e(int i) {
    int srcIndex = srcStartInclusive + i;
    if (srcIndex < 0 || srcIndex >= srcEndExclusive) throw new ArrayIndexOutOfBoundsException();
    return saEntry.isSourceUnaligned(srcIndex) ? new int[0] : saEntry.f2e(srcIndex);
  }
  
  /**
   * Generate an integer key from the alignment template.
   * 
   * @return
   */
  public int getAlignmentKey() {
    int key = srcStartInclusive + tgtEndExclusive;
    for (int i = srcStartInclusive; i < srcEndExclusive; ++i) {
      if (saEntry.isSourceUnaligned(i)) {
        key = i % 2 == 0 ? key << (i % 16) : key >> (i % 8);
      } else {
        int[] f2eI = saEntry.f2e(i);
        key *= MurmurHash.hash32(f2eI, f2eI.length, 1);
      }
    }
    return key;
  }
}