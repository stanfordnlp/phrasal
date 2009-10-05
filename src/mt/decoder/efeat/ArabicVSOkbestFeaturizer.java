package mt.decoder.efeat;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

import mt.PseudoMoses;
import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.IString;
import mt.base.Sequence;
import mt.decoder.feat.IncrementalFeaturizer;

public class ArabicVSOkbestFeaturizer implements IncrementalFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "ArabicVSOkbestFeaturizer";

  private static final int NO_ALIGNMENT = Integer.MIN_VALUE;
  
  private final ArabicKbestSubjectBank subjectBank;

  public enum SubjectState {INCOMPLETE, COMPLETE, CANNOT_SCORE};
  
  public ArabicVSOkbestFeaturizer(String... args) {

    assert args.length == 3;
    
    File subjFile = new File(args[0]);
    if(!subjFile.exists())
      throw new RuntimeException(String.format("%s: File does not exist (%s)",this.getClass().getName(),subjFile.getPath()));

    int maxSubjLen = Integer.parseInt(args[1].trim());
    int verbDistance = Integer.parseInt(args[2].trim());
    
    //Do the loading here to accommodate multi-threading
    subjectBank = ArabicKbestSubjectBank.getInstance();
    subjectBank.load(subjFile,maxSubjLen,verbDistance);
  }

  private int getEStartPosition(final int fWord, final Featurizable<IString,String> f) {
    final int[] eRange = f.f2tAlignmentIndex[fWord];
    if(eRange == null)
      return NO_ALIGNMENT;

    return eRange[Featurizable.PHRASE_START];
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
                                       int verbIdx) {
    if(isCovered(subject,f)) {
      int eVerbIdx = getEStartPosition(verbIdx,f);
      if(eVerbIdx != NO_ALIGNMENT)
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
  private int getLastScoredSubject(Featurizable<IString,String> f, 
                                       int translationId, 
                                       SortedSet<Integer> verbs) {    
    int lastSubjId = -1;
    for(Integer verbIdx : verbs) {

      if(f == null)
        return lastSubjId;
      
      List<Triple<Integer,Integer,Double>> subjects = subjectBank.getSubjectsForVerb(translationId, verbIdx);
      boolean hasBeenScored = false;
      for(Triple<Integer,Integer,Double> subjTriple : subjects) {
        Pair<Integer,Integer> subject = new Pair<Integer,Integer>(subjTriple.first(),subjTriple.second());
        SubjectState state = getSubjectState(f,subject,verbIdx);
        if(state == SubjectState.COMPLETE)
          hasBeenScored = true;
      }
      
      if(!hasBeenScored)
        return lastSubjId;
      lastSubjId = verbIdx;
    }
    return lastSubjId;
  }
  
  /**
   * Returns the feature score for the specified subject vector. Greedily matches the first completed subject.
   * 
   * @param f
   * @param translationId
   * @param verbIdx
   * @return
   */
  private double getFeatureScore(Featurizable<IString,String> f, int translationId, int verbIdx) {
    
    List<Triple<Integer,Integer,Double>> subjects = subjectBank.getSubjectsForVerb(translationId, verbIdx);
    
    for(Triple<Integer,Integer,Double> subjTriple : subjects) {
      Pair<Integer,Integer> subject = new Pair<Integer,Integer>(subjTriple.first(),subjTriple.second());
      if(getSubjectState(f,subject,verbIdx) == SubjectState.COMPLETE)
        return subjTriple.third(); 
    }
    
    //The shit has hit the fan
    throw new RuntimeException(String.format("Expected to find feature score for transId %d verbIdx %d.",translationId,verbIdx));
  }

  public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    
    //Get the verbs. Return if there aren't any
    final int translationId = f.translationId + (PseudoMoses.local_procs > 1 ? 2 : 0);
    SortedSet<Integer> verbs = subjectBank.getVerbs(translationId);
    if(verbs == null || verbs.size() == 0)
      return null;
    
    //WSGDEBUG
    boolean VERBOSE = (translationId == 16);
    
    //Get the subject vector that we should consider. Return if we have covered them all
    final int lastSubjectVect = getLastScoredSubject(f.prior,translationId,verbs);
    final int currentSubjectVect = getLastScoredSubject(f,translationId,verbs);
      
    //These two will be equal for the last subject, so the feature will not fire
    if(lastSubjectVect != currentSubjectVect) {

      //WSGDEBUG
      if(VERBOSE) {
        System.err.printf("WSGDEBUG: %d --> %d\n", lastSubjectVect,currentSubjectVect);
        System.err.printf(" current: %s\n", f.partialTranslation.toString());
        if (f.prior == null)
          System.err.printf(" prior: %s\n", f.prior.partialTranslation.toString());
      }
      
      //Fire the feature
      double featScore = getFeatureScore(f,translationId,currentSubjectVect);
      
      //WSGDEBUG
      //Fix the sign
      featScore = 8.0 - featScore;
      
      return new FeatureValue<String>(FEATURE_NAME, featScore);
    }
          
    return null;
  }

  // Unused but required methods
  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {}
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }
  public void reset() {}

}
