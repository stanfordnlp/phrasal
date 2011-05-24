package edu.berkeley.nlp.lm.values;

public class ProbBackoffPair implements Comparable<ProbBackoffPair>
{

	static final long MANTISSA_MASK = 0x000fffffffffffffL;

	static final long REST_MASK = ~0x000fffffffffffffL;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Float.floatToIntBits(prob);
		result = prime * result + Float.floatToIntBits(backoff);
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		final ProbBackoffPair other = (ProbBackoffPair) obj;
		if (Float.floatToIntBits(prob) != Float.floatToIntBits(other.prob)) return false;
		if (Float.floatToIntBits(backoff) != Float.floatToIntBits(other.backoff)) return false;
		return true;
	}

	public ProbBackoffPair(final float logProb, final float backoff) {
		this.prob = round(logProb, 12);
		this.backoff = round(backoff, 12);
	}

	private float round(final float f, final int mantissaBits) {
		final long bits = Double.doubleToLongBits(f);

		final long mantissa = bits & MANTISSA_MASK;
		final long rest = bits & REST_MASK;
		final long highestBit = Long.highestOneBit(mantissa);
		long mask = highestBit;
		for (int i = 0; i < mantissaBits; ++i) {
			mask >>>= 1;
			mask |= highestBit;
		}
		final long maskedMantissa = mantissa & mask;
		final double newDouble = Double.longBitsToDouble(rest | maskedMantissa);
		return (float) newDouble;
	}

	@Override
	public String toString() {
		return "[FloatPair first=" + prob + ", second=" + backoff + "]";
	}

	public float prob;

	public float backoff;

	@Override
	public int compareTo(final ProbBackoffPair arg0) {
		final int c = Float.compare(prob, arg0.prob);
		if (c != 0) return c;
		return Float.compare(backoff, arg0.backoff);
	}
}