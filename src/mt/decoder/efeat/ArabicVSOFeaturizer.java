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
  private final String cFilePath;
  private final String rFilePath;
  private boolean noSubjects = false;
  private List<Pair<Integer,Integer>> subjectSpans = null;

  private static final int CROSSES_BOUNDARY = -2;
  private static final int NOT_IN_SUBJECT = -1;

  public ArabicVSOFeaturizer(String... args) {
    rFilePath = args[0];
    cFilePath = args[1];
  }

  private int getSubjectIdForPhrase(int phraseStart, int len) {
    Iterator<Pair<Integer,Integer>> itr = subjectSpans.iterator();
    int phraseEnd = phraseStart + len - 1;
    int subjectId = 0;
    boolean phraseInSubject = false;
    
    while(itr.hasNext()) {
      
      Pair<Integer,Integer> subject = itr.next();
      
      if(phraseStart >= subject.first() && phraseEnd <= subject.second()) {
        phraseInSubject = true;
        break;
      
      } else if(phraseStart < subject.first() && phraseEnd <= subject.second()) {
        subjectId = CROSSES_BOUNDARY;
        break;
      
      } else if(phraseStart >= subject.first() && phraseEnd > subject.second()) {
        subjectId = CROSSES_BOUNDARY;
        break;
      
      }
      subjectId++;
    }

    return (phraseInSubject) ? subjectId : NOT_IN_SUBJECT;
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
      if(subjectCoverage.cardinality() == span)
        return true;
    }

    return false;
  }

  @SuppressWarnings("unchecked")
  private double getPastAwardForSubject(int subjectId, Featurizable<IString,String> f) {

    Featurizable<IString,String> priorFeaturizable = f.prior;
    double cumulativeAward = 0.0;

    while(priorFeaturizable != null) {

      Pair<Integer,Boolean> action = (Pair<Integer,Boolean>) priorFeaturizable.extra;

      if(action != null) {
        if((action.first() == subjectId) && action.second()) {
          cumulativeAward += (double) priorFeaturizable.translatedPhrase.size();
        } else if(action.first() == (subjectId - 1))
          break;
      }
      priorFeaturizable = priorFeaturizable.prior;
    }

    return cumulativeAward;
  }

  @SuppressWarnings("unchecked")
  public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    if(noSubjects) return null;

    Pair<Integer,Boolean> thisAction = new Pair<Integer,Boolean>(0,false);
    if(f.prior != null && f.prior.extra != null) {
      Pair<Integer,Boolean> lastAction = (Pair<Integer,Boolean>) f.prior.extra;
      lastAction.setFirst(new Integer(lastAction.first()));
      lastAction.setSecond(new Boolean(lastAction.second()));
    } 
    f.extra = thisAction;

    int subjectInProgress = thisAction.first();		
    int thisPhrase = getSubjectIdForPhrase(f.foreignPosition,f.foreignPhrase.size());

    if(thisPhrase == NOT_IN_SUBJECT) {
      thisAction.setSecond(false);
      return null;

    } else if(thisPhrase == CROSSES_BOUNDARY) {
      thisAction.setSecond(false);
      double penalty = -1.0 * getPastAwardForSubject(subjectInProgress, f);
      if(penalty == 0.0)
        return null;
      else
        return new FeatureValue<String>(FEATURE_NAME, penalty);
    
    } else {

      if(thisPhrase == subjectInProgress) {
        thisAction.setSecond(true);
        double award = (double) f.translatedPhrase.size();
        return new FeatureValue<String>(FEATURE_NAME, award);

      } else { //it's in another subject
        if(isComplete(subjectInProgress,f)) {

          thisAction.setFirst(thisPhrase);
          thisAction.setSecond(true);
          double award = (double) f.translatedPhrase.size();

          if(isContiguous(subjectInProgress,f)) {
            return new FeatureValue<String>(FEATURE_NAME, award);
          }

          double penalty = -1.0 * getPastAwardForSubject(subjectInProgress,f);
          return new FeatureValue<String>(FEATURE_NAME, award + penalty);

        } else {
          thisAction.setFirst(thisPhrase);
          thisAction.setSecond(true);
          double award = (double) f.translatedPhrase.size();
          double penalty = -1.0 * getPastAwardForSubject(subjectInProgress,f);
          return new FeatureValue<String>(FEATURE_NAME, award + penalty);
        }
      }
    }
  }

  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {
    ArabicSubjectBank sb = ArabicSubjectBank.getInstance();
    sb.load(rFilePath,cFilePath);

    subjectSpans = sb.subjectsForSentence(foreign);
    if(subjectSpans == null)
      throw new RuntimeException(String.format("%s: Null subject span for sentence (%s)",this.getClass().getName(), foreign.toString()));

    System.err.printf("%s: %d subjects for sentence\n%s\n", this.getClass().getName(), subjectSpans.size(), foreign.toString());

    noSubjects = (subjectSpans.size() == 0);
  }


  // Unused but required methods
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }
  public void reset() {}

}
