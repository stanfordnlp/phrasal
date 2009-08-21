package mt.base;

import java.util.Map;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class PhraseAlignment {

  class AlignmentHolder {

    final IString str;
    final int[][] e2f;

    AlignmentHolder(String s) {
      //System.err.println("align: "+s);
      String stringRep = s.intern();
      if(stringRep.equals("I-I")) {
          e2f = null;
      } else {
        String[] els = stringRep.split(";");
        e2f = new int[els.length][];
        for(int i=0; i<e2f.length; ++i) {
          //System.err.printf("(%d): %s\n",i,els[i]);
          if(!els[i].equals("()")) {
            String[] els2 = els[i].split(",");
            e2f[i] = new int[els2.length];
            for(int j=0; j<e2f[i].length; ++j) {
              //System.err.printf("(%d): %s\n",j,els2[j]);
              String num = els2[j].replaceAll("[()]","");
              e2f[i][j] = Integer.parseInt(num);
            }
          }
        }
      }
      str = new IString(stringRep);
      //System.err.println(Arrays.deepToString(e2f));
    }

    @Override
    public boolean equals(Object o) {
      assert(o instanceof AlignmentHolder);
      AlignmentHolder a = (AlignmentHolder)o;
      return this.str.id == a.str.id;
    }

    @Override
    public int hashCode() {
      return str.hashCode();
    }

    public int[] e2f(int i) {
      return e2f[i];
    }
  }

  public static final Map<String,AlignmentHolder> map = new Object2ObjectOpenHashMap<String,AlignmentHolder>();

  private final AlignmentHolder holder;

  /**
   *
   * @param string
   */
  public PhraseAlignment(String string) {
    AlignmentHolder holder = map.get(string);
    if(holder == null) {
      holder = new AlignmentHolder(string);
      map.put(string, holder);
    }
    this.holder = holder;
  }

  @Override
  public boolean equals(Object o) {
    assert(o instanceof PhraseAlignment);
    PhraseAlignment pa = (PhraseAlignment)o;
    return this.holder.equals(pa.holder);
  }

  @Override
  public int hashCode() {
    return holder.str.hashCode();
  }

  @Override
  public String toString() {
    return holder.str.toString();
  }

  public IString toIString() {
    return holder.str;
  }

  public boolean hasAlignment() {
    return holder.e2f != null;
  }

  public int[] e2f(int i) {
    return holder.e2f[i];
  }

  public int size() {
    return holder.e2f.length;
  }
}
