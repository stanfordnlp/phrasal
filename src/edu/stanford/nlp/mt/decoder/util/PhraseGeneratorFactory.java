package edu.stanford.nlp.mt.decoder.util;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.mt.base.BinaryPhraseTable;
import edu.stanford.nlp.mt.base.CombinedPhraseGenerator;
import edu.stanford.nlp.mt.base.FlatPhraseTable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.DTUTable;
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;

/**
 * 
 * @author Daniel Cer
 */
public class PhraseGeneratorFactory {

  public static final String CONCATENATIVE_LIST_GENERATOR = "tablelist";
  public static final String BASIC_AUGMENTED_CONCATENATIVE_LIST_GENERATOR = "augmentedtablelist";
  public static final String PSEUDO_PHARAOH_GENERATOR = "pseudopharaoh";
  public static final String DTU_GENERATOR = "dtu";
  public static final String DYNAMIC_GENERATOR = "dpt";
  public static final String PHAROAH_PHRASE_TABLE = "pharaohphrasetable";
  public static final String PHAROAH_PHRASE_TABLE_ALT = "ppt";
  public static final String NEW_DYNAMIC_GENERATOR = "newdg";

  static public <FV> PhraseGenerator<IString,FV> factory(
      IsolatedPhraseFeaturizer<IString, FV> phraseFeaturizer,
      Boolean dropUnknownWords, String... pgSpecs) throws IOException {

    if (pgSpecs.length == 0) {
      throw new RuntimeException(
          "PhraseGenerator specifier is empty. PhraseGenerators "
              + "must be explicitly identified and parameterized.");
    }

    String pgName = pgSpecs[0].toLowerCase();

    if (pgName.equals(CONCATENATIVE_LIST_GENERATOR)
        || pgName.equals(BASIC_AUGMENTED_CONCATENATIVE_LIST_GENERATOR)) {
      List<PhraseGenerator<IString,FV>> phraseTables = new LinkedList<PhraseGenerator<IString,FV>>();

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
          phraseTables.add((new FlatPhraseTable<FV>(phraseFeaturizer,
              filename)));
        } else if (type.equals(DTU_GENERATOR)) {
          phraseTables
              .add((new DTUTable<FV>(phraseFeaturizer, filename)));
        } else {
          throw new RuntimeException(String.format(
              "Unknown phrase table type: '%s'\n", type));
        }
      }
      if (pgName.equals(CONCATENATIVE_LIST_GENERATOR)) {
        return new CombinedPhraseGenerator<IString,FV>(phraseTables,
            CombinedPhraseGenerator.Type.CONCATENATIVE);
      } else if (pgName.equals(BASIC_AUGMENTED_CONCATENATIVE_LIST_GENERATOR)) {
        List<PhraseGenerator<IString,FV>> augmentedList = new LinkedList<PhraseGenerator<IString,FV>>();

        // user specified translation tables and equal in ranking special
        // purpose phrase generators
        List<PhraseGenerator<IString,FV>> userEquivList = new LinkedList<PhraseGenerator<IString,FV>>(
            phraseTables); // user phrase tables

        CombinedPhraseGenerator<IString,FV> equivUserRanking = new CombinedPhraseGenerator<IString,FV>(
            userEquivList);
        augmentedList.add(equivUserRanking);

       
        return new CombinedPhraseGenerator<IString,FV>(augmentedList,
            CombinedPhraseGenerator.Type.STRICT_DOMINANCE);
      }
    } else if (pgName.equals(PSEUDO_PHARAOH_GENERATOR)
        || pgName.equals(DTU_GENERATOR)) {

      boolean withGaps = pgName.equals(DTU_GENERATOR);

      List<PhraseGenerator<IString,FV>> pharoahList = new LinkedList<PhraseGenerator<IString,FV>>();
      List<PhraseGenerator<IString,FV>> finalList = new LinkedList<PhraseGenerator<IString,FV>>();
      if (pgSpecs.length < 2) {
        throw new RuntimeException("A phrase table filename must be specified.");
      }
      if (pgSpecs.length > 3) {
        throw new RuntimeException("Unrecognized additional material.");
      }
      int phraseLimit = -1;
      if (pgSpecs.length == 3) {
        String phraseLimitStr = pgSpecs[2];
        try {
          phraseLimit = Integer.parseInt(phraseLimitStr);
        } catch (NumberFormatException e) {
          throw new RuntimeException(
              String
                  .format(
                      "Specified phrase limit, %s, can not be parsed as an integer value\n",
                      phraseLimitStr));
        }
      }

      String[] filenames = pgSpecs[1].split(System
          .getProperty("path.separator"));
      for (String filename : filenames) {
        // System.err.printf("loading pt: %s\n", filename);
        if (withGaps)
          pharoahList.add(new DTUTable<FV>(phraseFeaturizer, filename));
        else
          if (new File(filename).isDirectory()) {
             pharoahList.add(new BinaryPhraseTable<FV>(phraseFeaturizer,
                   filename)); 
          } else {
            pharoahList.add(new FlatPhraseTable<FV>(phraseFeaturizer, 
              filename));
          }
      }

      finalList.add(new CombinedPhraseGenerator<IString,FV>(pharoahList,
          CombinedPhraseGenerator.Type.CONCATENATIVE));
      
      CombinedPhraseGenerator.Type combinationType = withGaps ? CombinedPhraseGenerator.Type.CONCATENATIVE
          : CombinedPhraseGenerator.Type.STRICT_DOMINANCE;
      if (phraseLimit == -1) {
        return new CombinedPhraseGenerator<IString,FV>(finalList, combinationType);
      } else {
        return new CombinedPhraseGenerator<IString,FV>(finalList, combinationType,
            phraseLimit);
      }
    }

    throw new RuntimeException(String.format("Unknown phrase generator '%s'\n",
        pgName));
  }
}
