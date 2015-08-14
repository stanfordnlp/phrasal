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
  
  // A weight vector
  // Type: Counter<String>
  ModelWeights,
}
