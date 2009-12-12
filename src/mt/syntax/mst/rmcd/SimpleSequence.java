package mt.syntax.mst.rmcd;

import edu.stanford.nlp.util.Index;

/**
 * 
 * @author danielcer
 *
 * @param <T>
 */
public class SimpleSequence<T> extends AbstractSequence<T> {
	private final Object[] elements;
	private final int start, end;	
	
	/**
	 * 
	 */
	public SimpleSequence(boolean wrapDontCopy, T... elements) {
		if (wrapDontCopy) {
			this.elements = elements;
		} else {
			this.elements = elements.clone();
		}
		start = 0; 
		end = elements.length;
	}
	
	/**
	 * 
	 */
	public SimpleSequence(int[] intElements, Index<T> index) {
		elements = new Object[intElements.length];
		for (int i = 0; i < intElements.length; i++) {
			elements[i] = index.get(intElements[i]);
		}
		start = 0;
		end = intElements.length;
	}
	
	private SimpleSequence(SimpleSequence<T> sequence, int start, int end) {
		this.elements = sequence.elements;
		int oldLen = sequence.size();
		if (start > end || end > oldLen) {
			throw new IndexOutOfBoundsException(
			   String.format("length: %d start index: %d end index: %d\n", 
					   oldLen, start, end));
		}
		this.start = sequence.start + start; 
		this.end = sequence.start + end;		
	}
	
	@Override
	public Sequence<T> subsequence(int start, int end) {
		return new SimpleSequence<T>(this, start, end);
	}
	
	@SuppressWarnings("unchecked")
	public T get(int i) {
		int idx = i + start;
		if (idx >= end) {
			throw new IndexOutOfBoundsException(
					String.format("length: %d index: %d\n", size(), i));
		}
			
		return (T)elements[idx];
	}

	public int size() {
		return end-start;
	}
}
