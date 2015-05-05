package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.TranslationModelFeaturizer;
import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.decoder.util.SparseScorer;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.TranslationModelFactory;
import edu.stanford.nlp.mt.tm.TranslationModel;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.stats.Counter;

/**
 * Tool for comparing dynamic and pre-compiled TMs.
 * 
 * @author Spence Green
 *
 */
public final class TranslationModelComparator {

  private static String makePair(String label, String value) {
    return String.format("%s:%s", label, value);
  }
  
  /**
   * 
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 3) {
      System.err.printf("Usage: java %s source_file dyn_tm comp_tm [weights_dyn] [weights_comp]%n", 
          TranslationModelComparator.class.getName());
      System.exit(-1);
    }
    
    String sourceFile = args[0];
    String dynTMFile = args[1];
    TranslationModel<IString,String> dynTM = TranslationModelFactory.<String>factory(dynTMFile,
            makePair(TranslationModelFactory.QUERY_LIMIT_OPTION, "20"),
            makePair(TranslationModelFactory.DYNAMIC_INDEX, "true")).first();
    
    String comTMFile = args[2];
    TranslationModel<IString,String> compiledTM = TranslationModelFactory.<String>factory(comTMFile,
        makePair(TranslationModelFactory.QUERY_LIMIT_OPTION, "20")).first();
    Counter<String> weightsDyn = args.length > 3 ? IOTools.readWeights(args[3]) : null;
    Counter<String> weightsComp = args.length > 3 ? IOTools.readWeights(args[4]) : null;
    Scorer<String> scorer = weightsDyn == null ? null : new SparseScorer(weightsDyn);
    Scorer<String> scorerComp = weightsComp == null ? null : new SparseScorer(weightsComp);
    RuleFeaturizer<IString,String> feat = new TranslationModelFeaturizer(6);
    dynTM.setFeaturizer(feat);
    compiledTM.setFeaturizer(feat);
    
    List<Sequence<IString>> sourceSegments = IStrings.tokenizeFile(sourceFile);
    int sourceId = 0;
    for (Sequence<IString> source : sourceSegments) {
      System.out.printf("SOURCE ID %d%n", sourceId);
      RuleGrid<IString,String> dynRules = dynTM.getRuleGrid(source, null, null, sourceId, scorer);
      RuleGrid<IString,String> compRules = compiledTM.getRuleGrid(source, null, null, sourceId, scorerComp);
      for (int order = 1; order < 5; ++order) {
        for (int i = 0, sz = source.size() - order; i < sz; ++i) {
          int j = i + order  - 1;
          List<ConcreteRule<IString,String>> dynList = dynRules.get(i, j);
          List<ConcreteRule<IString,String>> compiledList = compRules.get(i, j);
          int max = Math.max(dynList.size(), compiledList.size());
          if (max == 0) continue;
          for (int k = 0; k < max; ++k) {
            ConcreteRule<IString,String> dynRule = k < dynList.size() ? dynList.get(k) : null;
            ConcreteRule<IString,String> compRule = k < compiledList.size() ? compiledList.get(k) : null;
            System.out.printf("%s (%.3f)\t%s (%.3f)%n", dynRule == null ? "null" : dynRule.abstractRule.toString(),
                dynRule == null ? 0.0 : dynRule.isolationScore,
                compRule == null ? "null" : compRule.abstractRule.toString(),
                compRule == null ? 0.0 : compRule.isolationScore);
          }
          System.out.println();
        }
      }
      System.out.println("################################");
      
//      List<ConcreteRule<IString,String>> dynamicRules = dynTM.getRules(source, null, null, sourceId, null);
//      List<ConcreteRule<IString,String>> compiledRules = compiledTM.getRules(source, null, null, sourceId, null);
//      Set<Rule<IString>> dynamicItems = dynamicRules.stream().map(r -> r.abstractRule).collect(Collectors.toSet());
//      Set<Rule<IString>> compiledItems = compiledRules.stream().map(r -> r.abstractRule).collect(Collectors.toSet());
//      
//      Map<Rule<IString>,List<Rule<IString>>> intersection = new HashMap<>();
//      for (Rule<IString> r : dynamicItems) {
//        if (compiledItems.contains(r)) {
//          intersection.put(r, new ArrayList<>());
//          intersection.get(r).add(r);
//        }
//      }
//      for (Rule<IString> r : compiledItems) {
//        if (intersection.containsKey(r)) {
//          intersection.get(r).add(r);
//        }
//      }
//      
//      System.out.printf("SOURCE ID %d%n", sourceId);
//      System.out.printf(" dynamic: %d%n compiled: %d%n intersection: %d%n", 
//          dynamicRules.size(), compiledRules.size(), intersection.keySet().size());
//      for (Map.Entry<Rule<IString>, List<Rule<IString>>> e : intersection.entrySet()) {
//        List<Rule<IString>> rules = e.getValue();
//        assert rules.size() == 2;
//        System.out.printf(" d: %s%n", rules.get(0).toString());
//        System.out.printf(" c: %s%n", rules.get(1).toString());
//        System.out.println("-------");
//      }
//      
//      Set<Rule<IString>> tmp = new HashSet<>(compiledItems);
//      tmp.removeAll(dynamicItems);
//      System.out.printf("compiled \\ dynamic: %d / %d%n", tmp.size(), compiledItems.size());
//      tmp.stream().forEach(r -> System.out.printf(" %s%n", r.toString()));
//      
//      System.out.println("--------");
//      tmp = new HashSet<>(dynamicItems);
//      tmp.removeAll(compiledItems);
//      System.out.printf("dynamic \\ compiled: %d / %d%n", tmp.size(), dynamicItems.size());
//      tmp.stream().forEach(r -> System.out.printf(" %s%n", r.toString()));
//      System.out.println();
      
      ++sourceId;
    }
  }
}
