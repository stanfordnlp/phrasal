package mt;

import java.io.Serializable;
import edu.stanford.nlp.util.*;

/**
 *
 * @author danielcer
 *
 */
public class IString implements CharSequence, Serializable, HasIntegerIdentity {
  // TODOX make serialization clean
  static final IndexInterface<String> index = new OAIndex<String>();

  private final String stringRep;
  public final int id;

  public IString() {
    this("");
  }
  /**
   *
   * @param string
   */
  public IString(String string) {
    stringRep = string;
    id = index.indexOf(stringRep, true);
  }

  /**
   *
   * @param id
   */
  public IString(int id) {
    this.id = id;
    stringRep = index.get(id);
  }

  /**
   *
   */
  private static final long serialVersionUID = 2718L;

  public char charAt(int index) {
    return stringRep.charAt(index);
  }

  public int length() {
    return stringRep.length();
  }


  public CharSequence subSequence(int start, int end) {
    return stringRep.subSequence(start, end);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof IString)) {
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
    return stringRep;
  }



  public int getId() {
    return id;
  }

  static private WrapperIndex wrapperIndex; // = null;

  static public IndexInterface<IString> identityIndex() {
    if (wrapperIndex == null) {
      wrapperIndex = new WrapperIndex();
    }
    return wrapperIndex;
  }


  static private class WrapperIndex implements IndexInterface<IString> {

    /**
     *
     */
    private static final long serialVersionUID = 2718L;

    public boolean contains(Object o) {
      if (!(o instanceof IString)) return false;
      IString istring = (IString)o;
      return index.contains(istring.stringRep);
    }

    public IString get(int i) {
      return new IString(index.get(i));
    }

    public int indexOf(IString o) {
      return index.indexOf(o.stringRep);
    }

    public int indexOf(IString o, boolean add) {
      return index.indexOf(o.stringRep, add);
    }

    public int size() {
      return index.size();
    }

  }

}

