package mt.train;

import edu.stanford.nlp.util.*;

public class Block extends IntQuadruple {
  public Block() { super(); }
  public Block(int f1, int f2, int e1, int e2) { super(f1,f2,e1,e2); }
  public int f1() { return get(0); }
  public int f2() { return get(1); }
  public int e1() { return get(2); }
  public int e2() { return get(3); }
}
