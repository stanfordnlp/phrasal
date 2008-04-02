package mt.base;

public class InsertedStartToken<TK> extends AbstractSequence<TK> {
	Sequence<TK> wrapped;
	TK startToken;
	public InsertedStartToken(Sequence<TK> wrapped, TK startToken) {
		this.wrapped = wrapped;
		this.startToken = startToken;
	}
	
	@Override
	public TK get(int i) {
		if (i == 0) return startToken;
		return wrapped.get(i-1);
	}

	@Override
	public int size() {
		return wrapped.size()+1;
	}
	
	@Override
	public Sequence<TK> subsequence(int start, int end) {
		if (start == 0) {
			return super.subsequence(start, end);
		} else {
			return wrapped.subsequence(start-1, end-1);
		}
	}	
	
}
