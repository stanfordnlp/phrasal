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
  private static final double DEFAULT_FEATURE_VALUE = -99.0;
  private static final double SCALING_CONSTANT = 10.0;
  
  private final ArabicKbestSubjectBank subjectBank;

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

  /**
   * Returns true if the span of token positions specified by span is completely covered
   * in the partial hypothesis of f. Otherwise, returns false.
   * 
   * @param span
   * @param f
   * @return
   */
  private boolean isCovered(final Pair<Integer,Integer> span, final int verbIdx, final Featurizable<IString,String> f) {
    if(f == null) 
      return false;

    boolean verbCovered = f.hyp.foreignCoverage.get(verbIdx);

    final int leftSubjectBoundary = span.first();
    final int rightSubjectBoundary = span.second();
    final int length = rightSubjectBoundary - leftSubjectBoundary + 1;
    
    final BitSet fCoverage = 
      f.hyp.foreignCoverage.get(leftSubjectBoundary, rightSubjectBoundary + 1);

    boolean subjCovered = (fCoverage.cardinality() == length);

    return verbCovered && subjCovered;
  }

  /**
   * Returns the index of the last subject for which the feature *could* have fired.
   * 
   * @param f
   * @param subjects
   * @param verbs
   * @return
   */
  private int getLastScoredSubject(final Featurizable<IString,String> f, 
                                   final int translationId, 
                                   final SortedSet<Integer> verbs) {    
    int prevSubjIndex = -1;
    for(int verbIdx : verbs) {
      final List<Triple<Integer,Integer,Double>> subjects = subjectBank.getSubjectsForVerb(translationId, verbIdx);
      
      boolean isComplete = false;
      for(Triple<Integer,Integer,Double> subjTriple : subjects) {
        Pair<Integer,Integer> subjectSpan = new Pair<Integer,Integer>(subjTriple.first(),subjTriple.second());
        if(isCovered(subjectSpan,verbIdx,f)) {
          isComplete = true;
          prevSubjIndex = verbIdx;
          break;
        }
      }
      
      if(!isComplete)
        break;
    }
    return prevSubjIndex;
  }
  
  
  private int getRightTargetSideBoundary(final Pair<Integer,Integer> span, final Featurizable<IString,String> f) {
    int maxRightIndex = -1;
    for(int i = span.first(); i <= span.second(); i++) {
      final int[] eRange = f.f2tAlignmentIndex[i];
      if(eRange == null) continue;

      final int rightIndex = eRange[Featurizable.PHRASE_END] - 1; //Convert to real index
      if(rightIndex > maxRightIndex)
        maxRightIndex = rightIndex;
    }
    
    return maxRightIndex;
  }
  
  /**
   * Returns the feature score for the specified subject vector. Greedily matches the first completed subject.
   * 
   * @param f
   * @param translationId
   * @param verbIdx
   * @return
   */
  private double getFeatureScore(final Featurizable<IString,String> f, 
                                 final int translationId, 
                                 final int verbIdx) {
        
    double accumulatedProbability = 0.0;
    
    final List<Triple<Integer,Integer,Double>> subjects = subjectBank.getSubjectsForVerb(translationId, verbIdx);
    for(Triple<Integer,Integer,Double> subjSpanAndScore : subjects) {
      Pair<Integer,Integer> subjectSpan = new Pair<Integer,Integer>(subjSpanAndScore.first(),subjSpanAndScore.second());
      
      if(isCovered(subjectSpan,verbIdx,f)) {
        
        final int eSubjRightBound = getRightTargetSideBoundary(subjectSpan,f);
        final int eVerbRightBound = getRightTargetSideBoundary(new Pair<Integer,Integer>(verbIdx,verbIdx), f);
        
        if(eVerbRightBound >= eSubjRightBound)
          accumulatedProbability += Math.exp(subjSpanAndScore.third());
      }
    }

    return (accumulatedProbability == 0.0) ? 0.0 : Math.log(accumulatedProbability);
  }

  public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    
    //Get the verbs. Return if there aren't any
    final int translationId = f.translationId + (PseudoMoses.local_procs > 1 ? 2 : 0);
    SortedSet<Integer> verbs = subjectBank.getVerbs(translationId);
    if(verbs == null || verbs.size() == 0)
      return null;
    
    //WSGDEBUG
    boolean VERBOSE = (translationId == 14);
    //|| (translationId == 16) || (translationId == 41);
    
    //Get the subject vector that we should consider. Return if we have covered them all
    final int lastSubjectVect = getLastScoredSubject(f.prior,translationId,verbs);
    final int currentSubjectVect = getLastScoredSubject(f,translationId,verbs);
      
    //These two will be equal for the last subject, so the feature will not fire
    if(lastSubjectVect != currentSubjectVect) {

      //WSGDEBUG
      if(VERBOSE) {
        System.err.printf("WSGDEBUG tId %d: Completed subject %d --> %d\n",translationId,lastSubjectVect,currentSubjectVect);
        System.err.println("=== Current Featurizer ===");
        System.err.printf(" TransOpt: %s ||| %s\n", f.foreignPhrase.toString(), f.translatedPhrase.toString());
        System.err.printf(" cov: %s\n", f.option.foreignCoverage.toString());
        System.err.printf(" hyp: %s\n", f.partialTranslation.toString());
        System.err.printf(" hyp cov: %s\n", f.hyp.foreignCoverage.toString());
        System.err.println("=== Prior Featurizer ===");
        if(f.prior != null) {
          System.err.printf(" TransOpt: %s ||| %s\n", f.prior.foreignPhrase.toString(), f.prior.translatedPhrase.toString());
          System.err.printf(" cov: %s\n", f.prior.option.foreignCoverage.toString());
          System.err.printf(" hyp: %s\n", f.prior.partialTranslation.toString());
          System.err.printf(" hyp cov: %s\n", f.prior.hyp.foreignCoverage.toString());        
        }
      }      
      //Get the accumulated feature score
      double featScore = SCALING_CONSTANT * getFeatureScore(f,translationId,currentSubjectVect);
      if(VERBOSE)
        System.err.printf(" FEATURE SCORE {%f}\n",featScore);
      
      if(f.prior == null)
        return new FeatureValue<String>(FEATURE_NAME, (featScore == 0.0) ? DEFAULT_FEATURE_VALUE : featScore);
      if(featScore == 0.0)
        return null;                            //Case 2: None of the re-orderings are correct
      else if(lastSubjectVect == -1)            //Case 3: Fire the feature and back out the penalty
        return new FeatureValue<String>(FEATURE_NAME, featScore - DEFAULT_FEATURE_VALUE);
      else                                      //Case 4: Otherwise just fire the feature
        return new FeatureValue<String>(FEATURE_NAME, featScore);

    } else if(f.prior == null)
      return new FeatureValue<String>(FEATURE_NAME, DEFAULT_FEATURE_VALUE);
          
    return null;
  }

  
  // Unused but required methods
  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {}
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }
  public void reset() {}

}
