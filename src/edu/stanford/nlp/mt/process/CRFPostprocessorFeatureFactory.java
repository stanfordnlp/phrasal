package edu.stanford.nlp.mt.process;

import java.util.Collection;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ParentAnnotation;
import edu.stanford.nlp.sequences.Clique;
import edu.stanford.nlp.sequences.FeatureFactory;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PaddedList;

/**
 * Feature factory for an post processor models.
 * 
 * @author Spence Green
 *
 * @param <IN>
 */
public class CRFPostprocessorFeatureFactory<IN extends CoreLabel> extends FeatureFactory<IN> {
  
  private static final long serialVersionUID = 6254391859573982318L;
  
  public void init(SeqClassifierFlags flags) {
    super.init(flags);
  }

  /**
   * Extracts all the features from the input data at a certain index.
   *
   * @param cInfo The complete data set as a List of WordInfo
   * @param loc  The index at which to extract features.
   */
  public Collection<String> getCliqueFeatures(PaddedList<IN> cInfo, int loc, Clique clique) {
    Collection<String> features = Generics.newHashSet();

    if (clique == cliqueC) {
      addAllInterningAndSuffixing(features, featuresC(cInfo, loc), "C");
    } else if (clique == cliqueCpC) {
      addAllInterningAndSuffixing(features, featuresCpC(cInfo, loc), "CpC");
    } 
//    else if (clique == cliqueCp2C) {
//      addAllInterningAndSuffixing(features, featuresCp2C(cInfo, loc), "Cp2C");
//    } else if (clique == cliqueCp3C) {
//      addAllInterningAndSuffixing(features, featuresCp3C(cInfo, loc), "Cp3C");
//    }

    return features;
  }

  protected Collection<String> featuresC(PaddedList<? extends CoreLabel> cInfo, int loc) {
    Collection<String> features = Generics.newArrayList();
    CoreLabel c = cInfo.get(loc);
    CoreLabel n = cInfo.get(loc + 1);
    CoreLabel n2 = cInfo.get(loc + 2);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel p2 = cInfo.get(loc - 2);

    String charc = c.get(CoreAnnotations.CharAnnotation.class);
    String charn = n.get(CoreAnnotations.CharAnnotation.class);
    String charn2 = n2.get(CoreAnnotations.CharAnnotation.class);
    String charp = p.get(CoreAnnotations.CharAnnotation.class);
    String charp2 = p2.get(CoreAnnotations.CharAnnotation.class);

    // Default feature set...a 5 character window
    // Adding actual characters causes overfitting.
//    features.add(charc +"-c");
//    features.add(charn + "-n1");
//    features.add(charn2 + "-n2" );
//    features.add(charp + "-p");
//    features.add(charp2 + "-p2");

    // Sequence start indicator
    if (loc == 0) features.add("seq-start");

    // Character-class features
    addCharacterClassFeatures(features, charp2, "-p2");
    addCharacterClassFeatures(features, charp, "-p");
    addCharacterClassFeatures(features, charn, "-n");
    addCharacterClassFeatures(features, charn2, "-n2");
    addCharacterClassFeatures(features, charc, "-c");
    
    // Token features
    if (charc != null && ! charc.equals(ProcessorTools.WHITESPACE)) {
      // Current token
      String cToken = tokenClass(c.get(ParentAnnotation.class));
      features.add(cToken + "-cword");
           
      // Character position in the current token.
      int cPosition = c.get(CharacterOffsetBeginAnnotation.class);
      if (cPosition == 0) {
        features.add("char-start");
        features.add("start-" + cToken);
      } else {
//        features.add("char-inside");
        features.add("inside-" + cToken);
      }

      // Left context
      String leftToken = "<S>";
      for (int i = loc-1; i >= 0; --i) {
        String leftC = cInfo.get(i).get(CoreAnnotations.CharAnnotation.class);
        if (leftC != null && leftC.equals(ProcessorTools.WHITESPACE)) {
          leftToken = tokenClass(cInfo.get(i-1).get(CoreAnnotations.ParentAnnotation.class));
          if (leftToken != null) {
            features.add(leftToken + "-lcontext"); 
          }
          break;
        }
      }
      
      // Left context bigram
      if (cPosition == 0) {
        features.add(leftToken + "-" + cToken + "-lbigram");
      }
    }
    
    // Indicator transition feature
    features.add("cliqueC");

    return features;
  }

  /**
   * Returns an equivalence class for an input string.
   * 
   * @param string
   * @return
   */
  private String tokenClass(String string) {
    if (string.startsWith("http://")) {
      return "#UrL#";
    } else if (isNumberAndPunctuation(string)) {
      return "#0#";
    }
    return string;
  }

