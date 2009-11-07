package mt.decoder.efeat;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Pair;

import mt.PseudoMoses;
import mt.base.ConcreteTranslationOption;
import mt.base.CoverageSet;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.IOTools;
import mt.base.IString;
import mt.base.IStrings;
import mt.base.Sequence;
import mt.base.SimpleSequence;
import mt.decoder.feat.ClonedFeaturizer;
import mt.decoder.feat.RichIncrementalFeaturizer;
import mt.decoder.feat.StatefulFeaturizer;
import mt.train.discrimdistortion.Datum;
import mt.train.discrimdistortion.DistortionModel;

public class DiscrimDistortionFeaturizer extends StatefulFeaturizer<IString, String> implements RichIncrementalFeaturizer<IString, String> {

  private final String FEATURE_NAME = "DiscrimDistortion";

  //Shared objects for all threads
  private static DistortionModel model;
  private final List<List<String>> posTags;
  private static Map<Sequence<IString>,Integer> sentenceToId;

  private final float DEFAULT_C;
  private final float TOL;

  //Threadsafe...the reference will be clone()'d, but the object will be set when
  //initialize() is called
  private double[][] logProbCache = null;
  private float sLen = 0;
  private Map<Pair<CoverageSet,Integer>,Double> futureCostCache;


  private static final Pattern ibmEscaper = Pattern.compile("#|\\+");
  private static final int TARGET_IDX = 0; 
  private static final double SKIPPED_WORD_PENALTY = -0.50;

  //WSGDEBUG Debugging objects
  private boolean VERBOSE = false;
  private boolean SET_STATE = true;
  //  private static final Set<Integer> debugIds = new HashSet<Integer>();
  //  static {
  //    debugIds.add(1);
  //    debugIds.add(4);
  //  }


  public DiscrimDistortionFeaturizer(String... args) {

    assert args.length == 5;

    //The serialized distortion model
    File modelFile = new File(args[0]);
    if(!modelFile.exists())
      throw new RuntimeException(String.format("%s: Model file %s does not exist!\n", this.getClass().getName(), modelFile.getPath()));

    try {
      model = (DistortionModel) IOUtils.readObjectFromFile(modelFile);
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not load discriminative distortion model");

    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not load discriminative distortion model");
    }

    //The tagged file to be translated
    File tagFile = new File(args[1]);
    if(!tagFile.exists())
      throw new RuntimeException(String.format("%s: Tag file %s does not exist!\n",this.getClass().getName(),tagFile.getPath()));

    posTags = getTagCache(tagFile);

    File unkFile = new File(args[2]);
    if(!unkFile.exists())
      throw new RuntimeException(String.format("%s: File does not exist (%s)",this.getClass().getName(),unkFile.getPath()));

    sentenceToId = getSentenceMap(unkFile);

    DEFAULT_C = Float.parseFloat(args[3].trim());
    TOL = Float.parseFloat(args[4].trim());
  }

