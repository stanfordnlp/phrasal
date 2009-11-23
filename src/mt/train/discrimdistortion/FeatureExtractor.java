package mt.train.discrimdistortion;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import mt.base.IOTools;

public class FeatureExtractor {

  private final File sourceFile;
  private final File alignFile;
  private final File targetFile;

  //Motivation: Same limit used for Google ngrams
  private static int WORD_COUNT_LIMIT = 40;

  private boolean VERBOSE = false;

  private boolean ADD_FEATURE_INDEX = true;

  private boolean SUB_SAMPLE = true;

  private float trainingThreshold = Integer.MAX_VALUE;

  protected TrainingSet ts = null;
  protected Index<String> tagIndex = null;
  protected Index<String> wordIndex = null;
  protected Index<String> slenIndex = null;

  protected Index<DistortionModel.Feature> featureIndex = null;
  protected Index<DistortionModel.Class> classIndex = null;

  private ExecutorService threadPool = null;

  private DistortionModel initModel = null;

  //Assumes that these files exist
  public FeatureExtractor(int numThreads, File sourceFile, File targetFile, File alignFile) {
    this.sourceFile = sourceFile;
    this.alignFile = alignFile;
    this.targetFile = targetFile;

    if(numThreads != 1)
      threadPool = Executors.newFixedThreadPool(numThreads);
  }

  public void setVerbose(boolean verbose) { VERBOSE = verbose; }

  public void setMinWordCount(int minWordCount) { WORD_COUNT_LIMIT = minWordCount; }	

  public void setExtractOnly() { ADD_FEATURE_INDEX = false; }

  public void initializeWithModel(DistortionModel d) { initModel = d; }

  public void setThreshold(float thresh) { trainingThreshold = thresh; }

  public void setSubSampling(boolean t) { SUB_SAMPLE = t; }

  protected class ExtractionTask implements Runnable {

    private final String sourceString;
    private final String alignments;
    private final int bitextLineNumber;

    public ExtractionTask(String source, String alignments, int targetLen, int bitextLineNumber) {
      sourceString = source;
      this.alignments = alignments;
      this.bitextLineNumber = bitextLineNumber;
    }

