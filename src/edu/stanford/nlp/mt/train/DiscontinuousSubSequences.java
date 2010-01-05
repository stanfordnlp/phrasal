package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.SimpleSequence;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Set;

public class DiscontinuousSubSequences {

  public static Sequence<IString> subsequence(Sequence<IString> seq, BitSet bs, Map<Integer,Integer> align) {
    return subsequence(seq,bs,align,-1);
  }

  public static Sequence<IString> subsequence(Sequence<IString> seq, BitSet bs, Map<Integer,Integer> align, int maxGaps) {
    List<IString> toks = new ArrayList<IString>(bs.cardinality()+3);
    if(align != null)
      align.clear();
    int pos = -1;
    int gaps = 0;
    for(;;) {
      pos = bs.nextSetBit(pos+1);
      if(pos == -1)
        break;
      if(toks.size() > 0 && !bs.get(pos-1)) {
        if(gaps++ == maxGaps)
          return null;
        toks.add(DTUPhraseExtractor.GAP_STR);
      }
      if(align != null)
        align.put(pos, toks.size());
      toks.add(seq.get(pos));
    }
    return new SimpleSequence<IString>(true, toks.toArray(new IString[toks.size()]));
  }
}
