package edu.stanford.nlp.mt.decoder.efeat;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.mt.decoder.feat.AlignmentFeaturizer;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.decoder.feat.ClonedFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.StatefulFeaturizer;

public class ArabicVSOkbestFeaturizer extends StatefulFeaturizer<IString,String> implements ClonedFeaturizer<IString, String>, AlignmentFeaturizer {

  private static final String FEATURE_NAME = "ArabicVSOkbestFeaturizer";

  private static Map<Sequence<IString>,Integer> sentenceToId;
  private final ArabicKbestSubjectBank subjectBank;
  private final int verbGap;

  //Set by initialize()
  private double defaultNullAnalysisLogProb;
  private Map<Integer,List<Triple<Integer,Integer,Double>>> verbMap = null;
  private List<Integer> orderedVerbs = null;

  public ArabicVSOkbestFeaturizer(String... args) {

    assert args.length == 3;

    File subjFile = new File(args[0]);
    if(!subjFile.exists())
      throw new RuntimeException(String.format("%s: File does not exist (%s)",this.getClass().getName(),subjFile.getPath()));

    subjectBank = ArabicKbestSubjectBank.getInstance();
    subjectBank.load(subjFile);

    File unkFile = new File(args[1]);
    if(!unkFile.exists())
      throw new RuntimeException(String.format("%s: File does not exist (%s)",this.getClass().getName(),unkFile.getPath()));

    sentenceToId = getSentenceMap(unkFile);

    verbGap = Integer.parseInt(args[2].trim());
  }


  private static Map<Sequence<IString>, Integer> getSentenceMap(File unkFile) {
    Map<Sequence<IString>,Integer> sentenceMap = new HashMap<Sequence<IString>,Integer>();

    LineNumberReader reader = IOTools.getReaderFromFile(unkFile);
    try {
      for(int transId = 0; reader.ready(); transId++) {
        String[] tokens = reader.readLine().split("\\s+");
        Sequence<IString> foreign = new SimpleSequence<IString>(true, IStrings.toIStringArray(tokens));
        sentenceMap.put(foreign, transId);
      }
      reader.close();

    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Error while reading unk file");
    }

    return sentenceMap;
  }

  /**
   * Returns true if the span of token positions specified by span is completely covered
   * in the partial hypothesis of f. Otherwise, returns false.
   *
   */
  private static boolean isCovered(int leftSubjectBoundary, int rightSubjectBoundary, final int verbIdx, final Featurizable<IString,String> f) {
    if(f == null)
      return false;

    boolean verbCovered = f.hyp.foreignCoverage.get(verbIdx);

    final int subjLength = rightSubjectBoundary - leftSubjectBoundary + 1;

    final BitSet fSubjCoverage =
      f.hyp.foreignCoverage.get(leftSubjectBoundary, rightSubjectBoundary + 1);

    boolean subjCovered = (fSubjCoverage.cardinality() == subjLength);

    return verbCovered && subjCovered;
  }

  private static int getRightTargetSideBoundary(int sLeft, int sRight, final Featurizable<IString,String> f) {
    int maxRightIndex = -1;
    for(int i = sLeft; i <= sRight; i++) {
      final int[] eRange = f.f2tAlignmentIndex[i];
      if(eRange == null) continue;

      final int rightIndex = eRange[Featurizable.PHRASE_END] - 1; //Convert to real index
      if(rightIndex > maxRightIndex)
        maxRightIndex = rightIndex;
    }

    return maxRightIndex;
  }

