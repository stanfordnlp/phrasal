package edu.berkeley.nlp.lm.util.hash;

/**
 * Taken from http://d3s.mff.cuni.cz/~holub/sw/javamurmurhash/MurmurHash.java
 * 
 */
public final class MurmurHash implements HashFunction
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public long hash(final int[] data, final int startPos, final int endPos, final int prime) {
		final int hash32 = hash32(data, startPos, endPos, prime);
		return hash32;
	}

	/**
	 * Generates 32 bit hash from byte array of the given length and seed.
	 * 
	 * @param data
	 *            byte array to hash
	 * @param length
	 *            length of the array to hash
	 * @param seed
	 *            initial seed value
	 * @param prime
	 * @return 32 bit hash of the given array
	 */
	public static int hash32(final int[] data, final int startPos, final int endPos, final int seed) {
		// 'm' and 'r' are mixing constants generated offline.
		// They're not really 'magic', they just happen to work well.
		final int m = 0x5bd1e995;
		final int r = 24;
		final int length = endPos - startPos;
		// Initialize the hash to a random value
		int h = seed ^ length;

		for (int i = startPos; i < endPos; i++) {
			//			final int i4 = i * 4;
			int k = data[i];// (data[i4 + 0] & 0xff) + ((data[i4 + 1] & 0xff) << 8) + ((data[i4 + 2] & 0xff) << 16) + ((data[i4 + 3] & 0xff) << 24);
			k *= m;
			k ^= k >>> r;
			k *= m;
			h *= m;
			h ^= k;
		}

		h *= m;

		h ^= h >>> 13;
		h *= m;
		h ^= h >>> 15;

		return h;
	}

	public static long hashOneLong(final long k_, final int seed) {
		long k = k_;
		final long m = 0xc6a4a7935bd1e995L;
		final int r = 47;

		long h = (seed & 0xffffffffl) ^ (1 * m);

		k *= m;
		k ^= k >>> r;
		k *= m;

		h ^= k;
		h *= m;

		h ^= h >>> r;
		h *= m;
		h ^= h >>> r;

		return h;
	}

}