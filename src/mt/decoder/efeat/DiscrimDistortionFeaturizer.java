package mt.decoder.efeat;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.io.IOUtils;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.IOTools;
import mt.base.IString;
import mt.base.IStrings;
import mt.base.Sequence;
import mt.base.SimpleSequence;
import mt.decoder.feat.IncrementalFeaturizer;
import mt.decoder.feat.StatefulFeaturizer;
import mt.train.discrimdistortion.Datum;
import mt.train.discrimdistortion.DistortionModel;

public class DiscrimDistortionFeaturizer extends StatefulFeaturizer<IString, String> implements IncrementalFeaturizer<IString, String> {

  private final String FEATURE_NAME = "DiscrimDistortion";

  //Shared objects for all threads
  private static DistortionModel model;
  private final List<List<String>> posTags;
  private static Map<Sequence<IString>,Integer> sentenceToId;
  
  private final float DEFAULT_C;
  private final float TOL;

  //Threadsafe...the reference will be clone()'d, but the object will be set when
  //initialize() is called
  private double[][] logProbCache;
  private static final int SOURCE_IDX = 0;
  private static final int TARGET_IDX = 1;
  private float sLen = 0;


  public DiscrimDistortionFeaturizer(String... args) {

    assert args.length == 4;

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
    
    DEFAULT_C = Float.parseFloat(args[2].trim());
    TOL = Float.parseFloat(args[3].trim());
  }

  
  //Re-factor to use a jagged array if memory is an issue
  private List<List<String>> getTagCache(final File tagFile) {
    List<List<String>> posTags = new ArrayList<List<String>>();
    sentenceToId = new HashMap<Sequence<IString>,Integer>();

    LineNumberReader reader = IOTools.getReaderFromFile(tagFile);
    try {
      for(int transId = 0; reader.ready(); transId++) {
        StringTokenizer st = new StringTokenizer(reader.readLine());
        List<String> words = new ArrayList<String>();
        List<String> tagsForSentence = new ArrayList<String>();

        while(st.hasMoreTokens()) {
          String[] parts = st.nextToken().split("#");
          assert parts.length == 2;   
          assert !parts[1].equals("");
          words.add(parts[0].trim().intern());
          tagsForSentence.add(parts[1].intern());
        }

        Sequence<IString> foreign = new SimpleSequence<IString>(true, IStrings.toIStringArray(words));
        sentenceToId.put(foreign, transId);

        posTags.add(tagsForSentence);
      }

      reader.close();

    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Error while reading POS tag file");
    }

    return posTags;
  }

  private int[][] copy(int[][] array) {
    int[][] thisCopy = new int[array.length][];
    for(int i = 0; i < array.length; i++)
      thisCopy[i] = Arrays.copyOf(array[i], array.length);
    return thisCopy;
  }
  
  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {

    //WSGDEBUG...This should never happen for a ConcreteOption??
    if(!f.option.abstractOption.alignment.hasAlignment())
      throw new RuntimeException(String.format("Option lacks alignment!\n%s\n", f.option.toString()));


    //Compute the previous and target length constants
    //Units are (target length / source length)
    final float lastC = (f.prior == null) ? DEFAULT_C :
      (float) f.prior.partialTranslation.size() / (float) f.prior.hyp.foreignSequence.size();
    final float thisC = (float) f.partialTranslation.size() / (float) f.hyp.foreignSequence.size();
    final float thisTLen = sLen * thisC;


    //Setup the state (with sanity check)
    int[][] f2e = (f.prior == null) ? new int[f.foreignSentence.size()][] : copy((int[][]) f.prior.getState(this));
    if(f.done)
      for(int i = 0; i < f2e.length; i++)
        if(f2e[i] == null)
          throw new RuntimeException(String.format("Completed hyp with incorrect discrim distortion score (%d tokens but index %d unscored)",f.foreignSentence.size(),i));


    //Calculate the feature score adjustment (if needed)
    double adjustment = 0.0;
    if(Math.abs(lastC - thisC) > TOL) {
      final float lastTLen = sLen * lastC;

      double oldScore = 0.0;
      double newScore = 0.0;
      for(int i = 0; i < f2e.length; i++) {
        if(f2e[i] == null) continue; //Skip over uncovered source tokens

        int sIdx = f2e[i][SOURCE_IDX];
        int tIdx = f2e[i][TARGET_IDX];

        float oldRel = (((float) tIdx / lastTLen) * 100.0f) - (((float) sIdx / sLen) * 100.0f);
        float newRel = (((float) tIdx / thisTLen) * 100.0f) - (((float) sIdx / sLen) * 100.0f);

        DistortionModel.Class oldClass = DistortionModel.discretizeDistortion(oldRel);
        DistortionModel.Class newClass = DistortionModel.discretizeDistortion(newRel);

        oldScore += logProbCache[sIdx][oldClass.ordinal()];
        newScore += logProbCache[sIdx][newClass.ordinal()];
      }

      adjustment = oldScore - newScore; //Difference in log scores
    }

    //Calculate the score of this translation option
    final int sOffset = f.foreignPosition;
    final int tOffset = f.translationPosition;
    final int tOptLen = f.translatedPhrase.size();

    //Iterate over target side alignments
    double optScore = 0;
    for(int i = 0; i < tOptLen; i++) {
      final int[] sIndices = f.option.abstractOption.alignment.e2f(i);
      for(int j = 0; j < sIndices.length; j++) {
        final int sIdx = sOffset + sIndices[j];

        if(sIdx >= sLen)
          throw new RuntimeException(String.format("%d alignment index for source sentence of length %f",sIdx,sLen));
        
        //Greedily score source words
        if(f2e[sIdx] != null) continue;

        //Calculate the score for this source word
        final int tIdx = tOffset + i;
        float relMovement = (((float) tIdx / thisTLen) * 100.0f) - (((float) sIdx / sLen) * 100.0f);

        optScore += computeScoreAndUpdateAlignments(sIdx,tIdx,relMovement,f2e);      
      }      
    }

    //Check for unaligned source tokens (iterate over source side)
    for(int i = sOffset; i < sOffset + f.foreignPhrase.size() && i < f2e.length; i++) {
      if(f2e[i] == null) {
        int tIdx = interpolateTargetPosition(i,f2e);
        int sIdx = sOffset + i;

        float relMovement = (((float) tIdx / thisTLen) * 100.0f) - (((float) sIdx / sLen) * 100.0f);
        optScore += computeScoreAndUpdateAlignments(sIdx,tIdx,relMovement,f2e);
      }
    }

    f.setState(this, f2e);

    return new FeatureValue<String>(FEATURE_NAME, optScore + adjustment);
  }


