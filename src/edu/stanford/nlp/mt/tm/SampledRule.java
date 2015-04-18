package edu.stanford.nlp.mt.tm;

import java.util.Arrays;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.MurmurHash;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.QueryResult;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TranslationModelIndex;

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
  public final QueryResult s;
  public float log_lex_e_f = Float.MAX_VALUE;
  public float log_lex_f_e = Float.MAX_VALUE;
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
    this.srcStartInclusive = srcStartInclusive;
    this.srcEndExclusive = srcEndExclusive;
    this.tgtStartInclusive = tgtStartInclusive;
    this.tgtEndExclusive = tgtEndExclusive;
    this.s = s;
    this.src = Arrays.copyOfRange(s.sentence.source, srcStartInclusive, srcEndExclusive);
    this.tgt = Arrays.copyOfRange(s.sentence.target, tgtStartInclusive, tgtEndExclusive);
    int fID = MurmurHash.hash32(src, src.length, 1);
    int eID = MurmurHash.hash32(tgt, tgt.length, 1);
    int[] fe = new int[] {fID, eID};
    this.hashCode = MurmurHash.hash32(fe, fe.length, 1);
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
  public Rule<IString> getRule(float[] scores, String[] featureNames, TranslationModelIndex index) {
    PhraseAlignment alignment = new PhraseAlignment(e2f());
    Sequence<IString> srcSeq = IStrings.toIStringSequence(src, index);
    Sequence<IString> tgtSeq = IStrings.toIStringSequence(tgt, index);
    return new Rule<IString>(scores, featureNames,
        tgtSeq, srcSeq, alignment);
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
   * 
   * @return
   */
  public int[][] e2f() {
    // TODO Auto-generated method stub
    return null;
  }
}