    @Override
    public void run() {
      StringTokenizer alignTokenizer = new StringTokenizer(alignments);

      //Tokenize the source string
      StringTokenizer sourceTokenizer = new StringTokenizer(sourceString);
      Set<Integer> nullAlignments = new HashSet<Integer>();
      List<String> sourceWords = new ArrayList<String>();
      List<String> posTags = new ArrayList<String>();
      int sourceIdx = 0;
      while(sourceTokenizer.hasMoreTokens()) {
        String token = sourceTokenizer.nextToken();
        if(featureIndex.contains(DistortionModel.Feature.CurrentTag)) {
          String[] parts = token.split("#");
          assert parts.length == 2;					
          assert !parts[1].equals("");
          sourceWords.add(parts[0]);
          posTags.add(parts[1].intern());
        } else
          sourceWords.add(token);
        nullAlignments.add(sourceIdx++); //Source isn't aligned yet, so add everything!
      }

      final int slenBin = DistortionModel.getSlenBin(sourceWords.size());

      //Process the alignment
      //Map is target --> source
      SortedMap<Integer,Integer> alignmentMap = new TreeMap<Integer,Integer>();
      Map<Integer,Integer> sTokMapping = new HashMap<Integer,Integer>();
      int newSIdx = 0;
      while(alignTokenizer.hasMoreTokens()) {
        String alignment = alignTokenizer.nextToken();
        String[] indices = alignment.split("-");

        assert indices.length == 2;

        int sIdx = Integer.parseInt(indices[0]);
        int tIdx = Integer.parseInt(indices[1]);

        sTokMapping.put(sIdx,newSIdx);
        newSIdx++;
        nullAlignments.remove(sIdx);

        if(alignmentMap.containsKey(tIdx))
          System.err.printf("%s: WARNING many-to-one alignment at line %d. Are you using the intersect heuristic?\n", this.getClass().getName(),bitextLineNumber);

        alignmentMap.put(tIdx, sIdx); //Sort by target side
      }
      
      //Work out the translation order
      // First coordinate is the normalized index in the set of aligned source words
      // Second coordinate is the index into the set of source words
      List<Pair<Integer,Integer>> sTranslationOrder = new ArrayList<Pair<Integer,Integer>>();
      for(Map.Entry<Integer, Integer> alignment : alignmentMap.entrySet()) {
        final int sIdx = alignment.getValue();
        
        assert sTokMapping.containsKey(sIdx);
        
        final int normSIdx = sTokMapping.get(sIdx);
        sTranslationOrder.add(new Pair<Integer,Integer>(normSIdx,sIdx));
      }
      
      //Add the null alignments, with normalized coordinates equal to -1
      for(int nullSIdx : nullAlignments)
        sTranslationOrder.add(new Pair<Integer,Integer>(-1,nullSIdx));
            
      //Train on the translation order
      Random rand = new Random();
      float sMaxIdx = (float) (sourceWords.size() - 1);
      for(int i = 0; i < sTranslationOrder.size(); i++) {
        final Pair<Integer,Integer> sIdxPair = sTranslationOrder.get(i);
        final int normSIdx = sIdxPair.first();
        final int sIdx = sIdxPair.second();
        
        if(featureIndex.contains(DistortionModel.Feature.Word) &&
            !wordIndex.contains(sourceWords.get(sIdx)) && ADD_FEATURE_INDEX) continue;

        final float targetValue = (normSIdx == -1) ? DistortionModel.NULL_VALUE : i - normSIdx;
        
        // Best values:
        // .50% / .65% (for 5m training sentences)
        if(SUB_SAMPLE && targetValue == 0.0) {
          if(rand.nextFloat() <= 0.57f) //Parameter set by experimentation%
            continue;
        } 
        else if(SUB_SAMPLE && normSIdx == -1) {
          if(rand.nextFloat() <= 0.65f) //Parameter set by experimentation%
            continue;
        }
        
        //Threshold training
        if(Math.abs(targetValue) > trainingThreshold)
          continue;

        float[] datum = new float[featureIndex.size()];
        int datPtr = 0;

        for(DistortionModel.Feature feature : featureIndex) {
          if(feature == DistortionModel.Feature.Word)
            datum[datPtr++] = (float) wordIndex.indexOf(sourceWords.get(sIdx));
          else if(feature == DistortionModel.Feature.CurrentTag)
            datum[datPtr++] = (float) tagIndex.indexOf(posTags.get(sIdx),ADD_FEATURE_INDEX);
          else if(feature == DistortionModel.Feature.SourceLen)
            datum[datPtr++] = (float) slenBin;
          else if(feature == DistortionModel.Feature.RelPosition)
            datum[datPtr++] = DistortionModel.getSlocBin((float) sIdx / sMaxIdx );
          else if(feature == DistortionModel.Feature.RightTag) {
            if(sIdx == sourceWords.size() - 1)
              datum[datPtr++] = (float) tagIndex.indexOf("</S>",ADD_FEATURE_INDEX);
            else
              datum[datPtr++] = (float) tagIndex.indexOf(posTags.get(sIdx + 1), ADD_FEATURE_INDEX);
          } else if(feature == DistortionModel.Feature.LeftTag) {
            if(sIdx == 0)
              datum[datPtr++] = (float) tagIndex.indexOf("<S>", ADD_FEATURE_INDEX);
            else
              datum[datPtr++] = (float) tagIndex.indexOf(posTags.get(sIdx - 1), ADD_FEATURE_INDEX);
          }
        }
        ts.addDatum(new Datum(targetValue, datum));
      }
    }
  }

  private Counter<String> extractVocabulary(String sourceFile, boolean useTags) {
    LineNumberReader reader = IOTools.getReaderFromFile(sourceFile);
    Counter<String> counter = new ClassicCounter<String>();
    try {
      while(reader.ready()) {
        StringTokenizer st = new StringTokenizer(reader.readLine());
        while(st.hasMoreTokens()) {
          if(useTags) {
            String[] parts = st.nextToken().split("#");
            assert parts.length == 2;					
            counter.incrementCount(parts[0]);
          } else
            counter.incrementCount(st.nextToken());
        }
      }
      
      if(VERBOSE)
        System.out.printf("%s: Read %d lines\n", this.getClass().getName(), reader.getLineNumber());

      reader.close();

    } catch (IOException e) {
      System.err.printf("%s: IO error while extracting vocabulary from %s (line %d)\n", this.getClass().getName(),sourceFile,reader.getLineNumber());
      e.printStackTrace();
    }

    return counter;
  }

