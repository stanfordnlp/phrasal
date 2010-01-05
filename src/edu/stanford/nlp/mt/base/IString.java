package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.OAIndex;

import java.io.Serializable;
import java.io.Writer;
import java.io.IOException;
import java.util.List;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * Represents a String with a corresponding integer ID.
 * Keeps a static index of all the Strings, indexed by ID.
 * 
 * @author danielcer
 *
 */
public class IString implements CharSequence, Serializable, HasIntegerIdentity, HasWord, Comparable<IString> {
  public static final OAIndex<String> index = new OAIndex<String>();

  private static boolean internStrings = true;

  private String stringRep;
  public final int id;

  private enum Classing { NONE, BACKSLASH, IBM }
  private static final Classing classing = Classing.IBM;


	public static Set<String> keySet() {
		return index.keySet();
	}

  public IString() {
    id = -1;
    stringRep = "";
  }
  /**
   *
   */
  public IString(String string) {
    if(classing == Classing.BACKSLASH) { // e.g., on december 4\\num
      int doubleBackSlashPos = string.indexOf("\\\\");
      if (doubleBackSlashPos != -1) {
        stringRep = string.substring(0, doubleBackSlashPos); //.intern();
        id = index.indexOf(string.substring(doubleBackSlashPos), true);
        return;
      }
    } else if(classing == Classing.IBM) { // e.g., on december $num_(4)
      if(string.length() > 2 && string.startsWith("$")) {
        int delim = string.indexOf("_(");
        if(delim != -1 && string.endsWith(")")) {
          stringRep = string.substring(delim+2,string.length()-1); //.intern();
          id = index.indexOf(string.substring(0,delim), true);
          return;
        }
      }
    }
    stringRep = internStrings ? string.intern() : string;
    id = index.indexOf(string, true);
  }

  /**
   *
   */
  public IString(int id) {
    this.id = id;
    stringRep = null; //index.get(id);
  }

	private String lazyStringRep() {
		if (stringRep == null) stringRep = index.get(id);
		return stringRep;
	}

  /**
   *
   */
  private static final long serialVersionUID = 2718L;

  public char charAt(int index) {
    return lazyStringRep().charAt(index);
  }

  public int length() {
    return lazyStringRep().length();
  }

  public static void internStrings(boolean i) {
    internStrings = i;
  }

  public CharSequence subSequence(int start, int end) {
    return lazyStringRep().subSequence(start, end);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof IString)) {
      System.err.printf("o class: %s\n", o.getClass());
      throw new UnsupportedOperationException();
    }
    IString istr = (IString)o;
    return this.id == istr.id;
  }

  public long longHashCode() {
    return id;
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Override
  public String toString() {
    return lazyStringRep();
  }

  public int getId() {
    return id;
  }

  public static String getString(int id) {
    return index.get(id);
  }

  public String word() {
    return toString();
  }

  public void setWord(String word) {
    throw new UnsupportedOperationException();
  }
  
  static private WrapperIndex wrapperIndex; // = null;

  static public Index<IString> identityIndex() {
    if (wrapperIndex == null) {
      wrapperIndex = new WrapperIndex();
    }
    return wrapperIndex;
  }

  static private class WrapperIndex implements Index<IString> {

    /**
     *
     */
    private static final long serialVersionUID = 2718L;

    public boolean contains(Object o) {
      if (!(o instanceof IString)) return false;
      IString istring = (IString)o;
      return index.contains(istring.lazyStringRep());
    }

    public IString get(int i) {
      return new IString(index.get(i));
    }

    public int indexOf(IString o) {
      return index.indexOf(o.lazyStringRep());
    }

    public int indexOf(IString o, boolean add) {
      return index.indexOf(o.lazyStringRep(), add);
    }

    public int size() {
      return index.size();
    }
    
    public List<IString> objectsList() {
      throw new UnsupportedOperationException();
    }

    public Collection<IString> objects(int[] ints) {
      throw new UnsupportedOperationException();
    }

    public boolean isLocked() {
      throw new UnsupportedOperationException();
    }

    public void lock() {
      throw new UnsupportedOperationException();
    }

    public void unlock() {
      throw new UnsupportedOperationException();
    }

    public void saveToWriter(Writer out) throws IOException {
      throw new UnsupportedOperationException();
    }

    public void saveToFilename(String s) {
      throw new UnsupportedOperationException();
    }

    public boolean isEmpty() {
      throw new UnsupportedOperationException();
    }

    public Iterator<IString> iterator() {
      throw new UnsupportedOperationException();
    }

    public <T> T[] toArray(T[] a) {
      throw new UnsupportedOperationException();
    }

    public boolean add(IString iString) {
      throw new UnsupportedOperationException();
    }

    public boolean remove(Object o) {
      throw new UnsupportedOperationException();
    }

    public boolean addAll(Collection<? extends IString> c) {
      throw new UnsupportedOperationException();
    }

    public void clear() {
      throw new UnsupportedOperationException();
    }
  }

  public int compareTo(IString o) {
    return lazyStringRep().compareTo(o.lazyStringRep());
  }

}

