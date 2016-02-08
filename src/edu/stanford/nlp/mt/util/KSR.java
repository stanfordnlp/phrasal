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

    Match bestMatch = getBestMatch(translations, nbest_size, reference, previousPrefixSize);
    
    if(bestMatch.fullMatch == null || bestMatch.fullMatch.size() < previousPrefixSize) {
      for(int i = previousPrefixSize; i < reference.size(); ++i) {
        // space after the word: + 1 keystroke
        ksrTotal += reference.get(i).length() + 1;
      }
      ksrTyped = ksrTotal;
      return new KSR(ksrTyped, ksrTotal, nextPrefix);
    } 
    
    // count 1 key stroke for accepting a character sequence of any length
    if(bestMatch.fullMatch.size() > previousPrefixSize || bestMatch.prefixMatchSize > 0)
      ++ksrTyped;
    
    int next = previousPrefixSize;
    for( ; next < bestMatch.fullMatch.size(); ++next) {
      ksrTotal += bestMatch.fullMatch.get(next).length() + 1;
    }

    if(reference.size() > next) {
      // type the next word
      int length = reference.get(next).length() + 1;
      ksrTyped += length - bestMatch.prefixMatchSize;
      ksrTotal += length;
      if(reference.size() > next + 1) nextPrefix = bestMatch.fullMatch.concat(reference.subsequence(next, next + 1));// else we're finished
    } 
    
    return new KSR(ksrTyped, ksrTotal, nextPrefix);
  }
  
  private static class Match {
    public Sequence<IString> fullMatch;
    public int prefixMatchSize;
    
    public Match(Sequence<IString> fullMatch, int prefixMatchSize) {
      this.fullMatch = fullMatch;
      this.prefixMatchSize = prefixMatchSize;
    }
  }
  
  private static Match getBestMatch(List<RichTranslation<IString, String>> translations, 
      int nbest_size, Sequence<IString> reference, int previousPrefixSize) {
    if(translations == null || translations.size() == 0) return null;
      
    Sequence<IString> bestMatch = null;
    int bestMatchSize = -1;
    int bestPrefixSize = -1;
    int n = 1;
    for(RichTranslation<IString, String> entry : translations) {
      int matchSize = previousPrefixSize;
      while(entry.translation.size() > matchSize 
          && reference.size() > matchSize
          && entry.translation.get(matchSize).equals(reference.get(matchSize))) {
        ++matchSize;
      }
        
      int prefixSize = 0;
      if(entry.translation.size() > matchSize) {
        String hypWord = entry.translation.get(matchSize).toString();
        String refWord = reference.get(matchSize).toString();
        while(prefixSize < hypWord.length() && prefixSize < refWord.length() && hypWord.charAt(prefixSize) == refWord.charAt(prefixSize))
          ++prefixSize;
      }
      
      matchSize = Math.min(matchSize, entry.translation.size());
      
      if(matchSize > bestMatchSize) {
        bestMatchSize = matchSize;
        bestMatch = entry.translation.subsequence(0, matchSize);
        bestPrefixSize = prefixSize;
      }
      else if(matchSize == bestMatchSize && prefixSize > bestPrefixSize) {
        bestPrefixSize = prefixSize;
      }
      
      ++n;
      if(n > nbest_size) break;
    }
    
    return new Match(bestMatch, bestPrefixSize);
  }

}

