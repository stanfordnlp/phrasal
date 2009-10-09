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
import edu.stanford.nlp.util.Triple;

public class ArabicVSOFeaturizer implements IncrementalFeaturizer<IString, String> {

  private String FEATURE_NAME = "ArabicVSOFeaturizer";
  private double FEATURE_VALUE = 1.0;

  private final ArabicSubjectBank subjectBank;

  public ArabicVSOFeaturizer(String... args) {

    assert args.length == 4;

    File subjFile = new File(args[0]);
    if(!subjFile.exists())
      throw new RuntimeException(String.format("%s: File does not exist (%s)",this.getClass().getName(),subjFile.getPath()));

    int maxSubjLen = Integer.parseInt(args[1].trim());
    int modeIndicator = Integer.parseInt(args[2].trim());
    int maxVerbGap = Integer.parseInt(args[3].trim());
    setFeatureValue(modeIndicator);

    //Do the loading here to accommodate multi-threading
    subjectBank = ArabicSubjectBank.getInstance();
    subjectBank.load(subjFile,maxSubjLen,maxVerbGap);
  }

  private void setFeatureValue(int modeIndicator) {
    if(modeIndicator == 1) {
      FEATURE_VALUE = 10.0;
      FEATURE_NAME += "1";
    }
    else {
      FEATURE_VALUE = -10.0;
      FEATURE_NAME += "0";
    }
  }

  /**
   * Returns true if the span of token positions specified by span is completely covered
   * in the partial hypothesis of f. Otherwise, returns false.
   * 
   * @param span
   * @param f
   * @return
   */
  private boolean isCovered(final Triple<Integer,Integer,Integer> span, final Featurizable<IString,String> f) {
    if(f == null) 
      return false;

    boolean verbCovered = f.hyp.foreignCoverage.get(span.first());

    final int leftSubjectBoundary = span.second();
    final int rightSubjectBoundary = span.third();
    final int length = rightSubjectBoundary - leftSubjectBoundary + 1;
    
    final BitSet fCoverage = 
      f.hyp.foreignCoverage.get(leftSubjectBoundary, rightSubjectBoundary + 1);

    boolean subjCovered = (fCoverage.cardinality() == length);

    return verbCovered && subjCovered;
  }

  /**
   * Returns the index of the last subject for which the feature fired.
   * 
   * @param f
   * @param subjects
   * @param verbs
   * @return
   */
  //WSGDEBUG - Should we constrain to non-monotonic subject scoring?
  private int getLastSubjectScored(Featurizable<IString,String> f, List<Triple<Integer,Integer,Integer>> subjects) {
    int prevSubjIndex = -1;
    for(Triple<Integer,Integer,Integer> subject : subjects) {
      if(isCovered(subject,f))
        prevSubjIndex++;
      else
        break;
    }

    return prevSubjIndex;
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

  private int getRightTargetSideBoundary(Pair<Integer,Integer> span, Featurizable<IString,String> f) {
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

  public FeatureValue<String> featurize(Featurizable<IString,String> f) {

    //Get the subject triplets Return if there aren't any
    final int translationId = f.translationId + (PseudoMoses.local_procs > 1 ? 2 : 0);
    List<Triple<Integer,Integer,Integer>> subjectSpans = subjectBank.subjectsForSentence(translationId);
    if(subjectSpans == null || subjectSpans.size() == 0)
      return null;

    //Get the subject that we should consider. Return if we have covered them all
    final int lastSubject = getLastSubjectScored(f.prior, subjectSpans);
    final int currentSubject = getLastSubjectScored(f, subjectSpans);

    //WSGDEBUG
    boolean VERBOSE = (translationId == 5);

    //If the subject has just been completed, then score it.
    if(lastSubject != currentSubject) {
      Triple<Integer,Integer,Integer> activeSubjectGroup = subjectSpans.get(currentSubject);
      final int vIdx = activeSubjectGroup.first();
      Pair<Integer,Integer> subject = new Pair<Integer,Integer>(activeSubjectGroup.second(),activeSubjectGroup.third());

      final int eSubjRightBound = getRightTargetSideBoundary(subject,f);
      final int eVerbRightBound = getRightTargetSideBoundary(new Pair<Integer,Integer>(vIdx,vIdx), f);

      if(VERBOSE) {
        System.err.printf("WSGDEBUG tId %d: Completed subject %d --> %d\n",translationId,lastSubject,currentSubject);
        System.err.printf(" vb %d lsb %d rsb %d\n", activeSubjectGroup.first(),activeSubjectGroup.second(),activeSubjectGroup.third());
        System.err.println("=== Current Featurizer ===");
        System.err.printf(" TransOpt: %s ||| %s\n", f.foreignPhrase.toString(), f.translatedPhrase.toString());
        System.err.printf(" cov: %s\n", f.option.foreignCoverage.toString());
        System.err.printf(" hyp: %s\n", f.partialTranslation.toString());
        System.err.printf(" hyp cov: %s\n", f.hyp.foreignCoverage.toString());
        System.err.println("=== Prior Featurizer ===");
        System.err.printf(" TransOpt: %s ||| %s\n", f.prior.foreignPhrase.toString(), f.prior.translatedPhrase.toString());
        System.err.printf(" cov: %s\n", f.prior.option.foreignCoverage.toString());
        System.err.printf(" hyp: %s\n", f.prior.partialTranslation.toString());
        System.err.printf(" hyp cov: %s\n", f.prior.hyp.foreignCoverage.toString());        
      }

      if(fireFeature(eVerbRightBound,eSubjRightBound)) {
        if(VERBOSE)
          System.err.printf("======>>FIRING FEATURE %f\n",FEATURE_VALUE);

        return new FeatureValue<String>(FEATURE_NAME, FEATURE_VALUE);
      } else {
        if(VERBOSE)
          System.err.printf("======>>NOT FIRING FEATURE %f\n",FEATURE_VALUE);
      }
    }

    return null;
  }

  // Unused but required methods
  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {}
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }
  public void reset() {}

}
