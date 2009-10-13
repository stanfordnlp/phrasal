package mt.classifyde;

public class SortByEndPair<T1,T2> implements Comparable<SortByEndPair<T1,T2>> {
  public T1 first;
  public T2 second;

  public SortByEndPair(T1 first, T2 second) {
    this.first = first;
    this.second = second;
  }

  @Override
  public String toString() {
    return "(" + first + "," + second + ")";
  }

  @SuppressWarnings("unchecked")
	public int compareTo(SortByEndPair<T1,T2> another) {
    //System.err.println("CompareTo");
    int comp = ((Comparable) second).compareTo(another.second);
    if (comp != 0) {
      //System.err.println("Compared: "+comp);
      return comp;
    } else {
      return ((Comparable) another.first).compareTo(first);
    }
  }

  @Override
  public int hashCode() {
    return (((first == null) ? 0 : first.hashCode()) << 16) ^ ((second == null) ? 0 : second.hashCode());
  }

}
