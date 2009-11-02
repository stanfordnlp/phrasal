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
import mt.decoder.feat.ClonedFeaturizer;
import mt.decoder.feat.StatefulFeaturizer;
import mt.train.discrimdistortion.Datum;
import mt.train.discrimdistortion.DistortionModel;

public class DiscrimDistortionFeaturizer extends StatefulFeaturizer<IString, String> implements ClonedFeaturizer<IString, String> {

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


  private static final Pattern ibmEscaper = Pattern.compile("#|\\+");
  private static final int TARGET_IDX = 0;  
  private static final double UNIFORM_SCORE = -1.0;
  private static final Set<String> uniformTags = new HashSet<String>();
  static {
    uniformTags.add("CC");
    uniformTags.add("IN");
    uniformTags.add("CD");
    uniformTags.add("PRP");
    uniformTags.add("PRP$");
    uniformTags.add("NOUN_QUANT");
    uniformTags.add("RB");
    uniformTags.add("WP");
  }

  //WSGDEBUG Debugging objects
  private boolean VERBOSE = false;
  private static final Set<Integer> debugIds = new HashSet<Integer>();
  static {
    debugIds.add(1);
    debugIds.add(4);
  }


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

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {

    //WSGDEBUG
    final int translationId = f.translationId + (PseudoMoses.local_procs > 1 ? 2 : 0);
    //VERBOSE = debugIds.contains(translationId);


    //Compute the previous and target length constants
    //Units are (target length / source length)
    //Added an (empirical) 10 word buffer because the first few options usually cause huge jumps in the value
    final int numCoveredSourceTokens = f.foreignSentence.size() - f.hyp.untranslatedTokens;
    float lastC, thisC;
    if(f.prior == null || f.partialTranslation.size() <= 10 || numCoveredSourceTokens <= 10) {
      lastC = DEFAULT_C;
      thisC = DEFAULT_C;

    } else {
      final int lastNumCoveredSourceTokens = f.foreignSentence.size() - f.prior.hyp.untranslatedTokens;

      lastC = (float) f.prior.hyp.length / lastNumCoveredSourceTokens;
      thisC = (float) f.hyp.length / numCoveredSourceTokens;
    }

    final float thisTLen = sLen * thisC;


    //
    // Setup the state
    //
    int[][] f2e = (f.prior == null) ? new int[f.foreignSentence.size()][] : copy((int[][]) f.prior.getState(this));
    if(VERBOSE) {
      System.err.println("\n\nPartial: " + f.partialTranslation.toString());
      System.err.printf("WSGDEBUG1: State:\n");
      for(int sIdx = 0; sIdx < f2e.length; sIdx++)
        if(f2e[sIdx] != null)
          System.err.printf(" %d --> %d\n", sIdx, f2e[sIdx][TARGET_IDX]);
      System.err.println();
    }


    //
    // Calculate the feature score adjustment (if needed)
    //
    double adjustment = 0.0;
    if(Math.abs(lastC - thisC) > TOL) {
      final float lastTLen = sLen * lastC;

      if(VERBOSE)
        System.err.printf("WSGDEBUG2: lastC %f  lastTLen %f ||| thisC %f  thisTLen %f ||| tol %f\n", lastC,lastTLen,thisC,thisTLen,TOL);

      double oldScore = 0.0;
      double newScore = 0.0;
      for(int sIdx = 0; sIdx < f2e.length; sIdx++) {
        if(f2e[sIdx] == null) continue; //Skip over uncovered source tokens

        final int tIdx = f2e[sIdx][TARGET_IDX];

        if(tIdx == -1) {
          oldScore += UNIFORM_SCORE;
          newScore += UNIFORM_SCORE;
        } else {
          float oldRel = (((float) tIdx / lastTLen) * 100.0f) - (((float) sIdx / sLen) * 100.0f);
          float newRel = (((float) tIdx / thisTLen) * 100.0f) - (((float) sIdx / sLen) * 100.0f);

          DistortionModel.Class oldClass = DistortionModel.discretizeDistortion(oldRel);
          DistortionModel.Class newClass = DistortionModel.discretizeDistortion(newRel);

          oldScore += logProbCache[sIdx][oldClass.ordinal()];
          newScore += logProbCache[sIdx][newClass.ordinal()];
        }
      }

      adjustment = newScore - oldScore; //Difference in log scores

      if(VERBOSE)
        System.err.printf("WSGDEBUG2: %f --> %f  (diff: %f)\n", oldScore, newScore, adjustment);
    }

    //
    // Calculate the score of this translation option
    //
    final int sOffset = f.foreignPosition;
    final int tOffset = f.translationPosition;
    final int tOptLen = f.translatedPhrase.size();

    double optScore = 0;
    for(int i = 0; i < tOptLen; i++) {

      if(!f.option.abstractOption.alignment.hasAlignment()) continue;
      final int[] sIndices = f.option.abstractOption.alignment.e2f(i);
      if(sIndices == null) continue;

      if(VERBOSE) {
        System.err.printf("WSGDEBUG3: option %s --> %s\n", f.option.abstractOption.foreign.toString(), f.option.abstractOption.translation.toString());
        System.err.print("WSGDEBUG3: algn ");
        if(f.option.abstractOption.alignment.hasAlignment())
          System.err.println(f.option.abstractOption.alignment.toString());
        else
          System.err.println();
      }

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
        if(uniformTags.contains(posTag))
          optScore += UNIFORM_SCORE;
        else {
          float relMovement = (((float) tIdx / thisTLen) * 100.0f) - (((float) sIdx / sLen) * 100.0f);

          final DistortionModel.Class thisClass = DistortionModel.discretizeDistortion(relMovement);
          final double logProb = logProbCache[sIdx][thisClass.ordinal()];

          if(VERBOSE)
            System.err.printf("WSGDEBUG3: score: %f  (sIdx %d  tIdx %d  mvmnt %f)\n", logProb, sIdx, tIdx, relMovement);

          optScore += logProb;
        }
      }      
    }

