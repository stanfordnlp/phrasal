package edu.stanford.nlp.mt.util;

import java.util.List;

public class KSR {
  public final int ksrTyped;
  public final int ksrTotal;
  public final Sequence<IString> nextPrefix;
  
  public KSR(int ksrTyped, int ksrTotal, Sequence<IString> nextPrefix) {
    this.ksrTyped = ksrTyped;
    this.ksrTotal = ksrTotal;
    this.nextPrefix = nextPrefix;
  }
  
  public static KSR getNextPrefix(List<RichTranslation<IString, String>> translations, 
      int nbest_size, Sequence<IString> reference, int previousPrefixSize) {
    
    int ksrTyped = 0;
    int ksrTotal = 0;
    Sequence<IString> nextPrefix = null;

    Sequence<IString> bestMatch = getBestMatch(translations, nbest_size, reference, previousPrefixSize);
    
    if(bestMatch == null || bestMatch.size() < previousPrefixSize) {
      for(int i = previousPrefixSize; i < reference.size(); ++i) {
        // space after the word: + 1 keystroke
        ksrTotal += reference.get(i).length() + 1;
      }
      ksrTyped = ksrTotal;
    } 
    int next = previousPrefixSize;
    
    if(bestMatch.size() < previousPrefixSize) {
      for(int i = previousPrefixSize; i < reference.size(); ++i) {
        ksrTotal += reference.get(i).length() + 1;
      }
      ksrTyped = ksrTotal;
      return new KSR(ksrTyped, ksrTotal, nextPrefix);
    }
    
    for( ; next < bestMatch.size(); ++next) {
      // count 1 key stroke for accepting a word
      ksrTyped += 1;
      ksrTotal += bestMatch.get(next).length() + 1;
    }
    
    if(reference.size() > next) {
      // type the next word
      int length = reference.get(next).length() + 1;
      ksrTyped += length;
      ksrTotal += length;
      if(reference.size() > next + 1) nextPrefix = bestMatch.concat(reference.subsequence(next, next + 1));// else we're finished
    } 
    
    return new KSR(ksrTyped, ksrTotal, nextPrefix);
  }

  private static Sequence<IString> getBestMatch(List<RichTranslation<IString, String>> translations, 
      int nbest_size, Sequence<IString> reference, int previousPrefixSize) {
    if(translations == null || translations.size() == 0) return null;
      
    Sequence<IString> bestMatch = null;
    int bestMatchSize = -1;
    int n = 1;
    for(RichTranslation<IString, String> entry : translations) {
      int matchSize = previousPrefixSize;
      while(entry.translation.size() > matchSize 
          && reference.size() > matchSize
          && entry.translation.get(matchSize).equals(reference.get(matchSize))) {
        ++matchSize;
      }
        
      matchSize = Math.min(matchSize, entry.translation.size());
      
      if(matchSize > bestMatchSize) {
        bestMatchSize = matchSize;
        bestMatch = entry.translation.subsequence(0, matchSize);
      }
      
      ++n;
      if(n > nbest_size) break;
    }
    
    return bestMatch;
  }


}
