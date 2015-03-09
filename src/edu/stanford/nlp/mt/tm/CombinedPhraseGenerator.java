package edu.stanford.nlp.mt.tm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * 
 * @author Daniel Cer
 * 
 * @param <TK>
 */
public class CombinedPhraseGenerator<TK,FV> implements PhraseGenerator<TK,FV> {
  static public final int FORCE_ADD_LIMIT = Integer.MAX_VALUE; // 200;
  static public final boolean DEBUG = false;

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  // Method of combining rules from multiple translation models.
  public enum Type {
    CONCATENATIVE, STRICT_DOMINANCE
  }

  static public final Type DEFAULT_TYPE = Type.CONCATENATIVE;
  static public final int DEFAULT_PHRASE_LIMIT = 50;

  private final List<PhraseGenerator<TK,FV>> phraseGenerators;
  private final Type type;
  private final int ruleQueryLimit;

  /**
   * Add to the rule list that is being constructed during a query
   * 
   * @param opt
   * @param ruleLists
   * @param modelId 
   */
  private void addToRuleList(ConcreteRule<TK,FV> opt,
      Map<CoverageSet, List<List<ConcreteRule<TK, FV>>>> ruleLists, int modelId) {
    if ( ! ruleLists.containsKey(opt.sourceCoverage)) {
      ruleLists.put(opt.sourceCoverage, new LinkedList<>());
    }
    if ( modelId >= ruleLists.get(opt.sourceCoverage).size()) {
      for (int i = 0; i <= modelId; ++i) {
        ruleLists.get(opt.sourceCoverage).add(new LinkedList<>());
      }
    }
    ruleLists.get(opt.sourceCoverage).get(modelId).add(opt);
  }
  
  @Override
  public List<String> getFeatureNames() {
    List<String> featureNames = new ArrayList<>();
    for (PhraseGenerator<TK,FV> generator : phraseGenerators) {
      featureNames.addAll(generator.getFeatureNames());
    }
    return featureNames;
  }

  @Override
  public RuleGrid<TK, FV> getRuleGrid(Sequence<TK> source, InputProperties sourceInputProperties, 
      List<Sequence<TK>> targets, int sourceInputId, Scorer<FV> scorer) {
    final Map<CoverageSet, List<List<ConcreteRule<TK,FV>>>> ruleLists = new HashMap<>(source.size() * source.size());
    
    if (DEBUG) {
      System.err.printf(
          "CombinedPhraseGenerator#translationOptions type: %s\n", type);
    }

    // TODO(spenceg) A decoder-local TM (if active) should be added to the list of phraseGenerators
    
    int modelId = 0;
    if (type.equals(Type.CONCATENATIVE)) {
      for (PhraseGenerator<TK,FV> phraseGenerator : phraseGenerators) {
         if (DEBUG) {
            System.err.println("PhraseGenerator: " + phraseGenerator.getClass().getCanonicalName());
         }
         
         try {
           for (ConcreteRule<TK,FV> opt : phraseGenerator.getRules(source, sourceInputProperties, 
               targets, sourceInputId, scorer)) {
             if (DEBUG) {
               System.err.println("  opt: " + opt);
             }       
             addToRuleList(opt, ruleLists, modelId);
           }
         
         } catch (Exception e) {
            System.err.printf("Warning %s threw exception %s", phraseGenerator.getClass().getCanonicalName(), e);
            e.printStackTrace();
         }
         ++modelId;
      }
    
    } else if (type.equals(Type.STRICT_DOMINANCE)) {
      CoverageSet coverage = new CoverageSet(source.size());
      for (PhraseGenerator<TK,FV> phraseGenerator : phraseGenerators) {
        
        if (DEBUG) {
          System.err.printf("Generator: %s%n", phraseGenerator.getClass()
              .getName());
        }
        
        CoverageSet novelCoverage = new CoverageSet(source.size());
        for (ConcreteRule<TK,FV> option : phraseGenerator
            .getRules(source, sourceInputProperties, targets, sourceInputId, scorer)) {
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
          addToRuleList(option, ruleLists, modelId);
        }
        coverage.or(novelCoverage);
        ++modelId;
      }
    
    } else {
      throw new RuntimeException(String.format(
          "Unsupported combination type: %s", type));
    }

    final RuleGrid<TK,FV> ruleGrid = new RuleGrid<TK,FV>(source.size());
    if (modelId == 1) {
      for (CoverageSet coverage : ruleLists.keySet()) {
        List<List<ConcreteRule<TK,FV>>> ruleList = ruleLists.get(coverage);
        assert ruleList.size() <= 1;
        int numRules = 0;
        for (ConcreteRule<TK,FV> rule : ruleList.get(0)) {
          if (numRules >= ruleQueryLimit) break;
          ruleGrid.addEntry(rule);
          ++numRules;
        }
      }
      
    } else {
      // TODO(spenceg) Merge the sorted lists of rules, and create a RuleGrid directly.
      for (CoverageSet coverage : ruleLists.keySet()) {
        List<List<ConcreteRule<TK,FV>>> ruleList = ruleLists.get(coverage);

        // Effectively cube pruning!
        Queue<Item<TK,FV>> pq = new PriorityQueue<Item<TK,FV>>(3);
        for (List<ConcreteRule<TK,FV>> list : ruleList) {
          if (list.size() > 0) {
            pq.add(new Item<TK,FV>(list.remove(0), list));
          }
        }
        int numPoppedItems = 0;
        while (numPoppedItems < ruleQueryLimit && ! pq.isEmpty()) {
          Item<TK, FV> item = pq.poll();
          if (item == null) {
            break;
          } else {
            ruleGrid.addEntry(item.rule);
            if (item.list.size() > 0) {
              pq.add(new Item<TK,FV>(item.list.remove(0), item.list));
            }
          }
        }
      }
    }
    
//    List<ConcreteRule<TK,FV>> cutoffOpts = new LinkedList<>();
//    for (Map<CoverageSet, List<ConcreteRule<TK,FV>>> optsMap : ruleLists) {
//    for (List<ConcreteRule<TK,FV>> preCutOpts : optsMap.values()) {
//      int sz = preCutOpts.size();
//      if (sz <= ruleQueryLimit) {
//        cutoffOpts.addAll(preCutOpts);
//        continue;
//      }
//
//      List<ConcreteRule<TK,FV>> preCutOptsArray = new ArrayList<ConcreteRule<TK,FV>>(
//          preCutOpts);
//
//      Collections.sort(preCutOptsArray);
//      
//      if (DEBUG) {
//        System.err.println("Sorted Options");
//        for (ConcreteRule<TK,FV> opt : preCutOpts) {
//          System.err.println("--");
//          System.err.printf("%s => %s : %f\n", opt.abstractRule.source,
//              opt.abstractRule.target, opt.isolationScore);
//          System.err.printf("%s\n", Arrays.toString(opt.abstractRule.scores));
//        }
//      }
//
//      int preCutOptsArraySz = preCutOptsArray.size();
//
//      int forceAddCnt = 0;
//      for (int i = 0; (i < ruleQueryLimit)
//          || (ruleQueryLimit == 0 && i < preCutOptsArraySz); i++) {
//        if (preCutOptsArray.get(i).abstractRule.forceAdd) {
//          forceAddCnt++;
//        }
//        cutoffOpts.add(preCutOptsArray.get(i));
//      }
//
//      if (ruleQueryLimit != 0) {
//        for (int i = ruleQueryLimit; i < preCutOptsArraySz
//            && forceAddCnt < FORCE_ADD_LIMIT; i++) {
//          if (preCutOptsArray.get(i).abstractRule.forceAdd) {
//            cutoffOpts.add(preCutOptsArray.get(i));
//            forceAddCnt++;
//          }
//        }
//      }
//    }
//    }

//    return cutoffOpts;
    return ruleGrid;
  }
  