  private Map<Sequence<IString>, Integer> getSentenceMap(File unkFile) {
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


  //Re-factor to use a jagged array if memory is an issue
  private List<List<String>> getTagCache(final File tagFile) {
    List<List<String>> posTags = new ArrayList<List<String>>();

    LineNumberReader reader = IOTools.getReaderFromFile(tagFile);
    try {
      for(int transId = 0; reader.ready(); transId++) {
        StringTokenizer st = new StringTokenizer(reader.readLine());
        List<String> tagsForSentence = new ArrayList<String>();

        while(st.hasMoreTokens()) {
          String[] parts = st.nextToken().split("#");
          assert parts.length == 2;   
          assert !parts[1].equals("");
          tagsForSentence.add(parts[1].intern());
        }

        posTags.add(tagsForSentence);
      }

      reader.close();

    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Error while reading POS tag file");
    }

    return posTags;
  }

  /**
   * Stolen from DanC
   * @param index
   * @param newLength
   * @return
   */
  private int[][] copy(final int[][] index) {
    int[][] newIndex = new int[index.length][];
    System.arraycopy(index, 0, newIndex, 0, index.length);
    return newIndex;
  }

  /**
   * Current target variable is relative movement.
   * 
   * @param tIdx
   * @param tLen
   * @param sIdx
   * @return
   */
  private float getTargetVariable(int tIdx, int tLen, int sIdx) {
    float tRelPosition = ((float) tIdx / (float) tLen) * 100.0f;
    float sRelPosition = ((float) sIdx / sLen) * 100.0f;

    return tRelPosition - sRelPosition;
  }

  /**
   * Estimated future translation cost for the current translation window.
   * 
   * @param f
   * @param tLenEstimate
   * @return
   */
  private double estimateFutureCost(Featurizable<IString, String> f, int tLenEstimate) {
    if(f == null || f.done) return 0.0;

    Pair<CoverageSet,Integer> cacheKey = new Pair<CoverageSet,Integer>(f.hyp.foreignCoverage,tLenEstimate);
    double futureCost = 0.0;

    if(futureCostCache.containsKey(cacheKey))
      futureCost = futureCostCache.get(cacheKey);
    else {

      final CoverageSet coverage = f.hyp.foreignCoverage;
      final int tPosition = f.partialTranslation.size();
      int sPosition = coverage.nextClearBit(0);
      int rightSBound = sPosition + PseudoMoses.distortionLimit;
      if(rightSBound > f.foreignSentence.size())
        rightSBound = f.foreignSentence.size();
      
      //Uncovered positions
      while(sPosition < coverage.length()) {

        double minLogCost = -999999.0;
        for(int tIdx = tPosition; tIdx < tLenEstimate; tIdx++) {
          float relMovement = getTargetVariable(tIdx, tLenEstimate, sPosition);
          DistortionModel.Class thisClass = DistortionModel.discretizeDistortion(relMovement);
          double logCost = logProbCache[sPosition][thisClass.ordinal()];
          if(logCost > minLogCost)
            minLogCost = logCost;
        }
        futureCost += minLogCost;
        sPosition = coverage.nextClearBit(sPosition + 1); 
      }

      // Rest of the sentence
      for(int sIdx = sPosition; sIdx < rightSBound; sIdx++) {
        
        double minLogCost = -999999.0;
        for(int tIdx = tPosition; tIdx < tLenEstimate; tIdx++) {
          float relMovement = getTargetVariable(tIdx, tLenEstimate, sPosition);
          DistortionModel.Class thisClass = DistortionModel.discretizeDistortion(relMovement);
          double logCost = logProbCache[sPosition][thisClass.ordinal()];
          if(logCost > minLogCost)
            minLogCost = logCost;
        }
        futureCost += minLogCost;
      }

      futureCostCache.put(cacheKey, futureCost); 
    }
    
    //WSGDEBUG
//    double skippedWordPenalty = 0.0;
//    if(f.prior == null)
//      skippedWordPenalty = (f.foreignPosition * SKIPPED_WORD_PENALTY);
//    else {
//      //No future cost payment just like Moses
//      int mosesDistortion = f.prior.foreignPosition + f.prior.foreignPhrase.size() - f.foreignPosition;
//      if (mosesDistortion > 0) mosesDistortion--; //Fix the step function
//      skippedWordPenalty = Math.abs(mosesDistortion) * SKIPPED_WORD_PENALTY;
//    }
//    futureCost += skippedWordPenalty;
    
    if(VERBOSE) {
      System.err.printf(" tLen       : %d\n", tLenEstimate);
      System.err.printf(" coverageSet: %s\n", f.hyp.foreignCoverage.toString());
      System.err.printf(" future cost: %f\n", futureCost);
//      System.err.printf(" skipped_penalty: %f\n", skippedWordPenalty);
    }

    return futureCost;
  }


  @Override
  /**
   * Feature score is a sum of:
   *  1. Cost adjustment (from the last iteration)
   *  2. Cost of this translation option
   *  3. Future cost estimate
   */
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {

    final int translationId = f.translationId + (PseudoMoses.local_procs > 1 ? 2 : 0);

    //
    // Setup the state
    //
    int[][] f2e = (f.prior == null) ? new int[f.foreignSentence.size()][] : copy((int[][]) f.prior.getState(this));
    if(VERBOSE) {
      System.err.println("FEATURIZABLE STATE");
      System.err.println(" Partial: " + f.partialTranslation.toString());
      System.err.printf(" State:\n");
      for(int sIdx = 0; sIdx < f2e.length; sIdx++)
        if(f2e[sIdx] != null)
          System.err.printf(" %d --> %d\n", sIdx, f2e[sIdx][TARGET_IDX]);
      System.err.println();
    }
    
    
    //
    //Compute the previous and target length constants
    //Units are (target length / source length)
    //Added an (empirical) 10 word buffer because the first few options usually cause huge jumps in the value
    //
    float lastC, thisC;
    if(f.prior == null) {
      lastC = DEFAULT_C;
      thisC = DEFAULT_C;

    } else {

      final int numCoveredSourceTokens = f.foreignSentence.size() - f.hyp.untranslatedTokens;
      thisC = (float) f.hyp.length / numCoveredSourceTokens;

      final int lastNumCoveredSourceTokens = f.foreignSentence.size() - f.prior.hyp.untranslatedTokens;
      if(f.prior.prior == null)
        lastC = DEFAULT_C;
      else
        lastC = (float) f.prior.hyp.length / lastNumCoveredSourceTokens;
    }

    //
    // Estimate the length of the translation
    //
    final int thisTLenEstimate = Math.round(sLen * thisC);

    //
    // Estimate the future cost to translate
    //
    if(VERBOSE)
      System.err.println("FUTURE COST ESTIMATE");
    double futureCostEstimate = estimateFutureCost(f, thisTLenEstimate);
    

    //
    // Calculate the feature score adjustment (if needed)
    //
    double adjustment = 0.0;
    if(Math.abs(lastC - thisC) > TOL) {
      final int lastTLenEstimate = Math.round(sLen * lastC);

      if(VERBOSE) {
        System.err.println("FEATURE SCORE ADJUSTMENT");
        System.err.printf(" lastC %f  lastTLen %d ||| thisC %f  thisTLen %d ||| tol %f\n", lastC,lastTLenEstimate,thisC,thisTLenEstimate,TOL);
      }
      
      double oldScore = estimateFutureCost(f.prior,lastTLenEstimate);
      double newScore = estimateFutureCost(f.prior,thisTLenEstimate);
      double oldWordScore = 0.0;
      double newWordScore = 0.0;
      for(int sIdx = 0; sIdx < f2e.length; sIdx++) {
        if(f2e[sIdx] == null) continue; //Skip over uncovered source tokens

        int tIdx = f2e[sIdx][TARGET_IDX];
        oldWordScore = 0.0;
        newWordScore = 0.0;

          float oldRel = getTargetVariable(tIdx, lastTLenEstimate, sIdx);
          DistortionModel.Class oldClass = DistortionModel.discretizeDistortion(oldRel);
          oldWordScore += logProbCache[sIdx][oldClass.ordinal()];

          float newRel = getTargetVariable(tIdx, thisTLenEstimate, sIdx);
          DistortionModel.Class newClass = DistortionModel.discretizeDistortion(newRel);
          newWordScore += logProbCache[sIdx][newClass.ordinal()];
          
          if(VERBOSE) {
            System.err.printf(" [ t %d s %d ] %f --> %f\n", tIdx, sIdx, oldWordScore, newWordScore);
            System.err.printf("  oldRel %f (class %s) --> newRel %f (class %s)\n", oldRel, oldClass.toString(), newRel, newClass.toString());
          }
        oldScore += oldWordScore;
        newScore += newWordScore;
      }

      adjustment = newScore - oldScore; //Difference in log scores

      if(VERBOSE)
        System.err.printf(" final adjustment: %f --> %f  (diff: %f)\n", oldScore, newScore, adjustment);
    }


    //
    // Calculate the score of this translation option
    //
    final int sOffset = f.foreignPosition;
    final int tOffset = f.translationPosition;
    final int tOptLen = f.translatedPhrase.size();

    if(VERBOSE) {
      System.err.println("SCORING TRANSLATION OPTION");
      System.err.printf(" option %s --> %s\n", f.option.abstractOption.foreign.toString(), f.option.abstractOption.translation.toString());
      System.err.printf(" sOffset %d  tOffset %d\n", sOffset, tOffset);
      if(f.option.abstractOption.alignment.hasAlignment())
        System.err.println("  align " + f.option.abstractOption.alignment.toString());
      else
        System.err.println("  align NONE");
    }

    double optScore = 0;
    if(f.option.abstractOption.alignment.hasAlignment()) {
      for(int i = 0; i < tOptLen; i++) {

        final int[] sIndices = f.option.abstractOption.alignment.e2f(i);
        if(sIndices == null) continue;

        for(int j = 0; j < sIndices.length; j++) {
          final int sIdx = sOffset + sIndices[j];
          final int tIdx = tOffset + i;

          if(sIdx >= sLen)
            throw new RuntimeException(String.format("%d alignment index for source sentence of length %f (%s)",sIdx,sLen,sIndices.toString()));

          int[] alignment = new int[1];
          alignment[TARGET_IDX] = tIdx;
          f2e[sIdx] = alignment;        

          //Calculate the score for this source word
          String posTag = posTags.get(translationId).get(sIdx);

          float relMovement = getTargetVariable(tIdx, thisTLenEstimate, sIdx);
          final DistortionModel.Class thisClass = DistortionModel.discretizeDistortion(relMovement);
          final double logProb = logProbCache[sIdx][thisClass.ordinal()];

          if(VERBOSE)
            System.err.printf("  optscore: %f  (sIdx %d  tIdx %d  pos %s  mvmnt %f  class %s)\n", logProb, sIdx, tIdx, posTag, relMovement, thisClass.toString());

          optScore += logProb;
        }
      }
    }

    //
    // Check for unaligned source tokens 
    //   Why now? (iterate over source side after surrounding tokens have been scored)
    //
    if(VERBOSE)
      System.err.println("CHECKING FOR UNALIGNED TOKENS");

    for(int sIdx = sOffset; sIdx < sOffset + f.foreignPhrase.size() && sIdx < f2e.length; sIdx++) {
      if(f2e[sIdx] == null) {

        final String posTag = posTags.get(translationId).get(sIdx);

        int tIdx = interpolateTargetPosition(sIdx,f2e,sOffset,sOffset + f.foreignPhrase.size());

        //Should ONLY happen for OOVs
        if(tIdx == Integer.MIN_VALUE) {
          tIdx = tOffset; //Assume monotone
          float relMovement = getTargetVariable(tIdx, thisTLenEstimate, sIdx);
          final DistortionModel.Class thisClass = DistortionModel.discretizeDistortion(relMovement);
          final double logProb = logProbCache[sIdx][thisClass.ordinal()];
          
          optScore += logProb;
          
          if(VERBOSE)
            System.err.printf(" unkscore: %f  (sIdx %d tIdx %d POS %s) OOV\n", logProb, sIdx, tIdx, posTag);

        } else {
          float relMovement = getTargetVariable(tIdx, thisTLenEstimate, sIdx);
          final DistortionModel.Class thisClass = DistortionModel.discretizeDistortion(relMovement);
          final double logProb = logProbCache[sIdx][thisClass.ordinal()];

          if(VERBOSE)
            System.err.printf(" unkscore: %f  (sIdx %d  tIdx %d  POS %s  mvmnt %f  class %s)\n", logProb, sIdx, tIdx, posTag, relMovement, thisClass.toString());

          optScore += logProb;
        }

        int[] alignment = new int[1];
        alignment[TARGET_IDX] = tIdx;
        f2e[sIdx] = alignment;             
      }
    }


    //
    // Record the state
    //
    if(SET_STATE)
      f.setState(this, f2e);

    return new FeatureValue<String>(FEATURE_NAME, futureCostEstimate + optScore + adjustment);
  }
  

  private int interpolateTargetPosition(int sIdx, int[][] f2e, int leftBound, int rightBound) {

    int leftTargetIdx = -1;
    int rightTargetIdx = -1;
    for(int offset = 1; offset < f2e.length; offset++) {
      final int leftSIdx = sIdx - offset;
      if(leftSIdx >= leftBound && f2e[leftSIdx] != null)
        leftTargetIdx = f2e[leftSIdx][TARGET_IDX];

      final int rightSIdx = sIdx + offset;
      if(rightSIdx < rightBound && f2e[rightSIdx] != null)
        rightTargetIdx = f2e[rightSIdx][TARGET_IDX];

      if(leftTargetIdx != -1 || rightTargetIdx != -1)
        break;
    }

    //Should only happen for OOVs
    if(leftTargetIdx == -1 && rightTargetIdx == -1)
      return Integer.MIN_VALUE;
    else if(leftTargetIdx != -1 && rightTargetIdx != -1) {
      if(leftTargetIdx == rightTargetIdx)
        return leftTargetIdx;
      else
        return Math.abs((rightTargetIdx + leftTargetIdx) / 2);
    } else
      return (leftTargetIdx != -1) ? leftTargetIdx : rightTargetIdx;
  }
  

  private void prettyPrint(Datum d, boolean isOOV, String word) {
    int i = 0;
    for(DistortionModel.Feature feat : model.featureIndex) {
      if(feat == DistortionModel.Feature.Word && isOOV)
        System.err.print(" " + word);
      else if(feat == DistortionModel.Feature.Word)
        System.err.printf(" %s",model.wordIndex.get((int) d.get(i)));
      else if(feat == DistortionModel.Feature.CurrentTag)
        System.err.printf(" %s",model.tagIndex.get((int) d.get(i)));
      else if(feat == DistortionModel.Feature.RelPosition)
        System.err.printf(" %d", (int) d.get(i));
      else if(feat == DistortionModel.Feature.SourceLen)
        System.err.printf(" %d", (int) d.get(i));
      else if(feat == DistortionModel.Feature.LeftTag)
        System.err.printf(" %s",model.tagIndex.get((int) d.get(i)));
      else if(feat == DistortionModel.Feature.RightTag)
        System.err.printf(" %s",model.tagIndex.get((int) d.get(i)));
      i++;
    }
    System.err.println();
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {

    if(!sentenceToId.containsKey(foreign))
      throw new RuntimeException(String.format("No translation ID for sentence:\n%s\n",foreign.toString()));

    final int translationId = sentenceToId.get(foreign);

    assert posTags.get(translationId).size() == foreign.size();

    //Fields that need initialization after clone()'ing
    logProbCache = new double[foreign.size()][];
    sLen = (float) foreign.size();
    futureCostCache = new HashMap<Pair<CoverageSet,Integer>,Double>();

    final int slenBin = DistortionModel.getSlenBin(foreign.size());
    final int numClasses = DistortionModel.Class.values().length;

    //WSGDEBUG
    System.err.println("FEATURE DATUMS:");

    for(int sIdx = 0; sIdx < logProbCache.length; sIdx++) {
      final String rawWord = foreign.get(sIdx).toString().trim();
      final Matcher m = ibmEscaper.matcher(rawWord);
      String word = m.replaceAll("");
      if(word.equals(""))
        word = rawWord;

      final int rPos = DistortionModel.getSlocBin((float) sIdx / (float) sLen);
      final boolean isOOV = !model.wordIndex.contains(word);
      final String posTag = posTags.get(translationId).get(sIdx);

      //Setup the datum
      float[] feats = new float[model.getFeatureDimension()];
      int featPtr = 0;
      for(DistortionModel.Feature feat : model.featureIndex) {
        if(feat == DistortionModel.Feature.Word && isOOV)
          featPtr++;
        else if(feat == DistortionModel.Feature.Word)
          feats[featPtr++] = (float) model.wordIndex.indexOf(word);
        else if(feat == DistortionModel.Feature.CurrentTag)
          feats[featPtr++] = (float) model.tagIndex.indexOf(posTag);
        else if(feat == DistortionModel.Feature.RelPosition)
          feats[featPtr++] = (float) rPos;
        else if(feat == DistortionModel.Feature.SourceLen)
          feats[featPtr++] = (float) slenBin;
        else if(feat == DistortionModel.Feature.RightTag) {
          if(sIdx == foreign.size() - 1)
            feats[featPtr++] = (float) model.tagIndex.indexOf("</S>");
          else
            feats[featPtr++] = (float) model.tagIndex.indexOf(posTags.get(translationId).get(sIdx + 1));
        } else if(feat == DistortionModel.Feature.LeftTag) {
          if(sIdx == 0)
            feats[featPtr++] = (float) model.tagIndex.indexOf("<S>", true);
          else
            feats[featPtr++] = (float) model.tagIndex.indexOf(posTags.get(translationId).get(sIdx - 1));
        }
      }

      final Datum datum = new Datum(0.0f,feats);

      //WSGDEBUG
      prettyPrint(datum, isOOV, word);

      //Cache the log probabilities for each class
      logProbCache[sIdx] = new double[numClasses];
      for(DistortionModel.Class c : DistortionModel.Class.values()) {
        double realProb = model.prob(datum,c,isOOV);
        if(realProb == 0.0)
          realProb = 0.000001; //Guard against underflow (compute logprob in model)
        logProbCache[sIdx][c.ordinal()] = Math.log(realProb);
      }
    }

    //WSGDEBUG
    //    if(VERBOSE) {
    System.err.printf("PROB CACHE FOR transId %d\n",translationId);
    for(int i = 0; i < logProbCache.length; i++) {
      System.err.printf("%d: %s / %s\n", i, foreign.get(i), posTags.get(translationId).get(i));
      for(int j = 0; j < logProbCache[i].length; j++)
        System.err.printf("  %d  %f\n", j, logProbCache[i][j]);
    }
    System.err.println("\n\n\n");
    //    }
  }

  @Override
  public DiscrimDistortionFeaturizer clone() throws CloneNotSupportedException {
    return (DiscrimDistortionFeaturizer) super.clone();
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }
  @Override
  public void reset() {}


  @Override
  public void debugBest(Featurizable<IString, String> f) {
    final int translationId = f.translationId + (PseudoMoses.local_procs > 1 ? 2 : 0);

    VERBOSE = true;
    SET_STATE = false;

    System.err.printf(">> Translation ID %d<<\n", translationId);

    //Walk back though the priors
    double finalValue = 0.0;
    Featurizable<IString,String> fPtr = f;
    for(int i = 0; fPtr != null; i++) {
      System.err.printf("ITERATION %d\n", i);
      FeatureValue<String> featScore = featurize(fPtr);
      System.err.printf("Value at end of iteration: %f\n\n", featScore.value);
      finalValue += featScore.value;
      fPtr = fPtr.prior;
    }

    System.err.printf("DiscrimDistortionScore: %f\n", finalValue);

    VERBOSE = false;
    SET_STATE = true;
  }

  @Override
  public void rerankingMode(boolean r) {}
}
