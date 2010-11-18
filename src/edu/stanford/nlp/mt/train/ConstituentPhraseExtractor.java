package edu.stanford.nlp.mt.train;

import java.util.*;

import edu.stanford.nlp.util.MutablePair;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Constituent;

/**
 * Same as MosesPhraseExtractor, but restricts phrases according to
 * constituencies read from a parse tree.
 *
 * @author Michel Galley
 */
public class ConstituentPhraseExtractor extends MosesPhraseExtractor {

  // NOTE: unfinished

  public ConstituentPhraseExtractor(Properties prop,
      AlignmentTemplates alTemps, List<AbstractFeatureExtractor> extractors) {
    super(prop, alTemps, extractors);
    System.err.println("Constituent phrase extractor.");
  }

  final Set<MutablePair<Integer, Integer>> spans = new HashSet<MutablePair<Integer, Integer>>();

  @Override
  public void setSentenceInfo(WordAlignment sent, String infoStr) {

    spans.clear();

    Tree t = Tree.valueOf(infoStr);
    for (Constituent c : t.constituents())
      spans.add(new MutablePair<Integer, Integer>(c.start(), c.end()));
  }

  @Override
  public boolean ignore(WordAlignment sent, int f1, int f2, int e1, int e2) {
    boolean ignore = ignorePhrase(sent, -1, -1, e1, e2);
    if (ignore)
      System.err.printf("ignore: %s\n", sent.e().subsequence(e1, e2 + 1));
    return ignore;
  }

  private boolean ignorePhrase(WordAlignment sent, int f1, int f2, int e1,
      int e2) {

    if (spans.isEmpty()) {
      System.err.println("warning: constituents missing!");
      return false;
    }

    return !spans.contains(new MutablePair<Integer, Integer>(e1, e2));
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

}
