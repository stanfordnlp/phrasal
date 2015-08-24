package edu.stanford.nlp.mt.tm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Translation model query from multiple phrase tables.
 * 
 * @author Daniel Cer
 * @author Spence Green
 * 
 * @param <TK>
 */
public class CombinedTranslationModel<TK,FV> implements TranslationModel<TK,FV> {

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
    this.models = Collections.singletonList(model);
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
  public CombinedTranslationModel(List<TranslationModel<TK,FV>> models, int queryLimit) {
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
   * @param rule
   * @param ruleLists
   * @param modelId 
   */
  private void addToRuleList(ConcreteRule<TK,FV> rule,
      Map<CoverageSet, List<List<ConcreteRule<TK, FV>>>> ruleLists, int modelId) {
    if ( ! ruleLists.containsKey(rule.sourceCoverage)) {
      ruleLists.put(rule.sourceCoverage, new LinkedList<>());
    }
    if ( modelId >= ruleLists.get(rule.sourceCoverage).size()) {
      for (int i = 0; i <= modelId; ++i) {
        ruleLists.get(rule.sourceCoverage).add(new LinkedList<>());
      }
    }
    ruleLists.get(rule.sourceCoverage).get(modelId).add(rule);
  }
  
  @Override
  public List<String> getFeatureNames() {
    List<String> featureNames = new ArrayList<>();
    for (TranslationModel<TK,FV> generator : models) {
      featureNames.addAll(generator.getFeatureNames());
    }
    return featureNames;
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
    for (TranslationModel<TK,FV> m : models) {
      m.setFeaturizer(featurizer);
    }
  }

  @Override
  public int maxLengthTarget() {
    int longest = -1;
    for (TranslationModel<TK,FV> m : models) {
      if (longest < m.maxLengthTarget())
        longest = m.maxLengthTarget();
    }
    return longest;
  }

  @Override
  public String getName() {
    return this.getClass().getName();
  }
  
  @Override
  public void setName(String name) {}

  
  @SuppressWarnings({ "rawtypes", "unchecked" })
  @Override
  public List<ConcreteRule<TK, FV>> getRules(Sequence<TK> source,
      InputProperties sourceInputProperties, int sourceInputId,
      Scorer<FV> scorer) {

    // Support for decoder-local translation models
    List<TranslationModel<TK,FV>> translationModels = models;
    if (sourceInputProperties.containsKey(InputProperty.ForegroundTM)) {
      TranslationModel<TK,FV> tm = (TranslationModel) sourceInputProperties.get(InputProperty.ForegroundTM);
      translationModels = new ArrayList<>(models);
      translationModels.add(tm);
    }
    
    final Map<CoverageSet, List<List<ConcreteRule<TK,FV>>>> ruleLists = 
        new HashMap<>(source.size() * source.size());

    int modelNumber = 0;
    for (TranslationModel<TK,FV> model : translationModels) {
      for (ConcreteRule<TK,FV> rule : model.getRules(source, sourceInputProperties, sourceInputId, 
          scorer)) {
        addToRuleList(rule, ruleLists, modelNumber);
      }
      ++modelNumber;
    }

    List<ConcreteRule<TK, FV>> mergedList = new ArrayList<>();
    for (CoverageSet coverage : ruleLists.keySet()) {
      List<List<ConcreteRule<TK,FV>>> ruleList = ruleLists.get(coverage);

      // Effectively cube pruning!
      Queue<Item<TK,FV>> pq = new PriorityQueue<Item<TK,FV>>(3);
      for (List<ConcreteRule<TK,FV>> list : ruleList) {
        if (list.size() > 0) {
          Collections.sort(list);
          pq.add(new Item<TK,FV>(list.remove(0), list));
        }
      }
      int numPoppedItems = 0;
      Set<Rule<TK>> uniqSet = new HashSet<>();
      while (numPoppedItems < ruleQueryLimit && ! pq.isEmpty()) {
        Item<TK, FV> item = pq.poll();
        if ( ! uniqSet.contains(item.rule.abstractRule)) {
          mergedList.add(item.rule);
          uniqSet.add(item.rule.abstractRule);
          if (item.list.size() > 0) {
            pq.add(new Item<TK,FV>(item.list.remove(0), item.list));
          }
        }
      }
    }
    return mergedList;
  }
}
