package mt.base;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;


import static java.lang.System.*;

/**
 * 
 * @author danielcer
 *
 */
public class IBMModel1 {
	final DynamicIntegerArrayIndex foreignIndex = new DynamicIntegerArrayIndex();
	final double scores[];
	final double epsilon = 1.0;
	final IString NULL_TOKEN = new IString("<<<NULL>>>");
	final double UNKNOWN_SCORE = 1e-7;
	
	static WeakHashMap<String, IBMModel1> model1Store = new WeakHashMap<String, IBMModel1>();
	
	
	public static IBMModel1  load(String filename) throws IOException {
		File f = new File(filename);
		if (model1Store.get(f.getAbsolutePath()) != null) {
			return model1Store.get(f.getAbsolutePath());
		}
		
		IBMModel1 m1 = new IBMModel1(filename);
		model1Store.put(f.getAbsolutePath(), m1);
		return m1;
	}
	
	public static IBMModel1 reload(String filename) throws IOException {
		File f = new File(filename);
		IBMModel1 m1 = new IBMModel1(filename);
		model1Store.put(f.getAbsolutePath(), m1);
		return m1;
	}
	
	private IBMModel1(String filename) throws IOException {
		LineNumberReader reader;
		
		if (filename.endsWith(".gz")) { 
			reader = new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename))));
		} else {
			reader = new LineNumberReader(new FileReader(filename));
		}
		List<Double> scores = new ArrayList<Double>();
		
		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			String[] fields = line.split("\t");
			if (fields.length != 3) {
				throw new RuntimeException(String.format("Only %d fields found on line %d, expected 3\n", fields.length, reader.getLineNumber()));
			}
			IString sourceWord = new IString(fields[0]); IString targetWord = new IString(fields[1]);
			double p;
			try {
				p = Double.parseDouble(fields[2]);
			} catch (NumberFormatException e) {
				throw new RuntimeException(String.format("Can't parse %s as a number on line %d\n", fields[2], reader.getLineNumber()));
			}
			int[] wordPair = new int[2]; wordPair[0] = sourceWord.id; wordPair[1] = targetWord.id;
			int idx = foreignIndex.indexOf(wordPair, true);
			if (scores.size() != idx) {
				throw new RuntimeException(String.format("Index error, likely cause : duplicate entries for %s=>%s\n",sourceWord,targetWord));
			}
			scores.add(p);
		}
		this.scores = new double[scores.size()];
		for (int i = 0; i < this.scores.length; i++) {
			this.scores[i] = scores.get(i);
		}
		
		err.printf("Done loading %s\n", filename);
	}
	
	public double score(IString sourceToken, IString targetToken) {
		if (sourceToken.id == targetToken.id) return 1.0; // XXX experimental hack for unknown words 
		int[] pair = new int[2];
		pair[0] = sourceToken.id; pair[1] = targetToken.id;
		int idx = foreignIndex.indexOf(pair);
		if (idx < 0) return UNKNOWN_SCORE;
		return scores[idx];
	}
	
	public double score(Sequence<IString> source, Sequence<IString> target) {
		return score(source,target, false);		
	}
	
	public double scoreTMOnly(Sequence<IString> source, Sequence<IString> target) {
		return score(source, target, true);
	}
	
	private double score(Sequence<IString> source, Sequence<IString> target, boolean TMOnly) {
	    int l = source.size();
		int m = target.size();
		double lengthScore = Math.log(epsilon);
		double singleAlignmentScore = -m*Math.log(l+1);
		double transScore = 0;
		for (int j = 0; j < m; j++) {
			double sumJ = score(NULL_TOKEN, target.get(j)); 
			for (int i = 0; i < l; i++) {
				sumJ += score(source.get(i), target.get(j));
			}
			transScore += Math.log(sumJ);
		}
		/*err.printf("s.sas: %e\n", singleAlignmentScore);
		err.printf("s.transScore: %e\n", transScore); */
		if (TMOnly) {
			return transScore;
		} else {
			return lengthScore+singleAlignmentScore+transScore;
		}
	}
	
	public PartialTargetFeatureState partialTargetFeatureState(Sequence<IString> source) {
		return new PartialTargetFeatureState(source);
	}
	
	public class PartialTargetFeatureState {
		private final Sequence<IString> source;
		private final int targetSz;
		private final double partialTransScore;
		
		public PartialTargetFeatureState(Sequence<IString> source) {
			partialTransScore = 0;
			targetSz = 0;
			this.source = source; 
		}
		
		private PartialTargetFeatureState(double partialTransScore, Sequence<IString> source, int targetSz) {
			this.partialTransScore = partialTransScore;
			this.source = source;
			this.targetSz = targetSz;
		}
		
		public double scoreTMOnly() {
			return partialTransScore;
		}
		
		public double score() {
			double lengthScore = Math.log(epsilon);
			double singleAlignmentScore = -targetSz*Math.log(source.size()+1);
			return lengthScore+singleAlignmentScore+partialTransScore;
		}
	
		public PartialTargetFeatureState appendTargetWord(IString targetWord) {
			double sumJ = IBMModel1.this.score(NULL_TOKEN, targetWord);
			int l = source.size();
			for (int i = 0; i < l; i++) {
				sumJ += IBMModel1.this.score(source.get(i), targetWord);
			}
			return new PartialTargetFeatureState(partialTransScore + Math.log(sumJ), source, targetSz+1);
		}
		
		public PartialTargetFeatureState appendPhrasePrecompute(PhrasePrecomputePTarget pppt) {
			return new PartialTargetFeatureState(partialTransScore + pppt.logSum, source, targetSz+pppt.targetSz);
		}
	}
	
	public PhrasePrecomputePTarget phrasePrecomputePTarget(Sequence<IString> targetPhrase, Sequence<IString> source) {
		int l = source.size();
		
		int m = targetPhrase.size();
		double transScore = 0;
		for (int j = 0; j < m; j++) {
			double sumJ = score(NULL_TOKEN, targetPhrase.get(j)); 
			for (int i = 0; i < l; i++) {
				sumJ += score(source.get(i), targetPhrase.get(j));
			}
			transScore += Math.log(sumJ);
		}
		return new PhrasePrecomputePTarget(transScore, m);
	}
	
	public class PhrasePrecomputePTarget {
		final double logSum;
		final int targetSz;
		public PhrasePrecomputePTarget(double logSum, int targetSz) {
			this.logSum = logSum;
			this.targetSz = targetSz;
		}
	}
	
	public PartialSourceFeatureState partialSourceFeatureState(Sequence<IString> target) {
		return new PartialSourceFeatureState(target);
	}
	

	/**
	 * 
	 * @author danielcer
	 *
	 */
	public class PartialSourceFeatureState {
		private final int sourceSz;
		private final double[] targetSums;
		private final Sequence<IString> target;
		
		public PartialSourceFeatureState(Sequence<IString> target) {
			targetSums = new double[target.size()];
			sourceSz = 0;
			this.target = target;
			for (int i = 0; i < targetSums.length; i++) {
				targetSums[i] = IBMModel1.this.score(NULL_TOKEN, target.get(i));
			}
		}
		
		private PartialSourceFeatureState(double[] targetSums, Sequence<IString> target, int sourceSz) {
			this.targetSums = targetSums;
			this.target = target;
			this.sourceSz = sourceSz;
		}
		
		public double score() {
			double lengthScore = Math.log(epsilon);
			double singleAlignmentScore = -targetSums.length*Math.log(sourceSz+1);
			double transScore = 0;
			for (int j = 0; j < targetSums.length; j++) {
				transScore += Math.log(targetSums[j]);
			}
			/*err.printf("ps.sas: %e\n", singleAlignmentScore);
			err.printf("ps.transScore: %e\n", transScore); */
			return lengthScore+singleAlignmentScore+transScore;
		}
		
		public PartialSourceFeatureState appendSourceWord(IString sourceWord) {
			PartialSourceFeatureState pfs = new PartialSourceFeatureState(new double[targetSums.length], target, sourceSz+1);
			for (int i = 0; i < targetSums.length; i++) {
				pfs.targetSums[i] = targetSums[i] + IBMModel1.this.score(sourceWord, target.get(i));
			}
			return pfs;
		}
	}
	

	static public void main(String[] args) throws IOException {
		if (args.length != 1) {
			err.printf("Usage:\n\tjava IBMModel1 (model.actual.t1) < file_with_sentence_pairs\n");
			exit(-1);
		}
		
		IBMModel1 model1 = new IBMModel1(args[0]);
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		for (String sourceStr = reader.readLine(); sourceStr != null; sourceStr = reader.readLine()) {
			String targetStr = reader.readLine();
			if (targetStr == null) break;
			Sequence<IString> sourceSeq = new SimpleSequence<IString>(IStrings.toIStringArray(sourceStr.split("\\s+")));
			Sequence<IString> targetSeq = new SimpleSequence<IString>(IStrings.toIStringArray(targetStr.split("\\s+")));
			out.printf("p(%s=>%s)\n", sourceSeq, targetSeq);
			if (sourceSeq.size() == 1 && targetSeq.size() == 1) {
				out.printf("%e (token score %e)\n", model1.score(sourceSeq, targetSeq), model1.score(sourceSeq.get(0), targetSeq.get(0)));
			} else {
				double l = sourceSeq.size();
				double m = targetSeq.size();
				double singleAlignmentScore = -m*Math.log(l+1);		
				out.printf("%e (single alignment factor: %e)\n", model1.score(sourceSeq, targetSeq), singleAlignmentScore);
				out.printf("Incremental Target Scores:\n");
				PartialTargetFeatureState ptfs = model1.partialTargetFeatureState(sourceSeq);
				out.printf("\t() => %e\n", ptfs.score());
				for (int i = 0; i < targetSeq.size(); i++) {
					ptfs = ptfs.appendTargetWord(targetSeq.get(i));
					out.printf("\t%s => %e (%e)\n", targetSeq.subsequence(0, i+1), ptfs.score(), model1.score(sourceSeq, targetSeq.subsequence(0, i+1)));
				}
				
				out.printf("Incremental Source Scores:\n");
				PartialSourceFeatureState psfs = model1.partialSourceFeatureState(targetSeq);
				out.printf("\t() => %e\n", psfs.score());
				for (int i = 0; i < sourceSeq.size(); i++) {
					psfs = psfs.appendSourceWord(sourceSeq.get(i));
					out.printf("\t%s => %e (%e)\n", sourceSeq.subsequence(0, i+1), psfs.score(), model1.score(sourceSeq.subsequence(0, i+1), targetSeq));
				}
			}
		}
	}
	
}
