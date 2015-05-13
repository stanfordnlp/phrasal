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
public class CombinedTranslationModel<TK,FV> implements TranslationModel<TK,FV> {
  
  static public final boolean DEBUG = false;

  // Moses default
  static public final int DEFAULT_PHRASE_LIMIT = 20;

  private final List<TranslationModel<TK,FV>> models;
  private final int ruleQueryLimit;

  /**
   * Constructor.
   * 
   * @param model
   */
  public CombinedTranslationModel(TranslationModel<TK,FV> model) {
    this(model, DEFAULT_PHRASE_LIMIT);
  }

  /**
   * Constructor
   * 
   * @param model
   * @param queryLimit
   */
  public CombinedTranslationModel(TranslationModel<TK,FV> model, int queryLimit) {
    this.models = new ArrayList<>(1);
    this.models.add(model);
    this.ruleQueryLimit = queryLimit;
  }
  
  /**
   * Constructor.
   * 
   * @param models
   */
  public CombinedTranslationModel(List<TranslationModel<TK,FV>> models) {
    this(models, DEFAULT_PHRASE_LIMIT);
  }

  /**
   * Constructor.
   * 
   * @param models
   * @param queryLimit
   */
  public CombinedTranslationModel(List<TranslationModel<TK,FV>> models,
      int queryLimit) {
    this.models = models;
    this.ruleQueryLimit = queryLimit;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

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
    for (TranslationModel<TK,FV> generator : models) {
      featureNames.addAll(generator.getFeatureNames());
    }
    return featureNames;
  }

  @SuppressWarnings("unchecked")
  @Override
  public RuleGrid<TK, FV> getRuleGrid(Sequence<TK> source, InputProperties sourceInputProperties, 
      List<Sequence<TK>> targets, int sourceInputId, Scorer<FV> scorer) {
    final Map<CoverageSet, List<List<ConcreteRule<TK,FV>>>> ruleLists = new HashMap<>(source.size() * source.size());

    // Support for decoder-local translation models
    List<TranslationModel<TK,FV>> translationModels = models;
    if (DecoderLocalTranslationModel.get() != null) {
      translationModels = new ArrayList<>(models);
      translationModels.add(DecoderLocalTranslationModel.get());
    }
    
    int modelId = 0;
    for (TranslationModel<TK,FV> phraseGenerator : translationModels) {
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

  @Override
  public int maxLengthSource() {
    int longest = -1;
    for (TranslationModel<TK,FV> phraseGenerator : models) {
      if (longest < phraseGenerator.maxLengthSource())
        longest = phraseGenerator.maxLengthSource();
    }
    return longest;
  }

  @Override
  public void setFeaturizer(RuleFeaturizer<TK, FV> featurizer) {
    for (TranslationModel<TK,FV> phraseTable : models) {
      phraseTable.setFeaturizer(featurizer);
    }
  }

  @Override
  public int maxLengthTarget() {
    int longest = -1;
    for (TranslationModel<TK,FV> phraseGenerator : models) {
      if (longest < phraseGenerator.maxLengthTarget())
        longest = phraseGenerator.maxLengthTarget();
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
    return getRuleGrid(source, sourceInputProperties, targets, sourceInputId, scorer).asList();
  }
}
