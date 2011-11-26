package edu.berkeley.nlp.lm.values;

import edu.berkeley.nlp.lm.collections.Indexer;
import edu.berkeley.nlp.lm.util.Annotations.PrintMemoryCount;

public final class CountValueContainer extends LmValueContainer<Long>
{

	private static final long serialVersionUID = 964277160049236607L;

	@PrintMemoryCount
	long[] countsForRank;

	public CountValueContainer(final Indexer<Long> countIndexer, final int valueRadix, final boolean storePrefixes) {
		super(countIndexer, valueRadix, storePrefixes);
	}

	@Override
	public CountValueContainer createFreshValues() {
		return new CountValueContainer(countIndexer, valueRadix, storePrefixIndexes);
	}

	public final long getCount(final int ngramOrder, final long index) {
		return getCount(ngramOrder, index, countsForRank);
	}

	/**
	 * @param ngramOrder
	 * @param index
	 * @param uncompressProbs2
	 */
	private long getCount(final int ngramOrder, final long index, final long[] array) {
		final int countIndex = (int) valueRanksCompressed[ngramOrder].get(index);
		return array[countIndex];
	}

	@Override
	protected Long getDefaultVal() {
		return -1L;
	}

	@Override
	protected void storeCounts() {
		countsForRank = new long[countIndexer.size()];
		int k = 0;
		for (final Long pair : countIndexer.getObjects()) {

			final int i = k;
			k++;

			countsForRank[i] = pair;
		}
	}

	@Override
	protected Long getCount(final int index, final int ngramOrder) {
		return countsForRank[index];
	}
}