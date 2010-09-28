package edu.stanford.nlp.mt.reranker;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;

import java.io.*;
import java.util.*;

/**
 * @author Pi-Chuan Chang
 * @author Dan Cer - author here of ~2 lines of code, yeah!
 */

public class FeatureIndex implements Serializable {

  private static final long serialVersionUID = 1L;

  Index<String> featureIndex = new HashIndex<String>();

  boolean add(String featName) {
    // return featureIndex.add(featName.intern());
    return featureIndex.add(featName);
  }

  String get(int idx) {
    return featureIndex.get(idx);
  }

  int indexOf(String featName) {
    return featureIndex.indexOf(featName);
  }

  int size() {
    return featureIndex.size();
  }

  List<String> objectsList() {
    return featureIndex.objectsList();
  }
}
