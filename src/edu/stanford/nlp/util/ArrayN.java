package edu.stanford.nlp.util;

/**
 * N-dimensional array implemented using an underlying 1-dimensional array.
 *
 * @author sbills
 */
public class ArrayN {
  private int [] dims;
  private int [] spans;
  private float[] elems;
  private int len;


  public ArrayN(int... dims) {
    len = dims.length;

    this.dims = new int[len];
    spans = new int[len];

    for (int i=0; i<len; i++){
      if (dims[i] > 0){
        this.dims[i] = dims[i];
      } else {
        this.dims[i] = 1;
      }
    }
    spans[0] = 1;
    for (int i=1; i<len; i++){
      spans[i] = spans[i-1] * dims[i-1];
    }

    int size = spans[len - 1] * dims[len - 1];

    //System.out.printf("length:%d size:%d\n", len, size);
    elems = new float[size];
  }

  private static String indexToString(int... index){
    StringBuilder sb = new StringBuilder("(");
    for (int i=0; i<index.length; i++){
      sb.append(index[i]);
      if (i != index.length-1){
        sb.append(", ");
      }
    }
    sb.append(')');
    return sb.toString();
  }

  public int get1DIndex(int... index) {
    int total=0;

    if (index.length > len){
        throw new java.lang.ArrayIndexOutOfBoundsException("Attempted to access " + indexToString(index) +
                                                           " in array of size " + indexToString(dims));
    }
    for (int i=0; i<index.length; i++){
      if (index[i] >= dims[i] || index[i] < 0){
        throw new java.lang.ArrayIndexOutOfBoundsException("Attempted to access " + indexToString(index) +
                                                           " in array of size " + indexToString(dims));
      }
      total += index[i] * spans[i];
    }
    return total;
  }

  public float get(int... index) {
    return elems[get1DIndex(index)];
  }

  public void set(float val, int... index) {
    elems[get1DIndex(index)] = val;
  }

  public void inc(float val, int... index) {
    elems[get1DIndex(index)] += val;
  }

  public void setZero() {
    for (int i = 0; i < elems.length; i++) {
      elems[i] = 0;
    }
  }

  public int getNumDims(){
    return len;
  }

  public int getDim(int i){
    if (i >= len) return 0;
    return dims[i];
  }

  public static void main(String[] args){
    ArrayN mda = new ArrayN(50, 20, 6, 7);
    mda.setZero();
    System.out.println(mda.getNumDims() + " " + mda.getDim(0) + ' ' + mda.getDim(1) + ' ' + mda.getDim(2) + ' ' +
                     mda.getDim(3) + ' ' + mda.getDim(4) + ' ');
    //System.out.println(mda.get(50, 0, 0, 0));
    mda.set((float)2.0, 1, 0, 0);
    mda.set((float)3.0, 1, 0, 0, 0);
    mda.inc((float)1.0, 49, 19, 5, 6);
    System.out.println(mda.get(1) + " " + mda.get(49, 19, 5, 6));
    mda.setZero();
    System.out.println(mda.get(1) + " " + mda.get(49, 19, 5, 6));

  }
}
