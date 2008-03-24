package mt;

import java.io.*;
import java.util.*;

/**
 * 
 * @author danielcer
 *
 */
public class ScorerFactory {
	
	static public String STATIC_SCORER = "staticscorer";
	static public String UNIFORM_SCORER = "uniformscorer";
	static public String DEFAULT_SCORER = UNIFORM_SCORER;
	
	static public String STATIC_SCORER_INLINE = "inline";
	static public String STATIC_SCORER_FILE = "file";
	
	private ScorerFactory() { }
	
	/**
	 * 
	 * @param scorerSpecs
	 * @return
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	static public Scorer<String> factory(String... scorerSpecs) throws IOException {
		if (scorerSpecs.length == 0) {
			scorerSpecs = new String[1];
			scorerSpecs[0] = DEFAULT_SCORER;
		}
		
		String scorerName = scorerSpecs[0];
		return factory(scorerName, Arrays.copyOfRange(scorerSpecs, 1, scorerSpecs.length));
	}
	
	/**
	 * 
	 * @param scorerSpecs
	 * @return
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	static public Scorer<String> factory(String scorerName, String[] config) throws IOException {		
		
		if (scorerName.equals(UNIFORM_SCORER)) {
			return new UniformScorer<String>();
		} else if (scorerName.equals(STATIC_SCORER)) {
			if (config.length < 1) {
				throw new RuntimeException(
						String.format("%s requires an additional mode parameter either %s or %s", 
								STATIC_SCORER, STATIC_SCORER_INLINE, STATIC_SCORER_FILE));
			}
			String mode = config[0];
			if (mode.equals(STATIC_SCORER_FILE)) {
				if (config.length < 2) {
					throw new RuntimeException( 
							String.format("%s %s requires that a file name is specified as an argument\n",
									STATIC_SCORER, STATIC_SCORER_FILE));
				}
				String filename = config[1];
				return new StaticScorer(filename);
			} else if (mode.equals(STATIC_SCORER_INLINE)) {
				if (config.length < 2) {
					throw new RuntimeException( 
							String.format("%s %s requires at least one feature weight is specified, e.g. someFeatureName:0.95, as an argument.\n",
									STATIC_SCORER, STATIC_SCORER_INLINE));
				}
				Map<String,Double> featureWts = new HashMap<String,Double>();
				for (int i = 1; i < config.length; i++) {
					String[] fields = config[i].split(":");
					String featureName = fields[0];
					for (int fi = 1; fi < fields.length-1; fi++) featureName += ":"+fields[fi];
					if (fields.length == 1) {
						throw new RuntimeException(
								String.format("No weight specified for the feature %s, i.e. %s should be something like %s:0.95",
										featureName, featureName, featureName));
					}
					String featureWt = fields[fields.length-1];
					try {
						featureWts.put(featureName, new Double(featureWt));
					} catch (NumberFormatException e) {
						throw new RuntimeException(
								String.format("In the feature weight pair %s:%s, %s can not be parsed as a number.", featureName,
										featureWt, featureWt));
					}
				}
				return new StaticScorer(featureWts);
			}
		}
		
		throw new RuntimeException(String.format("Unknown scorer \"%s\"", scorerName));
	}
}
