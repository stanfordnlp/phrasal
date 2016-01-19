package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.util.MurmurHash2;
import edu.stanford.nlp.mt.util.RichTranslation;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Heuristics for merging, filtering, and deduplicating nbest lists.
 * 
 * @author Spence Green
 *
 */
public final class NbestListUtils {

  private NbestListUtils() {}
  
  /**
   * Baseline implementation. Augments the "standard" list with alternatives.
   * 
   * @param l1
   * @param l2
   * @return
   */
  public static <TK,FV> List<RichTranslation<TK,FV>> mergeAndDedup(List<RichTranslation<TK,FV>> standard,
      List<RichTranslation<TK,FV>> alt, int maxAltItems) {
    
    IntSet hashCodeSet = new IntOpenHashSet(standard.size());
    for (RichTranslation<TK,FV> s : standard) {
      hashCodeSet.add(derivationHashCode(s.getFeaturizable().derivation));
    }
    
    List<RichTranslation<TK,FV>> returnList = new ArrayList<>(standard);
    for (int i = 0, sz = Math.min(maxAltItems, alt.size()); i < sz; ++i) {
      RichTranslation<TK,FV> t = alt.get(i);
      int hashCode = derivationHashCode(t.getFeaturizable().derivation);
      if (! hashCodeSet.contains(hashCode)) returnList.add(t);
    }
    Collections.sort(returnList);
    
    return returnList;
  }
  
  
  // TODO(spenceg) Incorporate the prefix. Add uniques off the end of the prefix
  
  
  private static <TK,FV> int derivationHashCode(Derivation<TK,FV> d) {
    int[] hashCodes = new int[d.depth];
    int i = 0;
    while (d.rule != null) {
      hashCodes[i++] = d.rule.hashCode();
      d = d.parent;
    }
    return MurmurHash2.hash32(hashCodes, hashCodes.length, 1);
  }
}
