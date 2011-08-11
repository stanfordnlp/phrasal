package edu.stanford.nlp.mt.base;

import junit.framework.TestCase;

public class FeatureValueTest extends TestCase  {
  public void testEquality() {
	  FeatureValue<String> fvLM1 = new FeatureValue<String>("LM", -10);
	  FeatureValue<String> fvLM2 = new FeatureValue<String>("LM", -20);
	  FeatureValue<String> fvTM1 = new FeatureValue<String>("TM", -10);
	  FeatureValue<String> fvTM2 = new FeatureValue<String>("TM", -20);
	  assertTrue(fvLM1.equals(fvLM1));
	  assertTrue(fvLM2.equals(fvLM2));
	  assertTrue(fvTM1.equals(fvTM1));
	  assertTrue(fvTM2.equals(fvTM2));
	  assertFalse(fvLM1.equals(fvLM2));
	  assertFalse(fvLM1.equals(fvTM1));
	  assertFalse(fvTM1.equals(fvLM1));
	  assertFalse(fvLM2.equals(fvLM1));
	  assertFalse(fvLM2.equals(fvTM2));
	  assertFalse(fvTM2.equals(fvLM2));
	  assertFalse(fvLM2.equals(fvLM1));
  }
  
  public void testConsistentHashing() {
	  FeatureValue<String> fv1 = new FeatureValue<String>("aFeature", 100);
	  FeatureValue<String> fv1a = new FeatureValue<String>("aFeature", 100);
	  FeatureValue<String> fv2 = new FeatureValue<String>("anotherFeature", 200);
	  FeatureValue<String> fv2a = new FeatureValue<String>("anotherFeature", 200);
	  assertTrue(fv1.hashCode() == fv1a.hashCode());
	  assertTrue(fv2.hashCode() == fv2a.hashCode());
      assertTrue(fv1.hashCode() != fv2.hashCode()); // check for degenerate hash codes
  }
}
