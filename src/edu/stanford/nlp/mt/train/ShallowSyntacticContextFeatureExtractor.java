package edu.stanford.nlp.mt.train;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;

/**
 * Feature extractor collecting function words appearing right next to a given alignment template.
 * These features are then used to induce syntactic phrase classes.
 * This version is still very incomplete compared to the C++ version.
 * 
 * @author Michel Galley
 */
public class ShallowSyntacticContextFeatureExtractor extends SparseVectorFeatureExtractor {

  // TODO: reimplement more stuff available in the C++ version.
	// TODO: make thread safe

  private static final boolean SKIP_UNK = true;
  private static final boolean SKIP_SENT_BND = false;

  private static final IString 
    Fm1 = new IString("f-1:"), Fp1 = new IString("f+1:"), 
    Fm = new IString("f-:"), Fp = new IString("f+:"), 
    Em1 = new IString("e-1:"), Ep1 = new IString("e+1:"),
    Em = new IString("e-:"), Ep = new IString("e+:");

  private static final IString 
    UNK_WORD = new IString("<unk>"), START_SENT = new IString("<s>"), END_SENT = new IString("</s>");

  private int winSize = 1;
  private Set<IString> 
    fCCWords = new HashSet<IString>(), eCCWords = new HashSet<IString>(),
    fOCWords = new HashSet<IString>(), eOCWords = new HashSet<IString>();

  /**
   * Preferred constructor. 
   * @param fCCVocab Source-language closed class words.
   * @param eCCVocab Target-language closed class words.
   * @param fOCVocab Source-language open class words.
   * @param eOCVocab Target-language open class words.
   */
  public ShallowSyntacticContextFeatureExtractor(String fCCVocab, String eCCVocab, String fOCVocab, String eOCVocab, int winSize) {
    this.winSize = winSize;
    try {
      fCCWords = IOTools.slurpIStringSet(fCCVocab);
      eCCWords = IOTools.slurpIStringSet(eCCVocab);
      fOCWords = IOTools.slurpIStringSet(fOCVocab);
      eOCWords = IOTools.slurpIStringSet(eOCVocab);
    } catch(IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  /**
   * Construct extractor with unrestricted vocabulary. Usage not recommended.
   */
  public ShallowSyntacticContextFeatureExtractor() {
    System.err.println("ShallowSyntacticContextFeatureExtractor: WARNING: extracting features without limits on vocab.");
  }

  /**
   * Collects word counts around phrases for distributional clustering. Word right before and after
   * each phrase (unless rare words), and functions words within a window of winSize.
   */
  @Override
	void addFeatureCountsToSparseVector(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid) {

    WordAlignment sent = alTemp.getSentencePair();
    int f1 = alTemp.fStartPos(), f2 = alTemp.fEndPos(), e1 = alTemp.eStartPos(), e2 = alTemp.eEndPos();
    Sequence<IString> f = sent.f(), e = sent.e();
    int ws = winSize;

    // Get the four words (open class or closed class) next to each alignment template:
    addWordFeatureToSparseVector(Fm1, f, f1-1, fOCWords);  
    addWordFeatureToSparseVector(Fp1, f, f2+1, fOCWords);  
    addWordFeatureToSparseVector(Em1, e, e1-1, eOCWords);  
    addWordFeatureToSparseVector(Ep1, e, e2+1, eOCWords);

    if(false) {
    // Get all function words close to alignment template:
    addBOWFeatureToSparseVector(Fm, f, f1-ws, f1-1, fCCWords);
    addBOWFeatureToSparseVector(Fp, f, f2+1, f2+ws, fCCWords);
    addBOWFeatureToSparseVector(Em, e, e1-ws, e1-1, eCCWords);
    addBOWFeatureToSparseVector(Ep, e, e2+1, e2+ws, eCCWords);
    }
  }

  /**
   * Extract word before and after each phrase.
   */
  private void addWordFeatureToSparseVector
      (IString featureName, Sequence<IString> seq, int pos, Set<IString> limitMap) {
    if(pos < -1 || pos > seq.size())
      return;
    IString word; // = null;
    if(pos == -1) {
      if(SKIP_SENT_BND) return;
      word = START_SENT;
    } else if(pos == seq.size()) {
      if(SKIP_SENT_BND) return;
      word = END_SENT;
    } else {
      word = seq.get(pos);
      if(limitMap.size() > 0 && !limitMap.contains(word)) {
        if(SKIP_UNK) return;
        word = UNK_WORD;
      }
    }
    addFeatureCountToSparseVector(featureName.toString()+word.toString(), 1);
  }

  /**
   * Extract words with range x to y.
   */
  void addBOWFeatureToSparseVector
      (IString featureName, Sequence<IString> seq, int x, int y, Set<IString> limitMap) {
    for(int i=x; i<=y; ++i) {
      IString word;
      if(i<-1) continue;
      if(i>seq.size()) break;
      if(i == -1) {
        if(SKIP_SENT_BND) continue;
        word = START_SENT;
      } else if(i == seq.size()) {
        if(SKIP_SENT_BND) continue;
        word = END_SENT;
      } else { 
        word = seq.get(i);
        if(limitMap.size() > 0 && !limitMap.contains(word)) {
          if(SKIP_UNK) continue;
          word = UNK_WORD;
        }
      }
      addFeatureCountToSparseVector(featureName.toString()+word.toString(), 1);
    }
  }
}
