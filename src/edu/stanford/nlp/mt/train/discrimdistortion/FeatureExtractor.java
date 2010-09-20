package edu.stanford.nlp.mt.train.discrimdistortion;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.mt.base.IOTools;

public class FeatureExtractor {

  private final File sourceFile;
  private final File alignFile;
  private final File targetFile;

  // Motivation: Same limit used for Google ngrams
  private static int WORD_COUNT_LIMIT = 40;

  private boolean VERBOSE = false;

  private boolean ADD_FEATURE_INDEX = true;
  private boolean EXTRACT_ONLY = false;

  private boolean SUB_SAMPLE = false;
  private float sampleRate = 0.0f;

  private boolean OUTBOUND = false;

  private boolean USE_DELIMS = false;
  private final String START_OF_SENTENCE = "<S>";
  private final String END_OF_SENTENCE = "</S>";
  private final String DELIM_POS = "SS";

  private float trainingThreshold = Integer.MAX_VALUE;

  protected TrainingSet ts = null;
  protected Index<String> tagIndex = null;
  protected Index<String> wordIndex = null;
  // protected Index<String> slenIndex = null;

  protected Index<DistortionModel.Feature> featureIndex = null;
  protected Index<DistortionModel.Class> classIndex = null;

  private ExecutorService threadPool = null;

  private DistortionModel initModel = null;

  // Assumes that these files exist
  public FeatureExtractor(int numThreads, File sourceFile, File targetFile,
      File alignFile) {
    this.sourceFile = sourceFile;
    this.alignFile = alignFile;
    this.targetFile = targetFile;

    if (numThreads != 1)
      threadPool = Executors.newFixedThreadPool(numThreads);
  }

  public void setVerbose(boolean verbose) {
    VERBOSE = verbose;
  }

  public void setMinWordCount(int minWordCount) {
    WORD_COUNT_LIMIT = minWordCount;
  }

  public void setExtractOnly() {
    EXTRACT_ONLY = true;
    ADD_FEATURE_INDEX = false;
  }

  public void initializeWithModel(DistortionModel d) {
    initModel = d;
  }

  public void setThreshold(float thresh) {
    trainingThreshold = thresh;
  }

  public void setSubSampling(boolean t, float rate) {
    SUB_SAMPLE = t;
    sampleRate = rate;
  }

  public void extractOutbound() {
    OUTBOUND = true;
  }

  public void insertDelims() {
    USE_DELIMS = true;
  }

  protected class ExtractionTask implements Runnable {

    protected List<String> sourceWords = null;
    protected List<String> posTags = null;
    protected SortedMap<Integer, Integer> alignmentMap = null;
    protected final float slenBin;
    protected final float sMaxIdx;
    protected boolean useTag;

    public ExtractionTask(String source, String alignments, int bitextLineNumber) {

      useTag = featureIndex.contains(DistortionModel.Feature.CurrentTag);

      // Tokenize the source string
      StringTokenizer sourceTokenizer = new StringTokenizer(source);
      sourceWords = new ArrayList<String>();
      posTags = new ArrayList<String>();
      while (sourceTokenizer.hasMoreTokens()) {
        String token = sourceTokenizer.nextToken();
        if (useTag) {
          String[] parts = token.split("#");
          assert parts.length == 2;
          assert !parts[1].equals("");
          sourceWords.add(parts[0]);
          posTags.add(parts[1].intern());
        } else
          sourceWords.add(token);
      }

      slenBin = (float) DistortionModel.getSlenBin(sourceWords.size());
      sMaxIdx = (float) (sourceWords.size() - 1);

      // Sort the alignments
      // Map is target --> source
      StringTokenizer alignTokenizer = new StringTokenizer(alignments);
      alignmentMap = new TreeMap<Integer, Integer>();
      while (alignTokenizer.hasMoreTokens()) {
        String alignment = alignTokenizer.nextToken();
        String[] indices = alignment.split("-");

        assert indices.length == 2;

        int sIdx = Integer.parseInt(indices[0]);
        int tIdx = Integer.parseInt(indices[1]);

        if (alignmentMap.containsKey(tIdx))
          System.err
              .printf(
                  "%s: WARNING many-to-one alignment at line %d. Are you using the intersect heuristic?\n",
                  this.getClass().getName(), bitextLineNumber);

        alignmentMap.put(tIdx, sIdx); // Sort by target side
      }
    }

