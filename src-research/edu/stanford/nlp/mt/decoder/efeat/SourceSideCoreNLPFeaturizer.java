package edu.stanford.nlp.mt.decoder.efeat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

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
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
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
 *@author John Bauer
 */
public class SourceSideCoreNLPFeaturizer implements IncrementalFeaturizer<IString, String>, AlignmentFeaturizer {
  
  /*
   * The CoreNLP annotation
   */
  private final Annotation annotation;
  
  /**
   * The list of sentences annotations
   */
  private final List<CoreMap> sentences;
  private final int numSentences;

  /**
   * Sentence-specific data structures
   */
  private boolean[] isHead;
  private String[] posTags;
  
  /**
   * All tag features will start with this prefix
   */
  public static final String TAG_FEATURE_NAME = "Syn-";

  /**
   * All dep features will start with this prefix
   */
  public static final String DEP_FEATURE_NAME = "DEP-";

  public SourceSideCoreNLPFeaturizer(String ... args) {
    if (args.length == 0) {
      throw new IllegalArgumentException("Need to provide this Featurizer with a path to a serialized Annotation");
    }
    try {
      annotation = IOUtils.readObjectFromFile(args[0]);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    if (sentences == null) {
      throw new RuntimeException("Unusable annotation (no sentences) in " + args[0]);
    }
    numSentences = sentences.size();
  }

  @Override
  public void reset() {}

  /**
   * Initialize on a new translation.  
   */
  @Override
  public void initialize(int sourceInputId,
                         List<ConcreteTranslationOption<IString, String>> options, Sequence<IString> foreign, Index<String> featureIndex) {
    int length = foreign.size();
    CoreMap currentSentence = sentences.get(sourceInputId);
    assert length == currentSentence.size();
    
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
   * We care about the features produced by the list of words, so
   * listFeaturize returns results and featurize does not.
   */
  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    return null;
  }

  /**
   * Return a set of features for the tagged sentence.
   * Each feature will be of the form TAGGER-sourcetag-targetword
   */  
  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
    int problemId = f.sourceInputId;
    if (problemId < 0 || problemId >= sentences.size()) {
      // TODO: now what do we do?  Return null or blow up?
      throw new RuntimeException("Given translation problem for sentence that wasn't cached, " + problemId + "; cached " + sentences.size() + " sentences");
    }

    CoreMap currentSentence = sentences.get(problemId);

    List<FeatureValue<String>> features = Generics.newArrayList();
    
    List<CoreLabel> words = currentSentence.get(CoreAnnotations.TokensAnnotation.class);
    PhraseAlignment reverseAlignment = 
          PhraseAlignment.getPhraseAlignment(
             f.option.abstractOption.alignment.s2tStr().replace(" ", ";"));
    
    for (int i : f.option.sourceCoverage) {
       int phraseI = i - f.sourcePosition;
       int phraseJs[] = reverseAlignment.t2s(phraseI);
       if (phraseJs == null) {
         // this word is not aligned to anything yet
         continue;         
       }
       
       for (int phraseJ : phraseJs) {
         int j = f.targetPosition + phraseJ;
         int sourceIndex = i;
         int targetIndex = j;

         String sourceTag = words.get(sourceIndex).tag();
         String targetWord = f.targetPrefix.get(targetIndex).toString();
         String feature = TAG_FEATURE_NAME + sourceTag + "-" + targetWord;
         
         // no attempt to look for repeated features; 
         // the system will find and sum those for us
         features.add(new FeatureValue<String>(feature, 1.0));
      }
    }

    SemanticGraph basicDependencies = currentSentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    for (SemanticGraphEdge edge : basicDependencies.edgeIterable()) {
      String relation = edge.getRelation().toString();
      int sourceIndex = edge.getSource().index() - 1; // IndexedWords are indexed from 1, not 0
      if (sourceIndex < f.sourcePosition || sourceIndex >= f.sourcePosition + f.sourcePhrase.size()) {
        continue;
      }

      if (reverseAlignment.t2s(sourceIndex-f.sourcePosition) == null) {
        // this word is not aligned to anything yet
        continue;
      }

      int[] targetAlignments = reverseAlignment.t2s(sourceIndex-f.sourcePosition);
      for (int j = 0; j < targetAlignments.length; ++j) {
        int targetIndex = targetAlignments[j];
        String targetWord = f.targetPrefix.get(targetIndex).toString();
        String feature = DEP_FEATURE_NAME + relation + "-" + targetWord;
        features.add(new FeatureValue<String>(feature, 1.0));
      }
    }

    return features;
  }
}
