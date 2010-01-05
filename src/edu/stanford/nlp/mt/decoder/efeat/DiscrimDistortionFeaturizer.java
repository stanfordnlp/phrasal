package edu.stanford.nlp.mt.decoder.efeat;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Pair;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.decoder.feat.RichIncrementalFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.StatefulFeaturizer;
import edu.stanford.nlp.mt.train.discrimdistortion.Datum;
import edu.stanford.nlp.mt.train.discrimdistortion.DistortionModel;

public class DiscrimDistortionFeaturizer extends StatefulFeaturizer<IString,String> implements RichIncrementalFeaturizer<IString, String>{

  private static final String FEATURE_NAME = "DiscrimDistortion";

  private static final String DEBUG_PROPERTY = "Debug" + FEATURE_NAME;
  private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
  
  //Shared objects for all threads initialized in the constructor
  private static boolean useTwoModels = false;
  private static DistortionModel inModel;
  private static DistortionModel outModel;
  private static List<List<String>> posTags;
  private static Map<Sequence<IString>,Integer> sentenceToId;
  private static boolean useDelims = false;

  //Threadsafe...the reference will be clone()'d, but the object will be set when
  //initialize() is called
  private double[][] inLogProbCache = null;
  private double[][] outLogProbCache = null;
  List<Datum> inDatums = null;
  List<Datum> outDatums = null;

  //Constants used for all hypotheses
  private static final Pattern ibmEscaper = Pattern.compile("#|\\+");

  //WSGDEBUG
  //TODO Re-factor later
  private static final int outerClassBound = 6;
  private static final double exponent = 1.5;
  
  public DiscrimDistortionFeaturizer(String... args) {

    assert args.length <= 4;

    //The serialized distortion model
    File inModelFile = new File(args[0]);
    if(!inModelFile.exists())
      throw new RuntimeException(String.format("%s: Model file %s does not exist!\n", this.getClass().getName(), inModelFile.getPath()));

    inModel = loadModel(inModelFile);

    //The tagged file to be translated
    File tagFile = new File(args[1]);
    if(!tagFile.exists())
      throw new RuntimeException(String.format("%s: Tag file %s does not exist!\n",this.getClass().getName(),tagFile.getPath()));

    posTags = getTagCache(tagFile);

    File unkFile = new File(args[2]);
    if(!unkFile.exists())
      throw new RuntimeException(String.format("%s: File does not exist (%s)",this.getClass().getName(),unkFile.getPath()));

    sentenceToId = getSentenceMap(unkFile);

    if(args.length == 4) {
      System.err.println(this.getClass().getName() + ": Using inbound and outbound models");
      useTwoModels = true;
      File outModelFile = new File(args[3]);
      if(!outModelFile.exists())
        throw new RuntimeException(this.getClass().getName() + ": Model file does not exist: " + outModelFile.getPath());

      outModel = loadModel(outModelFile);
    }

    useDelims = inModel.useBeginEndMarkers;

    System.err.printf("%s: useDelims (%b)\n", this.getClass().getName(), useDelims);
  }

  private DistortionModel loadModel(File modelFile) {
    DistortionModel model = null;
    try {
      model = (DistortionModel) IOUtils.readObjectFromFile(modelFile);
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not load discriminative distortion model: " + modelFile.getPath());

    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not load discriminative distortion model: " + modelFile.getPath());
    }

    return model;
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

    System.err.printf("%s: Loaded %d sentences\n", this.getClass().getName(), sentenceMap.size());

    return sentenceMap;
  }

  private List<List<String>> getTagCache(final File tagFile) {
    List<List<String>> posTags = new ArrayList<List<String>>();

    LineNumberReader reader = IOTools.getReaderFromFile(tagFile);
    int numTags = 0;
    try {
      for(int transId = 0; reader.ready(); transId++) {
        StringTokenizer st = new StringTokenizer(reader.readLine());
        List<String> tagsForSentence = new ArrayList<String>();

        while(st.hasMoreTokens()) {
          // mg: the tagger now uses "/" as delimited by default:
          String[] parts = st.nextToken().split("/");
          //String[] parts = st.nextToken().split("_");
          if (parts.length != 2)
            System.err.println("suspicious token: "+Arrays.toString(parts));
          if (parts.length > 1 && parts[1].equals(""))
            System.err.println("suspicious token: "+Arrays.toString(parts));
          tagsForSentence.add(parts[parts.length-1].intern());
        }

        numTags += tagsForSentence.size();

        posTags.add(tagsForSentence);
      }

      reader.close();

    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Error while reading POS tag file");
    }

    System.err.printf("%s: Loaded %d POS tags\n", this.getClass().getName(), numTags);

    return posTags;
  }

