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
  
}
