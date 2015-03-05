package edu.stanford.nlp.mt.util;

import java.io.Serializable;

/**
 * Represents a String with a corresponding integer ID. Keeps a static index of
 * all the Strings, indexed by ID.
 *
 * TODO(spenceg) Add support for decoder-local indices.
 *
 * @author danielcer
 * @author Spence Green
 *
 */
public class IString implements CharSequence, Serializable, HasIntegerIdentity, Comparable<IString> {

  private static final long serialVersionUID = 7535218805035757457L;

  public final int id;

  /**
   * Constructor.
   */
  public IString(String string) {
    id = TranslationModelIndex.systemAdd(string);
  }

  /**
   * Constructor.
   * 
   * @param id
   */
  public IString(int id) {
    this.id = id;
  }

  @Override
  public char charAt(int charIndex) {
    return TranslationModelIndex.systemGet(id).charAt(charIndex);
  }

  @Override
  public int length() {
    return TranslationModelIndex.systemGet(id).length();
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return TranslationModelIndex.systemGet(id).subSequence(start, end);
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
    return TranslationModelIndex.systemGet(id);
  }

  @Override
  public int getId() {
    return id;
  }

  @Override
  public int compareTo(IString o) {
    return TranslationModelIndex.systemGet(id).compareTo(TranslationModelIndex.systemGet(o.id));
  }
}
