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

  public static final String CONCATENATIVE_LIST_GENERATOR = "tablelist";
  public static final String PSEUDO_PHARAOH_GENERATOR = "pseudopharaoh";
  public static final String DTU_GENERATOR = "dtu";
  public static final String DYNAMIC_GENERATOR = "dpt";
  public static final String PHAROAH_PHRASE_TABLE = "pharaohphrasetable";
  public static final String PHAROAH_PHRASE_TABLE_ALT = "ppt";
  public static final String FILENAME_SEPARATOR = ":";

  /**
   * Factory method for phrase table loading.
   * 
   * @param dropUnknownWords
   * @param pgSpecs
   * @return
   * @throws IOException
   */
  @SuppressWarnings("unchecked")
  static public <FV> Pair<PhraseGenerator<IString,FV>,List<PhraseTable<IString>>> factory(
      Boolean dropUnknownWords,
      String... pgSpecs) throws IOException {

    if (pgSpecs.length == 0) {
      throw new RuntimeException(
          "PhraseGenerator specifier is empty. PhraseGenerators "
              + "must be explicitly identified and parameterized.");
    }

    String pgName = pgSpecs[0].toLowerCase();

    if (pgName.equals(CONCATENATIVE_LIST_GENERATOR)) {
      List<PhraseGenerator<IString,FV>> generators = new LinkedList<PhraseGenerator<IString,FV>>();
      List<PhraseTable<IString>> tables = Generics.newLinkedList();
      
      for (int i = 1; i < pgSpecs.length; i++) {
        String[] fields = pgSpecs[i].split(":");
        if (fields.length != 2) {
          throw new RuntimeException(String.format(
              "Expected the pair (phrasetable type):(filename), got '%s'",
              pgSpecs[i]));
        }
        String type = fields[0].toLowerCase();
        String filename = fields[1];
        if (type.equals(PHAROAH_PHRASE_TABLE)
            || type.equals(PHAROAH_PHRASE_TABLE_ALT)) {
          PhraseGenerator<IString,FV> pt = new FlatPhraseTable<FV>(filename);
          generators.add(pt);
          tables.add((PhraseTable<IString>) pt);
        
        } else if (type.equals(DTU_GENERATOR)) {
          PhraseGenerator<IString,FV> pt = new DTUTable<FV>(filename);
          generators.add(pt);
          tables.add((PhraseTable<IString>) pt);
        
        } else {
          throw new RuntimeException(String.format(
              "Unknown phrase table type: '%s'\n", type));
        }
      }
      Pair<PhraseGenerator<IString,FV>,List<PhraseTable<IString>>> pair =
          new Pair<PhraseGenerator<IString,FV>,List<PhraseTable<IString>>>(
              new CombinedPhraseGenerator<IString,FV>(generators, CombinedPhraseGenerator.Type.CONCATENATIVE), 
              tables);
      return pair;
    
    } else if (pgName.equals(PSEUDO_PHARAOH_GENERATOR)
        || pgName.equals(DTU_GENERATOR)) {

      final boolean withGaps = pgName.equals(DTU_GENERATOR);

      List<PhraseGenerator<IString,FV>> generators = new LinkedList<PhraseGenerator<IString,FV>>();
      List<PhraseTable<IString>> tables = Generics.newLinkedList();
      int phraseLimit = -1;
      if (pgSpecs.length == 3) {
        String phraseLimitStr = pgSpecs[2];
        phraseLimit = Integer.parseInt(phraseLimitStr);
      }

      String[] filenames = pgSpecs[1].split(FILENAME_SEPARATOR);
      for (String filename : filenames) {
        PhraseGenerator<IString,FV> pt = withGaps ? new DTUTable<FV>(filename) : new FlatPhraseTable<FV>(filename);
        generators.add(pt);
        tables.add((PhraseTable<IString>) pt);
      }

      CombinedPhraseGenerator<IString,FV> gen = phraseLimit == -1 ? 
          new CombinedPhraseGenerator<IString,FV>(generators, CombinedPhraseGenerator.Type.CONCATENATIVE) :
            new CombinedPhraseGenerator<IString,FV>(generators, CombinedPhraseGenerator.Type.CONCATENATIVE,
                phraseLimit);
      Pair<PhraseGenerator<IString,FV>,List<PhraseTable<IString>>> pair =
          new Pair<PhraseGenerator<IString,FV>,List<PhraseTable<IString>>>(
              gen, tables);
      return pair;
    }

    throw new RuntimeException(String.format("Unknown phrase generator '%s'%n",
        pgName));
  }
}
