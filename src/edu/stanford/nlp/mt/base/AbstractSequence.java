package edu.stanford.nlp.mt.base;

import java.util.Iterator;

/**
 * 
 * @author danielcer
 *
 * @param <T>
 */
abstract public class AbstractSequence<T> implements Sequence<T> {		
	public Iterator<T> iterator() {
		return new AbstractSequenceIterator();
	}
	
	private class AbstractSequenceIterator implements Iterator<T> {
		int position = 0;
		
		public boolean hasNext() {
			return position < AbstractSequence.this.size();
		}

		public T next() {
			return AbstractSequence.this.get(position++);
		}

		public void remove() {
			throw new UnsupportedOperationException();			
		}
	}
	
	@Override
	public Sequence<T> subsequence(int start, int end) {
		return new AbstractSequenceWrapper(start, end);
	}
	
	@Override
	public String toString(String prefix, String delimeter) {
		StringBuilder sbuf = new StringBuilder(prefix);
		int sz = size();
		for (int i = 0; i < sz; i++) {
			if (i > 0) sbuf.append(delimeter);
			sbuf.append(get(i));
		}
		return sbuf.toString();
	}
	
	@Override
	public String toString(String prefix, String delimeter, String suffix) {
		StringBuffer sbuf = new StringBuffer(prefix);
		int sz = size();
		for (int i = 0; i < sz; i++) {
			if (i > 0) sbuf.append(delimeter);
			sbuf.append(get(i));
		}
		sbuf.append(suffix);
		return sbuf.toString();
	}
	
	@Override
	public String toString(String delimeter) {
		return toString("", delimeter);
	}
	
	@Override 
	public String toString() {
		return toString(" ");
	}
	
	@SuppressWarnings("unchecked")
	public Sequence<T> subsequence(CoverageSet select) {
		Object[] newElements = new Object[select.cardinality()];
		int sz = size();
		for (int i = 0, j = 0; i < sz; i++) {
			if (select.get(i)) newElements[j++] = get(i);
		}
		return new RawSequence<T>((T[])newElements);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public int compareTo(Sequence<T> o) {
		int sz = size();
		int osz = o.size();
		int max = Math.min(sz, osz);
		for (int i = 0; i < max; i++) {
			int cmp = ((Comparable)get(i)).compareTo(o.get(i));
			if (cmp == 0) continue;
			return cmp;
		}
		return sz-osz;
	}
	
	private class AbstractSequenceWrapper extends AbstractSequence<T> {
		private final int size, start;
		
		
		public AbstractSequenceWrapper(int start, int end) {
			size = end-start;
			this.start = start;
		}

		public T get(int i) {
			if (i >= size) {
				throw new ArrayIndexOutOfBoundsException("index: "+i+" size: "+size);
			}
			
			return AbstractSequence.this.get(start+i);
		}

		public int size() {
			return size;
		}

		
	}
	
	private long hashCode = Long.MAX_VALUE;
	
	public long longHashCode() {
		if (hashCode != Long.MAX_VALUE) {
			return hashCode;
		}
		
		hashCode = 0;
		long mul = 0x5DEECE66DL;
		long offset = 0xBL;
		int sz = size();
		
		// okay, this is slightly evil
		// but, previously we were spend alot of time in IString#hashCode()
		if (sz != 0 && get(0) instanceof IString) {
			for (int i = 0; i < sz; i++) {
				T token = get(i);
				hashCode = mul * hashCode + offset;
				hashCode += mul * ((IString) token).id + offset;
			}
		} else {
			for (int i = 0; i < sz; i++) {
				T token = get(i);
				hashCode = mul * hashCode + offset;
				hashCode += mul * token.hashCode() + offset;
			}
		}
		
		return hashCode;
	}
	
	@Override 
	public int hashCode() {
		return (int)(longHashCode());
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Sequence)) return false;
		Sequence seq = (Sequence)o;
		int size = this.size();
		if (size != seq.size()) return false;
		if (size != 0 && get(0) instanceof IString) {
			for (int i = 0; i < size; i++) {
				if (((IString) get(i)).id != ((IString)seq.get(i)).id) return false;
			}
		} else {
			for (int i = 0; i < size; i++) {
				if (!this.get(i).equals(seq.get(i))) return false;
			}
		}
		return true;
	}
	
	@Override
	public boolean contains(Sequence<T> subsequence) {
		int subSequenceSize = subsequence.size();
		if (subSequenceSize == 0) return true;
		int size = size();
		int iMax = size-subSequenceSize;
		for (int i = 0; i <= iMax; i++) {
			boolean match = true;
			for (int j = 0; j < subSequenceSize; j++) {
				if (!get(i+j).equals(subsequence.get(j))) {
					match = false;
					break;
				}
			}
			if (match) return true;
		}
		return false;
	}
	
	public boolean startsWith(Sequence<T> prefix) {
		int prefixSize = prefix.size();
		if (prefixSize > this.size()) return false;
		if (prefixSize != 0 && get(0) instanceof IString) {
			for (int i = 0; i < prefixSize; i++) {
				if (((IString) get(i)).id != ((IString)prefix.get(i)).id) return false;
			}
		} else {
			for (int i = 0; i < prefixSize; i++) {
				if (!this.get(i).equals(prefix.get(i))) return false;
			}
		}
		return true;
	}
	
	@SuppressWarnings("unchecked")
	public boolean noisyEquals(Object o) {
		if (!(o instanceof Sequence)) return false;
		Sequence<IString> seq = (Sequence<IString>)o;
		int size = this.size();
		System.err.printf("sizes: %d %d\n", size, seq.size());
		if (size != seq.size()) return false;
		if (size != 0 && get(0) instanceof IString) {
			for (int i = 0; i < size; i++) {
				System.err.printf("%s: %s vs. %s\n", i, ((IString)get(i)).id, seq.get(i).id);
				if (((IString) get(i)).id != (seq.get(i)).id) return false;
			}
		} else {
			for (int i = 0; i < size; i++) {
				if (!this.get(i).equals(seq.get(i))) return false;
			}
		}
		return true;
	}
}
