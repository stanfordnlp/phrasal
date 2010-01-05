///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2007 University of Texas at Austin and (C) 2005
// University of Pennsylvania and Copyright (C) 2002, 2003 University
// of Massachusetts Amherst, Department of Computer Science.
//
// This software is licensed under the terms of the Common Public
// License, Version 1.0 or (at your option) any subsequent version.
// 
// The license is approved by the Open Source Initiative, and is
// available from their website at http://www.opensource.org.
///////////////////////////////////////////////////////////////////////////////

package mt.syntax.mst.rmcd;

import gnu.trove.*;

import java.util.*;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.MapFactory;
import edu.stanford.nlp.util.MutableDouble;
import it.unimi.dsi.fastutil.ints.IntArrayList;


/**
 * A <tt>FeatureVector</tt> that can hold up to two
 * <tt>FeatureVector</tt> instances inside it, which allows for a very
 * quick concatenation operation.
 * <p/>
 * <p>Also, in order to avoid copies, the second of these internal
 * <tt>FeatureVector</tt> instances can be negated, so that it has the
 * effect of subtracting any values rather than adding them.
 * <p/>
 * <p>
 * Created: Sat Nov 10 15:25:10 2001
 * </p>
 *
 * @author Jason Baldridge
 * @version $Id: FeatureVector.java 90 2007-01-17 07:42:20Z jasonbaldridge $
 * @see mt.syntax.mst.rmcd.Feature
 */
public final class FeatureVector extends TLinkedList<Feature> {
  private static final long serialVersionUID = -7512329174682925929L;
  private FeatureVector subfv1 = null;
  private FeatureVector subfv2 = null;
  private boolean negateSecondSubFV = false;

  public FeatureVector() {
  }

  public FeatureVector(FeatureVector fv1) {
    subfv1 = fv1;
  }

  public FeatureVector(FeatureVector fv1, FeatureVector fv2) {
    subfv1 = fv1;
    subfv2 = fv2;
  }

  public FeatureVector(FeatureVector fv1, FeatureVector fv2, boolean negSecond) {
    subfv1 = fv1;
    subfv2 = fv2;
    negateSecondSubFV = negSecond;
  }

  public FeatureVector(int[] keys) {
    for (int key : keys)
      add(new Feature(key, 1.0));
  }

  public void add(int index, double value) {
    add(new Feature(index, value));
  }


  public int[] keys() {
    TIntArrayList keys = new TIntArrayList();
    addKeysToList(keys);
    return keys.toNativeArray();
  }

  private void addKeysToList(TIntArrayList keys) {
    if (null != subfv1) {
      subfv1.addKeysToList(keys);

      if (null != subfv2)
        subfv2.addKeysToList(keys);
    }

    ListIterator<Feature> it = listIterator();
    while (it.hasNext())
      keys.add((it.next()).index);

  }

  public final FeatureVector cat(FeatureVector fl2) {
    return new FeatureVector(this, fl2);
  }

  // fv1 - fv2
  public FeatureVector getDistVector(FeatureVector fl2) {
    return new FeatureVector(this, fl2, true);
  }

  public final double getScore(double[] parameters) {
    return getScore(parameters, false);
  }

  private double getScore(double[] parameters, boolean negate) {
    double score = 0.0;

    if (null != subfv1) {
      score += subfv1.getScore(parameters, negate);

      if (null != subfv2) {
        if (negate) {
          score += subfv2.getScore(parameters, !negateSecondSubFV);
        } else {
          score += subfv2.getScore(parameters, negateSecondSubFV);
        }
      }
    }

    ListIterator<Feature> it = listIterator();

    if (negate) {
      while (it.hasNext()) {
        Feature f = it.next();
        score -= parameters[f.index] * f.value;
      }
    } else {
      while (it.hasNext()) {
        Feature f = it.next();
        if(f.index < parameters.length) {
          score += parameters[f.index] * f.value;
        }
      }
    }

    return score;
  }

