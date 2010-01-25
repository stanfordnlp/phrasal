package edu.stanford.nlp.mt.decoder.feat;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.mt.base.ARPALanguageModel;
import edu.stanford.nlp.mt.base.FactoryUtil;
import edu.stanford.nlp.mt.base.LanguageModel;
import edu.stanford.nlp.mt.base.PharaohPhraseTable;
import edu.stanford.nlp.mt.base.IString;

/**
 * @author danielcer
 *
 */
public class FeaturizerFactory {
	
	public static final String PSEUDO_PHARAOH_GENERATOR = "pseudopharaoh";
	public static final String BASELINE_FEATURIZERS = "baseline";
	public static final String DEFAULT_WEIGHTING_BASELINE_FEATURIZERS = "weightedbaseline";
	public static final String WEIGHTED_NGRAM_MATCH = "weightedngrammatch";
	public static final String DEFAULT_FEATURIZERS = DEFAULT_WEIGHTING_BASELINE_FEATURIZERS;
	public static final String DISCRIMINATIVE_TM_PARAMETER = "discrimtm";
	public static final String ARPA_LM_PARAMETER = "arpalm";
  public static final String ARPA_LM_VOC_PARAMETER = "arpalmvoc";
	public static final String DISCRIMINATIVE_LM_PARAMETER = "discrimlm";
	public static final String ADDITIONAL_FEATURIZER = "additionalfeaturizers";
	
	public static final String FEATURE_ALIASES_RESOURCE = "edu/stanford/nlp/mt/resources/feature.aliases"; 
	public static final Map<String,List<String>> featureAliases = readFeatureAliases(FEATURE_ALIASES_RESOURCE);
	
	
	// Legacy stuff
	public static final Map<String,Double> DEFAULT_TM_FEATURE_WEIGHTS_MAP = new HashMap<String,Double>();
	public static final double[] DEFAULT_BASELINE_WTS;
	public static final double DEFAULT_LINEAR_DISTORTION_WT = -2.0;
	public static final double DEFAULT_ARPALM_WT = 3.0;
	public static final double DEFAULT_COLLAPSE_TM_WT = 1.0;
	
	
	static {
		Map<String,Double> m = DEFAULT_TM_FEATURE_WEIGHTS_MAP;
		m.put(PhraseTableScoresFeaturizer.PREFIX+PharaohPhraseTable.FIVESCORE_PHI_t_f, 0.2);
		m.put(PhraseTableScoresFeaturizer.PREFIX+PharaohPhraseTable.FIVESCORE_LEX_t_f, 1.0);
		m.put(PhraseTableScoresFeaturizer.PREFIX+PharaohPhraseTable.FIVESCORE_PHI_f_t, 2.0);
		m.put(PhraseTableScoresFeaturizer.PREFIX+PharaohPhraseTable.FIVESCORE_LEX_f_t, 1.0);
		m.put(PhraseTableScoresFeaturizer.PREFIX+PharaohPhraseTable.FIVESCORE_PHRASE_PENALTY, -1.0);
		m.put(PhraseTableScoresFeaturizer.PREFIX+PharaohPhraseTable.ONESCORE_P_t_f, 4.0);
		
		DEFAULT_BASELINE_WTS = new double[]{DEFAULT_LINEAR_DISTORTION_WT, DEFAULT_ARPALM_WT, DEFAULT_COLLAPSE_TM_WT};
	}
	
