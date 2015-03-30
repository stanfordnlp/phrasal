package edu.stanford.nlp.mt.util;

/**
 * Properties of the source input that may be specified in an
 * <code>InputProperty</code>.
 * 
 * @author Spence Green
 *
 */
public enum InputProperty {
  //List of targets are prefixes
  TargetPrefix,
  
  // Domain of the input
  Domain,
  
  // CoreNLPAnnotation for the source input
  CoreNLPAnnotation,
  
  // Indicator feature index from PhraseExtract to trigger
  // various templates in the feature API.
  RuleFeatureIndex,
  
  //Sentence based distortion limit
  DistortionLimit,
  
  //Reference Permutations
  ReferencePermutation,
  
  // Generic flag for indicating a "validity" condition of the input
  IsValid,
  
  // A decoder-local translation model.
  // Type: String
  DecoderLocalTMPath,
  
  // A decoder-local weight vector
  // Type: Counter<String>
  DecoderLocalWeights,
}
