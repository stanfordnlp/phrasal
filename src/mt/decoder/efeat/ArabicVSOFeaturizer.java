package mt.decoder.efeat;

import java.util.*;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.decoder.feat.IncrementalFeaturizer;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.Pair;

public class ArabicVSOFeaturizer implements IncrementalFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "ArabicVSOFeaturizer";
  private static final double FEATURE_PENALTY = 1.0;
  private final String cFilePath;
  private final String rFilePath;
  private boolean noSubjects = false;
  private List<Pair<Integer,Integer>> subjectSpans = null;

  private static boolean VERBOSE = false;

  private static final int CROSSES_BOUNDARY = -2;
  private static final int NOT_IN_SUBJECT = -1;

  public ArabicVSOFeaturizer(String... args) {
    
    assert args.length == 2;
    rFilePath = args[0];
    cFilePath = args[1];
    
    //Do the loading here to accommodate multi-threading
    ArabicSubjectBank sb = ArabicSubjectBank.getInstance();
    sb.load(rFilePath,cFilePath);
  }

  private int getSubjectIdForPhrase(int phraseStart, int len) {
    Iterator<Pair<Integer,Integer>> itr = subjectSpans.iterator();
    int phraseEnd = phraseStart + len - 1;
    int subjectId = 0;
    
    while(itr.hasNext()) {

      Pair<Integer,Integer> subject = itr.next();

      //TODO More principled selection of preceding verb ... maybe grab this from the CRF input??
      int lowerBound = subject.first() - 1;
      
      if(phraseStart >= lowerBound && phraseEnd >= subject.first() && phraseEnd <= subject.second()) {
        return subjectId;

//      } else if(phraseStart < lowerBound && phraseEnd >= subject.first() && phraseEnd <= subject.second()) {
//        return CROSSES_BOUNDARY;
//
//      } else if(phraseStart >= subject.first() && phraseStart <= subject.second() && phraseEnd > subject.second()) {
//        return CROSSES_BOUNDARY;

      }
      subjectId++;
    }

    return NOT_IN_SUBJECT;
  }

  private boolean isComplete(int subjectId, Featurizable<IString,String> f) {
    Pair<Integer,Integer> subject = subjectSpans.get(subjectId);
    for(int i = subject.first(); i <= subject.second(); i++) {
      int[] eRange = f.f2tAlignmentIndex[i];
      if(eRange == null) {
        return false;
      }
    }
    return true;
  }

  private boolean isContiguous(int subjectId, Featurizable<IString,String> f) {
    Pair<Integer,Integer> subject = subjectSpans.get(subjectId);
    BitSet coverage = new BitSet(f.hyp.length);

    boolean complete = true;
    int eStart = Integer.MAX_VALUE;
    int eEnd = Integer.MIN_VALUE;
    for(int i = subject.first(); i <= subject.second(); i++) {
      int[] eRange = f.f2tAlignmentIndex[i];
      if(eRange == null) {
        complete = false;
        break;
      }
      else {
        if(eRange[Featurizable.PHRASE_START] < eStart)
          eStart = eRange[Featurizable.PHRASE_START];
        if(eRange[Featurizable.PHRASE_END] > eEnd)
          eEnd = eRange[Featurizable.PHRASE_END];

        coverage.set(eRange[Featurizable.PHRASE_START], eRange[Featurizable.PHRASE_END] + 1);
      } 
    }

    if(complete) {
      int span = eEnd - eStart + 1;
      BitSet subjectCoverage = coverage.get(eStart, eEnd + 1);
      if(subjectCoverage.cardinality() == span) {
        if(VERBOSE)
          System.err.printf("%s: Subject %d is contiguous\n", this.getClass().getName(), subjectId);
        return true;
      }
    }

    if(VERBOSE)
      System.err.printf("%s: Subject %d is NOT contiguous\n", this.getClass().getName(), subjectId);

    return false;
  }

  @SuppressWarnings("unchecked")
  private double getPastAwardForSubject(int subjectId, Featurizable<IString,String> f) {

    Featurizable<IString,String> priorFeaturizable = f.prior;
    double cumulativeAward = 0.0;

    while(priorFeaturizable != null) {

      Pair<Integer,Boolean> action = (Pair<Integer,Boolean>) priorFeaturizable.extra;

      if(action != null && action.second())
        cumulativeAward += FEATURE_PENALTY;

      priorFeaturizable = priorFeaturizable.prior;
    }

    if(VERBOSE)
      System.err.printf("%s: Cumulative award for subject %d (%f)\n", this.getClass().getName(), subjectId, cumulativeAward);

    return cumulativeAward;
  }

  @SuppressWarnings("unchecked")
  public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    if(noSubjects) return null;

    Set<Integer> scoredSubjects = (f.prior != null && f.prior.extra != null) 
          ? (Set<Integer>) f.prior.extra : new HashSet<Integer>();
    
    f.extra = scoredSubjects;

    int thisPhrase = getSubjectIdForPhrase(f.foreignPosition, f.foreignPhrase.size());

    //TODO v6 should advance past leading conjunctions and punctuation
    
    if(thisPhrase == NOT_IN_SUBJECT)
      return null;
//    else if(thisPhrase == CROSSES_BOUNDARY) {
//      return new FeatureValue<String>(FEATURE_NAME, -1.0 * FEATURE_PENALTY);
//    }
    else if(!scoredSubjects.contains(thisPhrase)) {
      if(isComplete(thisPhrase,f) && isContiguous(thisPhrase,f)) {
        scoredSubjects.add(thisPhrase);
        return new FeatureValue<String>(FEATURE_NAME,FEATURE_PENALTY);
      }
    }

    return null;
  }

  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {
    ArabicSubjectBank sb = ArabicSubjectBank.getInstance();
    subjectSpans = sb.subjectsForSentence(foreign);
    if(subjectSpans == null)
      throw new RuntimeException(String.format("%s: Unknown sentence in the MT test set that was not processed by the Subject Detector (%s)",this.getClass().getName(), foreign.toString()));

    System.err.printf("%s: %d subjects for sentence\n%s\n", this.getClass().getName(), subjectSpans.size(), foreign.toString());

    noSubjects = (subjectSpans.size() == 0);
    
    //WSGDEBUG
//    VERBOSE =(subjectSpans.size() == 2);
    if(VERBOSE) {
      System.err.println("WSGDEBUG\n");
      Iterator<Pair<Integer,Integer>> itr = subjectSpans.iterator();
      while(itr.hasNext()) {
        Pair<Integer,Integer> subj = itr.next();
        System.err.printf("  [ %d , %d ]\n", subj.first(), subj.second());
      }
    }
  }


  // Unused but required methods
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }
  public void reset() {}

}
