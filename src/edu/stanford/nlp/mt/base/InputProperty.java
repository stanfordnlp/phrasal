package edu.stanford.nlp.mt.base;

/**
 * Properties of the source input that may be specified in an
 * <code>InputProperty</code>.
 * 
 * @author Spence Green
 *
 */
public enum InputProperty {
  TargetPrefix,            // List of targets are prefixes
  Domain,                  // Domain of the input
  CoreNLPAnnotation        // CoreNLPAnnotation for the source input
}
