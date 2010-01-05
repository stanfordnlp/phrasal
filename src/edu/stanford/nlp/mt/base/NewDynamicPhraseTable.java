package edu.stanford.nlp.mt.base;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

import tokyocabinet.*;

import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Pair;


public class NewDynamicPhraseTable extends AbstractPhraseGenerator<IString,String> {
	final BiText bitext;
	final int[] sortedindex;
	final IndexWrapper indexwrapper; 
	final IBMModel1 model1F2E;
	final IBMModel1 model1E2F;
	final BDB commonDB = new BDB();

	static final int phraseLengthLimit = 5;
	static final int MAX_ABSOLUTE_DISTORTION = 12;
	static final int MAX_RAW_OPTIONS = 10000;
	
	Set<String> currentSequence = null;
	
	public NewDynamicPhraseTable(IsolatedPhraseFeaturizer<IString, String> phraseFeaturizer, Scorer<String> scorer, BiText bitext, IBMModel1 model1F2E, IBMModel1 model1E2F) {
		super(phraseFeaturizer, scorer);
		this.bitext = bitext;
		this.model1F2E = model1F2E;
		this.model1E2F = model1E2F;
		Runtime rt = Runtime.getRuntime();
		System.gc();
		
		long premem = rt.totalMemory()-rt.freeMemory();
		
		commonDB.open(bitext.BiTextName, BDB.OCREAT | BDB.OWRITER | BDB.OREADER);
		
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
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			 public void run() {
				System.err.println("Closing DB");
				commonDB.sync(); commonDB.close(); // avoid locking on close issue	
			 }
		});
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
	
	class IndexWrapper extends AbstractList<FIndexSequence> implements RandomAccess  {

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
	
	public static void main(String[] args) throws Exception {
		if (args.length != 2 && args.length != 4) {
			System.err.printf("Usage:\n\tjava ...NewDynamicPhraseTable source.txt target.txt (source2target model1) (target2source model1)");
			System.exit(-1);
		}
		
		BiText btext = new BiText(args[0], args[1]);
		NewDynamicPhraseTable ndpt;
		if (args.length == 4) {
			ndpt = new NewDynamicPhraseTable(null, null, btext, IBMModel1.load(args[2]), IBMModel1.load(args[3]));
		} else {
			ndpt = new NewDynamicPhraseTable(null, null, btext, null, null);
		}
		
  	int pos = -1;
  	for (FIndexSequence seq : ndpt.indexwrapper) { pos++;
  		System.out.println(pos+"::: "+seq);
  	}
  	BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
  	for (String line = reader.readLine(); line != null; line = reader.readLine()) {
  		Sequence<IString> phrase = new RawSequence<IString>(IStrings.toIStringArray(line.split("\\s+")));
  		System.out.printf("Foreign Phrase: %s\n", phrase);
  		for (TranslationOption<IString> opt : ndpt.getTranslationOptions(phrase)) {
  			System.out.printf("--->%s Scores: %s\n", opt.translation, Arrays.toString(opt.scores));
  		}
  	}
	}

	@Override
	public String getName() {
		return "NewDynamicPhr";
	}

	@Override
	public List<TranslationOption<IString>> getTranslationOptions(
			Sequence<IString> sequence) {
		//System.err.printf("===Looking up: %s\n", sequence);
		List<TranslationOption<IString>> opts = new LinkedList<TranslationOption<IString>>();
		
		int idx = Collections.binarySearch(indexwrapper, sequence);
		if (idx < 0) {
			idx = -idx-1;
		}
		
		if (idx >= sortedindex.length) return opts;
		if (!Sequences.startsWith(indexwrapper.get(idx), sequence)) return opts;
		
		// roll back idx
		while (idx-1 > 0) {
			idx--;
			if (!Sequences.startsWith(indexwrapper.get(idx), sequence)) break;
		}	if (!Sequences.startsWith(indexwrapper.get(idx), sequence)) idx++;
		

		RawSequence<IString> rawSequence = new RawSequence<IString>(sequence);
		
		List<Pair<RawSequence<IString>,Double>> sortedTrans = null;
		double cntNormalizer = Double.NaN;
		
		@SuppressWarnings("unchecked")
		long startCacheCheckTime = System.currentTimeMillis();
		@SuppressWarnings("unchecked")
		List<String> commonTrans = (List<String>)commonDB.getlist(rawSequence.toString());
		long endCacheCheckTime = System.currentTimeMillis();
		System.err.printf("Cache Check time %.3f\n", (endCacheCheckTime-startCacheCheckTime)/1000.0);
		
		if (commonTrans == null) {
			long startTime = System.currentTimeMillis();
  		Counter<RawSequence<IString>> transSet = new ClassicCounter<RawSequence<IString>>();
  		
  		// extract phrases
  		for ( ; idx < sortedindex.length && Sequences.startsWith(indexwrapper.get(idx), sequence); idx++) {
  			int line = sortedindex[idx] >>> 8;
  			int pos = sortedindex[idx] & 0xFF;
  			int tEquivFStart = (int)((pos/(double)bitext.fcorpus[line].length)*bitext.ecorpus[line].length);
  			// System.err.printf("Range %d %d\n",  Math.max(0, tEquivFStart-MAX_ABSOLUTE_DISTORTION), Math.min(bitext.ecorpus[line].length, tEquivFStart+MAX_ABSOLUTE_DISTORTION));
  			for (int tStart = Math.max(0, tEquivFStart-MAX_ABSOLUTE_DISTORTION); 
        tStart < Math.min(bitext.ecorpus[line].length, tEquivFStart+MAX_ABSOLUTE_DISTORTION); tStart++) {
  				for (int tEnd = tStart; tEnd < bitext.ecorpus[line].length && tEnd < tStart + phraseLengthLimit; tEnd++) {
  					int[] ids = new int[tEnd-tStart+1];
  					for (int i = 0; i < ids.length; i++) {
  						ids[i] = bitext.ecorpus[line][tStart+i];
  					}
  					RawSequence<IString> transSeq = new RawSequence<IString>(IStrings.toIStringArray(ids));
  					//System.err.printf("-->%d:%s\n", tStart, transSeq);
  					transSet.incrementCount(transSeq);					
  				}
  			}
				//if (idx-startIdx > 10000) break;
  		}
  		
  		sortedTrans = Counters.toDescendingMagnitudeSortedListWithCounts(transSet);
  		
  		if (sortedTrans.size() > MAX_RAW_OPTIONS) {
  			sortedTrans = sortedTrans.subList(0, MAX_RAW_OPTIONS);
  		}
  		
  		cntNormalizer = transSet.totalCount();
  		
			long totalTime = System.currentTimeMillis()-startTime;
			if (cntNormalizer > 20000) {
				//ptCache.put(rawSequence, cacheEntry);
				for (Pair<RawSequence<IString>, Double> trans : sortedTrans) {
					commonDB.putdup(rawSequence.toString(), trans.first.toString() + "|||" + trans.second/transSet.totalCount());
					//System.err.printf("%s=>%s %f\n", rawSequence.toString(), trans.first.toString(), trans.second);
				}
				System.err.printf("Cache miss! %.3f sec - ", totalTime/1000.0);
			} else {
				System.err.printf("Excluded from Cache! %.3f sec - ", totalTime/1000.0);
			}
		} else {			
			sortedTrans = new ArrayList<Pair<RawSequence<IString>,Double>>(commonTrans.size()); 	
			for (String transStr : commonTrans) {
			
				String[] fields = new String[2];
				int lastbar = transStr.lastIndexOf("|");
				fields[0] = transStr.substring(0,lastbar-2);
				fields[1] = transStr.substring(lastbar+1, transStr.length());
				StringTokenizer strToker = new StringTokenizer(fields[0]);
				String[] words = new String[strToker.countTokens()];
				for (int ti = 0; ti < words.length; ti++) {
					words[ti] = strToker.nextToken();
				}
				sortedTrans.add(new Pair<RawSequence<IString>, Double>(new RawSequence<IString>(IStrings.toIStringArray(words)), new Double(fields[1])));
			}
			cntNormalizer = 1.0;
			long endCacheTotalTime = System.currentTimeMillis();
			System.err.printf("Cache hit! %.3f sec - ", (endCacheTotalTime-startCacheCheckTime)/1000.0);
		}

		System.err.printf("%s %f\n", sequence, cntNormalizer);	

		for (Pair<RawSequence<IString>, Double> entry : sortedTrans) {
			RawSequence<IString> transSeq = entry.first;
			float PcEgF = (float)(Math.log(entry.second/cntNormalizer));
			String mappingKey = sequence+"=:=>"+transSeq.toString();
			if (model1F2E != null & model1E2F != null) {
				float pLexF2E = (float)model1F2E.score(rawSequence, transSeq);
				float pLexE2F = (float)model1E2F.score(transSeq, rawSequence);
				if (currentSequence == null) {
					opts.add(new TranslationOption<IString>(
						new float[]{(float)1.0, PcEgF, pLexF2E, pLexE2F},
					  new String[]{"PhrPen", "PcEgF", "pLexF2E", "pLexE2F"},
						transSeq,
						rawSequence,										
						null,
						false));
				} else if (currentSequence.contains(mappingKey)) {
					opts.add(new TranslationOption<IString>(
							new float[]{(float)1.0, PcEgF, pLexF2E, pLexE2F},
						  new String[]{"PhrPen", "PcEgF", "pLexF2E", "pLexE2F"},
							transSeq,
							rawSequence,										
							null,
							true));
				}
				//System.err.printf("%s=>%s\n", rawSequence, transSeq);
			} else {
				if (currentSequence == null) {
					opts.add(new TranslationOption<IString>(
							new float[]{(float)1.0, PcEgF},
							new String[]{"PhrPen", "PcEgF"},
							transSeq,
							rawSequence,										
							null,
							false));
				} else if (currentSequence.contains(mappingKey)) {
					opts.add(new TranslationOption<IString>(
							new float[]{(float)1.0, PcEgF},
							new String[]{"PhrPen", "PcEgF"},
							transSeq,
							rawSequence,										
							null,
							true));
				}
			}
		}
		
		
		//System.out.printf("search loc: %d\n", idx);
		
		return opts;
	}

	@Override
	public int longestForeignPhrase() {		
		return phraseLengthLimit;
	}

	@Override
	public void setCurrentSequence(Sequence<IString> foreign,
			List<Sequence<IString>> tranList) {
		
		if (tranList == null) {
			currentSequence = null;
			return;
		}
		currentSequence = new HashSet<String>();
		int pairSpecificPhrases = 0;
		for (Sequence<IString> trans : tranList) {
		for (int fStart = 0; fStart < foreign.size(); fStart++) {
		for (int fEnd = fStart; fEnd < foreign.size() && fEnd < fStart + phraseLengthLimit; fEnd++) {
		Sequence<IString> fPhrase = foreign.subsequence(fStart, fEnd+1);
		int phraseSpecificTranslations = 0;

		int tEquivFStart = (int)((fStart/(double)foreign.size())*trans.size());	
		for (int tStart = Math.max(0, tEquivFStart-MAX_ABSOLUTE_DISTORTION); 
             tStart < Math.min(trans.size(), tEquivFStart+MAX_ABSOLUTE_DISTORTION); 
						 tStart++) {
		for (int tEnd = tStart; tEnd < trans.size() && tEnd < tStart + phraseLengthLimit; tEnd++) {
				
			Sequence<IString> tPhrase = trans.subsequence(tStart, tEnd+1);
			String featRep = fPhrase+"=:=>"+tPhrase;
			currentSequence.add(featRep);
			// System.err.printf("putting '%s=:=>%s'\n", fPhrase, tPhrase);
			pairSpecificPhrases++;
			phraseSpecificTranslations++;
		}
		}
		}				
		}
		}		
	}
	
}