  private double computeScoreAndUpdateAlignments(int sIdx, int tIdx, float relMovement, int[][] f2e) {

    int[] alignment = new int[2];
    alignment[SOURCE_IDX] = sIdx;
    alignment[TARGET_IDX] = tIdx;
    f2e[sIdx] = alignment;

    final DistortionModel.Class thisClass = DistortionModel.discretizeDistortion(relMovement);

    return logProbCache[sIdx][thisClass.ordinal()];
  }

  private int interpolateTargetPosition(int sIdx, int[][] f2e) {

    final int maxOffset = (int) Math.floor((double) f2e.length / 2.0);
    int leftTargetIdx = -1;
    int rightTargetIdx = -1;
    for(int offset = 1; offset <= maxOffset; offset++) {
      final int leftSIdx = sIdx - offset;
      if(leftSIdx >= 0 && f2e[leftSIdx] != null)
        leftTargetIdx = f2e[leftSIdx][TARGET_IDX];

      final int rightSIdx = sIdx + offset;
      if(rightSIdx < f2e.length && f2e[rightSIdx] != null)
        rightTargetIdx = f2e[rightSIdx][TARGET_IDX];
      
      if(leftTargetIdx != -1 || rightTargetIdx != -1)
        break;
    }
    
    //Sanity check
    if(leftTargetIdx == -1 && rightTargetIdx == -1)
      throw new RuntimeException("No aligned words in source!");
    

    if(leftTargetIdx != -1 && rightTargetIdx != -1)
      return Math.round(((float) rightTargetIdx - leftTargetIdx) / 2.0f);
    else
      return (leftTargetIdx != -1) ? leftTargetIdx : rightTargetIdx;
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {

    if(!sentenceToId.containsKey(foreign))
      throw new RuntimeException(String.format("No translation ID for sentence:\n%s\n",foreign.toString()));
    
    final int translationId = sentenceToId.get(foreign);

    assert posTags.get(translationId).size() == foreign.size();
    
    logProbCache = new double[foreign.size()][];
    sLen = (float) foreign.size();

    final int slenBin = DistortionModel.getSlenBin(foreign.size());
    final int numClasses = DistortionModel.Class.values().length;

    for(int sIdx = 0; sIdx < logProbCache.length; sIdx++) {
      final String word = foreign.get(sIdx).toString().trim();
      final int rPos = DistortionModel.getSlocBin((float) sIdx / (float) sLen);
      final boolean isOOV = model.featureIndex.contains(word);
      final String posTag = posTags.get(translationId).get(sIdx);

      //Setup the datum
      float[] feats = new float[model.getFeatureDimension()];
      int featPtr = 0;
      for(DistortionModel.Feature feat : model.featureIndex) {
        if(feat == DistortionModel.Feature.Word && !isOOV)
          feats[featPtr++] = (float) model.wordIndex.indexOf(word);
        else if(feat == DistortionModel.Feature.CurrentTag)
          feats[featPtr++] = (float) model.tagIndex.indexOf(posTag);
        else if(feat == DistortionModel.Feature.RelPosition)
          feats[featPtr++] = (float) rPos;
        else if(feat == DistortionModel.Feature.SourceLen)
          feats[featPtr++] = (float) slenBin;
      }

      final Datum datum = new Datum(0.0f,feats);

      //Cache the log probabilities for each class
      logProbCache[sIdx] = new double[numClasses];
      for(DistortionModel.Class c : DistortionModel.Class.values())
        logProbCache[sIdx][c.ordinal()] = Math.log(model.prob(datum,c,isOOV));
    }
  }



  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }
  @Override
  public void reset() {}
}
