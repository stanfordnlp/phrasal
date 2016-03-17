package edu.stanford.nlp.mt.util;

import java.util.List;

public class WordPredictionAccuracy {

  public static boolean correctPrediction(List<RichTranslation<IString, String>> translations, 
      int nbest_size, Sequence<IString> reference, int previousPrefixSize) {
    
    if(translations != null) {
      int n = 1;
      for(RichTranslation<IString, String> entry : translations) {
        if(entry.translation.size() > previousPrefixSize &&
            reference.size() > previousPrefixSize &&
            entry.translation.get(previousPrefixSize).equals(reference.get(previousPrefixSize)) ) {
          return true;
        }
        ++n;
        if(n > nbest_size) break;
      }
    }
    
    return false;
  }
  
  public static Sequence<IString> getBestMatch(List<RichTranslation<IString, String>> translations, 
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
        bestMatch = entry.translation;
      }
      
      ++n;
      if(n > nbest_size) break;
    }
    return bestMatch;
  }
  
}
