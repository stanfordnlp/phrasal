package edu.stanford.nlp.mt.pt;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

/**
 * Load a phrase table from a filename
 * 
 * @author Daniel Cer
 * @author Spence Green
 */
public class PhraseGeneratorFactory {

  public static final String PSEUDO_PHARAOH_GENERATOR = "pseudopharaoh";
  public static final String DTU_GENERATOR = "dtu";
  public static final String QUERY_LIMIT_OPTION = "querylimit";
  public static final String FEATURE_PREFIX_OPTION = "featpref";
  public static final String SEPARATOR = ":";

  /**
   * Factory method for phrase table loading.
   * 
   * @param options
   * @return
   * @throws IOException
   */
  @SuppressWarnings("unchecked")
  static public <FV> Pair<PhraseGenerator<IString,FV>,List<PhraseTable<IString>>> factory(
      String pgName, String filenames, String...options) throws IOException {
    
    // Parse options
    int queryLimit = -1;
    String featurePrefix = null;
    for (String option : options) {
      String[] fields = option.split(SEPARATOR);
      assert fields.length == 2 : String.format("Invalid option: " + option);
      String key = fields[0];
      String value = fields[1];
      if (key.equals(QUERY_LIMIT_OPTION)) {
        queryLimit = Integer.parseInt(value);
      } else if (key.equals(FEATURE_PREFIX_OPTION)) {
        featurePrefix = value;
      }
    }
    
    // Create the phrase generators
    if (pgName.equals(PSEUDO_PHARAOH_GENERATOR)
        || pgName.equals(DTU_GENERATOR)) {

      final boolean withGaps = pgName.equals(DTU_GENERATOR);

      List<PhraseGenerator<IString,FV>> generators = new LinkedList<PhraseGenerator<IString,FV>>();
      List<PhraseTable<IString>> tables = Generics.newLinkedList();

      for (String filename : filenames.split(SEPARATOR)) {
        PhraseGenerator<IString,FV> pt;
        if (withGaps) {
          pt = new DTUTable<FV>(filename);
        } else {
          if (featurePrefix == null) {
            pt = new FlatPhraseTable<FV>(filename);
          } else {
            pt = new FlatPhraseTable<FV>(featurePrefix, filename);
          }
        }
        generators.add(pt);
        tables.add((PhraseTable<IString>) pt);
      }

      CombinedPhraseGenerator<IString,FV> gen = queryLimit == -1 ? 
          new CombinedPhraseGenerator<IString,FV>(generators, CombinedPhraseGenerator.Type.CONCATENATIVE) :
            new CombinedPhraseGenerator<IString,FV>(generators, CombinedPhraseGenerator.Type.CONCATENATIVE,
                queryLimit);
      Pair<PhraseGenerator<IString,FV>,List<PhraseTable<IString>>> pair =
          new Pair<PhraseGenerator<IString,FV>,List<PhraseTable<IString>>>(
              gen, tables);
      return pair;
    }

    throw new RuntimeException(String.format("Unknown phrase generator '%s'%n",
        pgName));
  }
}
