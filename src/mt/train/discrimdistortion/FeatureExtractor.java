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
import mt.base.IOTools;

public class FeatureExtractor {

	private final File sourceFile;
	private final File alignFile;
	private final File targetFile;

	//Motivation: Same limit used for Google ngrams
	private static int WORD_COUNT_LIMIT = 40;
	
	private boolean VERBOSE = false;
	
	protected TrainingSet ts = null;
	protected Index<String> tagIndex = null;
	protected Index<String> wordIndex = null;
	protected Index<String> slenIndex = null;
	
	protected Index<DistortionModel.Feature> featureIndex = null;
	protected Index<DistortionModel.Class> classIndex = null;
	
	private final ExecutorService threadPool;
	
	//Assumes that these files exist
	public FeatureExtractor(int numThreads, File sourceFile, File targetFile, File alignFile) {
		this.sourceFile = sourceFile;
		this.alignFile = alignFile;
		this.targetFile = targetFile;
		
		threadPool = Executors.newFixedThreadPool(numThreads);
	}
	
	public void setVerbose(boolean verbose) { VERBOSE = verbose; }
	
	public void setMinWordCount(int minWordCount) { WORD_COUNT_LIMIT = minWordCount; }	
	
//	public synchronized void printTarget(float f) {
//		ps.println(f);
//	}
	
	protected class ExtractionTask implements Runnable {

		private final String sourceString;
		private final String alignments;
		private final int bitextLineNumber;
		private final float targetLen;
		
		public ExtractionTask(String source, String alignments, int targetLen, int bitextLineNumber) {
			sourceString = source;
			this.alignments = alignments;
			this.bitextLineNumber = bitextLineNumber;
			this.targetLen = (float) targetLen;
		}
		
		@Override
		public void run() {
			StringTokenizer alignTokenizer = new StringTokenizer(alignments);
	
			//Tokenize the source string
			StringTokenizer sourceTokenizer = new StringTokenizer(sourceString);
			List<String> sourceWords = new ArrayList<String>();
			List<String> posTags = new ArrayList<String>();
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
			}
			
			final int slenBin = DistortionModel.getSlenBin(sourceWords.size());
			
			//Process the alignment
			//Map is source --> target
			Map<Integer,Integer> alignmentMap = new HashMap<Integer,Integer>();
			while(alignTokenizer.hasMoreTokens()) {
				String alignment = alignTokenizer.nextToken();
				String[] indices = alignment.split("-");
				
				assert indices.length == 2;
				
				int sIdx = Integer.parseInt(indices[0]);
				int tIdx = Integer.parseInt(indices[1]);
				
				if(alignmentMap.containsKey(sIdx))
					System.err.printf("%s: WARNING many-to-one alignment at line %d. Are you using the intersect heuristic?\n", this.getClass().getName(),bitextLineNumber);

				alignmentMap.put(sIdx, tIdx);
			}
			
			//WSGDEBUG (22 Oct 2009)
			// 1. Compute relative movement, not Moses distortion
			// We ignore unaligned words. They are interpolated at decoding time.
			for(Map.Entry<Integer, Integer> alignment : alignmentMap.entrySet()) {
				final int sIdx = alignment.getKey();
				if(!wordIndex.contains(sourceWords.get(sIdx))) continue;
				
				final int tIdx = alignment.getValue();

				//Scale up to percentages
				float sRel = ((float) sIdx / (float) sourceWords.size()) * 100.0f;	
				float tRel = ((float) tIdx / (float) targetLen) * 100.0f;
				
				//Target variable rel(T) - rel(S), so that right movement is positive and
				//left movement is negative
				//Units = %
				float targetValue = tRel - sRel;
								
				float[] datum = new float[featureIndex.size()];
				int datPtr = 0;
								
				for(DistortionModel.Feature feature : featureIndex) {
					if(feature == DistortionModel.Feature.Word)
						datum[datPtr++] = (float) wordIndex.indexOf(sourceWords.get(sIdx));
					else if(feature == DistortionModel.Feature.CurrentTag) {
						String thisTag = posTags.get(sIdx);
						datum[datPtr++] = (float) tagIndex.indexOf(thisTag,true);
					}
					else if(feature == DistortionModel.Feature.SourceLen)
						datum[datPtr++] = (float) slenBin;
					else if(feature == DistortionModel.Feature.RelPosition)
						datum[datPtr++] = DistortionModel.getSlocBin((float) sIdx / (float) sourceWords.size());
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
		
		if(featureIndex.contains(DistortionModel.Feature.CurrentTag))
			tagIndex = new HashIndex<String>();
		if(featureIndex.contains(DistortionModel.Feature.Word)) {
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
				
				threadPool.execute(new ExtractionTask(sourceLine, alignLine, targetToks, currentLine));
			}
			
			System.out.printf("%s: Finished reading input (%d lines). Waiting for threads to terminate...\n", this.getClass().getName(), sourceReader.getLineNumber());
			
			sourceReader.close();
			alignReader.close();
			targetReader.close();
			
			threadPool.shutdown();
			threadPool.awaitTermination(1, TimeUnit.DAYS);
			
			System.out.printf("%s: Successful thread pool shutdown\n",this.getClass().getName());
			
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
