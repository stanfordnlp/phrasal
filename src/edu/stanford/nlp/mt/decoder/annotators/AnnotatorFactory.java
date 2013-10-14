package edu.stanford.nlp.mt.decoder.annotators;

/**
 * 
 * @author danielcer
 *
 */
public class AnnotatorFactory {
	  @SuppressWarnings("unchecked")
	  static public <TK, FV> Class<Annotator<TK,FV>> loadAnnotator(
	      String name) {
	   Class<Annotator<TK,FV>> annotatorClass = null;

	    try {
	      annotatorClass = (Class<Annotator<TK,FV>>) ClassLoader
	          .getSystemClassLoader().loadClass(name);
	    } catch (ClassNotFoundException c) {
	      System.err.printf("Failed to load featurizer %s (class name: %s)\n",
	          name, name);
	      System.exit(-1);
	    }

	    return annotatorClass;
	  }
}
