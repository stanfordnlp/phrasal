package mt.decoder.efeat;

import java.io.File;
import java.util.*;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.base.SimpleSequence;
import mt.base.IString;
import mt.base.IStrings;
import mt.decoder.feat.IncrementalFeaturizer;

import edu.stanford.nlp.util.Pair;

public class ArabicVSOFeaturizer implements IncrementalFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "ArabicVSOFeaturizer";
  private boolean noSubjects = false;
  private List<Pair<Integer,Integer>> subjectSpans = null;
  private Map<Integer,Integer> verbs = null;

  private boolean VERBOSE = false;

  private static final int NOT_IN_SUBJECT = Integer.MIN_VALUE;
  private static final int NO_VERB = Integer.MIN_VALUE;
  private static final int NO_ALIGNMENT = Integer.MIN_VALUE;

  public ArabicVSOFeaturizer(String... args) {

    assert args.length == 2;
    File subjFile = new File(args[0]);
    if(!subjFile.exists())
      throw new RuntimeException(String.format("%s: File does not exist (%s)",this.getClass().getName(),subjFile.getPath()));

    int maxSubjLen = Integer.parseInt(args[1].trim());
    
    //Do the loading here to accommodate multi-threading
    ArabicSubjectBank sb = ArabicSubjectBank.getInstance();
    sb.load(subjFile,maxSubjLen);
  }

  private int getSubjectIdForPhrase(final int phraseStart, final int len) {
    final int phraseEnd = phraseStart + len - 1;

    Iterator<Pair<Integer,Integer>> itr = subjectSpans.iterator();
    for(int subjectId = 0; itr.hasNext(); subjectId++) {

      Pair<Integer,Integer> subject = itr.next();

      if(phraseStart >= subject.first() || phraseEnd <= subject.second())
        return subjectId;
    }

    return NOT_IN_SUBJECT;
  }

  //  private boolean isComplete(int subjectId, Featurizable<IString,String> f) {
  //    Pair<Integer,Integer> subject = subjectSpans.get(subjectId);
  //    for(int i = subject.first(); i <= subject.second(); i++) {
  //      int[] eRange = f.f2tAlignmentIndex[i];
  //      if(eRange == null) {
  //        return false;
  //      }
  //    }
  //    return true;
  //  }

  //  private boolean isContiguous(int subjectId, Featurizable<IString,String> f) {
  //    Pair<Integer,Integer> subject = subjectSpans.get(subjectId);
  //    BitSet coverage = new BitSet(f.hyp.length);
  //
  //    boolean complete = true;
  //    int eStart = Integer.MAX_VALUE;
  //    int eEnd = Integer.MIN_VALUE;
  //    for(int i = subject.first(); i <= subject.second(); i++) {
  //      int[] eRange = f.f2tAlignmentIndex[i];
  //      if(eRange == null) {
  //        complete = false;
  //        break;
  //      }
  //      else {
  //        if(eRange[Featurizable.PHRASE_START] < eStart)
  //          eStart = eRange[Featurizable.PHRASE_START];
  //        if(eRange[Featurizable.PHRASE_END] > eEnd)
  //          eEnd = eRange[Featurizable.PHRASE_END];
  //
  //        coverage.set(eRange[Featurizable.PHRASE_START], eRange[Featurizable.PHRASE_END] + 1);
  //      } 
  //    }
  //
  //    if(complete) {
  //      int span = eEnd - eStart + 1;
  //      BitSet subjectCoverage = coverage.get(eStart, eEnd + 1);
  //      if(subjectCoverage.cardinality() == span) {
  //        if(VERBOSE)
  //          System.err.printf("%s: Subject %d is contiguous\n", this.getClass().getName(), subjectId);
  //        return true;
  //      }
  //    }
  //
  //    if(VERBOSE)
  //      System.err.printf("%s: Subject %d is NOT contiguous\n", this.getClass().getName(), subjectId);
  //
  //    return false;
  //  }

  //  @SuppressWarnings("unchecked")
  //  private double getPastAwardForSubject(int subjectId, Featurizable<IString,String> f) {
  //
  //    Featurizable<IString,String> priorFeaturizable = f.prior;
  //    double cumulativeAward = 0.0;
  //
  //    while(priorFeaturizable != null) {
  //
  //      Pair<Integer,Boolean> action = (Pair<Integer,Boolean>) priorFeaturizable.extra;
  //
  //      if(action != null && action.second())
  //        cumulativeAward += FEATURE_PENALTY;
  //
  //      priorFeaturizable = priorFeaturizable.prior;
  //    }
  //
  //    if(VERBOSE)
  //      System.err.printf("%s: Cumulative award for subject %d (%f)\n", this.getClass().getName(), subjectId, cumulativeAward);
  //
  //    return cumulativeAward;
  //  }

  private int getVerbIdx(final int sId) {
    final Pair<Integer,Integer> subject = subjectSpans.get(sId);
    final int start = subject.first();
    if(verbs.keySet().contains(start-1))
      return start - 1;
    else if(verbs.keySet().contains(start-2))
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

  @SuppressWarnings("unchecked")
  public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    if(noSubjects) return null;

    Set<Integer> scoredSubjects = (f.prior != null && f.prior.extra != null) ? (Set<Integer>) f.prior.extra 
        : new HashSet<Integer>();

    f.extra = scoredSubjects;

    final int sId = getSubjectIdForPhrase(f.foreignPosition, f.foreignPhrase.size());
    if(sId == NOT_IN_SUBJECT)
      return null;

    final int vIdx = getVerbIdx(sId);
    if(vIdx != NO_VERB) {
      final Pair<Integer,Integer> fSubject = subjectSpans.get(sId);
      //      final int fSubjStart = fSubject.first();
      final int fSubjEnd = fSubject.second();
      final int eSubjEnd = getEEndPosition(fSubjEnd,f);
      final int fPhraseStart = f.foreignPosition;
      final int fPhraseEnd = f.foreignPosition + f.foreignPhrase.size() - 1;
      final int eVerbStart = getEStartPosition(vIdx,f);
      final int eVerbEnd = getEEndPosition(vIdx, f);

      final boolean notScored = !scoredSubjects.contains(sId);
    
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
//        scoredSubjects.add(sId);
//        return new FeatureValue<String>(FEATURE_NAME, 2.0);
        return null;
      
      } else if(eVerbStart == NO_ALIGNMENT || eSubjEnd == NO_ALIGNMENT) {
        return null;
      
      //Verb is aligned to the same phrase as the subject
      } else if(eVerbEnd == eSubjEnd && notScored) {
        return null;
//        scoredSubjects.add(sId);
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
        scoredSubjects.add(sId);
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
        scoredSubjects.add(sId);
      return new FeatureValue<String>(FEATURE_NAME, -1.0);
//        return null;
      }
    }
    //TODO could check here for contiguity if there is no verb

    return null;
  }

  private int translationId = -1;

  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {
    final ArabicSubjectBank sb = ArabicSubjectBank.getInstance();
    
    //Remove any trailing or leading whitespace
    Sequence<IString> trimmedSent = new SimpleSequence<IString>(true, IStrings.toIStringArray(foreign.toString().trim().split("\\s+")));
    subjectSpans = sb.subjectsForSentence(trimmedSent);
    
    if(subjectSpans == null)
      throw new RuntimeException(String.format("%s: Unknown sentence in the MT test set that was not processed by the Subject Detector (%s)",this.getClass().getName(), foreign.toString()));

    verbs = sb.verbsForSentence(trimmedSent);

    System.err.printf("%s: %d subjects for sentence\n", this.getClass().getName(), subjectSpans.size());
    System.err.printf("%s: %d verbs for sentence\n", this.getClass().getName(), verbs.keySet().size());

    noSubjects = (subjectSpans.size() == 0);


    translationId++;
    
    //WSGDEBUG
    VERBOSE = (translationId == 3 || translationId == 16);
  }


  // Unused but required methods
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }
  public void reset() {}

}
