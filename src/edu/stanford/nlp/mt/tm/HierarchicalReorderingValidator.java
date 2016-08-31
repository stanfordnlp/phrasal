package edu.stanford.nlp.mt.tm;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import edu.stanford.nlp.mt.train.AbstractPhraseExtractor;
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
    LexicalReorderingFeatureExtractor lexExtractor = new LexicalReorderingFeatureExtractor();
    final Properties properties = new Properties();
    properties.setProperty(PhraseExtract.LEX_REORDERING_HIER_OPT, Boolean.toString(true));
    properties.setProperty(PhraseExtract.LEX_REORDERING_TYPE_OPT, "msd-bidirectional-fe");
    lexExtractor.init(properties, featureIndex, new AlignmentTemplates(properties, f));
    AbstractPhraseExtractor.setPhraseExtractionProperties(properties);

    // Iterate line-by-line over aligned data
    try (LineNumberReader fReader = IOTools.getReaderFromFile(sourceFile);
        LineNumberReader eReader = IOTools.getReaderFromFile(targetFile);
        LineNumberReader feReader = IOTools.getReaderFromFile(feAlign)) {
  
      final InputProperties inProps = new InputProperties();
      int numRules = 0, numMismatches = 0;
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
        FlatPhraseExtractor extractor = new FlatPhraseExtractor(properties, new AlignmentTemplates(properties, f), 
            Collections.emptyList());
        SymmetricalWordAlignment sent = new SymmetricalWordAlignment(properties);
        sent.init(fLine, eLine, aLine);
        extractor.extractPhrases(sent);
        AlignmentGrid alGrid = extractor.getAlGrid();
        for (AlignmentTemplateInstance alTemp : alGrid.getAlTemps()) {
          // See if rule was also extracted by the dynamic model.
          List<ConcreteRule<IString,String>> sourceRules = sourceToRules.getOrDefault(alTemp.f(), Collections.emptyList());
          List<ConcreteRule<IString,String>> matchingRules = sourceRules.stream().filter(r -> {
            // WSGDEBUG
            return alTemp.fStartPos() == r.abstractRule.fSourcePos && alTemp.e().equals(r.abstractRule.target);
            
//            return alTemp.e().equals(r.abstractRule.target);
          }).collect(Collectors.toList());
          if (matchingRules.isEmpty()) {
//            System.out.println("WARNING: dynamic TM did not extract: " + alTemp.toString(true));
            continue;
          }
          
          if (alTemp.f().toString().equals("الرسول علي +ه") && alTemp.e().toString().equals("be upon him") &&
              alTemp.fStartPos() == 0) {
            System.err.println();
          }
          
          ConcreteRule<IString,String> rule = matchingRules.get(0);
          ReorderingTypes dynFwd = rule.abstractRule.forwardOrientation;
          ReorderingTypes dynBwd = rule.abstractRule.backwardOrientation;
          ReorderingTypes fwd = lexExtractor.getReorderingType(alTemp, alGrid, true);
          ReorderingTypes bwd = lexExtractor.getReorderingType(alTemp, alGrid, false);
          
          ++numRules;
          if (dynFwd != fwd || dynBwd != bwd) {
            System.out.printf("INCORRECT: %s fwd: %s/%s  bwd: %s/%s%n", rule.toString(), fwd, dynFwd,
                bwd, dynBwd);
            ++numMismatches;
          } else {
//            System.out.printf("CORRECT: %s (%s,%s)%n", rule.toString(), fwd, bwd);
          }
        }
      }
      System.out.println();
      System.out.println("========================");
      System.out.printf("rules: %d errors %d%n", numRules, numMismatches);
    }
  }
}
