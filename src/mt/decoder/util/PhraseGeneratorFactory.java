package mt.decoder.util;

import java.util.*;
import java.io.*;

import mt.base.CombinedPhraseGenerator;
import mt.base.DynamicPhraseTable;
import mt.base.IdentityPhraseGenerator;
import mt.base.PharaohPhraseTable;
import mt.base.SymbolFilter;
import mt.decoder.feat.IsolatedPhraseFeaturizer;
import mt.decoder.feat.UnknownWordFeaturizer;
import mt.tools.NumericFilter;

import edu.stanford.nlp.util.IString;

/**
 * 
 * @author Daniel Cer
 */
public class PhraseGeneratorFactory {
	
	public static final String CONCATENATIVE_LIST_GENERATOR = "tablelist"; 
	public static final String BASIC_AUGMENTED_CONCATENATIVE_LIST_GENERATOR = "augmentedtablelist";
	public static final String PSEUDO_PHARAOH_GENERATOR = "pseudopharaoh";
	public static final String DYNAMIC_GENERATOR = "dpt";
	public static final String PHAROAH_PHRASE_TABLE = "pharaohphrasetable";
	public static final String PHAROAH_PHRASE_TABLE_ALT = "ppt";
	
	/**
	 * 
	 * @param <T>
	 * @param t
	 * @param pgSpecs
	 * @return
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	static public <FV>  PhraseGenerator<IString> factory(IsolatedPhraseFeaturizer<IString, FV> phraseFeaturizer, Scorer<FV> scorer, String... pgSpecs) throws IOException {
		
		if (pgSpecs.length == 0) {
			throw new RuntimeException(
					"PhraseGenerator specifier is empty. PhraseGenerators "+
					"must be explicitly identified and parameterized.");
		}
		
		String pgName = pgSpecs[0].toLowerCase();
		
		if (pgName.equals(CONCATENATIVE_LIST_GENERATOR) ||
				pgName.equals(BASIC_AUGMENTED_CONCATENATIVE_LIST_GENERATOR))  {
			List<PhraseGenerator<IString>> phraseTables = new LinkedList<PhraseGenerator<IString>>();
			
			for (int i = 1; i < pgSpecs.length; i++) {
				String[] fields = pgSpecs[i].split(":");
				if (fields.length != 2) {
					throw new RuntimeException(
							String.format("Expected the pair (phrasetable type):(filename), got '%s'", pgSpecs[i]));
				}
				String type = fields[0].toLowerCase();
				String filename = fields[1];
				if (type.equals(PHAROAH_PHRASE_TABLE) || type.equals(PHAROAH_PHRASE_TABLE_ALT)) {
					phraseTables.add((new PharaohPhraseTable(phraseFeaturizer, scorer, filename)));					
				} else {
					throw new RuntimeException(String.format("Unknown phrase table type: '%s'\n", type));
				}
			}
			if (pgName.equals(CONCATENATIVE_LIST_GENERATOR)) {
				return new CombinedPhraseGenerator<IString,FV>(phraseTables, CombinedPhraseGenerator.Type.CONCATENATIVE);
			} else if (pgName.equals(BASIC_AUGMENTED_CONCATENATIVE_LIST_GENERATOR)){
				List<PhraseGenerator<IString>> augmentedList = new LinkedList<PhraseGenerator<IString>>();
				
				// special purpose numeric identity phrase translator
				augmentedList.add(new IdentityPhraseGenerator<IString,FV>(phraseFeaturizer, scorer, new NumericFilter<IString>()));

				// user specified translation tables and equal in ranking special purpose phrase generators
				List<PhraseGenerator<IString>> userEquivList = new LinkedList<PhraseGenerator<IString>>(phraseTables); // user phrase tables
				
				userEquivList.add(new IdentityPhraseGenerator<IString,FV>(phraseFeaturizer, scorer, new SymbolFilter())); // symbol identity phrase generator
				
				CombinedPhraseGenerator<IString,FV> equivUserRanking = new CombinedPhraseGenerator<IString,FV>(userEquivList);
				augmentedList.add(equivUserRanking);
				
				// catch all foreign phrase identity generator
				augmentedList.add(new IdentityPhraseGenerator<IString,FV>(phraseFeaturizer, scorer));
				
				return new CombinedPhraseGenerator<IString,FV>(augmentedList, CombinedPhraseGenerator.Type.STRICT_DOMINANCE);
			}
		} else if (pgName.equals(PSEUDO_PHARAOH_GENERATOR)) {
			List<PhraseGenerator<IString>> pharoahList = new LinkedList<PhraseGenerator<IString>>();
			if (pgSpecs.length < 2) {
				throw new RuntimeException("A phrase table filename must be specified.");
			}
			if (pgSpecs.length > 3) {
				throw new RuntimeException("One and only one phrase table filename should be specified followed by an optional phrase limit.");
			}
			int phraseLimit = -1;
			if (pgSpecs.length == 3) {
				String phraseLimitStr = pgSpecs[2];
				try {
					phraseLimit = Integer.parseInt(phraseLimitStr);
				} catch (NumberFormatException e) {
					throw new RuntimeException(String.format("Specified phrase limit, %s, can not be parsed as an integer value\n", phraseLimitStr));
				}
			}
			
			String filename = pgSpecs[1];
			pharoahList.add(new PharaohPhraseTable<FV>(phraseFeaturizer, scorer, filename));
			pharoahList.add(new IdentityPhraseGenerator<IString,FV>(phraseFeaturizer, scorer, UnknownWordFeaturizer.UNKNOWN_PHRASE_TAG));
      // TODO: check here

      if (phraseLimit == -1) {
				return new CombinedPhraseGenerator<IString,FV>(pharoahList, CombinedPhraseGenerator.Type.STRICT_DOMINANCE);
			} else {
				return new CombinedPhraseGenerator<IString,FV>(pharoahList, CombinedPhraseGenerator.Type.STRICT_DOMINANCE, phraseLimit);
			}
		}  else if (pgName.equals(DYNAMIC_GENERATOR)) {
			List<PhraseGenerator<IString>> ptgList = new LinkedList<PhraseGenerator<IString>>();
			int phraseLimit = -1;
			if (pgSpecs.length == 3) {
				String phraseLimitStr = pgSpecs[2];
				try {
					phraseLimit = Integer.parseInt(phraseLimitStr);
				} catch (NumberFormatException e) {
					throw new RuntimeException(String.format("Specified phrase limit, %s, can not be parsed as an integer value\n", phraseLimitStr));
				}
			}
			
			String filename = pgSpecs[1];
			
			if (filename.contains(".db:")) {
				String model1S2T = null, model1T2S = null;
				String[] fields = filename.split(":");
				filename = fields[0];
				model1S2T = fields[1];
				model1T2S = fields[2];
				ptgList.add(new DynamicPhraseTable<FV>(phraseFeaturizer, scorer, filename, model1S2T, model1T2S));
			} else {
				ptgList.add(new DynamicPhraseTable<FV>(phraseFeaturizer, scorer, filename));
			}
			
			ptgList.add(new IdentityPhraseGenerator<IString,FV>(phraseFeaturizer, scorer, UnknownWordFeaturizer.UNKNOWN_PHRASE_TAG));
			
			if (phraseLimit == -1) {
				return new CombinedPhraseGenerator<IString,FV>(ptgList, CombinedPhraseGenerator.Type.STRICT_DOMINANCE);
			} else {
				return new CombinedPhraseGenerator<IString,FV>(ptgList, CombinedPhraseGenerator.Type.STRICT_DOMINANCE, phraseLimit);
			}
		}
		
		throw new RuntimeException(String.format("Unknown phrase generator '%s'\n", pgName));
	}	
}