    //
    // Check for unaligned source tokens (iterate over source side after surrounding tokens have been scored)
    //
    for(int sIdx = sOffset; sIdx < sOffset + f.foreignPhrase.size() && sIdx < f2e.length; sIdx++) {
      int tIdx = -1;
      if(f2e[sIdx] == null) {
        String posTag = posTags.get(translationId).get(sIdx);
        if(uniformTags.contains(posTag))
          optScore += UNIFORM_SCORE;
        else {
          tIdx = interpolateTargetPosition(sIdx,f2e);
          if(tIdx == -1)
            optScore += UNIFORM_SCORE;
          else {
            float relMovement = (((float) tIdx / thisTLen) * 100.0f) - (((float) sIdx / sLen) * 100.0f);
            final DistortionModel.Class thisClass = DistortionModel.discretizeDistortion(relMovement);
            final double logProb = logProbCache[sIdx][thisClass.ordinal()];

            optScore += logProb;
          }
        }
      
        int[] alignment = new int[1];
        alignment[TARGET_IDX] = tIdx;
        f2e[sIdx] = alignment;             
      }
    }


    //WSGDEBUG
    //This opt completed the translation...check for completeness
    //Incompletions should only occur for OOVs
    if(f.done) {
      int unscored = 0;
      for(int i = 0; i < f2e.length; i++) {
        if(f2e[i] == null) {
          System.err.printf("WSGDEBUG5: fword %d in target phrase (%d %d)\n",i,f.f2tAlignmentIndex[i][Featurizable.PHRASE_START],f.f2tAlignmentIndex[i][Featurizable.PHRASE_END]);
          unscored++;
          optScore += UNIFORM_SCORE;
        }
      }
      if(VERBOSE)
        System.err.printf("WSGDEBUG6: %d uncovered of %d s tokens\n", unscored, f2e.length);
    }

    f.setState(this, f2e);

    return new FeatureValue<String>(FEATURE_NAME, optScore + adjustment);
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
      return -1;
    else if(leftTargetIdx != -1 && rightTargetIdx != -1)
      return Math.round(((float) rightTargetIdx - leftTargetIdx) / 2.0f);
    else
      return (leftTargetIdx != -1) ? leftTargetIdx : rightTargetIdx;
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {

    if(!sentenceToId.containsKey(foreign))
      throw new RuntimeException(String.format("No translation ID for sentence:\n%s\n",foreign.toString()));

    //WSGDEBUG
    final int translationId = sentenceToId.get(foreign);
    //VERBOSE = debugIds.contains(translationId);

    assert posTags.get(translationId).size() == foreign.size();

    logProbCache = new double[foreign.size()][];
    sLen = (float) foreign.size();

    final int slenBin = DistortionModel.getSlenBin(foreign.size());
    final int numClasses = DistortionModel.Class.values().length;

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
      for(DistortionModel.Class c : DistortionModel.Class.values()) {
        //   System.err.printf("WSGDEBUG: %s\n",datum.toString());
        double realProb = model.prob(datum,c,isOOV);
        if(realProb == 0.0)
          realProb = 0.000001; //Guard against underflow (compute logprob in model)
        logProbCache[sIdx][c.ordinal()] = Math.log(realProb);
      }
    }

    //WSGDEBUG
    if(VERBOSE) {
      System.err.printf("WSGDEBUG: Prob cache for transId %d\n",translationId);
      for(int i = 0; i < logProbCache.length; i++) {
        System.err.printf("%d: %s / %s\n", i, foreign.get(i), posTags.get(translationId).get(i));
        for(int j = 0; j < logProbCache[i].length; j++)
          System.err.printf("  %d  %f\n", j, logProbCache[i][j]);
      }
      System.err.println("\n\n\n");
    }
  }

  @Override
  public DiscrimDistortionFeaturizer clone() throws CloneNotSupportedException {
    return (DiscrimDistortionFeaturizer) super.clone();
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }
  @Override
  public void reset() {}
}
