package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.objectbank.ObjectBank;
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
 * Represents a String with a corresponding integer ID. Keeps a static index of
 * all the Strings, indexed by ID.
 * 
 * @author danielcer
 * 
 */
public class IString implements CharSequence, Serializable, HasIntegerIdentity,
    HasWord, Comparable<IString> {

  public static final OAIndex<String> index = new OAIndex<String>();

  private String stringRep;
  public final int id;

  private enum Classing {
    BACKSLASH, IBM
  }

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
    if (classing == Classing.BACKSLASH) { // e.g., on december 4\\num
      int doubleBackSlashPos = string.indexOf("\\\\");
      if (doubleBackSlashPos != -1) {
        stringRep = string.substring(0, doubleBackSlashPos); // .intern();
        id = index.indexOf(string.substring(doubleBackSlashPos), true);
        return;
      }
    } else if (classing == Classing.IBM) { // e.g., on december $num_(4)
      if (string.length() > 2 && string.startsWith("$")) {
        int delim = string.indexOf("_(");
        if (delim != -1 && string.endsWith(")")) {
          stringRep = string.substring(delim + 2, string.length() - 1); // .intern();
          id = index.indexOf(string.substring(0, delim), true);
          return;
        }
      }
    }
    stringRep = null; // string;
    id = index.indexOf(string, true);
  }

  /**
   *
   */
  public IString(int id) {
    this.id = id;
    stringRep = null; // index.get(id);
  }

  private String lazyStringRep() {
    if (stringRep == null)
      stringRep = index.get(id);
    return stringRep;
  }

  /**
   *
   */
  private static final long serialVersionUID = 2718L;

  @Override
  public char charAt(int index) {
    return lazyStringRep().charAt(index);
  }

  @Override
  public int length() {
    return lazyStringRep().length();
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return lazyStringRep().subSequence(start, end);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof IString)) {
      System.err.printf("o class: %s\n", o.getClass());
      throw new UnsupportedOperationException();
    }
    IString istr = (IString) o;
    return this.id == istr.id;
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Override
  public String toString() {
    return lazyStringRep();
  }

  @Override
  public int getId() {
    return id;
  }

  public static String getString(int id) {
    return index.get(id);
  }

  @Override
  public String word() {
    return toString();
  }

  @Override
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

  public static void load(String fileName) {
    for (String line : ObjectBank.getLineIterator(fileName)) {
      for (String word : line.split("\\s+")) {
        System.err.println("adding: " + word);
        new IString(word);
      }
    }
  }

  static private class WrapperIndex implements Index<IString> {

    /**
     *
     */
    private static final long serialVersionUID = 2718L;

    @Override
    public boolean contains(Object o) {
      if (!(o instanceof IString))
        return false;
      IString istring = (IString) o;
      return index.contains(istring.lazyStringRep());
    }

    @Override
    public IString get(int i) {
      return new IString(index.get(i));
    }

    @Override
    public int indexOf(IString o) {
      return index.indexOf(o.lazyStringRep());
    }

    @Override
    public int indexOf(IString o, boolean add) {
      return index.indexOf(o.lazyStringRep(), add);
    }

    @Override
    public int size() {
      return index.size();
    }

    @Override
    public List<IString> objectsList() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Collection<IString> objects(int[] ints) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLocked() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void lock() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void unlock() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void saveToWriter(Writer out) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void saveToFilename(String s) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<IString> iterator() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(T[] a) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(IString iString) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends IString> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public int compareTo(IString o) {
    return lazyStringRep().compareTo(o.lazyStringRep());
  }

}