  /**
   * Returns true if a strong consists entirely of digits and punctuation.
   * False otherwise.
   * 
   * @param string
   * @return
   */
  private boolean isNumberAndPunctuation(String string) {
    int length = string.length();
    for (int i = 0; i < length; ++i) {
      char c = string.charAt(i);
      int cType = Character.getType(c);
      if ( ! (Character.isDigit(c) || 
          cType == Character.START_PUNCTUATION ||
          cType == Character.END_PUNCTUATION ||
          cType == Character.OTHER_PUNCTUATION ||
          cType == Character.CONNECTOR_PUNCTUATION ||
          cType == Character.DASH_PUNCTUATION) ||
          cType == Character.INITIAL_QUOTE_PUNCTUATION ||
          cType == Character.FINAL_QUOTE_PUNCTUATION) {
        return false;
      }
    }
    return true;
  }

  /**
   * Internationalized character-level features.
   * 
   * @param features
   * @param string
   * @param suffix
   * @param b 
   */
  private void addCharacterClassFeatures(Collection<String> features, String string,
      String suffix) {
    if (string == null) {
      features.add("boundary" + suffix);
      return;
    }
    if (string.length() > 1) return;
    
    final char c = string.charAt(0);
    final int cType = Character.getType(c);
    
    if (Character.isLetter(c)) {
      features.add("alpha" + suffix);
    
    } else if (Character.isDigit(c)) {
      features.add("digit" + suffix);
    
    } else if (Character.isWhitespace(c)) {
      features.add("ws" + suffix);
    
    } else if (c == ',' || c == '"' || c == '.' || c == '/' || c == ':' || c == ';') {
      features.add(c + suffix);
    
    } else if (cType == Character.START_PUNCTUATION ||
        cType == Character.INITIAL_QUOTE_PUNCTUATION) {
      features.add("start_punc" + suffix);
    
    } else if (cType == Character.END_PUNCTUATION ||
        cType == Character.FINAL_QUOTE_PUNCTUATION) {
      features.add("end_punc" + suffix);
    
    } else if (cType == Character.OTHER_PUNCTUATION) {
      features.add("other_punc" + suffix);
    
    } else if (cType == Character.CONNECTOR_PUNCTUATION ||
        cType == Character.DASH_PUNCTUATION) {
      features.add("conn_punc" + suffix);
    
    } else if (cType == Character.CURRENCY_SYMBOL) {
      features.add("currency" + suffix);
    
    } else if (cType == Character.MATH_SYMBOL) {
      features.add("math" + suffix);
    
    } else {
      features.add("unk" + suffix);
    }
  }

  private Collection<String> featuresCpC(PaddedList<IN> cInfo, int loc) {
    Collection<String> features = Generics.newArrayList();

    // Caused overfitting.
//    CoreLabel c = cInfo.get(loc);
//    CoreLabel p = cInfo.get(loc - 1);
//    CoreLabel n = cInfo.get(loc + 1);
//    
//    String charc = c.get(CoreAnnotations.CharAnnotation.class);
//    String charp = p.get(CoreAnnotations.CharAnnotation.class);
//    String charn = n.get(CoreAnnotations.CharAnnotation.class);
//
//    addCharacterClassFeatures(features, charp, "-p");
//    addCharacterClassFeatures(features, charn, "-n");
//    addCharacterClassFeatures(features, charc, "-c");
 
    // Indicator transition feature
    features.add("cliqueCpC");
    
    return features;
  }

//  private Collection<String> featuresCp2C(PaddedList<IN> cInfo, int loc) {
//    Collection<String> features = new ArrayList<String>();
//    CoreLabel c = cInfo.get(loc);
//    CoreLabel p = cInfo.get(loc - 1);
//    CoreLabel p2 = cInfo.get(loc - 2);
//
//    String charc = c.get(CoreAnnotations.CharAnnotation.class);
//    String charp = p.get(CoreAnnotations.CharAnnotation.class);
//    String charp2 = p2.get(CoreAnnotations.CharAnnotation.class);
//
//    features.add(charc + charp + charp2 + "-cngram");
//
//    CoreLabel n = cInfo.get(loc + 1);
//    String charn = n.get(CoreAnnotations.CharAnnotation.class);
//    features.add(charn + "-n");
//    
//    // Indicator transition feature
//    features.add("cliqueCp2C");
//    
//    return features;
//  }
//
//  private Collection<String> featuresCp3C(PaddedList<IN> cInfo, int loc) {
//    Collection<String> features = new ArrayList<String>();
//    CoreLabel c = cInfo.get(loc);
//    CoreLabel p = cInfo.get(loc - 1);
//    CoreLabel p2 = cInfo.get(loc - 2);
//    CoreLabel p3 = cInfo.get(loc - 3);
//
//    String charc = c.get(CoreAnnotations.CharAnnotation.class);
//    String charp = p.get(CoreAnnotations.CharAnnotation.class);
//    String charp2 = p2.get(CoreAnnotations.CharAnnotation.class);
//    String charp3 = p3.get(CoreAnnotations.CharAnnotation.class);
//    
//    features.add(charc + charp + charp2 + charp3 + "-cngram");
//    
//    CoreLabel n = cInfo.get(loc + 1);
//    String charn = n.get(CoreAnnotations.CharAnnotation.class);
//    features.add(charn + "-n");
//    
//    // Indicator transition feature
//    features.add("cliqueCp3C");
//    
//    return features;
//  }
}