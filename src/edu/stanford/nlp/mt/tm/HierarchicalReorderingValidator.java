package edu.stanford.nlp.mt.tm;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import edu.stanford.nlp.mt.train.AlignmentGrid;
import edu.stanford.nlp.mt.train.AlignmentTemplateInstance;
import edu.stanford.nlp.mt.train.AlignmentTemplates;
import edu.stanford.nlp.mt.train.DynamicTMBuilder;
import edu.stanford.nlp.mt.train.FlatPhraseExtractor;
import edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor;
import edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor.ReorderingTypes;
import edu.stanford.nlp.mt.train.PhrasalSourceFilter;
import edu.stanford.nlp.mt.train.PhraseExtract;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.ParallelCorpus;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

/**
 * Validates the dynamic hierarchical model against the reference implementation.
 * 
 * @author Spence Green
 *
 */
public final class HierarchicalReorderingValidator {

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.err.printf("Usage: java %s srcfile tgtfile alignfile%n", DynamicTranslationModel.class.getName());
      System.exit(-1);
    }
    String sourceFile = args[0];
    String targetFile = args[1];
    String feAlign = args[2];
    
    // Setup the baseline feature extractor
    Index<String> featureIndex = new HashIndex<>();
    PhrasalSourceFilter f = new PhrasalSourceFilter(Integer.MAX_VALUE, false);
//    AlignmentTemplates alTemps = new AlignmentTemplates(new Properties(), f);
    LexicalReorderingFeatureExtractor lexExtractor = new LexicalReorderingFeatureExtractor();
    Properties lexExtractorProp = new Properties();
    lexExtractorProp.setProperty(PhraseExtract.LEX_REORDERING_HIER_OPT, Boolean.toString(true));
    lexExtractorProp.setProperty(PhraseExtract.LEX_REORDERING_TYPE_OPT, "msd-bidirectional-fe");
    lexExtractor.init(lexExtractorProp, featureIndex, new AlignmentTemplates(new Properties(), f));
//    List<AbstractFeatureExtractor> featurizers = Collections.singletonList(lexExtractor);
    
    // Compare extraction over the bitext
    final InputProperties inProps = new InputProperties();
    
    try (LineNumberReader fReader = IOTools.getReaderFromFile(sourceFile);
        LineNumberReader eReader = IOTools.getReaderFromFile(targetFile);
        LineNumberReader feReader = IOTools.getReaderFromFile(feAlign)) {
  
      for (String fLine; (fLine = fReader.readLine()) != null; ) {
        String eLine = eReader.readLine();
        String aLine = feReader.readLine();
      
        // Create a dynamic TM for this sentence pair.
        ParallelCorpus corpus = new ParallelCorpus(1);
        corpus.add(fLine, eLine, aLine);
        DynamicTMBuilder builder = new DynamicTMBuilder(corpus);
        DynamicTranslationModel<String> dynTM = builder.build();
        dynTM.initialize(false);
        dynTM.setReorderingScores(true);
        Map<Sequence<IString>, List<ConcreteRule<IString,String>>> sourceToRules = dynTM.getRules(IStrings.tokenize(fLine), 
            inProps, 0, null).stream().collect(Collectors.groupingBy(r -> r.abstractRule.source, 
                Collectors.mapping((ConcreteRule<IString, String> r) -> r, Collectors.toList())));
        
        // Extract the baseline unrestricted rules
        FlatPhraseExtractor extractor = new FlatPhraseExtractor(new Properties(), new AlignmentTemplates(new Properties(), f), 
            Collections.emptyList());
        SymmetricalWordAlignment sent = new SymmetricalWordAlignment(new Properties());
        sent.init(fLine, eLine, aLine);
        extractor.extractPhrases(sent);
        AlignmentGrid alGrid = extractor.getAlGrid();
        for (AlignmentTemplateInstance alTemp : alGrid.getAlTemps()) {
          // See if rule was also extracted by the dynamic model.
          List<ConcreteRule<IString,String>> sourceRules = sourceToRules.getOrDefault(alTemp.f(), Collections.emptyList());
          List<ConcreteRule<IString,String>> matchingRules = sourceRules.stream().filter(r -> {
            return alTemp.e().equals(r.abstractRule.target);
          }).collect(Collectors.toList());
          if (matchingRules.isEmpty()) {
            System.out.println("Dynamic TM did not extract: " + alTemp.toString(true));
            continue;
          }
          
          ConcreteRule<IString,String> rule = matchingRules.get(0);
          ReorderingTypes dynFwd = rule.abstractRule.forwardOrientation;
          ReorderingTypes dynBwd = rule.abstractRule.backwardOrientation;
          ReorderingTypes fwd = lexExtractor.getReorderingType(alTemp, alGrid, true);
          ReorderingTypes bwd = lexExtractor.getReorderingType(alTemp, alGrid, false);
          if (dynFwd != fwd) {
            System.err.printf("FWD: %s || %s%n", rule.toString(), alTemp.toString(true));
          }
          if (dynBwd != bwd) {
            System.err.printf("BWD: %s || %s%n", rule.toString(), alTemp.toString(true));            
          }
        }
      }
    }
  }
}
