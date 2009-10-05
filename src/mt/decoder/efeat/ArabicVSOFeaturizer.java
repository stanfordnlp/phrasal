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
  private static double FEATURE_VALUE = 1.0;
  
//  private static final int NOT_IN_SUBJECT = Integer.MAX_VALUE;
  private static final int NO_VERB = Integer.MIN_VALUE;
  private static final int NO_ALIGNMENT = Integer.MIN_VALUE;
  
  private final ArabicSubjectBank subjectBank;

  public enum SubjectState {INCOMPLETE, COMPLETE, CANNOT_SCORE};
  
  public ArabicVSOFeaturizer(String... args) {

    assert args.length == 3;
    
    File subjFile = new File(args[0]);
    if(!subjFile.exists())
      throw new RuntimeException(String.format("%s: File does not exist (%s)",this.getClass().getName(),subjFile.getPath()));

    int maxSubjLen = Integer.parseInt(args[1].trim());
    int modeIndicator = Integer.parseInt(args[2].trim());
    setFeatureValue(modeIndicator);
    
    //Do the loading here to accommodate multi-threading
    subjectBank = ArabicSubjectBank.getInstance();
    subjectBank.load(subjFile,maxSubjLen);
  }
  
  private void setFeatureValue(int modeIndicator) {
    if(modeIndicator == 1)
      FEATURE_VALUE = 1.0;
    else
      FEATURE_VALUE = -1.0;
  }

  /**
   * For a particular subject, return the token position of a verb at most two positions to the
   * left *if it exists*.
   * 
   * @param subjectSpans
   * @param verbs
   * @param sId
   * @return
   */
  private int getVerbIdx(final Pair<Integer,Integer> subject,
                         final Set<Integer> verbs) {
    final int start = subject.first();
    if(verbs.contains(start - 1))
      return start - 1;
    else if(verbs.contains(start - 2))
      return start - 2;

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

  /**
   * Returns true if the span of token positions specified by span is completely covered
   * in the partial hypothesis of f. Otherwise, returns false.
   * 
   * @param span
   * @param f
   * @return
   */
  private boolean isCovered(final Pair<Integer,Integer> span, 
                            final Featurizable<IString,String> f) {
    if(f == null) 
      return false;

    final BitSet fCoverage = 
      f.hyp.foreignCoverage.get(span.first(), span.second() + 1);

    return fCoverage.cardinality() == (span.second() - span.first() + 1);
  }
  
  private SubjectState getSubjectState(Featurizable<IString,String> f,
                                       Pair<Integer,Integer> subject,
                                       Set<Integer> verbs) {
    if(isCovered(subject,f)) {
      int verbIdx = getVerbIdx(subject,verbs);
      if(verbIdx == NO_VERB)
        return SubjectState.CANNOT_SCORE;
      else if(getEStartPosition(verbIdx,f) != NO_ALIGNMENT)
        return SubjectState.COMPLETE;
    }
    return SubjectState.INCOMPLETE;
  }
  
  /**
   * Returns the index of the last subject for which the feature fired.
   * 
   * @param f
   * @param subjects
   * @param verbs
   * @return
   */
  private int getLastSubjectScored(Featurizable<IString,String> f, 
                                   List<Pair<Integer,Integer>> subjects, 
                                   Set<Integer> verbs) {
    if(f == null)
      return -1;
    
    int lastSubj = -1;
    for(Pair<Integer,Integer> subject : subjects) {
      if(getSubjectState(f,subject,verbs) == SubjectState.INCOMPLETE)
        break;
      lastSubj++;
    }
    
    return lastSubj;
  }

  /**
   * Indicates when the feature should fire dependent on the scoring mode.
   * 
   * @param eVerbEnd
   * @param eSubjEnd
   * @return
   */
  private boolean fireFeature(int eVerbEnd, int eSubjEnd) {
    if(FEATURE_VALUE < 0.0 && eVerbEnd < eSubjEnd)
      return true;
    else if(FEATURE_VALUE > 0.0 && eVerbEnd >= eSubjEnd)
      return true;
    return false;
  }
  
  private int getMaxRightPosition(Pair<Integer,Integer> span, Featurizable<IString,String> f) {
    int max = -1;
    for(int i = span.first(); i <= span.second(); i++) {
      int rightPos = getEEndPosition(i, f);
      System.err.printf(" rightPos %d token %d\n", rightPos,i);
      if(rightPos > max)
        max = rightPos;
    }
    return (max == -1) ? NO_ALIGNMENT : max;
  }
  
  public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    
    //Get the subjects. Return if there aren't any
    final int translationId = f.translationId + (PseudoMoses.local_procs > 1 ? 2 : 0);
    List<Pair<Integer,Integer>> subjectSpans = subjectBank.subjectsForSentence(translationId);
    if(subjectSpans == null || subjectSpans.size() == 0)
      return null;
    
    //Get the verbs. Return if there aren't any
    Set<Integer> verbs = subjectBank.verbsForSentence(translationId); 
    if(verbs == null || verbs.size() == 0)
      return null;
    
    //Get the subject that we should consider. Return if we have covered them all
    final int lastSubjectScored = getLastSubjectScored(f.prior,subjectSpans,verbs);
    final int currentSubject = lastSubjectScored + 1;
    if(currentSubject >= subjectSpans.size())
      return null;
    
    //WSGDEBUG
    boolean VERBOSE = false;
    
    if(VERBOSE) {
      System.err.printf("WSGDEBUG: Subject status for tId %d\n",translationId);

      System.err.printf("  >> %d subjects\n",subjectSpans.size());
      for(Pair<Integer,Integer> s : subjectSpans)
        System.err.printf("  >>> (%d,%d)\n",s.first(),s.second());
      System.err.printf("  >> %s\n",verbs.toString());
    }
    
    //Get the state of the subject we should consider
    Pair<Integer,Integer> activeSubject = subjectSpans.get(currentSubject);
    SubjectState subjState = getSubjectState(f,activeSubject,verbs);

    //If the subject has just been completed, then score it.
    if(subjState == SubjectState.COMPLETE) {
      final int vIdx = getVerbIdx(activeSubject,verbs);
      final int eSubjEnd = getMaxRightPosition(activeSubject,f);
      final int eVerbEnd = getMaxRightPosition(new Pair<Integer,Integer>(vIdx,vIdx), f);

      if(VERBOSE) {
        System.err.printf("WSGDEBUG: Completed subject %d --> %d\n",lastSubjectScored,currentSubject);
        System.err.printf(" fphrase: %s\n", f.foreignPhrase.toString());
        System.err.printf(" ptrans:  %s\n", f.translatedPhrase.toString());
        System.err.printf(" hyp:     %s\n", f.partialTranslation.toString());
        if(f.prior != null)
          System.err.printf(" prior:   %s\n", f.prior.partialTranslation.toString());
        System.err.printf(" e_vb_end(%d) e_sbj_end(%d) vIdx(%d)\n",
            eVerbEnd,
            eSubjEnd,
            vIdx);
      }
      
      
      if(fireFeature(eVerbEnd,eSubjEnd)) {
        if(VERBOSE)
          System.err.printf(" FIRING FEATURE %f\n",FEATURE_VALUE);
      
        return new FeatureValue<String>(FEATURE_NAME, FEATURE_VALUE);
      }
    }

    return null;
  }

  // Unused but required methods
  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {}
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }
  public void reset() {}

}
