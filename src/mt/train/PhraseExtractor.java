package mt.train;

import java.util.*;


/**
 * Interface for phrase extraction (or, more specifically, extraction of {@link AlignmentTemplateInstance} 
 * objects). The main member of this interface -- extractPhrases -- does not explicitely return a list 
 * of AlignmentTemplateInstance objects. Instead, it is assumed that each PhraseExtractor instance will 
 * invoke {@link FeatureExtractor#extract(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid)} on each
 * AlignmentTemplateInstance object it constructs. The preferred way of instanciating PhraseExtractor
 * is to extend AbstractPhraseExtractor.
 * 
 * @author Michel Galley
 */
public interface PhraseExtractor {

  /**
   * Extract all admissible phrase pairs from a given word-aligned sentence pair.
   */
  public void extractPhrases(WordAlignment sent);

}
