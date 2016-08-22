package edu.stanford.nlp.mt.tm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.tm.DynamicTranslationModel.FeatureTemplate;
import edu.stanford.nlp.mt.train.AbstractFeatureExtractor;
import edu.stanford.nlp.mt.train.AlignmentGrid;
import edu.stanford.nlp.mt.train.AlignmentTemplateInstance;
import edu.stanford.nlp.mt.train.AlignmentTemplates;
import edu.stanford.nlp.mt.train.FlatPhraseExtractor;
import edu.stanford.nlp.mt.train.GIZAWordAlignment;
import edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor;
import edu.stanford.nlp.mt.train.PhrasalSourceFilter;
import edu.stanford.nlp.mt.train.PhraseExtract;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.train.WordAlignment;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.ParallelSuffixArrayEntry;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;
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
    if (args.length != 1) {
      System.err.printf("Usage: java %s tm_file%n", DynamicTranslationModel.class.getName());
      System.exit(-1);
    }
    String fileName = args[0];

    // Setup the Dynamic TM
    DynamicTranslationModel<String> tm = DynamicTranslationModel.load(fileName, true, DynamicTranslationModel.DEFAULT_NAME);
    boolean doHierarchical = true;
    tm.setReorderingScores(doHierarchical);
//    tm.createQueryCache(FeatureTemplate.DENSE_EXT_LOPEZ);

    // TODO(spenceg) Setup the baseline feature extractor
    Index<String> featureIndex = new HashIndex<>();
    PhrasalSourceFilter f = new PhrasalSourceFilter(Integer.MAX_VALUE, false);
//    AlignmentTemplates alTemps = new AlignmentTemplates(new Properties(), f);
    AbstractFeatureExtractor lexExtractor = new LexicalReorderingFeatureExtractor();
    Properties prop = new Properties();
    prop.setProperty(PhraseExtract.LEX_REORDERING_HIER_OPT, Boolean.toString(true));
    lexExtractor.init(new Properties(), featureIndex, new AlignmentTemplates(new Properties(), f));
    List<AbstractFeatureExtractor> featurizers = Collections.singletonList(lexExtractor);
    
    // Compare extraction over the bitext
    InputProperties inProps = new InputProperties();
    tm.sa.stream().forEach(s -> {
      ParallelSuffixArrayEntry e = s.getParallelEntry();
      Sequence<IString> source = IStrings.toIStringSequence(e.source);
      
      // Extract the baseline unrestricted rules
      FlatPhraseExtractor extractor = new FlatPhraseExtractor(prop, new AlignmentTemplates(new Properties(), f), 
          featurizers);
      SymmetricalWordAlignment sent = new SymmetricalWordAlignment(new Properties());
      String sourceStr = source.toString();
      String targetStr = String.join(" ", e.target);
      String alignStr = GIZAWordAlignment.toGizaString(e.f2e);
      sent.init(sourceStr, targetStr, alignStr);
      extractor.extractPhrases(sent);
      AlignmentGrid alGrid = extractor.getAlGrid();
      for (AlignmentTemplateInstance alTemp : alGrid.getAlTemps()) {
        lexExtractor.featurizePhrase(alTemp, alGrid);
      }
      
      // Extract the dynamic rules
      List<ConcreteRule<IString,String>> ruleList = tm.getRules(source, inProps, 0, null);
      
      // Compare the two
    });
  }

}
