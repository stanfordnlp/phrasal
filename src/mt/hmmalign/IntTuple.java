package mt.hmmalign;

public abstract class IntTuple {

//  int numElements;


//  public IntTuple(int num) {
//    numElements = num;
//  }

//
//  public IntTuple() {
//  }

  public static IntTuple getIntTuple(int num) {

    if ((num == 1) || (num == 2)) {
      return new IntPair();
    }
    if (num == 3) {
      return new IntTriple();
    }
    if (num == 4) {
      return new IntQuadruple();
    }
    throw new RuntimeException("Can't have an IntTuple with " + num + " elements.");
  }

  public abstract IntTuple getCopy();

  public abstract void set(int num, int val);

  public abstract int get(int num);

  public abstract void print();

  public abstract String toNameStringE();

  public abstract String toNameStringF();

  public String toNameString(int mask) {
    int current = 0;
    String name = "";
    if ((mask & 32) > 0) {
      name += SentenceHandler.sTableE.getName(get(current));
      name += " ";
      current++;
    }

    if ((mask & 16) > 0) {
      name += SentenceHandler.sTableE.getName(get(current));
      name += " ";
      current++;
    }
    if ((mask & 8) > 0) {
      name += SentenceHandler.sTableE.getName(get(current));
      name += " ";
      current++;
    }
    if ((mask & 4) > 0) {
      name += SentenceHandler.sTableF.getName(get(current));
      name += " ";
      current++;
    }
    if ((mask & 2) > 0) {
      name += SentenceHandler.sTableF.getName(get(current));
      name += " ";
      current++;
    }
    if ((mask & 1) > 0) {
      name += SentenceHandler.sTableF.getName(get(current));
      name += " ";
      current++;
    }

    return name;

  }


}
