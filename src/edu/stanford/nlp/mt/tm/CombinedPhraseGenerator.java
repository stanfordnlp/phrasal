package edu.stanford.nlp.mt.tm;

import java.util.ArrayList;
import java.util.Collections;
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
 * Translation model query from multiple phrase tables.
 * 
 * @author Daniel Cer
 * 
 * @param <TK>
 */
public class CombinedPhraseGenerator<TK,FV> implements PhraseGenerator<TK,FV> {
  
  static public final boolean DEBUG = false;

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  static public final int DEFAULT_PHRASE_LIMIT = 50;

  private final List<PhraseGenerator<TK,FV>> phraseGenerators;
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

    // Support for decoder-local translation models
    List<PhraseGenerator<TK,FV>> translationModels = phraseGenerators;
    if (DecoderLocalTranslationModel.get() != null) {
      translationModels = new ArrayList<>(phraseGenerators);
      translationModels.add(DecoderLocalTranslationModel.get());
    }
    
    int modelId = 0;
    for (PhraseGenerator<TK,FV> phraseGenerator : translationModels) {
      for (ConcreteRule<TK,FV> opt : phraseGenerator.getRules(source, sourceInputProperties, targets, 
          sourceInputId, scorer)) {
        addToRuleList(opt, ruleLists, modelId);
      }
      ++modelId;
    }
    
    // Merge the lists of rules
    final RuleGrid<TK,FV> ruleGrid = new RuleGrid<TK,FV>(source.size());
    if (modelId == 1) {
      for (CoverageSet coverage : ruleLists.keySet()) {
        List<ConcreteRule<TK,FV>> ruleList = ruleLists.get(coverage).get(0);
        Collections.sort(ruleList);
        for (int i = 0, sz = ruleList.size(); i < ruleQueryLimit && i < sz; ++i) {
          ruleGrid.addEntry(ruleList.get(i));
        }
      }
      
    } else {
      for (CoverageSet coverage : ruleLists.keySet()) {
        List<List<ConcreteRule<TK,FV>>> ruleList = ruleLists.get(coverage);
        
        // Effectively cube pruning!
        Queue<Item<TK,FV>> pq = new PriorityQueue<Item<TK,FV>>(3);
        for (List<ConcreteRule<TK,FV>> list : ruleList) {
          Collections.sort(list);
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
    return ruleGrid;
  }
  
  /**
   * Queue item for merging multiple query lists.
   * 
   * @author Spence Green
   *
   * @param <TK>
   * @param <FV>
   */
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
    this(phraseGenerators, DEFAULT_PHRASE_LIMIT);
  }

  /**
   * Constructor.
   * 
   * @param phraseGenerators
   * @param phraseLimit
   */
  public CombinedPhraseGenerator(List<PhraseGenerator<TK,FV>> phraseGenerators,
      int phraseLimit) {
    this.phraseGenerators = phraseGenerators;
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
    throw new UnsupportedOperationException("Not yet implemented. Call getRuleGrid()");
  }
}