  /**
   * -1 indicates that this is an inbound link from the sentence delimiter
   * 
   * @param fromIdx
   * @param toIdx
   * @return
   */
  private int getDistortion(int fromIdx, int toIdx) {
    int distortion = 0;
    if(fromIdx == -1)
      distortion = toIdx;
    else {
      distortion = fromIdx + 1 - toIdx;
      if(distortion > 0)
        distortion--; //Adjust for bias 
      distortion *= -1; //Turn it into a cost
    }

    return distortion;
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { 
    List<FeatureValue<String>> features = new ArrayList<FeatureValue<String>>();

    final int lastSIdx = (f.prior == null) ? -1 : (Integer) f.prior.getState(this);

    final Pair<Integer,Double> inBoundScore = inFeaturize(f,lastSIdx);
    features.add(new FeatureValue<String>("In:" + FEATURE_NAME, inBoundScore.second()));

    if(useTwoModels) {
      final Pair<Integer,Double> outBoundScore = outFeaturize(f,lastSIdx);
      // TODO: mg2009: check if assert really needed since fails in some extremely rare cases (i.e., MERT may
      // run several iterations without problems then suddenly crash).
      //assert inBoundScore.first() == outBoundScore.first(); //They need to end at the same place!!
      features.add(new FeatureValue<String>("Out:" + FEATURE_NAME, outBoundScore.second()));
    }

    f.setState(this, inBoundScore.first());

    return features; 
  }

  public Pair<Integer, Double> outFeaturize(Featurizable<IString, String> f, int lastSIdx) {

    final int sOffset = f.foreignPosition;

    double optScore = 0.0;
    if(f.option.abstractOption.alignment.hasAlignment()) {
      final int tOptLen = f.translatedPhrase.size();
      for(int i = 0; i < tOptLen; i++) {

        final int[] sIndices = f.option.abstractOption.alignment.e2f(i);
        if(sIndices == null || sIndices.length == 0)
          continue; //skip over null aligned target tokens

        final int sIdx = sOffset + sIndices[0];

        if(!useDelims && lastSIdx == -1) {
          lastSIdx = sIdx;
          continue;
        }

        int distortion = getDistortion(lastSIdx,sIdx);
        int cacheIndex = (useDelims) ? lastSIdx + 1 : lastSIdx;

        optScore += getScoreFromCache(outLogProbCache, cacheIndex, distortion);
        lastSIdx = sIdx;
      }

    } else {
      if(useDelims || lastSIdx != -1) {
        int distortion = getDistortion(lastSIdx,sOffset);
        int cacheIndex = (useDelims) ? lastSIdx + 1 : lastSIdx;
        optScore += getScoreFromCache(outLogProbCache, cacheIndex, distortion);
      }
      lastSIdx = sOffset + f.foreignPhrase.size() - 1;
    }

    if(useDelims && f.done) {
      int distortion = getDistortion(lastSIdx,f.foreignSentence.size());
      int cacheIndex = (useDelims) ? lastSIdx + 1 : lastSIdx;
      optScore += getScoreFromCache(outLogProbCache, cacheIndex, distortion);
    }

    return new Pair<Integer,Double>(lastSIdx,optScore);
  }

  public Pair<Integer, Double> inFeaturize(Featurizable<IString, String> f, int lastSIdx) {

    //final int translationId = f.translationId + (Phrasal.local_procs > 1 ? 2 : 0);

    final int sOffset = f.foreignPosition;

    double optScore = 0.0;      
    if(f.option.abstractOption.alignment.hasAlignment()) {
      final int tOptLen = f.translatedPhrase.size();
      for(int i = 0; i < tOptLen; i++) {

        final int[] sIndices = f.option.abstractOption.alignment.e2f(i);
        if(sIndices == null || sIndices.length == 0)
          continue; //skip over null aligned target tokens

        final int sIdx = sOffset + sIndices[0];
        int distortion = getDistortion(lastSIdx,sIdx);

        int cacheIndex = (useDelims) ? sIdx + 1 : sIdx;
        optScore += getScoreFromCache(inLogProbCache, cacheIndex, distortion);
        lastSIdx = sIdx;
      }

    } else {
      int distortion = getDistortion(lastSIdx,sOffset);
      int cacheIndex = (useDelims) ? sOffset + 1 : sOffset;
      optScore += getScoreFromCache(inLogProbCache, cacheIndex, distortion);      
      lastSIdx = sOffset + f.foreignPhrase.size() - 1;
    }

    if(useDelims && f.done) {
      int distortion = getDistortion(lastSIdx,f.foreignSentence.size());
      int cacheIndex = inLogProbCache.length - 1;
      optScore += getScoreFromCache(inLogProbCache, cacheIndex, distortion);
    }

    return new Pair<Integer,Double>(lastSIdx, optScore);
  }
  
  private double getScoreFromCache(double[][] cache, int cacheIndex, int distortion) {
    DistortionModel.Class thisClass = DistortionModel.discretizeDistortion(distortion);
    double bigHammer = 0.0;
    if(Math.abs(distortion) > outerClassBound) {
      int diff = Math.abs(distortion) - outerClassBound;
      bigHammer = -1.0 * Math.pow(diff, exponent);
    }
    
    return cache[cacheIndex][thisClass.ordinal()] + bigHammer;
  }

  private String prettyPrint(DistortionModel model, Datum d, String word) {
    int i = 0;
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for(DistortionModel.Feature feat : model.featureIndex) {
      if(feat == DistortionModel.Feature.Word && model.wordIndex.contains(word))
        sb.append(String.format(" %s", model.wordIndex.get((int) d.get(i))));
      else if(feat == DistortionModel.Feature.CurrentTag)
        sb.append(String.format(" %s", model.tagIndex.get((int) d.get(i))));
      else if(feat == DistortionModel.Feature.RelPosition)
        sb.append(String.format(" %d", (int) d.get(i)));
      else if(feat == DistortionModel.Feature.SourceLen)
        sb.append(String.format(" %d", (int) d.get(i)));
      //      else if(feat == DistortionModel.Feature.LeftTag)
      //        sb.append(String.format(" %s",model.tagIndex.get((int) d.get(i))));
      //      else if(feat == DistortionModel.Feature.RightTag)
      //        sb.append(String.format(" %s",model.tagIndex.get((int) d.get(i))));
      else if(feat == DistortionModel.Feature.ArcTag)
        sb.append(String.format(" %s",model.tagIndex.get((int) d.get(i))));      
      i++;
    }
    sb.append(" ]");

    return sb.toString();
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {

    if(!sentenceToId.containsKey(foreign))
      throw new RuntimeException(this.getClass().getName() + ": No translation ID for sentence:\n " + foreign.toString());

    final int translationId = sentenceToId.get(foreign);

    //Sanity check
    assert posTags.get(translationId).size() == foreign.size();

    final int cacheSize = (useDelims) ? foreign.size() + 2 : foreign.size();

    //Setup the two caches here
    inLogProbCache = new double[cacheSize][];
    inDatums = new ArrayList<Datum>();
    List<String> inDebugDatums = setupModelCache(inModel, inLogProbCache, foreign, translationId);

    List<String> outDebugDatums = null;
    if(useTwoModels) {
      outLogProbCache = new double[cacheSize][];
      outDatums = new ArrayList<Datum>();
      outDebugDatums = setupModelCache(outModel, outLogProbCache, foreign, translationId);
    }

    if(DEBUG) {
      synchronized(System.err) {
        System.err.printf("INBOUND LOG PROB CACHE FOR transId %d\n",translationId);
        for(int i = 0; i < inLogProbCache.length; i++) {
          System.err.printf("%d: %s\n",i,inDebugDatums.get(i));
          for(DistortionModel.Class c : DistortionModel.Class.values())
            System.err.printf("  %s  %f\n", c.toString(), inLogProbCache[i][c.ordinal()]);
        }
        System.err.println("\n");

        if(useTwoModels) {
          System.err.printf("OUTBOUND LOG PROB CACHE FOR transId %d\n",translationId);
          for(int i = 0; i < outLogProbCache.length; i++) {
            System.err.printf("%d: %s\n",i, outDebugDatums.get(i));
            for(DistortionModel.Class c : DistortionModel.Class.values())
              System.err.printf("  %s  %f\n", c.toString(), outLogProbCache[i][c.ordinal()]);
          }
          System.err.println("\n");
        }
        System.err.println();
      }
    }
  }

  private List<String> setupModelCache(DistortionModel model, double[][] cache, Sequence<IString> foreign, final int translationId) {

    List<String> debugDatums = new ArrayList<String>();

    final float sMaxIdx = (float) foreign.size() - 1.0f;
    final float slenBin = (float) DistortionModel.getSlenBin(foreign.size());
    final int numClasses = DistortionModel.Class.values().length;
    for(int sIdx = 0; sIdx < cache.length; sIdx++) {
      float wordFeat, tagFeat, sLocBin;
      String word;

      if(useDelims && sIdx == 0) {
        wordFeat = model.wordIndex.indexOf(model.START_OF_SENTENCE);
        tagFeat = model.tagIndex.indexOf(model.DELIM_POS);
        sLocBin = DistortionModel.getSlocBin(0.0f);
        word = model.START_OF_SENTENCE;

      } else if(useDelims && sIdx == cache.length - 1) {
        wordFeat = model.wordIndex.indexOf(model.END_OF_SENTENCE);
        tagFeat = model.tagIndex.indexOf(model.DELIM_POS);
        sLocBin = DistortionModel.getSlocBin(1.0f);
        word = model.END_OF_SENTENCE;

      } else {
        int realSIdx = (useDelims) ? sIdx - 1 : sIdx;

        final String rawWord = foreign.get(realSIdx).toString().trim();
        word = ibmEscaper.matcher(rawWord).replaceAll("");
        if(word.equals("")) //Don't nullify the damn thing
          word = rawWord;

        wordFeat = model.wordIndex.indexOf(word);

        tagFeat = model.tagIndex.indexOf(posTags.get(translationId).get(realSIdx));
        sLocBin = DistortionModel.getSlocBin((float) realSIdx / sMaxIdx);
      }

      //Setup the datum
      float[] feats = new float[model.getFeatureDimension()];
      int featPtr = 0;
      for(DistortionModel.Feature feat : model.featureIndex) {
        if(feat == DistortionModel.Feature.Word)
          feats[featPtr++] = wordFeat;
        else if(feat == DistortionModel.Feature.CurrentTag)
          feats[featPtr++] = tagFeat;
        else if(feat == DistortionModel.Feature.RelPosition)
          feats[featPtr++] = sLocBin;
        else if(feat == DistortionModel.Feature.SourceLen)
          feats[featPtr++] = slenBin;
        else if(feat == DistortionModel.Feature.RightTag) {
          System.err.println("Context tag feature is broken!");
        } else if(feat == DistortionModel.Feature.LeftTag) {
          System.err.println("Context tag feature is broken!");
        } 
        //WARNING: Arctag feature not loaded here...must be done on the fly
      }

      final Datum datum = new Datum(0.0f,feats);

      inDatums.add(datum);
      debugDatums.add(prettyPrint(model, datum, word));

      //Cache the log probabilities for each class
      cache[sIdx] = new double[numClasses];
      for(DistortionModel.Class c : DistortionModel.Class.values())
        cache[sIdx][c.ordinal()] = model.logProb(datum, c);
    }

    return debugDatums;
  }  

  @Override
  public DiscrimDistortionFeaturizer clone() throws CloneNotSupportedException {
    return (DiscrimDistortionFeaturizer) super.clone();
  }

  @Override
  public void debugBest(Featurizable<IString, String> f) {

    if(!DEBUG) return;

    final int translationId = f.translationId + (Phrasal.local_procs > 1 ? 2 : 0);

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

        int numNulls = (thisF.prior == null) ? 0 : (Integer) thisF.getState(this);

        System.err.printf("T STEP %d\n", iter++);
        System.err.println(" partial: " + thisF.partialTranslation);
        System.err.println(" coverage: " + thisF.hyp.foreignCoverage.toString());
        System.err.println(" algn: " + thisF.option.abstractOption.alignment.toString());
        System.err.printf(" opt: %s --> %s\n\n", thisF.foreignPhrase.toString(), thisF.translatedPhrase.toString());
        System.err.printf(" tpos: %d\n", thisF.translationPosition);
        System.err.printf(" spos: %d\n", thisF.foreignPosition);
        System.err.printf(" lastSIdx: %d\n", numNulls);
      }
    }
  }

  @Override
  public void rerankingMode(boolean r) {}
  @Override
  public void reset() {}
  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) { return null; }
}
