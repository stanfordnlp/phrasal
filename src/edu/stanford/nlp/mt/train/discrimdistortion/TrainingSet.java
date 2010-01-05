package edu.stanford.nlp.mt.train.discrimdistortion;

import java.util.*;

import edu.stanford.nlp.util.Index;

public class TrainingSet implements Iterable<Datum> {

	private final List<Datum> examples;
	private final Map<DistortionModel.Feature,Integer> featureOffsets;
	private final Map<DistortionModel.Feature,Integer> featureDimensions;
	private final Map<DistortionModel.Feature,DistortionModel.FeatureType> featureTypes;
		
	private int numFeatures = 0;
	
	//WSGDEBUG - Make it public for now
	public final Index<DistortionModel.Feature> featureIndex;
	public final Index<DistortionModel.Class> classIndex;
	
	public TrainingSet(final Index<DistortionModel.Feature> featIndex, final Index<DistortionModel.Class> classIndex, final int numExpectedFeatures) {
		examples = Collections.synchronizedList(new ArrayList<Datum>(numExpectedFeatures));
		this.featureIndex = featIndex;
		this.classIndex = classIndex;
		
		featureOffsets = new HashMap<DistortionModel.Feature,Integer>();
		featureDimensions = new HashMap<DistortionModel.Feature, Integer>();
		featureTypes = new HashMap<DistortionModel.Feature, DistortionModel.FeatureType>();
	}

	public void addDatum(Datum d) throws RuntimeException { 
		if(d.numFeatures() != featureIndex.size())
			throw new RuntimeException(String.format("%s: Datum has dimension %d, but training set requires dimension %d", this.getClass().getName(), d.numFeatures(),featureIndex.size()));
		examples.add(d); 
	}
	
	public int getNumExamples() { return examples.size(); }
	
	public void addFeatureParameters(DistortionModel.Feature feat, DistortionModel.FeatureType type, int offset, int dimension) {
		featureOffsets.put(feat,offset);
		featureDimensions.put(feat, dimension);
		featureTypes.put(feat, type);
		numFeatures += dimension;
	}
	
	public int getFeatureOffset(DistortionModel.Feature f) { return (featureOffsets == null) ? -1 : featureOffsets.get(f); }
	
	public int getFeatureDimension(DistortionModel.Feature f ) { return (featureDimensions == null) ? -1 : featureDimensions.get(f); }
	
	public DistortionModel.FeatureType getFeatureType(DistortionModel.Feature f) { return (featureTypes == null) ? null : featureTypes.get(f); }
	
	public Map<DistortionModel.Feature, Integer> getFeatureOffsets() { return Collections.unmodifiableMap(featureOffsets); }
	
	public Map<DistortionModel.Feature, Integer> getFeatureDimensions() { return Collections.unmodifiableMap(featureDimensions); }
	
	public Map<DistortionModel.Feature, DistortionModel.FeatureType> getFeatureTypes() { return Collections.unmodifiableMap(featureTypes); }
	
	public int getNumFeatures() { return numFeatures; }
	
	public int getNumClasses() { return classIndex.size(); }
		
	@Override
	public Iterator<Datum> iterator() { return examples.iterator(); }
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("TrainingSet: \n");
		sb.append(String.format(" Datum Dimension : %d\n", featureIndex.size()));
		sb.append(String.format(" Examples        : %d\n", examples.size()));
		sb.append(String.format(" Features        : %d\n", numFeatures));
		for(Map.Entry<DistortionModel.Feature,Integer> feat : featureOffsets.entrySet())
			sb.append(String.format("  %s (%d)\n",feat.getKey().toString(), feat.getValue()));
		
		return sb.toString();
	}
	
	//WSGDEBUG
	public void printExamples() {
		System.err.println(">> Examples <<");
		for(Datum d : examples)
			System.err.println(d.toString());
	}
}
