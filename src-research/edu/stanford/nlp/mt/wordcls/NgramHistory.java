package edu.stanford.nlp.mt.wordcls;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.mt.base.DynamicIntegerArrayIndex;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.IntegerArrayIndex;

/**
 * An (semi!)-efficient implementation of n-gram history storage.
 * 
 * @author Spence Green
 *
 */
public class NgramHistory implements Iterable<IString> {
  
  private static final IntegerArrayIndex index = new DynamicIntegerArrayIndex();
  
  private final int id;
  private final int hashCode;
  
  public NgramHistory(List<IString> hist) {
    IString[] history = new IString[hist.size()];
    int i = 0;
    for (IString s : hist) {
      history[i++] = s;
    }
    int[] h = IStrings.toIntArray(history);
    id = index.indexOf(h, true);
    hashCode = Arrays.hashCode(h);
  }

  public static void lockIndex() { index.lock(); }
  
  @Override
  public Iterator<IString> iterator() {
    return new HistoryIterator();
  }
  
  private class HistoryIterator implements Iterator<IString> {
    private int i = 0;
    private final IString[] history;
    public HistoryIterator() {
      history = IStrings.toIStringArray(index.get(id));
    }
    
    @Override
    public boolean hasNext() {
      return i < history.length-1;
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
    IString[] history = IStrings.toIStringArray(index.get(id));
    for (IString t : history) {
      if (sb.length() > 0) sb.append(" ");
      sb.append(t.toString());
    }
    return sb.toString();
  }
  
  @Override
  public int hashCode() {
    return hashCode;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if ( ! (o instanceof NgramHistory)) {
      return false;
    } else {
      NgramHistory other = (NgramHistory) o;
      return this.id == other.id;
    }
  }
}
