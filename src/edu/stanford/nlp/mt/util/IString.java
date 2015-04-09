package edu.stanford.nlp.mt.util;

import java.io.Serializable;

/**
 * Represents a String with a corresponding integer ID. Keeps a static index of
 * all the Strings, indexed by ID.
 *
 * @author danielcer
 * @author Spence Green
 *
 */
public class IString implements CharSequence, Serializable, Comparable<IString> {

  private static final long serialVersionUID = 7535218805035757457L;
  
  public final int id;
  private final TranslationModelIndex index;
  
  /**
   * Constructor.
   */
  public IString(String string) {
    this(string, null);
  }

  /**
   * Constructor.
   * 
   * @param string
   * @param index
   */
  public IString(String string, TranslationModelIndex index) {
    this.id = index == null ? TranslationModelIndex.systemAdd(string) :
      index.add(string);
    this.index = index;
  }
  
  /**
   * Constructor.
   * 
   * @param id
   */
  public IString(int id) {
    this(id, null);
  }
  
  /**
   * Constructor.
   * 
   * @param id
   * @param index
   */
  public IString(int id, TranslationModelIndex index) {
    if (id == TranslationModelIndex.UNKNOWN_ID) {
      throw new IllegalArgumentException("Invalid id: " + String.valueOf(id));
    }
    this.id = id;
    this.index = index;
  }

  @Override
  public char charAt(int charIndex) {
    return index == null ? TranslationModelIndex.systemGet(id).charAt(charIndex) :
        index.get(id).charAt(charIndex);
  }

  @Override
  public int length() {
    return index == null ? TranslationModelIndex.systemGet(id).length() :
        index.get(id).length();
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return index == null ? TranslationModelIndex.systemGet(id).subSequence(start, end) :
      index.get(id).substring(start, end);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if ( !(o instanceof IString)) {
      return false;
    } else {
      IString other = (IString) o;
      return this.id == other.id;
    }
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Override
  public String toString() {
    return index == null ? TranslationModelIndex.systemGet(id) :
      index.get(id);
  }

  /**
   * Get the underlying integer id.
   * 
   * @return
   */
  public int getId() {
    return id;
  }

  @Override
  public int compareTo(IString o) {
    return index == null ? TranslationModelIndex.systemGet(id).compareTo(TranslationModelIndex.systemGet(o.id)) :
      index.get(id).compareTo(index.get(o.id));
  }
}
