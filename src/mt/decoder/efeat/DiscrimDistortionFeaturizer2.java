package mt.decoder.efeat;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import edu.stanford.nlp.io.IOUtils;

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
import mt.train.discrimdistortion.Datum;
import mt.train.discrimdistortion.DistortionModel;

public class DiscrimDistortionFeaturizer2 implements RichIncrementalFeaturizer<IString, String>{

  private final String FEATURE_NAME = "DiscrimDistortion";

  //Shared objects for all threads initialized in the constructor
  private static DistortionModel model;
  private static List<List<String>> posTags;
  private static Map<Sequence<IString>,Integer> sentenceToId;

  //Threadsafe...the reference will be clone()'d, but the object will be set when
  //initialize() is called
  private double[][] logProbCache = null;

  private boolean DEBUG = true;

  //Constants used for all hypotheses
  private static final Pattern ibmEscaper = Pattern.compile("#|\\+");


  public DiscrimDistortionFeaturizer2(String... args) {

    assert args.length == 3;

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




  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {

    //final int translationId = f.translationId + (PseudoMoses.local_procs > 1 ? 2 : 0);

    if(!f.option.abstractOption.alignment.hasAlignment())
      throw new RuntimeException(String.format("Unaligned translation option (did you filter the input for OOVs?):\n %s\n",f.option.toString()));

    final int sOffset = f.foreignPosition;

    //Work out the null alignments
    //
    final BitSet optCoverage = f.hyp.foreignCoverage.get(sOffset, f.foreignPhrase.size() + 1);
    final Set<Integer> nullAlignments = new HashSet<Integer>();

    int uncoveredIdx = optCoverage.nextClearBit(0);
    while(uncoveredIdx < f.foreignPhrase.size()) {
      nullAlignments.add(sOffset + uncoveredIdx);
      uncoveredIdx++;
      if(uncoveredIdx < optCoverage.size()) //Skip over covered tokens
        uncoveredIdx = optCoverage.nextClearBit(uncoveredIdx);
    }


    //Now score the alignments
    //   (the partial hypotheses is built left-to-right, so no sorting necessary)
    //
    int sHatPosition = (f.prior == null) ? 0 :
      f.foreignSentence.size() - f.prior.hyp.untranslatedTokens;
    double optScore = 0.0;      
    final int tOptLen = f.translatedPhrase.size();

    for(int i = 0; i < tOptLen; i++) {

      final int[] sIndices = f.option.abstractOption.alignment.e2f(i);
      if(sIndices == null || sIndices.length == 0)
        continue; //skip over null aligned target tokens

      final int sIdx = sOffset + sIndices[0];
      DistortionModel.Class predClass;
      if(nullAlignments.contains(sIdx))
        predClass = DistortionModel.MONOTONE;
      else {
        int movement = sHatPosition - sIdx;
        predClass = DistortionModel.discretizeDistortion(movement);
      }

      optScore += logProbCache[sIdx][predClass.ordinal()];

      sHatPosition++;
    }

    return new FeatureValue<String>(FEATURE_NAME, optScore);
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

    final float sMaxIdx = (float) foreign.size() - 1.0f;
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

      final int rPos = DistortionModel.getSlocBin((float) sIdx / sMaxIdx);
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
      System.err.printf("LOG PROB CACHE FOR transId %d\n",translationId);
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

    if(!DEBUG) return;

    final int translationId = f.translationId + (PseudoMoses.local_procs > 1 ? 2 : 0);

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

        System.err.printf("T STEP %d\n", iter++);
        System.err.println(" partial: " + thisF.partialTranslation);
        System.err.println(" coverage: " + thisF.hyp.foreignCoverage.toString());
        System.err.println(" algn: " + thisF.option.abstractOption.alignment.toString());
        System.err.printf(" opt: %s --> %s\n\n", thisF.foreignPhrase.toString(), thisF.translatedPhrase.toString());
        System.err.printf(" tpos: %d\n", thisF.translationPosition);
        System.err.printf(" spos: %d\n", thisF.foreignPosition);
      }
    }
  }

  @Override
  public void rerankingMode(boolean r) {}
  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }
  @Override
  public void reset() {}
}
