package edu.stanford.nlp.mt.decoder.efeat;

import java.io.IOException;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
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
  Annotation annotation;
  List<CoreMap> sentences;

  /**
   * All tag features will start with this prefix
   */
  public static final String TAG_FEATURE_NAME = "TAGGER-";

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
  }

  @Override
  public void reset() {}

  /**
   * Initialize on a new translation.  
   * TODO: would be nice to cache values for the sentence here
   */
  @Override
  public void initialize(List<ConcreteTranslationOption<IString, String>> options,
                         Sequence<IString> foreign, Index<String> featureIndex) {
  }

  /**
   * We care about the features produced by the list of words, so
   * listFeaturize returns results and featurize does not.
   */
  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    return null;
  }
  private Set<Integer> warnSet = new HashSet<Integer>();

  private static CoreMap findSentence(List<CoreMap> sentences, int id) {
    return findSentence(sentences, id, 0, sentences.size() - 1);
  }

  private static CoreMap findSentence(List<CoreMap> sentences, int id, int start, int end) {
    if (start > end) {
      return null;
    }
    int midpoint = (start + end) / 2;
    int currentId = sentences.get(midpoint).get(CoreAnnotations.LineNumberAnnotation.class);
    if (currentId == id) {
      return sentences.get(midpoint);
    } else if (start == end) {
      return null;
    } else if (currentId < id) {
      return findSentence(sentences, id, midpoint + 1, end);
    } else {
      return findSentence(sentences, id, start, midpoint - 1);
    }
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

    CoreMap currentSentence = findSentence(sentences, problemId);

    List<FeatureValue<String>> features = Generics.newArrayList();
    
    List<CoreLabel> words = currentSentence.get(CoreAnnotations.TokensAnnotation.class);
    
    if (words.size() != f.sourceSentence.size()) {
       if (!warnSet.contains(problemId)) {
         System.err.printf("WARNING: tokenization mismatch for %d - Phrasal: <%s> CoreNLP <%s>\n", problemId, f.sourceSentence, words);
         warnSet.add(problemId);
       }
       return null;
    }
    /*
    System.err.printf("f.source: %s\n", f.option.abstractOption.source);
    System.err.printf("f.target: %s\n", f.option.abstractOption.target);
    System.err.printf("f.option.abstractOption: %s\n",  
      f.option.abstractOption); 
    System.err.printf("f.option.sourceCoverage: %s\n", f.option.sourceCoverage); 
    System.err.printf("alignment: %s\n",  f.option.abstractOption.alignment);
    */
    PhraseAlignment reverseAlignment = 
          PhraseAlignment.getPhraseAlignment(
             f.option.abstractOption.alignment.s2tStr().replace(" ", ";"));
    /* System.err.printf("reverseAlignment: %s\n",  reverseAlignment);
    System.err.printf("t-s\n"); 
    if (f.option.abstractOption.alignment.t2s != null) {
    for (int[] row : f.option.abstractOption.alignment.t2s) {
       System.err.printf("\t%s\n", java.util.Arrays.toString(row));
    }
    } */
  
    /* System.err.printf("/t-s\n");

    System.err.printf("t-s reverse\n"); 
    if (reverseAlignment.t2s != null) {
    for (int[] row : reverseAlignment.t2s) {
       System.err.printf("\t%s\n", java.util.Arrays.toString(row));
    }
    }
    System.err.printf("/t-s reverse\n");
   */

    for (int i : f.option.sourceCoverage) {
       int phraseI = i - f.sourcePosition;
//       System.err.printf("phraseI: %d i: %d f.sourcePosition: %d\n", phraseI, i, f.sourcePosition); 
       int phraseJs[] = reverseAlignment.t2s(phraseI);
       if (phraseJs == null) {
         // this word is not aligned to anything yet
         continue;         
       }
       
       for (int phraseJ : phraseJs) {
         int j = f.targetPosition + phraseJ;
         int sourceIndex = i;

         String sourceTag = words.get(sourceIndex).tag();
         String targetWord = f.option.abstractOption.target.get(phraseJ).toString();
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
        int phraseJ = targetAlignments[j];
        String targetWord = f.option.abstractOption.target.get(phraseJ).toString();
        String feature = DEP_FEATURE_NAME + relation + "-" + targetWord;
        features.add(new FeatureValue<String>(feature, 1.0));
      }
    }

    return features;
  }
}
