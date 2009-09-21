package mt.decoder.efeat;

import java.io.File;
import java.util.*;

import mt.PseudoMoses;
import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.base.IString;
import mt.decoder.feat.IncrementalFeaturizer;

import edu.stanford.nlp.util.Pair;

public class ArabicVSOFeaturizer implements IncrementalFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "ArabicVSOFeaturizer";
  
  private boolean VERBOSE = false;

  private static final int NOT_IN_SUBJECT = Integer.MIN_VALUE;
  private static final int NO_VERB = Integer.MIN_VALUE;
  private static final int NO_ALIGNMENT = Integer.MIN_VALUE;
  
  private final ArabicSubjectBank subjectBank;

  public ArabicVSOFeaturizer(String... args) {

    assert args.length == 2;
    File subjFile = new File(args[0]);
    if(!subjFile.exists())
      throw new RuntimeException(String.format("%s: File does not exist (%s)",this.getClass().getName(),subjFile.getPath()));

    int maxSubjLen = Integer.parseInt(args[1].trim());
    
    //Do the loading here to accommodate multi-threading
    subjectBank = ArabicSubjectBank.getInstance();
    subjectBank.load(subjFile,maxSubjLen);
  }

  private int getSubjectIdForPhrase(final List<Pair<Integer,Integer>> subjectSpans,
                                    final int phraseStart,
                                    final int len) {
    final int phraseEnd = phraseStart + len - 1;

    Iterator<Pair<Integer,Integer>> itr = subjectSpans.iterator();
    for(int subjectId = 0; itr.hasNext(); subjectId++) {

      Pair<Integer,Integer> subject = itr.next();

      if(phraseStart >= subject.first() || phraseEnd <= subject.second())
        return subjectId;
    }

    return NOT_IN_SUBJECT;
  }


  private int getVerbIdx(final List<Pair<Integer,Integer>> subjectSpans,
                         final Set<Integer> verbs,
                         final int sId) {
    final Pair<Integer,Integer> subject = subjectSpans.get(sId);
    final int start = subject.first();
    if(verbs.contains(start-1))
      return start - 1;
    else if(verbs.contains(start-2))
      return start - 2;
    else
      return NO_VERB;
  }

  private int getEStartPosition(final int fWord, final Featurizable<IString,String> f) {
    final int[] eRange = f.f2tAlignmentIndex[fWord];
    if(eRange == null)
      return NO_ALIGNMENT;

    return eRange[Featurizable.PHRASE_START];
  }

  private int getEEndPosition(final int fWord, final Featurizable<IString,String> f) {
    final int[] eRange = f.f2tAlignmentIndex[fWord];
    if(eRange == null)
      return NO_ALIGNMENT;

    return eRange[Featurizable.PHRASE_END] - 1; //Convert to real index
  }

  //Returns whether this is 
  private boolean isFirstPhraseInSubject(final Pair<Integer,Integer> subject, 
                                         final Featurizable<IString,String> f) {

    final BitSet fCoverage = 
      f.hyp.foreignCoverage.get(subject.first(), subject.second() + 1);
    
    return fCoverage.cardinality() == 0;
  }
  
  public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    final int translationId = f.translationId + (PseudoMoses.local_procs > 1 ? 2 : 0);
    List<Pair<Integer,Integer>> subjectSpans = subjectBank.subjectsForSentence(translationId);
    if(subjectSpans == null || subjectSpans.size() == 0)
      return null;
    
    Set<Integer> verbs = subjectBank.verbsForSentence(translationId); 
    
    //WSGDEBUG
    VERBOSE = (translationId == 3);
    
    final int sId = getSubjectIdForPhrase(subjectSpans,f.foreignPosition, f.foreignPhrase.size());
    if(sId == NOT_IN_SUBJECT)
      return null;

    final int vIdx = getVerbIdx(subjectSpans,verbs,sId);
    if(vIdx != NO_VERB) {
      final Pair<Integer,Integer> fSubject = subjectSpans.get(sId);
      //      final int fSubjStart = fSubject.first();
      final int fSubjEnd = fSubject.second();
      final int eSubjEnd = getEEndPosition(fSubjEnd,f);
      final int fPhraseStart = f.foreignPosition;
      final int fPhraseEnd = f.foreignPosition + f.foreignPhrase.size() - 1;
      final int eVerbStart = getEStartPosition(vIdx,f);
      final int eVerbEnd = getEEndPosition(vIdx, f);

      final boolean notScored = (f.prior == null) ? true : isFirstPhraseInSubject(fSubject, f.prior);
    
      //Case 1
      if((vIdx >= fPhraseStart && fSubjEnd <= fPhraseEnd) && notScored) {
        if(VERBOSE) {
          System.err.printf("%s (1): f_verb(%d) f_sbj_end(%d) p_start(%d) p_end(%d) e_vb_start(%d) e_vb_end(%d) e_sbj_end(%d)\n", this.getClass().getName(),
                                                                                              vIdx,
                                                                                              fSubjEnd,
                                                                                              fPhraseStart,
                                                                                              fPhraseEnd,
                                                                                              eVerbStart,
                                                                                              eVerbEnd,
                                                                                              eSubjEnd);
          System.err.printf(" fphrase: %s\n", f.foreignPhrase.toString());
          System.err.printf(" ptrans: %s\n", f.translatedPhrase.toString());
          System.err.printf(" hyp: %s\n", f.partialTranslation.toString());
        }
//        return new FeatureValue<String>(FEATURE_NAME, 2.0);
        return null;
      
      } else if(eVerbStart == NO_ALIGNMENT || eSubjEnd == NO_ALIGNMENT) {
        return null;
      
      //Verb is aligned to the same phrase as the subject
      } else if(eVerbEnd == eSubjEnd && notScored) {
        return null;
//        return new FeatureValue<String>(FEATURE_NAME, 1.0);
        
      //Case 2
      } else if(eVerbStart > eSubjEnd && notScored) {
        if(VERBOSE) {
          System.err.printf("%s (2): f_verb(%d) f_sbj_end(%d) p_start(%d) p_end(%d) e_vb_start(%d) e_vb_end(%d) e_sbj_end(%d)\n", this.getClass().getName(),
              vIdx,
              fSubjEnd,
              fPhraseStart,
              fPhraseEnd,
              eVerbStart,
              eVerbEnd,
              eSubjEnd);
          System.err.printf(" fphrase: %s\n", f.foreignPhrase.toString());
          System.err.printf(" ptrans: %s\n", f.translatedPhrase.toString());
          System.err.printf(" hyp: %s\n", f.partialTranslation.toString());
        }
//        return new FeatureValue<String>(FEATURE_NAME, 1.0);
        return null;

      //Case 3 & 4
      } else if(eVerbStart < eSubjEnd && notScored) {
        if(VERBOSE) {
          System.err.printf("%s (3): f_verb(%d) f_sbj_end(%d) p_start(%d) p_end(%d) e_vb_start(%d) e_vb_end(%d) e_sbj_end(%d)\n", this.getClass().getName(),
              vIdx,
              fSubjEnd,
              fPhraseStart,
              fPhraseEnd,
              eVerbStart,
              eVerbEnd,
              eSubjEnd);
          System.err.printf(" fphrase: %s\n", f.foreignPhrase.toString());
          System.err.printf(" ptrans: %s\n", f.translatedPhrase.toString());
          System.err.printf(" hyp: %s\n", f.partialTranslation.toString());
        }
        
        return new FeatureValue<String>(FEATURE_NAME, -1.0);
      }
    }
    //TODO could check here for contiguity if there is no verb

    return null;
  }

  // Unused but required methods
  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {}
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }
  public void reset() {}

}