  public void update(double[] parameters, double[] total, double alpha_k, double upd) {
    update(parameters, total, alpha_k, upd, false);
  }

  private void update(double[] parameters, double[] total,
                            double alpha_k, double upd, boolean negate) {

    if (null != subfv1) {
      subfv1.update(parameters, total, alpha_k, upd, negate);

      if (null != subfv2) {
        if (negate) {
          subfv2.update(parameters, total, alpha_k, upd, !negateSecondSubFV);
        } else {
          subfv2.update(parameters, total, alpha_k, upd, negateSecondSubFV);
        }
      }
    }

    ListIterator<Feature> it = listIterator();

    if (negate) {
      while (it.hasNext()) {
        Feature f = it.next();
        parameters[f.index] -= alpha_k * f.value;
        total[f.index] -= upd * alpha_k * f.value;
      }
    } else {
      while (it.hasNext()) {
        Feature f = it.next();
        parameters[f.index] += alpha_k * f.value;
        total[f.index] += upd * alpha_k * f.value;
      }
    }

  }

  public Counter<Integer> toCounter() {
    TIntDoubleHashMap hm = new TIntDoubleHashMap(this.size());
    addFeaturesToMap(hm, false);
    int[] keys = hm.keys();

    Counter<Integer> counter = new ClassicCounter<Integer>(MapFactory.<Integer, MutableDouble>arrayMapFactory());
    for(int key : keys) {
      double d = hm.get(key);
      if(d != 0.0) {
        counter.setCount(key, d);
      }
    }
    return counter;
  }

  public Collection<Integer> toCollection() {
    List<Integer> list = new IntArrayList(this.size());
    addBinaryFeaturesToList(list);
    return list;
  }

  public double dotProduct(FeatureVector fl2) {

    TIntDoubleHashMap hm1 = new TIntDoubleHashMap(this.size());
    addFeaturesToMap(hm1, false);
    hm1.compact();

    TIntDoubleHashMap hm2 = new TIntDoubleHashMap(fl2.size());
    fl2.addFeaturesToMap(hm2, false);
    hm2.compact();

    int[] keys = hm1.keys();

    double result = 0.0;
    for (int key : keys)
      result += hm1.get(key) * hm2.get(key);

    return result;

  }

  private void addFeaturesToMap(TIntDoubleHashMap map, boolean negate) {
    if (null != subfv1) {
      subfv1.addFeaturesToMap(map, negate);

      if (null != subfv2) {
        if (negate) {
          subfv2.addFeaturesToMap(map, !negateSecondSubFV);
        } else {
          subfv2.addFeaturesToMap(map, negateSecondSubFV);
        }
      }
    }

    ListIterator<Feature> it = listIterator();
    if (negate) {
      while (it.hasNext()) {
        Feature f = it.next();
        if (!map.adjustValue(f.index, -f.value))
          map.put(f.index, -f.value);
      }
    } else {
      while (it.hasNext()) {
        Feature f = it.next();
        if (!map.adjustValue(f.index, f.value)) {
          map.put(f.index, f.value);
        }
      }
    }
  }

  private void addBinaryFeaturesToList(List<Integer> list) {
    if (null != subfv1)
      subfv1.addBinaryFeaturesToList(list);
    if (null != subfv2)
      subfv2.addBinaryFeaturesToList(list);
    ListIterator<Feature> it = listIterator();
    while (it.hasNext()) {
      Feature f = it.next();
      if(f.value > 0.0)
        list.add(f.index);
    }
  }

  public final String toString() {
    StringBuilder sb = new StringBuilder();
    toString(sb);
    return sb.toString();
  }

  private void toString(StringBuilder sb) {
    if (null != subfv1) {
      subfv1.toString(sb);

      if (null != subfv2)
        subfv2.toString(sb);
    }
    ListIterator<Feature> it = listIterator();
    while (it.hasNext())
      sb.append(it.next().toString()).append(' ');
  }
}
