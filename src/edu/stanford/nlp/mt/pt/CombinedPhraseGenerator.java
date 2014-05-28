package edu.stanford.nlp.mt.pt;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.util.Generics;

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

  private void addToMap(ConcreteRule<TK,FV> opt,
      Map<CoverageSet, List<ConcreteRule<TK,FV>>> optsMap) {
    List<ConcreteRule<TK,FV>> optList = optsMap
        .get(opt.sourceCoverage);
    if (optList == null) {
      optList = new LinkedList<ConcreteRule<TK,FV>>();
      optsMap.put(opt.sourceCoverage, optList);
    }
    optList.add(opt);
  }

  public int getPhraseLimit() {
    return phraseLimit;
  }
  
  @Override
  public List<String> getFeatureNames() {
    List<String> featureNames = Generics.newArrayList();
    for (PhraseGenerator<TK,FV> generator : phraseGenerators) {
      featureNames.addAll(generator.getFeatureNames());
    }
    return featureNames;
  }

  @Override
  public List<ConcreteRule<TK,FV>> getRules(
      Sequence<TK> sequence, InputProperties sourceInputProperties, List<Sequence<TK>> targets, int sourceInputId, Scorer<FV> scorer) {
    Map<CoverageSet, List<ConcreteRule<TK,FV>>> optsMap = new HashMap<CoverageSet, List<ConcreteRule<TK,FV>>>();
    
    if (DEBUG) {
      System.err.printf(
          "CombinedPhraseGenerator#translationOptions type: %s\n", type);
    }

    if (type.equals(Type.CONCATENATIVE)) {
      for (PhraseGenerator<TK,FV> phraseGenerator : phraseGenerators) {
         if (DEBUG) {
            System.err.println("PhraseGenerator: "+phraseGenerator.getClass().getCanonicalName());
         }
         try {
           for (ConcreteRule<TK,FV> opt : phraseGenerator
              .getRules(sequence, sourceInputProperties, targets, sourceInputId, scorer)) {
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
        List<ConcreteRule<TK,FV>> potentialOptions = phraseGenerator
            .getRules(sequence, sourceInputProperties, targets, sourceInputId, scorer);
        BitSet novelCoverage = new CoverageSet(sequence.size());
        for (ConcreteRule<TK,FV> option : potentialOptions) {
          if (DEBUG) {
            System.err.println("  opt: " + option);
          }
          if (coverage.intersects(option.sourceCoverage)) {
            if (DEBUG) {
              System.err.printf("Skipping %s intersects %s\n", coverage,
                  option.sourceCoverage);
              System.err.printf("%s\n--\n", option);
            }
            continue;
          }
          novelCoverage.or(option.sourceCoverage);
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
       for (List<ConcreteRule<TK,FV>> preCutOpts : optsMap.values()) {       
             System.err.println(preCutOpts);
       }   
    }
    
    List<ConcreteRule<TK,FV>> cutoffOpts = new LinkedList<ConcreteRule<TK,FV>>();
    for (List<ConcreteRule<TK,FV>> preCutOpts : optsMap.values()) {
      int sz = preCutOpts.size();
      if (sz <= phraseLimit) {
        cutoffOpts.addAll(preCutOpts);
        continue;
      }

      List<ConcreteRule<TK,FV>> preCutOptsArray = new ArrayList<ConcreteRule<TK,FV>>(
          preCutOpts);

      Collections.sort(preCutOptsArray);

      if (DEBUG) {
        System.err.println("Sorted Options");
        for (ConcreteRule<TK,FV> opt : preCutOpts) {
          System.err.println("--");
          System.err.printf("%s => %s : %f\n", opt.abstractRule.source,
              opt.abstractRule.target, opt.isolationScore);
          System.err.printf("%s\n", Arrays.toString(opt.abstractRule.scores));
        }
      }

      int preCutOptsArraySz = preCutOptsArray.size();

      int forceAddCnt = 0;
      for (int i = 0; (i < phraseLimit)
          || (phraseLimit == 0 && i < preCutOptsArraySz); i++) {
        if (preCutOptsArray.get(i).abstractRule.forceAdd) {
          forceAddCnt++;
        }
        cutoffOpts.add(preCutOptsArray.get(i));
      }

      if (phraseLimit != 0)
        for (int i = phraseLimit; i < preCutOptsArraySz
            && forceAddCnt < FORCE_ADD_LIMIT; i++) {
          if (preCutOptsArray.get(i).abstractRule.forceAdd) {
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

  @Override
  public int longestSourcePhrase() {
    int longest = -1;
    for (PhraseGenerator<TK,FV> phraseGenerator : phraseGenerators) {
      if (longest < phraseGenerator.longestSourcePhrase())
        longest = phraseGenerator.longestSourcePhrase();
    }
    return longest;
  }

  @Override
  public void setFeaturizer(RuleFeaturizer<TK, FV> featurizer) {
    for (PhraseGenerator<TK,FV> phraseTable : phraseGenerators) {
      phraseTable.setFeaturizer(featurizer);
    }
  }

  @Override
  public int longestTargetPhrase() {
    int longest = -1;
    for (PhraseGenerator<TK,FV> phraseGenerator : phraseGenerators) {
      if (longest < phraseGenerator.longestTargetPhrase())
        longest = phraseGenerator.longestTargetPhrase();
    }
    return longest;
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

    PhraseGenerator<IString,String> ptGen = PhraseGeneratorFactory.factory(true, conf);
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    System.out.println("Interactive SimpleLookupPhraseGenerator");
    System.out
        .println("Please enter foreign sentences for which you want to look up translation phrases.");
    System.out.println();
    for (String line; (line = reader.readLine()) != null;) {
      String[] tokens = line.split("\\s+");
      SimpleSequence<IString> sequence = new SimpleSequence<IString>(
          IStrings.toIStringArray(tokens));
      List<ConcreteRule<IString,String>> options = ptGen
          .getRules(sequence, null, null, -1, null);
      System.out.printf("Sequence: '%s'\n", sequence);
      System.out.println("Translation Options:\n");
      for (ConcreteRule<IString,String> option : options) {
        System.out.printf("\t%s -> %s coverage: %s score: %s\n",
            sequence.subsequence(option.sourceCoverage),
            option.abstractRule.target, option.sourceCoverage,
            Arrays.toString(option.abstractRule.scores));
      }
    }
  }
}
