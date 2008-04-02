package mt.base;

import edu.stanford.nlp.util.IndexInterface;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public class RawSequence<TK> extends AbstractSequence<TK> {
	public final Object[] elements;
	
	public RawSequence(TK[] elements) {
		this.elements = elements;
	}
	
	public RawSequence(Sequence<TK> sequence) {
		elements = new Object[sequence.size()];
		for (int i = 0; i < elements.length; i++) {
			elements[i] = sequence.get(i);
		}
	}

	public RawSequence(int[] intElements, IndexInterface<TK> index) {
		elements = new Object[intElements.length];
		for (int i = 0; i < intElements.length; i++) {
			elements[i] = index.get(intElements[i]);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public TK get(int i) {
		return (TK)elements[i];
	}

	@Override
	public int size() {
		return elements.length;
	}

	@Override
	public Sequence<TK> subsequence(int start, int end) {
		return new SimpleSequence<TK>(this, start, end);
	}
}