	static private Map<String,List<String>> readFeatureAliases(String aliasResource) {
	  if(ClassLoader.getSystemClassLoader().getResource(aliasResource) == null) {
      System.err.println("Warning: could not load alias file: "+aliasResource);
      return null;
    }
		
		Map<String,List<String>> aliases = new HashMap<String,List<String>>();
		
		try {
			LineNumberReader reader = new LineNumberReader(new InputStreamReader(ClassLoader.getSystemClassLoader().getResource(aliasResource).openStream()));
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				String lineOrig = line;
				line = line.replaceAll("#.*", "").replaceAll("\\s+$", "");
				if (line.matches("^\\s*$")) continue;
				String[] fields = line.split("\\s+");
				if (fields.length < 2) {
					System.err.printf("Error in resource: %s (line: %d)\n", aliasResource, reader.getLineNumber());
					System.err.printf("Expecting: someAlias someFullName1 someFullName2 ....\n");
					System.err.printf("Found: %s\n", lineOrig);
					System.exit(-1);
				}
				String alias = fields[0];
				List<String> names = new ArrayList<String>(fields.length-1);
				for (int i = 1; i < fields.length; i++) {
					String className = fields[i];
					names.add(className);
					try {
						ClassLoader.getSystemClassLoader().loadClass(className);
					} catch (ClassNotFoundException e) {
						System.err.printf("Error in resource: %s (line: %d)\n", aliasResource, reader.getLineNumber());
						System.err.printf("Can't load class: %s\n", className);
						System.err.printf("Associated alias: %s\n", alias);
						System.exit(-1);
					}
				}
				aliases.put(alias, names);
			}
		} catch (IOException e) {
			System.err.printf("Unable to load resouce: %s\n", aliasResource);
			System.exit(-1);
		}
		
