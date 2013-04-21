package edu.stanford.nlp.mt.decoder.efeat;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Index;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.AlignmentFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;

/**
 * Adds features to the MT system based on various data calculated by
 * CoreNLP.  
 * <br>
 * Just like the SourceSideTaggerFeaturizer, it adds combination of
 * source side POS tags and the target side words.  For example, dog
 * in English is tagged as NN, and in Chinese dog is 狗, so the
 * resulting feature would be TAGGER-NN-狗.
 * <br>
 * Furthermore, this featurizer adds dependency features over the
 * basic dependencies.  Each head word is mapped to the word that it
 * is translated to, if that word is part of the hypothesis, and the
 * feature DEP-reln-targetword is added.
 *
 * @author John Bauer
 * @author Spence Green
 * 
 */
public class SourceSideCoreNLPFeaturizer implements IncrementalFeaturizer<IString, String>, 
AlignmentFeaturizer {
   
  /**
   * The list of sentence annotations
   */
  private final CoreMap[] sentences;

  /**
   * Sentence-specific data structures
   */
  private boolean[] isHead;
  private String[] posTags;
  
  private final boolean addHeadAlignmentFeature;
  private final boolean addContentWordDeletionFeature;
  
  private final Set<IString> targetFunctionWordList; 
  /**
   * All tag features will start with this prefix
   */
  public static final String FEATURE_PREFIX = "SRC";

  public SourceSideCoreNLPFeaturizer(String ... args) {
    if (args.length < 3) {
      throw new IllegalArgumentException("Required arguments: serialized Annotation, target frequency file, top-k count");
    }
        
    // Load CoreNLP annotations
    try {
      Annotation annotation = IOUtils.readObjectFromFile(args[0]);
      List<CoreMap> sentenceList = annotation.get(CoreAnnotations.SentencesAnnotation.class);
      if (sentenceList == null) {
        throw new RuntimeException("Unusable annotation (no sentences) in " + args[0]);
      }
      int numSentences = sentenceList.size();
      sentences = new CoreMap[numSentences];
      for (CoreMap annotationSet : sentenceList) {
        // 1-indexed
        int id = annotationSet.get(CoreAnnotations.LineNumberAnnotation.class);
        sentences[id-1] = annotationSet;
      }
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    
    // Load target frequency annotation
    String targetFrequencyFile = args[1];
    int topK = Integer.parseInt(args[2]);
    addContentWordDeletionFeature = args.length > 3 ? Boolean.parseBoolean(args[3]) : true;
    addHeadAlignmentFeature = args.length > 4 ? Boolean.parseBoolean(args[4]) : false;
    targetFunctionWordList = loadList(targetFrequencyFile, topK);
  }

  /**
   * Load the target word frequency counts for content word deletion.
   * 
   * @param targetFrequencyFile
   * @param topK
   * @return
   */
  private Set<IString> loadList(String targetFrequencyFile, int topK) {
    LineNumberReader reader = IOTools.getReaderFromFile(targetFrequencyFile);
    Set<IString> wordSet = new HashSet<IString>(topK);
    try {
      for(String line; (line = reader.readLine()) != null && reader.getLineNumber() < topK;) {
        String[] toks = line.split("\\s+");
        assert toks.length == 2;
        wordSet.add(new IString(toks[0]));
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return wordSet;
  }

  @Override
  public void reset() {}

  
  /**
   * Initialize annotations for a new source input. 
   */
  @Override
  public void initialize(int sourceInputId,
                         List<ConcreteTranslationOption<IString, String>> options, 
                         Sequence<IString> foreign, Index<String> featureIndex) {
    int length = foreign.size();
    CoreMap currentSentence = sentences[sourceInputId];
    
    if(length != currentSentence.size()) {
      throw new RuntimeException(String.format(
          "Annotation mismatch at source line %d (%d vs. %d)", sourceInputId, length, currentSentence.size()));
    }
    
    isHead = new boolean[length];
    // Sanity check
    Arrays.fill(isHead, false);
    SemanticGraph basicDependencies = currentSentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    for (SemanticGraphEdge edge : basicDependencies.edgeIterable()) {
      IndexedWord head = edge.getGovernor();
      isHead[head.index()] = true;
    }
    
    posTags = new String[length];
    List<CoreLabel> words = currentSentence.get(CoreAnnotations.TokensAnnotation.class);
    for(int i = 0; i < length; ++i) {
      posTags[i] = words.get(i).tag();
    }
  }


  /**
   * Return a set of features for the tagged sentence.
   * Each feature will be of the form TAGGER-sourcetag-targetword
   */  
  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> featureList = new LinkedList<FeatureValue<String>>();
    PhraseAlignment alignment = f.option.abstractOption.alignment;
    final int targetPhraseLength = f.targetPhrase.size();
    final int sourcePhraseLength = f.sourcePhrase.size();
    
    List<Set<Integer>> s2tAlignments = new ArrayList<Set<Integer>>(sourcePhraseLength);
    for (int i = 0; i < sourcePhraseLength; ++i) {
      s2tAlignments.add(new HashSet<Integer>());
    }
    
    if (addContentWordDeletionFeature) {
      int numContentDeletions = 0;
      for (int i = 0; i < targetPhraseLength; ++i) {
        int[] sourceIndices = alignment.t2s(i);
        IString targetWord = f.targetPhrase.get(i);
        for (int j : sourceIndices) {
          int sourceIndex = f.sourcePosition + j;
          assert sourceIndex < posTags.length;
          s2tAlignments.get(j).add(i);
          ++numContentDeletions;
          if (targetFunctionWordList.contains(targetWord)) {
            featureList.add(new FeatureValue<String>(FEATURE_PREFIX + ".fnalign." + posTags[sourceIndex], 1.0));
            if (isHead[sourceIndex] && addHeadAlignmentFeature) {
              featureList.add(new FeatureValue<String>(FEATURE_PREFIX + ".headalign." + posTags[sourceIndex], 1.0));  
            }
          }
        }
      }
      featureList.add(new FeatureValue<String>(FEATURE_PREFIX + ".fntot", numContentDeletions));
    }
    
    if (addHeadAlignmentFeature) {
      // TODO Implement DCA per Hwa et al. (2002) ACL paper.
    }
    
    return featureList;
  }
  
  /**
   * We care about the features produced by the list of words, so
   * listFeaturize returns results and featurize does not.
   */
  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    return null;
  }

}
