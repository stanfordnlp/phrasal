package edu.stanford.nlp.mt.mkcls;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.mt.base.IString;

/**
 * TODO(spenceg): Cache these histories internally.
 * 
 * @author rayder441
 *
 */
public class NgramHistory implements Iterable<IString> {
  private final IString[] history;
  
  public NgramHistory(List<IString> hist) {
    history = new IString[hist.size()];
    int i = 0;
    for (IString s : hist) {
      history[i++] = s;
    }
  }
  
  @Override
  public Iterator<IString> iterator() {
    return new HistoryIterator();
  }
  
  private class HistoryIterator implements Iterator<IString> {
    private int i = 0;
    private int hLen = history.length;
    
    @Override
    public boolean hasNext() {
      return i < hLen-1;
    }

    @Override
    public IString next() {
      return history[i++];
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (IString t : history) {
      if (sb.length() > 0) sb.append(" ");
      sb.append(t.toString());
    }
    return toString();
  }
  
  @Override
  public int hashCode() {
    return history.hashCode();
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if ( ! (o instanceof NgramHistory)) {
      return false;
    } else {
      NgramHistory other = (NgramHistory) o;
      return Arrays.equals(this.history, other.history);
    }
  }
}
