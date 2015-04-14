package edu.stanford.nlp.mt.tm;

import java.util.Arrays;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.MurmurHash;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.SentenceSample;
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
  public final int srcI;
  public final int srcJ;
  public final int tgtI;
  public final int tgtJ;
  public final SentenceSample s;
  public final int fID;
  public final int eID;
  private final int hashCode;
  public int[][] e2f;
  
  /**
   * Constructor.
   * 
   * @param i
   * @param j
   * @param a
   * @param b
   * @param s
   */
  public SampledRule(int i, int j, int a, int b, SentenceSample s, int[][] e2f) {
    srcI = i;
    srcJ = j;
    tgtI = a;
    tgtJ = b;
    this.s = s;
    int[] f = Arrays.copyOfRange(s.sentence.source, i, j);
    int[] e = Arrays.copyOfRange(s.sentence.target, a, b);
    this.fID = MurmurHash.hash32(f, f.length, 1);
    this.eID = MurmurHash.hash32(e, e.length, 1);
    int[] fe = new int[] {fID, eID};
    this.hashCode = MurmurHash.hash32(fe, fe.length, 1);
    this.e2f = e2f;
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
      int[][] e2f, TranslationModelIndex index) {
    PhraseAlignment alignment = new PhraseAlignment(e2f);
    int[] src = Arrays.copyOfRange(s.sentence.source, srcI, srcJ+1);
    Sequence<IString> srcSeq = IStrings.toIStringSequence(src, index);
    int[] tgt = Arrays.copyOfRange(s.sentence.target, tgtI, tgtJ+1);
    Sequence<IString> tgtSeq = IStrings.toIStringSequence(tgt, index);
    return new Rule<IString>(scores, featureNames,
        tgtSeq, srcSeq, alignment);
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
}