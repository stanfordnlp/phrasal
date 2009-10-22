package mt.train;

import mt.base.Sequence;
import mt.base.IString;
import mt.base.SimpleSequence;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.BitSet;

public class DiscontinuousSubSequences {

  private static final int BUFFER_SIZE = 100;

  static List<IString> toks = new ArrayList<IString>(BUFFER_SIZE);

  public static Sequence<IString> subsequence(Sequence<IString> seq, BitSet bs, Map<Integer,Integer> align) {
    toks.clear();
    if(align != null)
      align.clear();
    int pos = -1;
    for(;;) {
      pos = bs.nextSetBit(pos+1);
      if(pos == -1)
        break;
      if(toks.size() > 0 && !bs.get(pos-1)) {
        toks.add(DTUPhraseExtractor.GAP_STR);
      }
      if(align != null)
        align.put(pos, toks.size());
      toks.add(seq.get(pos));
    }
    return new SimpleSequence<IString>(true, toks.toArray(new IString[toks.size()]));
  }

}