    protected int getDistortion(int fromIdx, int toIdx) {
      int distortion; // = 0;
      if (fromIdx == -1)
        distortion = toIdx;
      else {
        distortion = fromIdx + 1 - toIdx;
        if (distortion > 0)
          distortion--; // Adjust for bias
        distortion *= -1; // Turn it into a cost
      }

      return distortion;
    }

    @Override
    public void run() {
      throw new RuntimeException(String.format(
          "%s: This class should not be executed!!!\n", this.getClass()
              .getName()));
    }
  }

  // "Inbound" is equivalent to "Linear Distortion"
  protected class InboundExtractionTask extends ExtractionTask implements
      Runnable {

    public InboundExtractionTask(String source, String alignments,
        int bitextLineNumber) {
      super(source, alignments, bitextLineNumber);
    }

    @Override
    public void run() {

      if (USE_DELIMS) {
        sourceWords.add(END_OF_SENTENCE);
        alignmentMap.put(Integer.MAX_VALUE, (int) sMaxIdx + 1); // Put it at the
                                                                // end
        if (useTag)
          posTags.add(DELIM_POS);
      }

      // Train on the translation order
      Random rand = new Random();
      int lastSIdx = Integer.MIN_VALUE;
      for (Map.Entry<Integer, Integer> algnPair : alignmentMap.entrySet()) {
        final int sIdx = algnPair.getValue();
        float targetValue; // = 0.0f;
        boolean WAS_FIRST = false;
        if (lastSIdx == Integer.MIN_VALUE) {
          targetValue = sIdx;
          WAS_FIRST = true;

        } else {
          targetValue = (float) getDistortion(lastSIdx, sIdx);
        }

        // Conditions for skipping this example
        //
        if (featureIndex.contains(DistortionModel.Feature.Word)
            && !wordIndex.contains(sourceWords.get(sIdx)) && !EXTRACT_ONLY) {
          lastSIdx = sIdx;
          continue;

        } else if (SUB_SAMPLE && targetValue == 0.0f) {
          if (rand.nextFloat() <= sampleRate) {
            lastSIdx = sIdx;
            continue;
          }

        } else if (Math.abs(targetValue) > trainingThreshold) {
          lastSIdx = sIdx;
          continue;
        }

        float[] datum = new float[featureIndex.size()];
        int datPtr = 0;
        for (DistortionModel.Feature feature : featureIndex) {
          if (feature == DistortionModel.Feature.Word)
            datum[datPtr++] = (float) wordIndex.indexOf(sourceWords.get(sIdx));
          else if (feature == DistortionModel.Feature.CurrentTag)
            datum[datPtr++] = (float) tagIndex.indexOf(posTags.get(sIdx),
                ADD_FEATURE_INDEX);
          else if (feature == DistortionModel.Feature.SourceLen)
            datum[datPtr++] = slenBin;
          else if (feature == DistortionModel.Feature.RelPosition)
            datum[datPtr++] = (float) DistortionModel.getSlocBin((float) sIdx
                / sMaxIdx); // Ok for USE_DELIM since function will return last
                            // class
          else if (feature == DistortionModel.Feature.RightTag) {
            System.err.println("Context tags broken for inbound model!");
            // if(sIdx == sourceWords.size() - 1)
            // datum[datPtr++] = (float)
            // tagIndex.indexOf(DELIM_POS,ADD_FEATURE_INDEX);
            // else
            // datum[datPtr++] = (float) tagIndex.indexOf(posTags.get(sIdx + 1),
            // ADD_FEATURE_INDEX);
          } else if (feature == DistortionModel.Feature.LeftTag) {
            System.err.println("Context tags broken for inbound model!");
            // if(sIdx == 0)
            // datum[datPtr++] = (float) tagIndex.indexOf(DELIM_POS,
            // ADD_FEATURE_INDEX);
            // else
            // datum[datPtr++] = (float) tagIndex.indexOf(posTags.get(sIdx - 1),
            // ADD_FEATURE_INDEX);
          } else if (feature == DistortionModel.Feature.ArcTag) {
            if (WAS_FIRST)
              datum[datPtr++] = (float) tagIndex.indexOf(DELIM_POS,
                  ADD_FEATURE_INDEX); // Inbound from start of sentence
            else
              datum[datPtr++] = (float) tagIndex.indexOf(posTags.get(lastSIdx),
                  ADD_FEATURE_INDEX);
          }
        }
        ts.addDatum(new Datum(targetValue, datum));
        lastSIdx = sIdx;
      }
    }
  }

