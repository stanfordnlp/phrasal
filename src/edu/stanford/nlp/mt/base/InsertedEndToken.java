package edu.stanford.nlp.mt.base;

/**
 * Wrap a sequence with an end symbol.
 * 
 * @author Spence Green
 *
 * @param <TK>
 */
public class InsertedEndToken<TK> extends AbstractSequence<TK> {
  final Sequence<TK> wrapped;
  final TK endToken;
  final int wrappedSz;

  public InsertedEndToken(Sequence<TK> wrapped, TK endToken) {
    this.wrapped = wrapped;
    this.endToken = endToken;
    this.wrappedSz = wrapped.size();
  }

  @Override
  public TK get(int i) {
    if (i < wrappedSz) {
      return wrapped.get(i);
    }
    if (i == wrappedSz) {
      return endToken;
    }

    throw new IndexOutOfBoundsException(String.format(
        "Index: %d Sequence Length: %s\n", i, size()));
  }

  @Override
  public int size() {
    return wrapped.size() + 1;
  }

  @Override
  public Sequence<TK> subsequence(int start, int end) {
    if (end == size()) {
      return super.subsequence(start, end);
    } else {
      return wrapped.subsequence(start - 1, end - 1);
    }
  }
}
