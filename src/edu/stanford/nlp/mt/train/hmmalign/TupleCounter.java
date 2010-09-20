package edu.stanford.nlp.mt.train.hmmalign;

import edu.stanford.nlp.util.MutableInteger;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

/**
 * This class keeps track of how many times a certain tuple occurred, in order
 * to be able to discard the rare ones. It maintains a HashMap IntTuple -&gt;
 * MutableInteger.
 * 
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
public class TupleCounter {
  private HashMap<IntTuple, MutableInteger> map = new HashMap<IntTuple, MutableInteger>();

  public void add(IntTuple iT, int count) {

    // System.out.println("adding "+iT.toNameString(30));
    MutableInteger iH = map.get(iT);
    if (iH == null) {

      IntTuple iN = iT.getCopy();
      MutableInteger hN = new MutableInteger();
      hN.incValue(count);
      map.put(iN, hN);
      // System.out.println("new ");

    } else {

      // System.out.println("Already there ");
      iH.incValue(count);

    }

  }

  public TupleCounter() {
  }

  public Iterator<Map.Entry<IntTuple, MutableInteger>> getIterator() {

    return map.entrySet().iterator();

  }

}
