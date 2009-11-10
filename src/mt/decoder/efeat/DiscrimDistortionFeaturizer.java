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
  private static final double UNALIGNED_WORD_PENALTY = 2.0; 

  //WSGDEBUG Debugging objects
  private boolean VERBOSE = false;


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

//    Pair<CoverageSet,Integer> cacheKey = new Pair<CoverageSet,Integer>(f.hyp.foreignCoverage,tLenEstimate);
    double futureCost = 0.0;

//    if(futureCostCache.containsKey(cacheKey))
//      futureCost = futureCostCache.get(cacheKey);
//      if(VERBOSE)
//        System.err.printf(" cached: %f\n", futureCost);
//    else {

      final CoverageSet coverage = f.hyp.foreignCoverage;
      final int tPosition = f.partialTranslation.size();
      int sPosition = coverage.nextClearBit(0);
      final int rightSBound = Math.min(f.foreignSentence.size(), sPosition + PseudoMoses.distortionLimit);
      
      if(tPosition > tLenEstimate) {
        final float numCoveredSourceTokens = f.foreignSentence.size() - f.hyp.untranslatedTokens;
        float thisC = (float) f.partialTranslation.size() / numCoveredSourceTokens;
        System.err.printf(" c:   %f\n", thisC);
        System.err.printf(" partial: %s\n", f.partialTranslation);
        System.err.printf(" cov: %s\n", coverage.toString());
        throw new RuntimeException(String.format("WSGDEBUG: tPos %d tLenEst %d  sPos %d sBound %d\n", tPosition,tLenEstimate,sPosition,rightSBound));
      }
      
      if(VERBOSE)
        System.err.printf(" params: tPos %d tLenEst %d  sPos %d sBound %d\n", tPosition,tLenEstimate,sPosition,rightSBound);
      
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
      
      if(VERBOSE)
        System.err.printf(" uncovered: %f (sPos %d)\n", futureCost,sPosition);

      // Rest of the sentence
      for(int sIdx = sPosition; sIdx < rightSBound; sIdx++) {
        
        double minLogCost = -999999.0;
        for(int tIdx = tPosition; tIdx < tLenEstimate; tIdx++) {
          float relMovement = getTargetVariable(tIdx, tLenEstimate, sIdx);
          DistortionModel.Class thisClass = DistortionModel.discretizeDistortion(relMovement);
          double logCost = logProbCache[sIdx][thisClass.ordinal()];
          if(logCost > minLogCost)
            minLogCost = logCost;
        }
        futureCost += minLogCost;
      }
      
      if(VERBOSE)
        System.err.printf(" rest: %f (sPos %d)\n", futureCost,rightSBound - 1);

