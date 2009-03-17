package mt.base;

import java.util.*;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;

public class NewDynamicPhraseTable {
	final BiText bitext;
	final int[] sortedindex;
	final IndexWrapper indexwrapper; 
	
	public NewDynamicPhraseTable(BiText bitext) {
		this.bitext = bitext;
		Runtime rt = Runtime.getRuntime();
		System.gc();
		
		long premem = rt.totalMemory()-rt.freeMemory();
		
		sortedindex = new int[bitext.sourceWordCount];
		
		long postmem = rt.totalMemory()-rt.freeMemory();
		System.err.printf("Memory used by index: %d MiB (positions: %d)\n", (postmem-premem)/(1024*1024), sortedindex.length);
		
		System.err.printf("Loading index....\n");
		int pos = 0;
		for (int i = 0; i < bitext.fcorpus.length; i++) {
			for (int j = 0; j < bitext.fcorpus[i].length; j++) {
				if (j > 255) throw new RuntimeException(); 
				sortedindex[pos++] = (i<<8) + j;
//				System.out.print(bitext.fcorpus[i][j]+" ");				
			}
//			System.out.println("\n");
		}
		indexwrapper = new IndexWrapper();
		
		System.err.printf("Sorting index....\n");
		Collections.sort(indexwrapper);
	}
	
	class FIndexSequence extends AbstractSequence<IString> {
		int fidx;
		
		public FIndexSequence(int fidx) {
			this.fidx = fidx;
		}
		
		@Override
		public IString get(int i) {			
			//System.out.printf("--idx: %d line: %d offset: %d\n", fidx, fidx>>>8, fidx&0xFF);
			//System.out.printf("pos: %d\n", i);
			//System.out.printf("istring id: %d\n", bitext.fcorpus[fidx>>>8][(fidx&0xFF)+i]);
			return new IString(bitext.fcorpus[fidx>>>8][(fidx&0xFF)+i]);
		}

		@Override
		public int size() {
			return bitext.fcorpus[fidx>>>8].length - fidx&0xFF;
		}
		
	}
	
	class IndexWrapper extends AbstractList<FIndexSequence> {

		@Override
		public FIndexSequence get(int idx) {
			//System.out.printf("idx: %d val: %d line: %d offset: %d\n", idx, sortedindex[idx], sortedindex[idx]>>>8, sortedindex[idx]&0xFF);
			return new FIndexSequence(sortedindex[idx]);
		}

		@Override
		public int size() {
			return sortedindex.length;
		}
		
		@Override
		public FIndexSequence set(int index, FIndexSequence newseq) {
			FIndexSequence old = get(index);
			sortedindex[index] = newseq.fidx;
			return old;
		}
	}
	
	public static void main(String[] args) {
		BiText btext = new BiText(args[0], args[1]);
		NewDynamicPhraseTable ndpt = new NewDynamicPhraseTable(btext);
		for (FIndexSequence seq : ndpt.indexwrapper) {
			System.out.println(seq);
		}
	}
	
}
