package mt.metrics;

import java.io.*;
import java.util.*;

import mt.base.IString;
import mt.base.IStrings;
import mt.base.RawSequence;
import mt.base.Sequence;


/**
 *
 * @author danielcer
 *
 */
public class Metrics {
	private Metrics() { }

	/**
	 * note: future plans for javanlp Count will make this method irrelevant.
	 *
	 * @param <TK>
	 * @param counts
	 * @param sequence
	 */
	static private <TK> void IncCount(Map<Sequence<TK>, Integer> counts, Sequence<TK> sequence) {
		Integer cnt = counts.get(sequence);
		if (cnt == null) {
			counts.put(sequence, 1);
		} else {
			counts.put(sequence, cnt+1);
		}
	}

	/**
	 *
	 * @param <TK>
	 * @param sequence
	 * @param maxOrder
	 * @return
	 */
	static public <TK> Map<Sequence<TK>, Integer> getNGramCounts(Sequence<TK> sequence, int maxOrder) {
		Map<Sequence<TK>, Integer> counts = new HashMap<Sequence<TK>, Integer>();

		int sz = sequence.size();
		for (int i = 0; i < sz; i++) {
			int jMax = Math.min(sz, i + maxOrder);
			for (int j = i+1; j <= jMax; j++) {
				Sequence<TK> ngram = sequence.subsequence(i, j);
				IncCount(counts, ngram);
			}
		}
		return counts;
	}

	/**
	 *
	 * @param <TK>
	 * @param sequences
	 * @param maxOrder
	 * @return
	 */
	static public <TK> Map<Sequence<TK>, Integer> getMaxNGramCounts(List<Sequence<TK>> sequences, int maxOrder) {
		Map<Sequence<TK>, Integer> maxCounts = new HashMap<Sequence<TK>, Integer>();

		for (Sequence<TK> sequence : sequences) {
			Map<Sequence<TK>, Integer> counts = getNGramCounts(sequence, maxOrder);
			for (Sequence<TK> key : counts.keySet()) {
				Integer countValue = counts.get(key);
				Integer maxCountValue = maxCounts.get(key);
				if (maxCountValue == null || maxCountValue.compareTo(countValue) < 0) {
					maxCounts.put(key, countValue);
				}
			}
		}
		return maxCounts;
	}

	static <TK> void clipCounts(Map<Sequence<TK>, Integer> counts, Map<Sequence<TK>, Integer> maxRefCount) {
		for (Sequence<TK> ngram : new HashSet<Sequence<TK>>(counts.keySet())) {
			Integer cnt = maxRefCount.get(ngram);
			if (cnt == null) {
				counts.remove(ngram);
				continue;
			}
			Integer altCnt = counts.get(ngram);
			if (cnt.compareTo(altCnt) < 0) {
				counts.put(ngram, cnt);
			}
			// System.err.printf("clipped count: %s Cnt: %d Orig: %d\n", ngram, counts.get(ngram), altCnt);
		}
	}
	static public List<List<Sequence<IString>>> readReferences(String[] referenceFilenames) throws IOException {
		List<List<Sequence<IString>>> referencesList = new ArrayList<List<Sequence<IString>>>();
		for (String referenceFilename : referenceFilenames) {
			LineNumberReader reader = new LineNumberReader(new FileReader(referenceFilename));
			for (String line; (line = reader.readLine()) != null; ) {
				int lineNumber = reader.getLineNumber();
				if (referencesList.size() < lineNumber) {
					List<Sequence<IString>> list = new ArrayList<Sequence<IString>>(referenceFilenames.length);
					line = line.replaceAll("\\s+$", "");
					line = line.replaceAll("^\\s+", "");
					list.add(new RawSequence<IString>(IStrings.toIStringArray(line.split("\\s+"))));
					referencesList.add(list);
				} else {
					referencesList.get(lineNumber-1).add(new RawSequence<IString>(IStrings.toIStringArray(line.split("\\s+"))));
				}
			}
			reader.close();
		}
		return referencesList;
	}
}