  protected class OutboundExtractionTask extends ExtractionTask implements
      Runnable {

    public OutboundExtractionTask(String source, String alignments,
        int bitextLineNumber) {
      super(source, alignments, bitextLineNumber);
    }

    @Override
    public void run() {

      if (USE_DELIMS) {
        sourceWords.add(0, START_OF_SENTENCE);
        alignmentMap.put(Integer.MIN_VALUE, -1);

        sourceWords.add(END_OF_SENTENCE);
        alignmentMap.put(Integer.MAX_VALUE, (int) sMaxIdx + 1);
        if (useTag) {
          posTags.add(0, DELIM_POS);
          posTags.add(DELIM_POS);
        }
      }

      // Train on the translation order
      Random rand = new Random();
      int lastSIdx = Integer.MIN_VALUE;
      for (Map.Entry<Integer, Integer> algnPair : alignmentMap.entrySet()) {
        if (lastSIdx == Integer.MIN_VALUE) {
          lastSIdx = algnPair.getValue();
          continue;
        }

        int thisRealSIdx = algnPair.getValue(); // Only used to calculate the
                                                // targetValue
        int thisTIdx = algnPair.getKey();
        float targetValue, sLocBin;
        int arcTagIdx;
        if (USE_DELIMS && lastSIdx == -1) {
          targetValue = (float) thisRealSIdx;
          sLocBin = (float) DistortionModel.getSlocBin(0.0f);
          arcTagIdx = thisRealSIdx + 1;

        } else if (USE_DELIMS && thisTIdx == Integer.MAX_VALUE) {
          sLocBin = (float) DistortionModel.getSlocBin(1.0f);
          targetValue = (float) getDistortion(lastSIdx, thisRealSIdx);

          arcTagIdx = sourceWords.size() - 1; // where we put the delimiter

        } else {
          sLocBin = (float) DistortionModel.getSlocBin((float) lastSIdx
              / sMaxIdx);
          targetValue = (float) getDistortion(lastSIdx, thisRealSIdx);

          arcTagIdx = (USE_DELIMS) ? thisRealSIdx + 1 : thisRealSIdx;
        }

        final int sFeatureIdx = (USE_DELIMS) ? lastSIdx + 1 : lastSIdx;

        // Conditions for skipping this example
        //
        if (featureIndex.contains(DistortionModel.Feature.Word)
            && !wordIndex.contains(sourceWords.get(sFeatureIdx))
            && !EXTRACT_ONLY) {
          lastSIdx = algnPair.getValue();
          continue;

        } else if (SUB_SAMPLE && targetValue == 0.0f) {
          if (rand.nextFloat() <= sampleRate) {
            lastSIdx = algnPair.getValue();
            continue;
          }

        } else if (Math.abs(targetValue) > trainingThreshold) {
          lastSIdx = algnPair.getValue();
          continue;
        }

        float[] datum = new float[featureIndex.size()];
        int datPtr = 0;
        for (DistortionModel.Feature feature : featureIndex) {
          if (feature == DistortionModel.Feature.Word)
            datum[datPtr++] = (float) wordIndex.indexOf(sourceWords
                .get(sFeatureIdx));
          else if (feature == DistortionModel.Feature.CurrentTag)
            datum[datPtr++] = (float) tagIndex.indexOf(
                posTags.get(sFeatureIdx), ADD_FEATURE_INDEX);
          else if (feature == DistortionModel.Feature.SourceLen)
            datum[datPtr++] = slenBin;
          else if (feature == DistortionModel.Feature.RelPosition)
            datum[datPtr++] = sLocBin;
          else if (feature == DistortionModel.Feature.RightTag) {
            System.err.println("Context tags broken for outbound model!");
            // if(lastSIdx == sourceWords.size() - 1)
            // datum[datPtr++] = (float)
            // tagIndex.indexOf(DELIM_POS,ADD_FEATURE_INDEX);
            // else
            // datum[datPtr++] = (float) tagIndex.indexOf(posTags.get(lastSIdx +
            // 1), ADD_FEATURE_INDEX);
          } else if (feature == DistortionModel.Feature.LeftTag) {
            System.err.println("Context tags broken for outbound model!");
            // if(lastSIdx == 0)
            // datum[datPtr++] = (float) tagIndex.indexOf(DELIM_POS,
            // ADD_FEATURE_INDEX);
            // else
            // datum[datPtr++] = (float) tagIndex.indexOf(posTags.get(lastSIdx -
            // 1), ADD_FEATURE_INDEX);
          }

          else if (feature == DistortionModel.Feature.ArcTag) {
            datum[datPtr++] = (float) tagIndex.indexOf(posTags.get(arcTagIdx),
                ADD_FEATURE_INDEX);
          }
        }
        ts.addDatum(new Datum(targetValue, datum));
        lastSIdx = algnPair.getValue();
      }
    }
  }

