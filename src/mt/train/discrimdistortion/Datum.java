package mt.train.discrimdistortion;

public class Datum {
	
	private final float target;
	private final float[] features;
	
	public Datum(float target, float[] features) {
		this.target = target;
		this.features = features;
	}
	
	public float getTarget() { return target; }

	public float get(int i) {
		if(i < 0 || i >= features.length)
			throw new RuntimeException(String.format("%s: Index %d out-of-bounds (%d dimensions)\n",this.getClass().getName(),i,features.length));
		return features[i];
	}

	public int numFeatures() { return features.length; }
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("target: %f [ ",target));
		for(int i = 0; i < features.length; i++)
			sb.append(String.format("%f ",features[i]));
		sb.append("]");
		return sb.toString();
	}
}
