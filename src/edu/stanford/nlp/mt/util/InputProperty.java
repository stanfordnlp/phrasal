package edu.stanford.nlp.mt.util;

/**
 * Properties of the source input that may be specified in an
 * <code>InputProperty</code>.
 * 
 * IMPORTANT: If you set input properties programmatically, then you must
 * used the types specified in the comments below.
 * 
 * @author Spence Green
 *
 */
public enum InputProperty {
  //List of targets are prefixes
  // Type: Boolean
  TargetPrefix,
  
  // Domain of the input
  // Type: String
  Domain,
  
  // CoreNLPAnnotation for the source input
  CoreNLPAnnotation,
  
  // Indicator feature index from PhraseExtract to trigger
  // various templates in the feature API.
  // Type: Integer
  RuleFeatureIndex,
  
  //Sentence based distortion limit
  // Type: Integer
  DistortionLimit,
  
  //Reference Permutations
  ReferencePermutation,
  
  // Generic flag for indicating a "validity" condition of the input
  // Type: Boolean
  IsValid,
  
  // A foreground translation model.
  // Type: TranslationModel
  ForegroundTM,
  
  // A termbase translation model.
  // Type: TranslationModel
  TermbaseTM,
  
  // A weight vector
  // Type: Counter<String>
  ModelWeights,
  
  // The phrase query limit
  // Type: Integer
  RuleQueryLimit,
  
  // in tuning, decode with these prefix lengths for evaluation
  // Type: int[]
  PrefixLengths,
  
  // Flag that is true if the last word of the prefix may be incomplete
  // Type: Boolean
  AllowIncompletePrefix,
  
  //Sentence based beam size
  // Type: Integer
  BeamSize
  
}
