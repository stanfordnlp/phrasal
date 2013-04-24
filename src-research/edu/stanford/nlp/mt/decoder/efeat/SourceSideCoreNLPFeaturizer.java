package edu.stanford.nlp.mt.decoder.efeat;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
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
import edu.stanford.nlp.mt.decoder.feat.ClonedFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;

/**
 * Adds features to the MT system based on various data calculated by
 * CoreNLP.
 * <br>
 * CoreNLP features that extract features beyond basic annotation lookups 
 * should inherit from this featurizer.
 *
 * @author John Bauer
 * @author Spence Green
 * 
 */
public class SourceSideCoreNLPFeaturizer implements IncrementalFeaturizer<IString, String>, 
AlignmentFeaturizer, ClonedFeaturizer<IString,String> {

  public static final String FEATURE_PREFIX = "CoreNLP:";

  // The list of raw English CoreNLP annotations
  protected final Map<Integer,CoreMap> sentences;

  /**
   * Sentence-specific data structures for faster lookup
   */
  protected boolean[] isHead;
  protected String[] posTags;
  
  // src-head word -> target word
  private final boolean addHeadAlignmentFeature;
  
  // src word -> target-content word
  private final boolean addContentWordDeletionFeature;
  
  // src-tag -> target word
  private final boolean addTagAlignmentFeature;
  
  // Data structures for content word deletion features
  protected final Set<IString> targetFunctionWordList; 
  protected static final Set<String> sourceFunctionPOSTags;
  static {
    // PTB POS tags to ignore with the content word deletion feature
    String[] tags = {"IN","DT","RP","CC",":",",","MD","PDT","TO","``","''",".","$"};
    sourceFunctionPOSTags = new HashSet<String>(Arrays.asList(tags));
  }
  
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
      sentences = new HashMap<Integer,CoreMap>(sentenceList.size());
      for (CoreMap annotationSet : sentenceList) {
        // 1-indexed
        int lineId = annotationSet.get(CoreAnnotations.LineNumberAnnotation.class);
        sentences.put(lineId-1, annotationSet);
      }
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }

    // Load target frequency annotation
    String targetFrequencyFile = args[1];
    int topK = Integer.parseInt(args[2]);
    targetFunctionWordList = loadList(targetFrequencyFile, topK);
    
    // Enable features
    addContentWordDeletionFeature = args.length > 3 ? Boolean.parseBoolean(args[3]) : true;
    addHeadAlignmentFeature = args.length > 4 ? Boolean.parseBoolean(args[4]) : false;
    addTagAlignmentFeature = args.length > 5 ? Boolean.parseBoolean(args[5]) : false;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
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
        wordSet.add(new IString(toks[1]));
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return wordSet;
  }

  @Override
  public void reset() {
    isHead = null;
    posTags = null;
  }


  /**
   * Initialize annotations for a new source input. 
   */
  @Override
  public void initialize(int sourceInputId,
      List<ConcreteTranslationOption<IString, String>> options, 
      Sequence<IString> foreign, Index<String> featureIndex) {
    final int length = foreign.size();
    final CoreMap currentSentence = sentences.get(sourceInputId);
    if (currentSentence == null) return;

    List<CoreLabel> words = currentSentence.get(CoreAnnotations.TokensAnnotation.class);
    if(length != words.size()) {
      throw new RuntimeException(String.format(
          "Annotation mismatch at source line %d (input: %d vs. annotation: %d)", 
          sourceInputId, length, words.size()));
    }

    isHead = new boolean[length];
    // Sanity check
    Arrays.fill(isHead, false);
    SemanticGraph basicDependencies = currentSentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    for (SemanticGraphEdge edge : basicDependencies.edgeIterable()) {
      IndexedWord head = edge.getGovernor();
      int idx = head.index();
      if (idx >= 0 && idx < isHead.length) {
        // Filter out the root
        isHead[idx] = true;
      }
    }

    posTags = new String[length];
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
    if (posTags == null || isHead == null) return null;    
    List<FeatureValue<String>> featureList = new LinkedList<FeatureValue<String>>();
    PhraseAlignment alignment = f.option.abstractOption.alignment;
    final int targetPhraseLength = f.targetPhrase.size();
    boolean[] srcIsAligned = new boolean[f.sourcePhrase.size()];
    
    int numContentDeletions = 0;
    for (int i = 0; i < targetPhraseLength; ++i) {
      // Get tgt -> src alignments
      int[] sourceIndices = alignment.t2s(i);
      IString targetWord = f.targetPhrase.get(i);
      if (sourceIndices != null) {
        for (int j : sourceIndices) {
          // Mark aligned source words
          srcIsAligned[j] = true;
          final int sourceIndex = f.sourcePosition + j;
          assert sourceIndex < posTags.length : String.format("%d vs. %d", sourceIndex, posTags.length);

          if (addTagAlignmentFeature) {
            String sourceTag = posTags[sourceIndex];
            featureList.add(new FeatureValue<String>(FEATURE_PREFIX + "srctag:" + sourceTag + ">" + targetWord, 1.0));
          }

          String sourceTag = posTags[sourceIndex];
          if (addContentWordDeletionFeature &&
              ! sourceFunctionPOSTags.contains(sourceTag) &&
              targetFunctionWordList.contains(targetWord)) {
            ++numContentDeletions;
            featureList.add(new FeatureValue<String>(FEATURE_PREFIX + "delete:" + sourceTag, 1.0));
          }
          
          if (addHeadAlignmentFeature && isHead[sourceIndex]) {
            String sourceWord = f.sourceSentence.get(sourceIndex).toString();
            featureList.add(new FeatureValue<String>(FEATURE_PREFIX + "head:" + sourceWord + ">" + targetWord, 1.0));
          }
        }
      }
    }

    if (addContentWordDeletionFeature) {
      // Add unaligned source words
      for (int i = 0; i < srcIsAligned.length; ++i) {
        if ( ! srcIsAligned[i]) {
          final int sourceIndex = f.sourcePosition + i;
          String sourceTag = posTags[sourceIndex];
          if ( ! sourceFunctionPOSTags.contains(sourceTag)) {
            ++numContentDeletions;
            featureList.add(new FeatureValue<String>(FEATURE_PREFIX + "delete:" + sourceTag, 1.0));
          }
        }
      }
      featureList.add(new FeatureValue<String>(FEATURE_PREFIX + "delete:all", numContentDeletions));
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
