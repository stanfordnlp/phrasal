package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.mt.base.CoverageSet;

/**
 * @author Michel Galley
 */
@SuppressWarnings("unused")
public class DTUFeatureExtractor extends MosesFeatureExtractor {

  public static final String DEBUG_PROPERTY = "DebugDTUFeatureExtractor";
  public static final int DEBUG_LEVEL = Integer.parseInt(System.getProperty(DEBUG_PROPERTY, "0"));

  @Override
  public void featurizePhrase(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid) {
    super.featurizePhrase(alTemp, alGrid);
    
    if (alTemp instanceof DTUInstance)  {

      DTUInstance dtu = (DTUInstance) alTemp;

      CoverageSet fSet = dtu.fSet;
      CoverageSet eSet = dtu.fSet;

      int fKey = alTemp.fKey;
      int eKey = alTemp.eKey;

      // TODO
    }
  }

  @Override
  public String toString(AlignmentTemplateInstance phrase, boolean withAlignment) {

    // TODO
    
    return null;
  }
}

