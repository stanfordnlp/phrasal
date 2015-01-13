package edu.stanford.nlp.mt.train;

import java.util.Properties;

import edu.stanford.nlp.util.Index;


/**
 * Extracts dependency treelets using dependency parses of the target sentences.
 * 
 * @author Sebastian Schuster
 */

public class DependencyTreeFreatureExtractor extends AbstractFeatureExtractor {

 public DependencyTreeFreatureExtractor(String...args) {
   
   //load dependency trees into memory
   
 }
 
 @Override
  public void featurizePhrase(AlignmentTemplateInstance alTemp,
      AlignmentGrid alGrid) {
   // Have a hashmap indexed by source->target
   // If key does not exist: add dependencies (relative indexing)
   // 
   
  }
  
}
