package mt.decoder.efeat;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Pair;

import mt.PseudoMoses;
import mt.base.ConcreteTranslationOption;
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

  //Shared objects for all threads initialized in the constructor
  private static DistortionModel model;
  private static List<List<String>> posTags;
  private static Map<Sequence<IString>,Integer> sentenceToId;

  private final float DEFAULT_C;
  private final double UNALIGNED_WORD_PENALTY;

  //Threadsafe...the reference will be clone()'d, but the object will be set when
  //initialize() is called
  private double[][] logProbCache = null;
  private float sMaxIdx = 0;

  //Constants used for all hypotheses
  private static final Pattern ibmEscaper = Pattern.compile("#|\\+");
  private static final int TARGET_IDX = 0;

  //WSGDEBUG (23 Nov): Dead code
  private static final double[] MIDPOINTS = null;
//  private static final double[] MIDPOINTS = new double[DistortionModel.classRightBounds.length];
//  static {
//    double lastX = -100.0;
//    for(int i = 0; i < MIDPOINTS.length; i++) {
//      MIDPOINTS[i] = (lastX + DistortionModel.classRightBounds[i]) / 2.0;
//      lastX = DistortionModel.classRightBounds[i];
//    }
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
    UNALIGNED_WORD_PENALTY = Double.parseDouble(args[4].trim());

    //WSGDEBUG
    System.err.println(">> Discriminative Distortion Class Midpoints <<");
    for(DistortionModel.Class c : DistortionModel.Class.values())
      System.err.printf(" %s:\t%f\n", c.toString(), MIDPOINTS[c.ordinal()]);
    System.err.println("===============================================");
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
   * @param tMaxIdx
   * @param sIdx
   * @return
   */
  private float getTargetVariable(int tIdx, int tMaxIdx, int sIdx) {
    float tRelPosition = ((float) tIdx / (float) tMaxIdx) * 100.0f;
    float sRelPosition = ((float) sIdx / sMaxIdx) * 100.0f;

    return tRelPosition - sRelPosition;
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


  private double interpolate(final int left, final int right, final double x, final int sIdx) {
    double x1, x2, y1, y2;

    if(left == right) {

      if(left == DistortionModel.FIRST_CLASS.ordinal()) {
        x1 = -100.0;
        y1 = 2.0 * logProbCache[sIdx][left];

        x2 = MIDPOINTS[left];
        y2 = logProbCache[sIdx][left];

      } else {
        x1 = MIDPOINTS[left];
        y1 = logProbCache[sIdx][left];

        x2 = 100.0;
        y2 = 2.0 * logProbCache[sIdx][left];
      }

    } else {
      x1 = MIDPOINTS[left];
      y1 = logProbCache[sIdx][left];

      x2 = MIDPOINTS[right];
      y2 = logProbCache[sIdx][right];
    }

    double m = (y2 - y1) / (x2 - x1);
    double logProb = (m * (x - x1)) + y1;

    return logProb;
  }

  /**
   * Do linear interpolation between the two boundary classes
   * 
   * @param tIdx
   * @param tLenEstimate
   * @param sIdx
   * @return
   */
  private double getLogProb(final int tIdx, final int tLenEstimate, final int sIdx) {
    final float relMovement = getTargetVariable(tIdx, tLenEstimate, sIdx);
    
    //TODO wsg: 2009 Need to revert for relmovement
    final DistortionModel.Class thisClass = DistortionModel.discretizeDistortion((int)relMovement);

    if(thisClass == DistortionModel.MONOTONE)
      return logProbCache[sIdx][thisClass.ordinal()];

    //Interpolate for the other classes
    if(thisClass == DistortionModel.FIRST_CLASS) {
      if(relMovement < MIDPOINTS[thisClass.ordinal()])
        return interpolate(thisClass.ordinal(), thisClass.ordinal(), relMovement, sIdx);
      else
        return interpolate(thisClass.ordinal(), thisClass.ordinal() + 1, relMovement, sIdx);

    } else if(thisClass == DistortionModel.LAST_CLASS) {
      if(relMovement < MIDPOINTS[thisClass.ordinal()])
        return interpolate(thisClass.ordinal() - 1, thisClass.ordinal(), relMovement, sIdx);
      else
        return interpolate(thisClass.ordinal(), thisClass.ordinal(), relMovement, sIdx);  
    }

    if(relMovement < MIDPOINTS[thisClass.ordinal()])
      return interpolate(thisClass.ordinal() - 1, thisClass.ordinal(), relMovement, sIdx);
    else
      return interpolate(thisClass.ordinal(), thisClass.ordinal() + 1, relMovement, sIdx);
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

    //
    //Compute the previous and target length constants
    //Units are (target length / source length)
    //
    final Pair<Float,Float> c = computeC(f);
    final float lastC = c.first();
    final float thisC = c.second();

    //
    // Estimate the length of the translation, or get the actual length if finished
    //
    final int lastTMaxIdxEstimate = (int) Math.ceil(sMaxIdx * lastC);
    final int thisTMaxIdxEstimate = (f.done) ? f.partialTranslation.size() - 1 : (int) Math.ceil(sMaxIdx * thisC);

    //
    // Calculate the feature score adjustment (if needed)
    //   Always adjust when the hypothesis is completed
    //
    double adjustment = 0.0;
    if(lastTMaxIdxEstimate != thisTMaxIdxEstimate) {

      double oldScore = 0.0;
      double newScore = 0.0;
      int alignedTargetTokens = 0;
      for(int sIdx = 0; sIdx < f2e.length; sIdx++) {
        if(f2e[sIdx] == null) continue; //Skip over uncovered source tokens

        alignedTargetTokens++;

        final int tIdx = f2e[sIdx][TARGET_IDX];

        String pos = posTags.get(translationId).get(sIdx);
        if(sIdx == 0 && pos.equals("CC")) {
          oldScore += UNALIGNED_WORD_PENALTY;
          newScore += UNALIGNED_WORD_PENALTY;

        } else {
          oldScore += getLogProb(tIdx, lastTMaxIdxEstimate, sIdx);
          newScore += getLogProb(tIdx, thisTMaxIdxEstimate, sIdx);
        }
      }

      //Unaligned target word penalty
      double penalty = UNALIGNED_WORD_PENALTY * (f.prior.partialTranslation.size() - alignedTargetTokens);
      oldScore += penalty;
      newScore += penalty;

      adjustment = newScore - oldScore; //Difference in log scores
    }

    //
    // Calculate the score of this translation option
    //
    final int sOffset = f.foreignPosition;
    final int tOffset = f.translationPosition;
    final int tOptLen = f.translatedPhrase.size();

    double optScore = 0.0;
    if(f.option.abstractOption.alignment.hasAlignment()) {
      for(int i = 0; i < tOptLen; i++) {

        final int[] sIndices = f.option.abstractOption.alignment.e2f(i);
        if(sIndices == null) {
          optScore += UNALIGNED_WORD_PENALTY;
          continue;
        }
        else if(sIndices.length != 1)
          throw new RuntimeException(String.format("Many-to-one alignment...Shouldn't happen with intersect heuristic (sIndices %d)",sIndices.length));

        final int tIdx = tOffset + i;
        final int sIdx = sOffset + sIndices[0];

        if(sIdx > sMaxIdx)
          throw new RuntimeException(String.format("%d alignment index for source sentence of length %f (%s)",sIdx,sMaxIdx,sIndices.toString()));

        //WSGDEBUG
        String pos = posTags.get(translationId).get(sIdx);
        if(sIdx == 0 && pos.equals("CC"))
          optScore += UNALIGNED_WORD_PENALTY;
        else
          optScore += getLogProb(tIdx, thisTMaxIdxEstimate, sIdx);

        int[] alignment = new int[1];
        alignment[TARGET_IDX] = tIdx;
        f2e[sIdx] = alignment;        
      }
    }

    f.setState(this, f2e);

    return new FeatureValue<String>(FEATURE_NAME, optScore + adjustment);
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
    sMaxIdx = (float) foreign.size() - 1.0f;

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

      final int rPos = DistortionModel.getSlocBin((float) sIdx / (float) sMaxIdx);
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
        logProbCache[sIdx][c.ordinal()] = model.logProb(datum,c,isOOV);
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
        System.err.println(" algn: " + thisF.option.abstractOption.alignment.toString());
        System.err.printf(" opt: %s --> %s\n\n", thisF.foreignPhrase.toString(), thisF.translatedPhrase.toString());
        System.err.printf(" tpos: %d\n", thisF.translationPosition);
        System.err.printf(" spos: %d\n", thisF.foreignPosition);

        //Is this a re-estimation iteration?
        final Pair<Float,Float> c = computeC(thisF);
        final float lastC = c.first();
        final float thisC = c.second();
        final int lastTMaxIdxEstimate = (int) Math.ceil(sMaxIdx * lastC);
        final int thisTMaxIdxEstimate = (f.done) ? f.partialTranslation.size() - 1 : (int) Math.ceil(sMaxIdx * thisC);
        if(lastTMaxIdxEstimate != thisTMaxIdxEstimate)
          System.err.printf(" re-estimate tLen: old %d --> new %d\n", lastTMaxIdxEstimate, thisTMaxIdxEstimate);

        //Print feature scores
        final int[][] f2e = (int[][]) thisF.getState(this);
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
