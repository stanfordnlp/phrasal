package mt.syntax.mst.rmcd;

/**
 * @author Michel Galley
 */

public interface DependencyInstance {

  void setFeatureVector(FeatureVector fv);
  
  FeatureVector getFeatureVector();

  void setParseTree(String t); 

  String getParseTree();

  int length();

  int relFeatLength();

  String toString();

  String prettyPrint();

  boolean hasForms();

  String[] getForms();

  String[] getLemmas();

  String getForm(int i);

  String getLemma(int i);

  String getCPOSTag(int i);

  String getPOSTag(int i);

  String[] inBetweenPOS(int i, int j, boolean coarse);

  // Features that are active when current word is w[i]:
  String[] getFeats(int i);
  String getFeat(int i, int j);

  // Features that are active when modifier is w[i] and head is w[j]:
  String[] getPairwiseFeats(int i, int j);

  void setFeats(int i, String[] f);

  int getHead(int i);

  String getDepRel(int i);

  RelationalFeature getRelFeat(int i);

  void setHeads(int[] h);

  void setDepRels(String[] d);

  DependencyInstance getPrefixInstance(int i);

  ////////////////////////////////////////
  // Bilingual stuff:
  ////////////////////////////////////////

  DependencyInstance getSourceInstance();

  int[] getSource(int i);
}