//      futureCostCache.put(cacheKey, futureCost); 
//    }
    
    if(VERBOSE) {
      System.err.printf(" tLen       : %d\n", tLenEstimate);
      System.err.printf(" coverageSet: %s\n", f.hyp.foreignCoverage.toString());
      System.err.printf(" future cost: %f\n", futureCost);
    }

    return futureCost;
  }

  private Pair<Float,Float> computeC(Featurizable<IString, String> f) {
    //Compute the defaults
    float lastC = DEFAULT_C;
    float thisC = DEFAULT_C;
    if(Math.ceil(DEFAULT_C * f.foreignSentence.size()) < f.partialTranslation.size()) {
      lastC = (float) f.partialTranslation.size() / (float) f.foreignSentence.size();
      thisC = lastC;
    }
      
    //Adjust if sufficient information exists
    if(f.prior != null) {
      final float numCoveredSourceTokens = f.foreignSentence.size() - f.hyp.untranslatedTokens;
      thisC = (float) f.partialTranslation.size() / numCoveredSourceTokens;

      if(f.prior.prior != null) {
        final float lastNumCoveredSourceTokens = f.foreignSentence.size() - f.prior.hyp.untranslatedTokens;
        lastC = (float) f.prior.partialTranslation.size() / lastNumCoveredSourceTokens;
      }
    }
    
    return new Pair<Float,Float>(lastC,thisC);
  }
  
  private double getLogProb(int tIdx, int tLenEstimate, int sIdx) {
    float relMovement = getTargetVariable(tIdx, tLenEstimate, sIdx);
    final DistortionModel.Class thisClass = DistortionModel.discretizeDistortion(relMovement);
    
    return logProbCache[sIdx][thisClass.ordinal()];
  }
  
  @Override
  /**
   * Feature score is a sum of:
   *  1. Cost adjustment (from the last iteration)
   *  2. Cost of this translation option
   *  3. Future cost estimate
   */
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {

//    final int translationId = f.translationId + (PseudoMoses.local_procs > 1 ? 2 : 0);

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
    //
    Pair<Float,Float> c = computeC(f);
    final float lastC = c.first();
    final float thisC = c.second();

    //
    // Estimate the length of the translation, or get the actual length if finished
    //
    final int thisTLenEstimate = (f.done) ? f.partialTranslation.size() : (int) Math.ceil(sLen * thisC);

    //
    // Estimate the future cost to translate
    //
    if(VERBOSE)
      System.err.println("FUTURE COST ESTIMATE");
    double futureCostEstimate = 0.0;
    //double futureCostEstimate = estimateFutureCost(f, thisTLenEstimate);
    

    //
    // Calculate the feature score adjustment (if needed)
    //   Always adjust when the hypothesis is completed
    //
    double adjustment = 0.0;
    if(lastC != thisC || f.done) {
      final int lastTLenEstimate = (int) Math.ceil(sLen * lastC);

      if(VERBOSE) {
        System.err.println("FEATURE SCORE ADJUSTMENT");
        System.err.printf(" lastC %f  lastTLen %d ||| thisC %f  thisTLen %d ||| tol %f\n", lastC,lastTLenEstimate,thisC,thisTLenEstimate,TOL);
      }
      
      double oldScore = 0.0;
      double newScore = 0.0;
//      double oldScore = estimateFutureCost(f.prior,lastTLenEstimate);
//      double newScore = estimateFutureCost(f.prior,thisTLenEstimate);
      for(int sIdx = 0; sIdx < f2e.length; sIdx++) {
        if(f2e[sIdx] == null) continue; //Skip over uncovered source tokens

        int tIdx = f2e[sIdx][TARGET_IDX];
        double MULTIPLIER = 1.0;
        if(tIdx < 0) {
          tIdx *= -1;
          MULTIPLIER = UNALIGNED_WORD_PENALTY; 
        }
        
        oldScore += MULTIPLIER * getLogProb(tIdx, lastTLenEstimate, sIdx);
        newScore += MULTIPLIER * getLogProb(tIdx, thisTLenEstimate, sIdx);
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
          int tIdx = tOffset + i;

          final int sIdx = sOffset + sIndices[j];
          if(sIdx >= sLen)
            throw new RuntimeException(String.format("%d alignment index for source sentence of length %f (%s)",sIdx,sLen,sIndices.toString()));

          //Break the tie by adding in the score of the most costly alignment
          if(f2e[sIdx] == null)
            optScore += getLogProb(tIdx, thisTLenEstimate, sIdx);
            
          else {
            final int lastTidx = f2e[sIdx][TARGET_IDX];
            double lastOptScore = getLogProb(lastTidx,thisTLenEstimate,sIdx);
            double thisOptScore = getLogProb(tIdx, thisTLenEstimate,sIdx);
            if(thisOptScore < lastOptScore)
              optScore += (thisOptScore - lastOptScore);
            else
              tIdx = lastTidx;
          }
          
          int[] alignment = new int[1];
          alignment[TARGET_IDX] = tIdx;
          f2e[sIdx] = alignment;        
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

        int tIdx = interpolateTargetPosition(sIdx,f2e,sOffset,sOffset + f.foreignPhrase.size());

        //Should ONLY happen for OOVs
        if(tIdx == Integer.MIN_VALUE)
          tIdx = tOffset; //Assume monotone
        
        optScore += UNALIGNED_WORD_PENALTY * getLogProb(tIdx, thisTLenEstimate, sIdx);

        int[] alignment = new int[1];
        alignment[TARGET_IDX] = -1 * tIdx;
        f2e[sIdx] = alignment;             
      }
    }


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
    else if(leftTargetIdx != -1 && rightTargetIdx != -1)
      return (rightTargetIdx + leftTargetIdx) / 2;
    else
      return (leftTargetIdx != -1) ? leftTargetIdx : rightTargetIdx;
  }
  

  private String prettyPrint(Datum d, boolean isOOV, String word) {
    int i = 0;
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for(DistortionModel.Feature feat : model.featureIndex) {
      if(feat == DistortionModel.Feature.Word && isOOV)
        sb.append(String.format(" " + word));
      else if(feat == DistortionModel.Feature.Word)
        sb.append(String.format(" %s",model.wordIndex.get((int) d.get(i))));
      else if(feat == DistortionModel.Feature.CurrentTag)
        sb.append(String.format(" %s",model.tagIndex.get((int) d.get(i))));
      else if(feat == DistortionModel.Feature.RelPosition)
        sb.append(String.format(" %d", (int) d.get(i)));
      else if(feat == DistortionModel.Feature.SourceLen)
        sb.append(String.format(" %d", (int) d.get(i)));
      else if(feat == DistortionModel.Feature.LeftTag)
        sb.append(String.format(" %s",model.tagIndex.get((int) d.get(i))));
      else if(feat == DistortionModel.Feature.RightTag)
        sb.append(String.format(" %s",model.tagIndex.get((int) d.get(i))));
      i++;
    }
    sb.append(" ]");
    
    return sb.toString();
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
    ArrayList<String> datums = new ArrayList<String>();

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
            feats[featPtr++] = (float) model.tagIndex.indexOf("<S>");
          else
            feats[featPtr++] = (float) model.tagIndex.indexOf(posTags.get(translationId).get(sIdx - 1));
        }
      }

      final Datum datum = new Datum(0.0f,feats);

      //WSGDEBUG
      datums.add(prettyPrint(datum, isOOV, word));

      //Cache the log probabilities for each class
      logProbCache[sIdx] = new double[numClasses];
      for(DistortionModel.Class c : DistortionModel.Class.values())
        logProbCache[sIdx][c.ordinal()] = model.prob(datum,c,isOOV);
    }

    synchronized(System.err) {
      System.err.printf("PROB CACHE FOR transId %d\n",translationId);
      for(int i = 0; i < logProbCache.length; i++) {
        System.err.printf("%d: %s\n",i,datums.get(i));
        for(DistortionModel.Class c : DistortionModel.Class.values())
          System.err.printf("  %s  %f\n", c.toString(), logProbCache[i][c.ordinal()]);
      }
      System.err.println("\n\n\n");
    }
  }

  @Override
  public DiscrimDistortionFeaturizer clone() throws CloneNotSupportedException {
    return (DiscrimDistortionFeaturizer) super.clone();
  }

  @Override
  public void debugBest(Featurizable<IString, String> f) {
    final int translationId = f.translationId + (PseudoMoses.local_procs > 1 ? 2 : 0);
    final Sequence<IString> foreign = f.foreignSentence;
    
    //Walk back through the priors so that the output
    //is in the correct translation order
    Stack<Featurizable<IString,String>> featurizers = new Stack<Featurizable<IString,String>>();
    Featurizable<IString,String> fPtr = f;
    while(fPtr != null) {
      featurizers.push(fPtr);
      fPtr = fPtr.prior;
    }
    
    synchronized(System.err) {

      System.err.printf(">> Translation ID %d<<\n", translationId);
      
      int iter = 0;
      while(!featurizers.empty()) {
        Featurizable<IString, String> thisF = featurizers.pop();
        final Sequence<IString> translation = thisF.partialTranslation;
        
        System.err.printf("ITERATION %d\n", iter++);
        System.err.println(" partial: " + thisF.partialTranslation);
        System.err.println(" coverage: " + thisF.hyp.foreignCoverage.toString());
        System.err.println(" opt:\n" + thisF.option.toString());

        //Get state
        final int[][] f2e = (int[][]) thisF.getState(this);

        //Is this a re-estimation iteration?
        Pair<Float,Float> c = computeC(thisF);
        float lastC = c.first();
        float thisC = c.second();
        if(Math.abs(lastC - thisC) > TOL || thisF.done)
          System.err.printf(" re-estimate tLen: oldC %f --> newC %f\n", lastC, thisC);
        
        //Future cost
        VERBOSE = true;
        final int thisTLenEstimate = (thisF.done) ? thisF.partialTranslation.size() : (int) Math.ceil(sLen * thisC);
        final double estFutureCost = estimateFutureCost(thisF, thisTLenEstimate);
        System.err.printf(" future cost: %f (tlen: %d)\n", estFutureCost,thisTLenEstimate);
        VERBOSE = false;
        
        //Print feature scores
        for(int sIdx = 0; sIdx < f2e.length; sIdx++) {
          if(f2e[sIdx] == null) continue; //Skip over uncovered source tokens

          int tIdx = f2e[sIdx][TARGET_IDX];
          int tWordIdx = (tIdx > 0) ? tIdx : -1 * tIdx;

          System.err.printf("  %d||%s\t-->\t%d||%s\n", sIdx,foreign.get(sIdx), tIdx, translation.get(tWordIdx));
        }        
        System.err.println("==============\n");
      }
  
    }
  }

  //Unused methods
  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }
  @Override
  public void reset() {}
  @Override
  public void rerankingMode(boolean r) {}
}