  public TrainingSet extract(final Index<DistortionModel.Feature> feats, 
      final Index<DistortionModel.Class> classes, 
      final int numExpectedFeatures) {
    featureIndex = feats;
    classIndex = classes;

    ts = new TrainingSet(featureIndex, classIndex, numExpectedFeatures);

    if(featureIndex.contains(DistortionModel.Feature.CurrentTag)) {
      tagIndex = (initModel == null) ? new HashIndex<String>() : initModel.tagIndex;
    }
    if(featureIndex.contains(DistortionModel.Feature.Word)) {
      if(initModel == null) {
        if(VERBOSE)
          System.out.printf("%s: Extracting vocabulary...\n", this.getClass().getName());
        Counter<String> vocab = extractVocabulary(sourceFile.getPath(), featureIndex.contains(DistortionModel.Feature.CurrentTag));
        wordIndex = new HashIndex<String>();
        for(String word : vocab.keySet())
          if(vocab.getCount(word) >= WORD_COUNT_LIMIT)
            wordIndex.add(word);
        if(VERBOSE)
          System.out.printf("%s: Extracted %d terms (discarded %d \\ final vocab %d)\n", 
              this.getClass().getName(), vocab.keySet().size(), vocab.keySet().size() - wordIndex.size(), wordIndex.size());
      }
      else
        wordIndex = initModel.wordIndex;
    }

      LineNumberReader sourceReader = IOTools.getReaderFromFile(sourceFile);
      LineNumberReader alignReader = IOTools.getReaderFromFile(alignFile);
      LineNumberReader targetReader = IOTools.getReaderFromFile(targetFile);

      try {
        while(sourceReader.ready() && alignReader.ready() && targetReader.ready()) {

          int currentLine = sourceReader.getLineNumber();
          if(VERBOSE && (currentLine + 1 % 100000) == 0)
            System.out.printf("%s: %d examples extracted\n",this.getClass().getName(),ts.getNumExamples());

          String sourceLine = sourceReader.readLine();
          String alignLine = alignReader.readLine();
          int targetToks = targetReader.readLine().split("\\s+").length;

          ExtractionTask task = new ExtractionTask(sourceLine, alignLine, targetToks, currentLine);
          if(threadPool == null)
            task.run();
          else
            threadPool.execute(task);
        }

        System.out.printf("%s: Finished reading input (%d lines). Waiting for threads to terminate...\n", this.getClass().getName(), sourceReader.getLineNumber());

        sourceReader.close();
        alignReader.close();
        targetReader.close();

        if(threadPool != null) {
          threadPool.shutdown();
          threadPool.awaitTermination(2, TimeUnit.HOURS);
          System.out.printf("%s: Successful thread pool shutdown\n",this.getClass().getName());
        }			

        //Set the feature offsets and dimensions
        int offset = 0;
        for(DistortionModel.Feature feature : featureIndex) {
          if(feature == DistortionModel.Feature.Word) {
            ts.addFeatureParameters(feature, DistortionModel.FeatureType.Binary, offset, wordIndex.size());
            offset += wordIndex.size();
          } else if(feature == DistortionModel.Feature.CurrentTag) {
            ts.addFeatureParameters(feature, DistortionModel.FeatureType.Binary, offset, tagIndex.size());
            offset += tagIndex.size();
          } else if(feature == DistortionModel.Feature.SourceLen) {
            ts.addFeatureParameters(feature, DistortionModel.FeatureType.Binary, offset, DistortionModel.NUM_SLEN_BINS);
            offset += DistortionModel.NUM_SLEN_BINS;
          } else if(feature == DistortionModel.Feature.RelPosition) {
            ts.addFeatureParameters(feature, DistortionModel.FeatureType.Binary, offset, DistortionModel.NUM_SLOC_BINS);
            offset += DistortionModel.NUM_SLOC_BINS;
          } else if(feature == DistortionModel.Feature.RightTag) {
            ts.addFeatureParameters(feature, DistortionModel.FeatureType.Binary, offset, tagIndex.size());
            offset += tagIndex.size();
          } else if(feature == DistortionModel.Feature.LeftTag) {
            ts.addFeatureParameters(feature, DistortionModel.FeatureType.Binary, offset, tagIndex.size());
            offset += tagIndex.size();
          }
        }

      } catch (IOException e) {
        System.err.printf("%s: File IO error while extracting features (line %d)\n", this.getClass().getName(),sourceReader.getLineNumber());
        e.printStackTrace();
      } catch (InterruptedException e) {
        System.err.printf("%s: Threadpool execution interrupted\n", this.getClass().getName());
        e.printStackTrace();
      }

      return ts;
  }

  public DistortionModel getModel() {
    DistortionModel model = new DistortionModel();

    model.featureIndex = featureIndex;
    model.classIndex = classIndex;
    model.tagIndex = tagIndex;
    model.wordIndex = wordIndex;
    model.featureOffsets = ts.getFeatureOffsets();
    model.featureDimensions = ts.getFeatureDimensions();
    model.featureTypes = ts.getFeatureTypes();

    return model;
  }

}