  protected static class Item<TK,FV> implements Comparable<Item<TK,FV>> {
    public final ConcreteRule<TK,FV> rule;
    public final List<ConcreteRule<TK,FV>> list;

    public Item(ConcreteRule<TK,FV> rule, List<ConcreteRule<TK,FV>> list) {
      this.rule = rule;
      this.list = list;
    }

    @Override
    public int compareTo(Item<TK,FV> o) {
      if (rule == null && o.rule == null) {
        return 0;
      } else if (rule == null) {
        return -1;
      } else if (o.rule == null) {
        return 1;
      }
      return this.rule.compareTo(o.rule);
    }
  }

  /**
   * Constructor.
   * 
   * @param phraseGenerators
   */
  public CombinedPhraseGenerator(List<PhraseGenerator<TK,FV>> phraseGenerators) {
    this.phraseGenerators = phraseGenerators;
    this.type = DEFAULT_TYPE;
    this.ruleQueryLimit = DEFAULT_PHRASE_LIMIT;
  }

  /**
   * Constructor.
   * 
   * @param phraseGenerators
   * @param type
   */
  public CombinedPhraseGenerator(List<PhraseGenerator<TK,FV>> phraseGenerators,
      Type type) {
    this.phraseGenerators = phraseGenerators;
    this.type = type;
    this.ruleQueryLimit = DEFAULT_PHRASE_LIMIT;
  }

  /**
   * Constructor.
   * 
   * @param phraseGenerators
   * @param type
   * @param phraseLimit
   */
  public CombinedPhraseGenerator(List<PhraseGenerator<TK,FV>> phraseGenerators,
      Type type, int phraseLimit) {
    this.phraseGenerators = phraseGenerators;
    this.type = type;
    this.ruleQueryLimit = phraseLimit;
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

  @Override
  public String getName() {
    return this.getClass().getSimpleName();
  }

  @Override
  public List<ConcreteRule<TK, FV>> getRules(Sequence<TK> source,
      InputProperties sourceInputProperties, List<Sequence<TK>> targets,
      int sourceInputId, Scorer<FV> scorer) {
    // TODO Auto-generated method stub
    return null;
  }
}
