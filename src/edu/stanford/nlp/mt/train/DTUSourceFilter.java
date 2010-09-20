package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.Sequences;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.base.TrieIntegerArrayIndex;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * @author Michel Galley
 */
public class DTUSourceFilter extends AbstractSourceFilter {

  private final int maxSpanF;

  public DTUSourceFilter(int maxPhraseLenF, int maxSpanF) {
    super(maxPhraseLenF, new TrieIntegerArrayIndex(0));
    this.maxSpanF = maxSpanF;
  }

  static class PartialBitSet {

    private static final int MAX_GAP = 2;

    final BitSet bs;
    int phraseStartPos, xStartPos, phraseEndPos;
    int xCount;

    public int hashCode() {
      return Arrays.hashCode(new int[] { bs.hashCode(), phraseStartPos,
          xStartPos, phraseEndPos });
    }

    public boolean equals(Object o) {
      if (!(o instanceof PartialBitSet))
        return false;
      PartialBitSet s = (PartialBitSet) o;
      return bs.equals(s.bs) && (xStartPos == s.xStartPos);
    }

    PartialBitSet(int phraseStartPos) {
      bs = new BitSet();
      bs.set(phraseStartPos);
      this.phraseStartPos = phraseStartPos;
      this.phraseEndPos = phraseStartPos;
      xStartPos = phraseEndPos + 1;
      xCount = 0;
    }

    PartialBitSet(PartialBitSet o) {
      bs = (BitSet) o.bs.clone();
      phraseStartPos = o.phraseStartPos;
      phraseEndPos = o.phraseEndPos;
      xStartPos = o.xStartPos;
      xCount = o.xCount;
    }

    PartialBitSet resizeNoGap() {
      if (xStartPos > phraseEndPos) {
        PartialBitSet ns = new PartialBitSet(this);
        ++ns.xStartPos;
        ++ns.phraseEndPos;
        ns.bs.set(phraseEndPos);
        return ns;
      }
      return null;
    }

    PartialBitSet resizeGap() {
      if (xStartPos > phraseEndPos && xCount == MAX_GAP)
        return null;
      PartialBitSet ns = new PartialBitSet(this);
      ++ns.phraseEndPos;
      return ns;
    }

    PartialBitSet closeGap() {
      if (xStartPos > phraseEndPos)
        return null;
      PartialBitSet ns = new PartialBitSet(this);
      ns.xStartPos = ns.phraseEndPos + 1;
      ++ns.xCount;
      return ns;
    }
  }

  @Override
  public void filterAgainstSentence(String fLine) {

    // Enumerate all sub-sequences of fLine and add them to sourcePhraseTable:
    fLine = fLine.trim();
    Sequence<IString> f = new SimpleSequence<IString>(true,
        IStrings.toIStringArray(fLine.split("\\s+")));
    Deque<PartialBitSet> oq = new LinkedList<PartialBitSet>();
    Set<PartialBitSet> cq = new HashSet<PartialBitSet>();
    for (int i = 0; i < f.size(); ++i)
      oq.add(new PartialBitSet(i));
    while (!oq.isEmpty()) {
      PartialBitSet s = oq.pop();
      if (s == null)
        continue;
      if (s.xStartPos > s.phraseEndPos && s.phraseEndPos <= f.size())
        if (!cq.add(s))
          continue;
      if (s.phraseEndPos - s.phraseStartPos + 1 > maxSpanF)
        continue;
      if (s.bs.cardinality() >= maxPhraseLenF)
        continue;
      oq.push(s.closeGap());
      if (s.phraseEndPos + 1 <= f.size()) {
        oq.push(s.resizeGap());
        oq.push(s.resizeNoGap());
      }
    }

    for (PartialBitSet s : cq) {
      Sequence<IString> fPhrase = DiscontinuousSubSequences.subsequence(f,
          s.bs, null, -1);
      if (fPhrase != null) {
        if (SHOW_PHRASE_RESTRICTION)
          System.err.printf("Restrict to dtu (i=%d,j=%d,M=%d): %s\n",
              s.phraseStartPos, s.phraseEndPos, maxPhraseLenF,
              fPhrase.toString());
        sourcePhraseTable.indexOf(Sequences.toIntArray(fPhrase), true);
      }
    }
    isEnabled = true;
  }

  public TrieIntegerArrayIndex getSourceTrie() {
    assert (sourcePhraseTable != null);
    return (TrieIntegerArrayIndex) sourcePhraseTable;
  }
}
