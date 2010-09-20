package edu.stanford.nlp.mt.train;

import java.util.*;
import java.io.IOException;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Constituent;


/**
 * Same as MosesPhraseExtractor, but restricts phrases according to consituencies read from a parse tree.
 *
 * @author Michel Galley
 */
public class ConstituentPhraseExtractor extends MosesPhraseExtractor {

  // NOTE: unfinished

  public ConstituentPhraseExtractor(Properties prop, AlignmentTemplates alTemps, List<AbstractFeatureExtractor> extractors) {
    super(prop, alTemps, extractors);
    System.err.println("Constituent phrase extractor.");
  }

  final Set<Pair<Integer,Integer>> spans = new HashSet<Pair<Integer,Integer>>();

  @Override
  public void setSentenceInfo(WordAlignment sent, String infoStr) {

    spans.clear();

    try {
      Tree t = Tree.valueOf(infoStr);
      for (Constituent c : t.constituents())
        spans.add(new Pair<Integer,Integer>(c.start(), c.end()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean ignore(WordAlignment sent, int f1, int f2, int e1, int e2) {
    boolean ignore = ignorePhrase(sent, -1, -1, e1, e2);
    if (ignore) System.err.printf("ignore: %s\n", sent.e().subsequence(e1, e2+1));
    return ignore;
  }

  private boolean ignorePhrase(WordAlignment sent, int f1, int f2, int e1, int e2) {

    if (spans.isEmpty()) {
      System.err.println("warning: constituents missing!");
      return false;
    }

    return !spans.contains(new Pair<Integer,Integer>(e1,e2));
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}

