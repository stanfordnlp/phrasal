package edu.stanford.nlp.mt.base;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.PhraseGeneratorFactory;
import edu.stanford.nlp.mt.decoder.util.Scorer;

/**
 * 
 * @author Daniel Cer
 * 
 * @param <TK>
 */
public class CombinedPhraseGenerator<TK,FV> implements PhraseGenerator<TK,FV> {
  static public final int FORCE_ADD_LIMIT = Integer.MAX_VALUE; // 200;
  static public final String DEBUG_OPT = "CombinedPhraseGeneratorDebug";
  static public final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_OPT, "false"));

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  public enum Type {
    CONCATENATIVE, STRICT_DOMINANCE
  }

  static public final Type DEFAULT_TYPE = Type.CONCATENATIVE;
  static public final int DEFAULT_PHRASE_LIMIT = 50;

  final List<PhraseGenerator<TK,FV>> phraseGenerators;
  final Type type;
  final int phraseLimit;

  private void addToMap(ConcreteTranslationOption<TK> opt,
      Map<CoverageSet, List<ConcreteTranslationOption<TK>>> optsMap) {
    List<ConcreteTranslationOption<TK>> optList = optsMap
        .get(opt.foreignCoverage);
    if (optList == null) {
      optList = new LinkedList<ConcreteTranslationOption<TK>>();
      optsMap.put(opt.foreignCoverage, optList);
    }
    optList.add(opt);
  }

  public int getPhraseLimit() {
    return phraseLimit;
  }

  @Override
  public List<ConcreteTranslationOption<TK>> translationOptions(
      Sequence<TK> sequence, List<Sequence<TK>> targets, int translationId, Scorer<FV> scorer) {
    Map<CoverageSet, List<ConcreteTranslationOption<TK>>> optsMap = new HashMap<CoverageSet, List<ConcreteTranslationOption<TK>>>();
    
    if (DEBUG) {
      System.err.printf(
          "CombinedPhraseGenerator#translationOptions type: %s\n", type);
    }

    for (PhraseGenerator<TK,FV> phraseGenerator : phraseGenerators) {
      phraseGenerator.setCurrentSequence(sequence, targets);
    }

    if (type.equals(Type.CONCATENATIVE)) {
      for (PhraseGenerator<TK,FV> phraseGenerator : phraseGenerators) {
         if (DEBUG) {
            System.err.println("PhraseGenerator: "+phraseGenerator.getClass().getCanonicalName());
         }
         try {
           for (ConcreteTranslationOption<TK> opt : phraseGenerator
              .translationOptions(sequence, targets, translationId, scorer)) {
             if (DEBUG) {
               System.err.println("  opt: " + opt);
             }
                      
             addToMap(opt, optsMap);
           }
         } catch (Exception e) {
            System.err.printf("Warning %s threw exception %s", phraseGenerator.getClass().getCanonicalName(), e);
            e.printStackTrace();
         }
      }
    } else if (type.equals(Type.STRICT_DOMINANCE)) {
      CoverageSet coverage = new CoverageSet(sequence.size());
      for (PhraseGenerator<TK,FV> phraseGenerator : phraseGenerators) {
        if (DEBUG) {
          System.err.printf("Generator: %s\n", phraseGenerator.getClass()
              .getName());
        }
        List<ConcreteTranslationOption<TK>> potentialOptions = phraseGenerator
            .translationOptions(sequence, targets, translationId, scorer);
        BitSet novelCoverage = new CoverageSet(sequence.size());
        for (ConcreteTranslationOption<TK> option : potentialOptions) {
          if (DEBUG) {
            System.err.println("  opt: " + option);
          }
          if (coverage.intersects(option.foreignCoverage)) {
            if (DEBUG) {
              System.err.printf("Skipping %s intersects %s\n", coverage,
                  option.foreignCoverage);
              System.err.printf("%s\n--\n", option);
            }
            continue;
          }
          novelCoverage.or(option.foreignCoverage);
          addToMap(option, optsMap);
        }
        coverage.or(novelCoverage);
      }
    } else {
      throw new RuntimeException(String.format(
          "Unsupported combination type: %s", type));
    }

    if (DEBUG) { 
       System.err.println("All preCutOpts:");
       System.err.println("===============");
       for (List<ConcreteTranslationOption<TK>> preCutOpts : optsMap.values()) {       
             System.err.println(preCutOpts);
       }   
    }
    
    List<ConcreteTranslationOption<TK>> cutoffOpts = new LinkedList<ConcreteTranslationOption<TK>>();
    for (List<ConcreteTranslationOption<TK>> preCutOpts : optsMap.values()) {
      int sz = preCutOpts.size();
      if (sz <= phraseLimit) {
        cutoffOpts.addAll(preCutOpts);
        continue;
      }

      List<ConcreteTranslationOption<TK>> preCutOptsArray = new ArrayList<ConcreteTranslationOption<TK>>(
          preCutOpts);

      Collections.sort(preCutOptsArray);

      if (DEBUG) {
        System.err.println("Sorted Options");
        for (ConcreteTranslationOption<TK> opt : preCutOpts) {
          System.err.println("--");
          System.err.printf("%s => %s : %f\n", opt.abstractOption.foreign,
              opt.abstractOption.translation, opt.isolationScore);
          System.err.printf("%s\n", Arrays.toString(opt.abstractOption.scores));
        }
      }

      int preCutOptsArraySz = preCutOptsArray.size();

      int forceAddCnt = 0;
      for (int i = 0; (i < phraseLimit)
          || (phraseLimit == 0 && i < preCutOptsArraySz); i++) {
        if (preCutOptsArray.get(i).abstractOption.forceAdd) {
          forceAddCnt++;
        }
        cutoffOpts.add(preCutOptsArray.get(i));
      }

      if (phraseLimit != 0)
        for (int i = phraseLimit; i < preCutOptsArraySz
            && forceAddCnt < FORCE_ADD_LIMIT; i++) {
          if (preCutOptsArray.get(i).abstractOption.forceAdd) {
            cutoffOpts.add(preCutOptsArray.get(i));
            forceAddCnt++;
          }
        }
    }

    return cutoffOpts;
  }

  /**
	 * 
	 */
  public CombinedPhraseGenerator(List<PhraseGenerator<TK,FV>> phraseGenerators) {
    this.phraseGenerators = phraseGenerators;
    this.type = DEFAULT_TYPE;
    this.phraseLimit = DEFAULT_PHRASE_LIMIT;
  }

  /**
	 * 
	 */
  public CombinedPhraseGenerator(List<PhraseGenerator<TK,FV>> phraseGenerators,
      Type type) {
    this.phraseGenerators = phraseGenerators;
    this.type = type;
    this.phraseLimit = DEFAULT_PHRASE_LIMIT;
  }

  /**
	 * 
	 */
  public CombinedPhraseGenerator(List<PhraseGenerator<TK,FV>> phraseGenerators,
      Type type, int phraseLimit) {
    this.phraseGenerators = phraseGenerators;
    this.type = type;
    this.phraseLimit = phraseLimit;
  }

  /**
   * 
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.out
          .printf("Usage:\n\tjava ...PhraseTableListGenerator (pharse table type):(filename) (pharse table type1):(filename1) ...\n");
      System.exit(-1);
    }

    String[] conf = new String[args.length + 1];
    conf[0] = PhraseGeneratorFactory.CONCATENATIVE_LIST_GENERATOR;
    System.arraycopy(args, 0, conf, 1, args.length);

    PhraseGenerator<IString,String> ptGen = PhraseGeneratorFactory.factory(null, true,
        conf);
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    System.out.println("Interactive SimpleLookupPhraseGenerator");
    System.out
        .println("Please enter foreign sentences for which you want to look up translation phrases.");
    System.out.println();
    for (String line; (line = reader.readLine()) != null;) {
      String[] tokens = line.split("\\s+");
      SimpleSequence<IString> sequence = new SimpleSequence<IString>(
          IStrings.toIStringArray(tokens));
      List<ConcreteTranslationOption<IString>> options = ptGen
          .translationOptions(sequence, null, -1, null);
      System.out.printf("Sequence: '%s'\n", sequence);
      System.out.println("Translation Options:\n");
      for (ConcreteTranslationOption<IString> option : options) {
        System.out.printf("\t%s -> %s coverage: %s score: %s\n",
            sequence.subsequence(option.foreignCoverage),
            option.abstractOption.translation, option.foreignCoverage,
            Arrays.toString(option.abstractOption.scores));
      }
    }
  }

  @Override
  public void setCurrentSequence(Sequence<TK> foreign,
      List<Sequence<TK>> tranList) {
    for (PhraseGenerator<TK,FV> pGen : phraseGenerators) {
      pGen.setCurrentSequence(foreign, tranList);
    }

  }

  @Override
  public int longestForeignPhrase() {
    int longest = -1;
    for (PhraseGenerator<TK,FV> phraseGenerator : phraseGenerators) {
      if (longest < phraseGenerator.longestForeignPhrase())
        longest = phraseGenerator.longestForeignPhrase();
    }
    return longest;
  }
}
