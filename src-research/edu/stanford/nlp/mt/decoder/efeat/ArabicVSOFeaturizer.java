package edu.stanford.nlp.mt.decoder.efeat;

import java.io.File;
import java.util.*;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.AlignmentFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

public class ArabicVSOFeaturizer implements
    IncrementalFeaturizer<IString, String>, AlignmentFeaturizer {

  private String FEATURE_NAME = "ArabicVSOFeaturizer";
  private static final double FEATURE_VALUE = -10.0;
  private static final double DEFAULT_BIN1_VALUE = -99.0;
  private final ArabicSubjectBank subjectBank;
  private final int modeIndicator;

  public ArabicVSOFeaturizer(String... args) {

    assert args.length == 4;

    File subjFile = new File(args[0]);
    if (!subjFile.exists())
      throw new RuntimeException(String.format("%s: File does not exist (%s)",
          this.getClass().getName(), subjFile.getPath()));

    int maxSubjLen = Integer.parseInt(args[1].trim());

    modeIndicator = Integer.parseInt(args[2].trim());
    FEATURE_NAME += (modeIndicator == 1) ? "1" : "0";

    int maxVerbGap = Integer.parseInt(args[3].trim());

    // Do the loading here to accommodate multi-threading
    subjectBank = ArabicSubjectBank.getInstance();
    subjectBank.load(subjFile, maxSubjLen, maxVerbGap);
  }

  /**
   * We can fire as soon as the verb is laid down.
   * 
   */
  private boolean isCovered(final Triple<Integer, Integer, Integer> span,
      final Featurizable<IString, String> f) {
    if (f == null)
      return false;

    boolean verbCovered = f.hyp.sourceCoverage.get(span.first());

    return verbCovered;
  }

  /**
   * Returns the index of the last subject for which the feature fired.
   * 
   */
  private int getLastSubjectScored(Featurizable<IString, String> f,
      List<Triple<Integer, Integer, Integer>> subjects) {
    int prevSubjIndex = -1;
    for (Triple<Integer, Integer, Integer> subject : subjects) {
      if (isCovered(subject, f))
        prevSubjIndex++;
      else
        break;
    }

    return prevSubjIndex;
  }

  /**
   * Indicates when the feature should fire dependent on the scoring mode.
   * 
   */
  private boolean fireFeature(final Pair<Integer, Integer> subject,
      final int verbIdx, Featurizable<IString, String> f, boolean VERBOSE) {

    final int length = subject.second() - subject.first() + 1;
    final BitSet fCoverage = f.hyp.sourceCoverage.get(subject.first(),
        subject.second() + 1);

    final boolean subjCovered = (fCoverage.cardinality() == length);

    final int eSubjRightBound = getRightTargetSideBoundary(subject, f);
    final int eVerbRightBound = getRightTargetSideBoundary(
        new Pair<Integer, Integer>(verbIdx, verbIdx), f);
    final int eVerbLeftBound = getLeftTargetSideBoundary(
        new Pair<Integer, Integer>(verbIdx, verbIdx), f);

    if (VERBOSE) {
      System.err.println("=== Feature Values ===");
      System.err.printf(" mode:   %d\n", modeIndicator);
      System.err.printf(" len:    %d\n", length);
      System.err.printf(" fCov:   %s\n", fCoverage.toString());
      System.err.printf(" IsCov:  %b\n", subjCovered);
      System.err.printf(" eSubR:  %d\n", eSubjRightBound);
      System.err.printf(" eVbL/R: %d %d \n", eVerbLeftBound, eVerbRightBound);
    }

    // Positive feature representation
    if (modeIndicator == 1) {
      if (subjCovered
          && (eVerbRightBound == eSubjRightBound || (eSubjRightBound + 1) == eVerbLeftBound))
        return true;
    } else {
      if (!subjCovered || eVerbRightBound < eSubjRightBound)
        return true;
    }

    return false;
  }

  private int getLeftTargetSideBoundary(Pair<Integer, Integer> span,
      Featurizable<IString, String> f) {
    int minLeftIndex = Integer.MAX_VALUE;
    for (int i = span.first(); i <= span.second(); i++) {
      final int[] eRange = f.s2tAlignmentIndex[i];
      if (eRange == null)
        continue;

      final int leftIndex = eRange[Featurizable.PHRASE_START];
      if (leftIndex < minLeftIndex)
        minLeftIndex = leftIndex;
    }
    return (minLeftIndex == Integer.MAX_VALUE) ? Integer.MIN_VALUE
        : minLeftIndex;
  }

  private int getRightTargetSideBoundary(Pair<Integer, Integer> span,
      Featurizable<IString, String> f) {
    int maxRightIndex = Integer.MIN_VALUE;
    for (int i = span.first(); i <= span.second(); i++) {
      final int[] eRange = f.s2tAlignmentIndex[i];
      if (eRange == null)
        continue;

      final int rightIndex = eRange[Featurizable.PHRASE_END] - 1; // Convert to
                                                                  // real index
      if (rightIndex > maxRightIndex)
        maxRightIndex = rightIndex;
    }

    return (maxRightIndex == Integer.MIN_VALUE) ? Integer.MAX_VALUE
        : maxRightIndex;
  }

  public FeatureValue<String> featurize(Featurizable<IString, String> f) {

    final int translationId = f.sourceInputId;

    // Get the subject triplets Return if there aren't any
    List<Triple<Integer, Integer, Integer>> subjectSpans = subjectBank
        .subjectsForSentence(translationId);
    if (subjectSpans == null || subjectSpans.size() == 0)
      return null;

    // Get the subject that we should consider. Return if we have covered them
    // all
    final int lastSubject = getLastSubjectScored(f.prior, subjectSpans);
    final int currentSubject = getLastSubjectScored(f, subjectSpans);

    // WSGDEBUG
    boolean VERBOSE = (translationId == 54);

    // If the subject has just been completed, then score it.
    if (lastSubject != currentSubject) {
      Triple<Integer, Integer, Integer> activeSubjectGroup = subjectSpans
          .get(currentSubject);
      final int verbIdx = activeSubjectGroup.first();
      Pair<Integer, Integer> subject = new Pair<Integer, Integer>(
          activeSubjectGroup.second(), activeSubjectGroup.third());

      if (VERBOSE) {
        System.err.printf("WSGDEBUG %s tId %d: Completed subject %d --> %d\n",
            FEATURE_NAME, translationId, lastSubject, currentSubject);
        System.err.printf(" vb %d lsb %d rsb %d\n", activeSubjectGroup.first(),
            activeSubjectGroup.second(), activeSubjectGroup.third());
        System.err.println("=== Current Featurizer ===");
        System.err.printf(" TransOpt: %s ||| %s\n", f.sourcePhrase.toString(),
            f.targetPhrase.toString());
        System.err.printf(" cov: %s\n", f.option.sourceCoverage.toString());
        System.err.printf(" hyp: %s\n", f.targetPrefix.toString());
        System.err.printf(" hyp cov: %s\n", f.hyp.sourceCoverage.toString());
        System.err.println("=== Prior Featurizer ===");
        if (f.prior != null) {
          System.err.printf(" TransOpt: %s ||| %s\n",
              f.prior.sourcePhrase.toString(),
              f.prior.targetPhrase.toString());
          System.err.printf(" cov: %s\n",
              f.prior.option.sourceCoverage.toString());
          System.err
              .printf(" hyp: %s\n", f.prior.targetPrefix.toString());
          System.err.printf(" hyp cov: %s\n",
              f.prior.hyp.sourceCoverage.toString());
        }
      }

      if (fireFeature(subject, verbIdx, f, VERBOSE)) {
        if (VERBOSE)
          System.err.printf("======>>FIRING %s %f\n", FEATURE_NAME,
              FEATURE_VALUE);

        if (modeIndicator == 1) {
          if (f.prior == null)
            return new FeatureValue<String>(FEATURE_NAME, DEFAULT_BIN1_VALUE
                - (DEFAULT_BIN1_VALUE / (double) subjectSpans.size()));
          else
            return new FeatureValue<String>(FEATURE_NAME, -1
                * DEFAULT_BIN1_VALUE / (double) subjectSpans.size());
          // else if(currentSubject == 0)
          // return new FeatureValue<String>(FEATURE_NAME, FEATURE_VALUE -
          // DEFAULT_BIN1_VALUE); //Back out the default penalty
          // else { //Need to check if this is the first time that the feature
          // has fired
          // for(int i = currentSubject - 1; i >= 0; i--) {
          // Triple<Integer,Integer,Integer> prevSubjectGroup =
          // subjectSpans.get(i);
          // final int vIdx = prevSubjectGroup.first();
          // Pair<Integer,Integer> subjSpan = new
          // Pair<Integer,Integer>(prevSubjectGroup.second(),prevSubjectGroup.third());
          // if(fireFeature(subjSpan,vIdx,f,VERBOSE))
          // return new FeatureValue<String>(FEATURE_NAME, -1 * FEATURE_VALUE /
          // (currentSubject + 2));
          // }
          // //A first-timer....
          // return new FeatureValue<String>(FEATURE_NAME, FEATURE_VALUE);
          // }
        }

        return new FeatureValue<String>(FEATURE_NAME, FEATURE_VALUE);

      } else if (VERBOSE)
        System.err.printf("======>>NOT FIRING %s\n", FEATURE_NAME);

    } else if (f.prior == null && modeIndicator == 1)
      return new FeatureValue<String>(FEATURE_NAME, DEFAULT_BIN1_VALUE);

    return null;
  }

  // Unused but required methods
  public void initialize(List<ConcreteTranslationOption<IString,String>> options,
      Sequence<IString> foreign, Index<String> featureIndex) {
  }

  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    return null;
  }

  public void reset() {
  }
}