  private Counter<String> extractVocabulary(String sourceFile,
      boolean inputHasTags) {
    LineNumberReader reader = IOTools.getReaderFromFile(sourceFile);
    Counter<String> counter = new ClassicCounter<String>();

    try {
      while (reader.ready()) {

        StringTokenizer st = new StringTokenizer(reader.readLine());
        while (st.hasMoreTokens()) {
          if (inputHasTags) {
            String[] parts = st.nextToken().split("#");
            assert parts.length == 2;
            counter.incrementCount(parts[0]);

          } else
            counter.incrementCount(st.nextToken());
        }
      }

      if (VERBOSE)
        System.out.printf("%s: Read %d lines\n", this.getClass().getName(),
            reader.getLineNumber());

      reader.close();

    } catch (IOException e) {
      System.err.printf(
          "%s: IO error while extracting vocabulary from %s (line %d)\n", this
              .getClass().getName(), sourceFile, reader.getLineNumber());
      e.printStackTrace();
    }

    return counter;
  }

  public TrainingSet extract(final Index<DistortionModel.Feature> feats,
      final Index<DistortionModel.Class> classes, final int numExpectedFeatures) {

    featureIndex = feats;
    classIndex = classes;

    ts = new TrainingSet(featureIndex, classIndex, numExpectedFeatures);

    if (featureIndex.contains(DistortionModel.Feature.CurrentTag)) {
      if (initModel == null) {
        tagIndex = new HashIndex<String>();
        if (USE_DELIMS)
          tagIndex.add(DELIM_POS);

      } else {
        tagIndex = initModel.tagIndex;
      }
    }

    if (featureIndex.contains(DistortionModel.Feature.Word)) {
      if (initModel == null) {
        if (VERBOSE)
          System.out.printf("%s: Extracting vocabulary...\n", this.getClass()
              .getName());

        Counter<String> vocab = extractVocabulary(sourceFile.getPath(),
            featureIndex.contains(DistortionModel.Feature.CurrentTag));
        wordIndex = new HashIndex<String>();

        for (String word : vocab.keySet()) {
          if (vocab.getCount(word) >= WORD_COUNT_LIMIT)
            wordIndex.add(word);
        }

        if (VERBOSE)
          System.out.printf(
              "%s: Extracted %d terms (discarded %d \\ final vocab %d)\n", this
                  .getClass().getName(), vocab.keySet().size(), vocab.keySet()
                  .size() - wordIndex.size(), wordIndex.size());

        if (USE_DELIMS) {
          wordIndex.add(START_OF_SENTENCE);
          wordIndex.add(END_OF_SENTENCE);
        }

      } else {
        wordIndex = initModel.wordIndex;
      }
    }

    LineNumberReader sourceReader = IOTools.getReaderFromFile(sourceFile);
    LineNumberReader alignReader = IOTools.getReaderFromFile(alignFile);
    LineNumberReader targetReader = IOTools.getReaderFromFile(targetFile);

    try {
      while (sourceReader.ready() && alignReader.ready()
          && targetReader.ready()) {

        int currentLine = sourceReader.getLineNumber();
        if (VERBOSE && (currentLine + 1 % 100000) == 0)
          System.out.printf("%s: %d examples extracted\n", this.getClass()
              .getName(), ts.getNumExamples());

        String sourceLine = sourceReader.readLine();
        String alignLine = alignReader.readLine();
        targetReader.readLine().split("\\s+");

        ExtractionTask task = (OUTBOUND) ? new OutboundExtractionTask(
            sourceLine, alignLine, currentLine) : new InboundExtractionTask(
            sourceLine, alignLine, currentLine);

        if (threadPool == null)
          task.run();
        else
          threadPool.execute(task);
      }

      System.out
          .printf(
              "%s: Finished reading input (%d lines). Waiting for threads to terminate...\n",
              this.getClass().getName(), sourceReader.getLineNumber());

      sourceReader.close();
      alignReader.close();
      targetReader.close();

      if (threadPool != null) {
        threadPool.shutdown();
        threadPool.awaitTermination(2, TimeUnit.HOURS);
        System.out.printf("%s: Successful thread pool shutdown\n", this
            .getClass().getName());
      }

      // Set the feature offsets and dimensions
      int offset = 0;
      for (DistortionModel.Feature feature : featureIndex) {
        if (feature == DistortionModel.Feature.Word) {
          ts.addFeatureParameters(feature, DistortionModel.FeatureType.Binary,
              offset, wordIndex.size());
          offset += wordIndex.size();
        } else if (feature == DistortionModel.Feature.CurrentTag) {
          ts.addFeatureParameters(feature, DistortionModel.FeatureType.Binary,
              offset, tagIndex.size());
          offset += tagIndex.size();
        } else if (feature == DistortionModel.Feature.SourceLen) {
          ts.addFeatureParameters(feature, DistortionModel.FeatureType.Binary,
              offset, DistortionModel.NUM_SLEN_BINS);
          offset += DistortionModel.NUM_SLEN_BINS;
        } else if (feature == DistortionModel.Feature.RelPosition) {
          ts.addFeatureParameters(feature, DistortionModel.FeatureType.Binary,
              offset, DistortionModel.NUM_SLOC_BINS);
          offset += DistortionModel.NUM_SLOC_BINS;
        } else if (feature == DistortionModel.Feature.RightTag) {
          ts.addFeatureParameters(feature, DistortionModel.FeatureType.Binary,
              offset, tagIndex.size());
          offset += tagIndex.size();
        } else if (feature == DistortionModel.Feature.LeftTag) {
          ts.addFeatureParameters(feature, DistortionModel.FeatureType.Binary,
              offset, tagIndex.size());
          offset += tagIndex.size();
        } else if (feature == DistortionModel.Feature.ArcTag) {
          ts.addFeatureParameters(feature, DistortionModel.FeatureType.Binary,
              offset, tagIndex.size());
          offset += tagIndex.size();
        }
      }

    } catch (IOException e) {
      System.err.printf(
          "%s: File IO error while extracting features (line %d)\n", this
              .getClass().getName(), sourceReader.getLineNumber());
      e.printStackTrace();
    } catch (InterruptedException e) {
      System.err.printf("%s: Threadpool execution interrupted\n", this
          .getClass().getName());
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
    model.useBeginEndMarkers = USE_DELIMS;
    model.START_OF_SENTENCE = START_OF_SENTENCE;
    model.END_OF_SENTENCE = END_OF_SENTENCE;
    model.DELIM_POS = DELIM_POS;
    model.isOutbound = OUTBOUND;

    return model;
  }

}
