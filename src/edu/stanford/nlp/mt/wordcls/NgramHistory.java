package edu.stanford.nlp.mt.wordcls;

import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.mt.base.HasIntegerIdentity;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.IntegerArrayIndex;
import edu.stanford.nlp.mt.base.TrieIntegerArrayIndex;

/**
 * An (semi!)-efficient implementation of n-gram history storage.
 * 
 * @author Spence Green
 *
 */
public class NgramHistory implements Iterable<IString>,HasIntegerIdentity {
  
  // This data structure is threadsafe
  private static final IntegerArrayIndex index = new TrieIntegerArrayIndex();
  
  private final int id;
  
  /**
   * Constructor.
   * 
   * @param hist
   */
  public NgramHistory(List<IString> hist) {
    int[] intArray = new int[hist.size()];
    for (int i = 0; i < intArray.length; i++) {
      intArray[i] = hist.get(i).id;
    }
    id = index.indexOf(intArray, true);
  }

  @Override
  public int getId() { return id; }
  
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
    return id;
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
