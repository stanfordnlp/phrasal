package mt.base;

import java.util.*;

public class SimpleDynamicIntegerArrayIndex implements Iterable<int[]> {

  static int INIT_SZ = 1<<10;
  static final float MAX_LOAD = 0.2f;

  List<Object> objects; // List of int[]
  Map<ThinArrayWrapper,Integer> indexes;

  public SimpleDynamicIntegerArrayIndex(int init_sz) { 
    INIT_SZ = init_sz;
    init(); 
  } 

  public SimpleDynamicIntegerArrayIndex() { 
    init(); 
  } 

  public int[] get(int idx) {
    return (int[]) objects.get(idx);
  }

  public int indexOf(int[] key) { 
    return indexOf(key, false);
  }
  
  public int indexOf(int[] key, boolean add) {
    ThinArrayWrapper wrapper = new ThinArrayWrapper(key);
    Integer index = indexes.get(wrapper);
    if (index == null) {
      if (add) {
        add(wrapper);
        index = indexes.get(wrapper);
      } else {
        return -1;
      }
    }
    return index.intValue();
  } 

  private boolean add(ThinArrayWrapper wrapper) {
    Integer index = indexes.get(wrapper);
    if (index == null) {
      index = new Integer(objects.size());
      objects.add(wrapper.array);
      indexes.put(wrapper, index);
      return true;
    } else {
      return false;
    }
  }

  public int size() {
    return objects.size();
  }

  public Iterator<int[]> iterator() {
    return new Itr();
  }

  private void init() {
    objects = new ArrayList<Object>(INIT_SZ);
    indexes = new HashMap<ThinArrayWrapper,Integer>(INIT_SZ,MAX_LOAD);
  }
  
  private class ThinArrayWrapper {
    
    // Need a wrapper around arrays, otherwise o.hashCode()
    // will fail to call Arrays.hashCode(o).

    final int[] array;
    final int hashCode;

    ThinArrayWrapper(int[] array) {
      this.array = array; 
      this.hashCode = Arrays.hashCode(array);
    }

    public boolean equals(Object o) {
      if(!(o instanceof ThinArrayWrapper))
        return false;
      ThinArrayWrapper wrapper2 = (ThinArrayWrapper) o;
      return Arrays.equals(array,wrapper2.array);
    }

    public int hashCode() {
      return hashCode;
    }
  }

  private class Itr implements Iterator<int[]> {

    int cursor = 0;

    public boolean hasNext() {
      return cursor < size();
    }

    public int[] next() {
       return get(cursor++);
    }

    public void remove() {
       throw new UnsupportedOperationException();
    }
    
  }
}
