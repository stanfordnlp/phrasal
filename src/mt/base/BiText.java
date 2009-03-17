package mt.base;

import java.io.BufferedReader;
import java.io.FileReader;

import edu.stanford.nlp.util.IStrings;

public class BiText {
	final int[][] ecorpus;
	final int[][] fcorpus;
	final int sourceWordCount;
	
	private int[] wc(String filename) {
		try {
		int lineCnt = 0;
		int wordCnt = 0;
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			lineCnt++;
			wordCnt += line.split("\\s+").length;			
		}
		reader.close();
		return new int[]{lineCnt, wordCnt};
		} catch (Exception e) { throw new RuntimeException(); }
	}
	
	private void readCorpus(String filename, int[][] store) {
		try {
			BufferedReader reader = new BufferedReader(new FileReader(filename));
			int pos = -1;
			for (String line = reader.readLine(); line != null; line = reader.readLine()) { pos++;
				store[pos] = IStrings.toIntArray(IStrings.toIStringArray(line.split("\\s+")));
			}
			reader.close();
		} catch (Exception e) { throw new RuntimeException(); }
	}
	
	public BiText(String filenameF, String filenameE) {
		int[] fSizes = wc(filenameF);
		int[] eSizes = wc(filenameE);
		if (fSizes[0] != eSizes[0]) {
			throw new RuntimeException(String.format("Bitext line counts don't match. %d != %d", fSizes[0], eSizes[0]));
		}
		ecorpus = new int[eSizes[0]][];
		fcorpus = new int[fSizes[0]][];
		
		Runtime rt = Runtime.getRuntime();
		System.gc();
		
		long premem = rt.totalMemory()-rt.freeMemory();
		System.err.printf("Reading Corpus: %s\n", filenameE);
		readCorpus(filenameE, ecorpus);
		long postmem = rt.totalMemory()-rt.freeMemory();
		System.err.printf("Memory used: %d MiB\n", (postmem-premem)/(1024*1024));
		System.err.printf("Reading Corpus: %s\n", filenameF);
		readCorpus(filenameF, fcorpus);
		postmem = rt.totalMemory()-rt.freeMemory();
		System.err.printf("Memory used: %d MiB\n", (postmem-premem)/(1024*1024));
		sourceWordCount = fSizes[1];
	}
	
	public static void main(String[] args) {
		BiText btext = new BiText(args[0], args[1]);
	}
}