  private static boolean matches(Featurizable<IString, String> f,
                          Triple<Integer, Integer, Double> subject, int thisVerbIdx) {

    if(isCovered(subject.first(),subject.second(),thisVerbIdx,f)) {
      int rightSubjTargetBoundary = getRightTargetSideBoundary(subject.first(), subject.second(), f);
      int rightVerbTargetBoundary = getRightTargetSideBoundary(thisVerbIdx,thisVerbIdx,f);
      int leftVerbTargetBoundary = f.f2tAlignmentIndex[thisVerbIdx][Featurizable.PHRASE_START];

      if(rightSubjTargetBoundary == rightVerbTargetBoundary || rightSubjTargetBoundary + 1 == leftVerbTargetBoundary)
        return true;
    }

    return false;
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString,String> f) {

    //No subjects
    if(verbMap == null)
      return null;

//    final int translationId = f.translationId + (Phrasal.local_procs > 1 ? 2 : 0);
//    boolean VERBOSE = (translationId == 14);

    //Check for the last verb scored
    final int lastVerbIdx = (f.prior == null) ? -1 : (Integer) f.prior.getState(this);
    final int thisVerbIdx = (lastVerbIdx + 1 < orderedVerbs.size()) ?
        orderedVerbs.get(lastVerbIdx + 1) : -1;
    if(thisVerbIdx == -1)
      return null; //We're done

    //Score as soon as the verb is laid down
    if(f.hyp.foreignCoverage.get(thisVerbIdx)) {

      List<Triple<Integer,Integer,Double>> subjectAnalyses = verbMap.get(thisVerbIdx);
      if(subjectAnalyses == null)
        throw new RuntimeException(String.format("%s: No subjects for verb idx (%d)", FEATURE_NAME, thisVerbIdx));

      Triple<Integer,Integer,Double> nullAnalysis = null;
      double logProb = 0.0;
      for(Triple<Integer,Integer,Double> subject : subjectAnalyses) {
        if(subject.first() == -1 && subject.second() == -1) {
          nullAnalysis = subject;
        } else if(matches(f,subject,thisVerbIdx)) {
          logProb = subject.third();
          break;
        }
      }

      f.setState(this, thisVerbIdx);

      //Compute the feature score
      if(logProb != 0.0)
        return new FeatureValue<String>(FEATURE_NAME,logProb);
      else if(nullAnalysis != null)
        return new FeatureValue<String>(FEATURE_NAME,nullAnalysis.third());
      else
        return new FeatureValue<String>(FEATURE_NAME,defaultNullAnalysisLogProb);
    }

    f.setState(this, lastVerbIdx);

    return null;

      //WSGDEBUG
//      if(VERBOSE) {
//        System.err.printf("WSGDEBUG tId %d: Completed subject %d --> %d\n",translationId,lastSubjectVect,currentSubjectVect);
//        System.err.println("=== Current Featurizer ===");
//        System.err.printf(" TransOpt: %s ||| %s\n", f.foreignPhrase.toString(), f.translatedPhrase.toString());
//        System.err.printf(" cov: %s\n", f.option.foreignCoverage.toString());
//        System.err.printf(" hyp: %s\n", f.partialTranslation.toString());
//        System.err.printf(" hyp cov: %s\n", f.hyp.foreignCoverage.toString());
//        System.err.println("=== Prior Featurizer ===");
//        if(f.prior != null) {
//          System.err.printf(" TransOpt: %s ||| %s\n", f.prior.foreignPhrase.toString(), f.prior.translatedPhrase.toString());
//          System.err.printf(" cov: %s\n", f.prior.option.foreignCoverage.toString());
//          System.err.printf(" hyp: %s\n", f.prior.partialTranslation.toString());
//          System.err.printf(" hyp cov: %s\n", f.prior.hyp.foreignCoverage.toString());
//        }
//      }
  }


  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {

    if(!sentenceToId.containsKey(foreign))
      throw new RuntimeException(String.format("%s: No mapping found for sentence:\n%s\n", FEATURE_NAME, foreign.toString()));

    final int translationId = sentenceToId.get(foreign);
    List<ArabicKbestAnalysis> analyses = subjectBank.getAnalyses(translationId);

    if(analyses != null) {

      //Set the null analysis log prob and get the verbs
      Set<Integer> allVerbs = new HashSet<Integer>();
      double nullAnalRealAccumulator = 0.0;
      double minLogCRFScore = Integer.MAX_VALUE;
      double nullDenom = 0.0;
      for(ArabicKbestAnalysis anal : analyses) {
        if (anal.subjects.keySet().isEmpty())
          nullAnalRealAccumulator += Math.exp(anal.logCRFScore);
        else {
          allVerbs.addAll(anal.verbs);
          if(anal.logCRFScore < minLogCRFScore)
            minLogCRFScore = anal.logCRFScore;
        }
        nullDenom += Math.exp(anal.logCRFScore);
      }

      if(nullAnalRealAccumulator == 0.0)
        defaultNullAnalysisLogProb = Math.log(nullAnalRealAccumulator) - Math.log(nullDenom);
      else //Some arbitrarily (low) score
        defaultNullAnalysisLogProb = (3.0 * minLogCRFScore) - Math.log(nullDenom);


      //Set the other probabilities (for each verb)
      verbMap = new HashMap<Integer,List<Triple<Integer,Integer,Double>>>(allVerbs.size());

      for(int verbIdx : allVerbs) {
        Map<Pair<Integer,Integer>,Double> subjects = new HashMap<Pair<Integer,Integer>,Double>();

        //Iterate over the analyses and compute the real-valued numerator(s) and
        //denominator
        double denom = 0.0;
        double nullNumerator = 0.0;
        for(ArabicKbestAnalysis anal : analyses) {

          boolean noAnalysis = true;
          for(int gap = 1; gap <= verbGap; gap++) {

            if(anal.subjects.containsKey(verbIdx + gap)) {
              denom += Math.exp(anal.logCRFScore);
              noAnalysis = true;
              Pair<Integer,Integer> thisSubj = new Pair<Integer,Integer>(verbIdx + gap, anal.subjects.get(verbIdx + gap));

              if(subjects.containsKey(thisSubj)) {
                double realScore = subjects.get(thisSubj);
                realScore += Math.exp(anal.logCRFScore);
                subjects.put(thisSubj, realScore);
              } else {
                subjects.put(thisSubj, Math.exp(anal.logCRFScore));
              }
              break;

            }
          }
          if(noAnalysis)
            nullNumerator += Math.exp(anal.logCRFScore);
        }

        //Now compute the log probabilities from the real values
        List<Triple<Integer,Integer,Double>> probList = new ArrayList<Triple<Integer,Integer,Double>>(subjects.keySet().size());

        //Check for null analyses
        if(nullNumerator != 0.0) {
          double logProb = Math.log(nullNumerator) - Math.log(denom);
          Triple<Integer,Integer,Double> nullAnal = new Triple<Integer,Integer,Double>(-1,-1,logProb);
          probList.add(nullAnal);
        }

        //Iterate over the other analyses
        for(Map.Entry<Pair<Integer,Integer>, Double> subject : subjects.entrySet()) {
          double logProb = Math.log(subject.getValue()) - Math.log(denom);
          Pair<Integer,Integer> span = subject.getKey();
          Triple<Integer,Integer,Double> finalSubj = new Triple<Integer,Integer,Double>(span.first(),span.second(),logProb);
          probList.add(finalSubj);
        }

        verbMap.put(verbIdx, probList);
      }

      orderedVerbs = new ArrayList<Integer>(verbMap.keySet());
      Collections.sort(orderedVerbs);
    }

  }

  @Override
  public ArabicVSOkbestFeaturizer clone() throws CloneNotSupportedException {
    return (ArabicVSOkbestFeaturizer)super.clone();
  }

  // Unused but required methods
  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }

  @Override
  public void reset() {}

}