		return aliases;
	}
	
	@SuppressWarnings("unchecked")
	static public Class loadFeaturizer(String name) {
		String trueName = (featureAliases.containsKey(name) ? featureAliases.get(name).get(0) : name);
		Class featurizerClass = null;
		
		try {
			featurizerClass = ClassLoader.getSystemClassLoader().loadClass(trueName);
		} catch (ClassNotFoundException c) {
			System.err.printf("Failed to load featurizer %s (class name: %s)\n", name, trueName);
			System.exit(-1);
		}
		
		return featurizerClass;
	}
	
	/**
	 */
	@SuppressWarnings("unchecked")
	public static CombinedFeaturizer<IString,String> factory(String... featurizerSpecs) throws IOException {
		String featurizerName;
		
		if (featurizerSpecs.length == 0) {
			featurizerName = DEFAULT_FEATURIZERS; 
		} else {
			featurizerName = featurizerSpecs[0].toLowerCase();
		}
		
		Map<String,String> paramPairs = FactoryUtil.getParamPairs(featurizerSpecs);
		
		if (featurizerName.equals(BASELINE_FEATURIZERS) ||
			featurizerName.equals(DEFAULT_WEIGHTING_BASELINE_FEATURIZERS)) {
			
			if (!paramPairs.containsKey(ARPA_LM_PARAMETER)) {
				throw new RuntimeException(String.format(
						"Baseline featurizers requires that a language model is specificed using the parameter '%s'",
						ARPA_LM_PARAMETER));
			}
			List<IncrementalFeaturizer<IString,String>> baselineFeaturizers = new LinkedList<IncrementalFeaturizer<IString,String>>();			
			
			IncrementalFeaturizer<IString,String> arpaLmFeaturizer=null, phraseTableScoresFeaturizer, linearDistortionFeaturizer;
			
			// ARPA LM
      String lmVoc = paramPairs.get(ARPA_LM_VOC_PARAMETER);
      if(lmVoc == null || lmVoc.isEmpty()) {
        String lm = paramPairs.get(ARPA_LM_PARAMETER);
        arpaLmFeaturizer = new NGramLanguageModelFeaturizer<IString>(ARPALanguageModel.load(lm));
        baselineFeaturizers.add(arpaLmFeaturizer);
      }

			// Precomputed phrase to phrase translation scores
			phraseTableScoresFeaturizer = new PhraseTableScoresFeaturizer<IString>();
			baselineFeaturizers.add(phraseTableScoresFeaturizer);
			
			// Linear distortion
			linearDistortionFeaturizer = new LinearDistortionFeaturizer<IString>();
			baselineFeaturizers.add(linearDistortionFeaturizer);
			
			if (featurizerName.equals(BASELINE_FEATURIZERS)) {
			   return new CombinedFeaturizer<IString, String>(baselineFeaturizers);
			} else {				
				IncrementalFeaturizer<IString,String> collapsedTmFeaturizer = 
					new CollapsedFeaturizer<IString, String>("comboBaselineTM:", DEFAULT_TM_FEATURE_WEIGHTS_MAP, phraseTableScoresFeaturizer);				
				CollapsedFeaturizer<IString,String> fullModel = new CollapsedFeaturizer<IString,String>("comboBaselineModel", 
						DEFAULT_BASELINE_WTS, linearDistortionFeaturizer, arpaLmFeaturizer, collapsedTmFeaturizer);				
				return new CombinedFeaturizer<IString,String>(fullModel);
			}
		} else if (featurizerName.equals(PSEUDO_PHARAOH_GENERATOR)) {
			List<IncrementalFeaturizer<IString,String>> pharaohFeaturizers = new LinkedList<IncrementalFeaturizer<IString,String>>();			
			
			IncrementalFeaturizer<IString,String> arpaLmFeaturizer, phraseTableScoresFeaturizer, linearDistortionFeaturizer, wordPenaltyFeaturizer,
				unknownWordFeaturizer;
			
			// ARPA LM
      String lmVoc = paramPairs.get(ARPA_LM_VOC_PARAMETER);
      if (lmVoc == null || lmVoc.isEmpty()) {
        String lm = paramPairs.get(ARPA_LM_PARAMETER);
        arpaLmFeaturizer = new NGramLanguageModelFeaturizer<IString>(ARPALanguageModel.load(lm));
        pharaohFeaturizers.add(arpaLmFeaturizer);
      }

      String discriminativeLMOrderStr = paramPairs.get(DISCRIMINATIVE_LM_PARAMETER);
			int discriminativeLMOrder = (discriminativeLMOrderStr == null ? 0 : Integer.parseInt(discriminativeLMOrderStr));
			
			String discriminativeTMStr = paramPairs.get(DISCRIMINATIVE_TM_PARAMETER);
			boolean discriminativeTM = (discriminativeTMStr == null ? false : Boolean.parseBoolean(discriminativeTMStr));
			
			if (discriminativeLMOrder != 0) {
				LanguageModel<IString> dLM = new IndicatorFunctionLM(discriminativeLMOrder);
				NGramLanguageModelFeaturizer<IString> dLMFeaturizer = new NGramLanguageModelFeaturizer<IString>(dLM, "DLM", true);
				pharaohFeaturizers.add(dLMFeaturizer);
			}
			
			if (discriminativeTM) {
				System.err.printf("Using discriminative TM\n");
				PhraseTableScoresFeaturizer<IString> dTMFeaturizer = new PhraseTableScoresFeaturizer<IString>(false, true);
				pharaohFeaturizers.add(dTMFeaturizer);
			}
			
			// Precomputed phrase to phrase translation scores
			phraseTableScoresFeaturizer = new PhraseTableScoresFeaturizer<IString>();
			pharaohFeaturizers.add(phraseTableScoresFeaturizer);
			
			// Linear distortion
			linearDistortionFeaturizer = new LinearDistortionFeaturizer<IString>();
			pharaohFeaturizers.add(linearDistortionFeaturizer);
			
			// Word Penalty
			wordPenaltyFeaturizer = new WordPenaltyFeaturizer<IString>();
			pharaohFeaturizers.add(wordPenaltyFeaturizer);
			
			// Unknown Word Featurizer
			unknownWordFeaturizer = new UnknownWordFeaturizer<IString>();
			pharaohFeaturizers.add(unknownWordFeaturizer);
			
			// return combined model
			return new CombinedFeaturizer<IString,String>(pharaohFeaturizers);
		}
		
		throw new RuntimeException(String.format("Unrecognized featurizer '%s'", featurizerName));
	}

}
