package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;

import edu.stanford.nlp.mt.decoder.feat.NeedsCloneable;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Features based on source-side CoreNLP annotations.
 *
 * @author Spence Green
 * 
 */
public class SourceSideCoreNLPFeaturizer extends DerivationFeaturizer<IString, String> implements   
NeedsCloneable<IString,String> {

  public static final String FEATURE_PREFIX = "CNLP:";

  /**
   * Sentence-specific data structures for faster lookup
   */
  protected boolean[] isHead;
  protected String[] posTags;
  
  // src-head word -> target-function word
  private final boolean addHeadDeletion;
  
  // src word -> target-function word
  private final boolean addContentDeletion;
  
  // Data structures for content word deletion features
  protected static final Set<String> sourceFunctionPOSTags;
  static {
    // PTB POS tags to ignore with the content word deletion feature
    String[] tags = {"IN","DT","RP","CC",":",",","MD","PDT","TO","``","''",".","$","EX"};
    sourceFunctionPOSTags = new HashSet<String>(Arrays.asList(tags));
  }
  
  public SourceSideCoreNLPFeaturizer(String ... args) {
    // Enable features
    addContentDeletion = args.length > 0 ? Boolean.parseBoolean(args[0]) : true;
    addHeadDeletion = args.length > 1 ? Boolean.parseBoolean(args[1]) : false;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  /**
   * Initialize annotations for a new source input. 
   */
  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString, String>> options, 
      Sequence<IString> foreign) {
    final int length = foreign.size();
    // TODO(spenceg): Integrate with new CoreNLP annotations
    final CoreMap currentSentence = null;//CoreNLPCache.get(sourceInputId);
    if (currentSentence == null) {
      throw new RuntimeException("No annotation for source id " + String.valueOf(sourceInputId));
    }

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
   */  
  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    if (posTags == null || isHead == null) return null;    
    List<FeatureValue<String>> featureList = Generics.newLinkedList();
    PhraseAlignment alignment = f.rule.abstractRule.alignment;
    final int targetPhraseLength = f.targetPhrase.size();
    boolean[] srcIsAligned = new boolean[f.sourcePhrase.size()];
    
    // TODO Iterate over the target looking for the number of times that each source
    // word is aligned.
    
    // TODO Extract features from that counts array
    
    return featureList;
  }

}
