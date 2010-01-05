package mt.base;

import edu.stanford.nlp.util.Index;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Michel Galley
 */
public class FeatureValueArray<E> extends ArrayList<FeatureValue<E>> {
	
	private static final long serialVersionUID = 1L;
	
	double[] arr;

  public FeatureValueArray() {
    super();
  }

  public FeatureValueArray(Collection<? extends FeatureValue<E>> c) {
    super(c);
  }

  public void setArrayFromIndex(Index<E> featureIndex) {
		arr = new double[featureIndex.size()];
		for (FeatureValue<E> feature : this) {
			int index = featureIndex.indexOf(feature.name);
			if (index >= 0) arr[index] = feature.value;
		}
	}

	public double[] toDoubleArray() {
		return arr;
	}

	@Override
	public boolean add(FeatureValue<E> e) {
		arr = null;
		return super.add(e);
	}

	@Override
	public void add(int index, FeatureValue<E> e) {
		arr = null;
		super.add(index, e);
	}

	@Override
	public FeatureValue<E> remove(int index) {
		arr = null;
		return super.remove(index);
	}

	@Override
	public void removeRange(int from, int to) {
		arr = null;
		super.removeRange(from, to);
	}

	@Override
	public FeatureValue<E> set(int index, FeatureValue<E> e) {
		arr = null;
		return super.set(index, e);
	}

	@Override
	public boolean addAll(Collection<? extends FeatureValue<E>> c) {
		arr = null;
		return super.addAll(c);
	}
}
