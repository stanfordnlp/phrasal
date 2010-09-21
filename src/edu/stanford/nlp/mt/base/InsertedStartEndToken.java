package edu.stanford.nlp.mt.base;

public class InsertedStartEndToken<TK> extends AbstractSequence<TK> {
  final Sequence<TK> wrapped;
  final TK startToken;
  final TK endToken;
  final int wrappedSz;

  public InsertedStartEndToken(Sequence<TK> wrapped, TK startToken, TK endToken) {
    this.wrapped = wrapped;
    this.startToken = startToken;
    this.endToken = endToken;
    this.wrappedSz = wrapped.size();
  }

  @Override
  public TK get(int i) {
    if (i == 0) {
      return startToken;
    }
    if (i < wrappedSz + 1) {
      return wrapped.get(i - 1);
    }
    if (i == wrappedSz + 1) {
      return endToken;
    }

    throw new IndexOutOfBoundsException(String.format(
        "Index: %d Sequence Length: %s\n", i, size()));
  }

  @Override
  public int size() {
    return wrapped.size() + 2;
  }

  @Override
  public Sequence<TK> subsequence(int start, int end) {
    if (start == 0 || end == size()) {
      return super.subsequence(start, end);
    } else {
      return wrapped.subsequence(start - 1, end - 1);
    }
  }
}
