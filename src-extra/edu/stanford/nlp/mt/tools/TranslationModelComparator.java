package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;

import edu.stanford.nlp.mt.tm.CompiledPhraseTable;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel.FeatureTemplate;
import edu.stanford.nlp.mt.tm.Rule;
import edu.stanford.nlp.mt.tm.TranslationModel;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Tool for comparing dynamic and pre-compiled TMs.
 * 
 * @author Spence Green
 *
 */
public final class TranslationModelComparator {

  /**
   * 
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.err.printf("Usage: java %s source_file dyn_tm comp_tm%n", 
          TranslationModelComparator.class.getName());
      System.exit(-1);
    }
    
    String sourceFile = args[0];
    String dynTMFile = args[1];
    DynamicTranslationModel<String> dynTM = DynamicTranslationModel.load(dynTMFile, true);
    dynTM.setFeatureTemplate(FeatureTemplate.DENSE_EXT);
    
    String comTMFile = args[2];
    TranslationModel<IString,String> compiledTM = new CompiledPhraseTable<String>(comTMFile);
    
    List<Sequence<IString>> sourceSegments = IStrings.tokenizeFile(sourceFile);
    int sourceId = 0;
    for (Sequence<IString> source : sourceSegments) {
      List<ConcreteRule<IString,String>> dynamicRules = dynTM.getRules(source, null, null, sourceId, null);
      List<ConcreteRule<IString,String>> compiledRules = compiledTM.getRules(source, null, null, sourceId, null);
      Set<Rule<IString>> dynamicItems = dynamicRules.stream().map(r -> r.abstractRule).collect(Collectors.toSet());
      Set<Rule<IString>> compiledItems = compiledRules.stream().map(r -> r.abstractRule).collect(Collectors.toSet());
      
      Map<Rule<IString>,List<Rule<IString>>> intersection = new HashMap<>();
      for (Rule<IString> r : dynamicItems) {
        if (compiledItems.contains(r)) {
          intersection.put(r, new ArrayList<>());
          intersection.get(r).add(r);
        }
      }
      for (Rule<IString> r : compiledItems) {
        if (intersection.containsKey(r)) {
          intersection.get(r).add(r);
        }
      }
      
      System.out.printf("SOURCE ID %d%n", sourceId);
      System.out.printf(" dynamic: %d%n compiled: %d%n intersection: %d%n", 
          dynamicRules.size(), compiledRules.size(), intersection.keySet().size());
      for (Map.Entry<Rule<IString>, List<Rule<IString>>> e : intersection.entrySet()) {
        List<Rule<IString>> rules = e.getValue();
        assert rules.size() == 2;
        System.out.printf(" d: %s%n", rules.get(0).toString());
        System.out.printf(" c: %s%n", rules.get(1).toString());
        System.out.println("-------");
      }
      
      Set<Rule<IString>> tmp = new HashSet<>(compiledItems);
      tmp.removeAll(dynamicItems);
      System.out.printf("compiled \\ dynamic: %d / %d%n", tmp.size(), compiledItems.size());
      tmp.stream().forEach(r -> System.out.printf(" %s%n", r.toString()));
      
      System.out.println("--------");
      tmp = new HashSet<>(dynamicItems);
      tmp.removeAll(compiledItems);
      System.out.printf("dynamic \\ compiled: %d / %d%n", tmp.size(), dynamicItems.size());
      tmp.stream().forEach(r -> System.out.printf(" %s%n", r.toString()));
      System.out.println();
      
      ++sourceId;
    }
  }
}
