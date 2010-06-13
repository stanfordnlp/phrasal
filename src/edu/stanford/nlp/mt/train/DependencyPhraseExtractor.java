package edu.stanford.nlp.mt.train;

import java.util.*;

import it.unimi.dsi.fastutil.ints.IntArrayList;


/**
 * Same as MosesPhraseExtractor, but restricts phrases according to dependencies read from an info file.
 *
 * @author Michel Galley
 */
@SuppressWarnings("unused")
public class DependencyPhraseExtractor extends MosesPhraseExtractor {

  private static final boolean DEBUG = System.getProperty("debugDependencyPhraseExtractor") != null;
  private static final boolean EXTRACT_MODIFIER_PHRASES = System.getProperty("extractModifierPhrases") != null;

  private static final int NO_ID = -2;
  private static final int ROOT_ID = -1;

  public DependencyPhraseExtractor(Properties prop, AlignmentTemplates alTemps, List<AbstractFeatureExtractor> extractors) {
    super(prop, alTemps, extractors);
    System.err.println("Dependency phrase extractor.");
  }

  final List<Integer> deps = new IntArrayList(500);

  @Override
  public void setSentenceInfo(WordAlignment sent, String infoStr) {

    deps.clear();
    StringTokenizer tok = new StringTokenizer(infoStr);

    // Read tokens:
    while(tok.hasMoreTokens()) {
      String t = tok.nextToken();
      int idx = t.indexOf(":");
      if(idx < 0)
        throw new RuntimeException("Bad token: "+t);
      int src = Integer.parseInt(t.substring(0,idx));
      int tgt = Integer.parseInt(t.substring(idx+1));

      if(src <= ROOT_ID || tgt < ROOT_ID)
        throw new RuntimeException(String.format("Ill-formed dependency: %d -> %d\n", src, tgt));

      while(deps.size() <= src)
        deps.add(NO_ID);
      deps.set(src, tgt);

    }

    if(DEBUG) {
      System.err.println("sent: "+sent.e().toString());
      System.err.println("dependencies: "+infoStr);
      for (int src = 0; src < deps.size(); src++) {
        int tgt = deps.get(src);
        System.err.printf("%d(%s)-%d(%s)\n", src, sent.e().get(src), tgt, (tgt>=0) ? sent.e().get(tgt) : "<root>");
      }
    }

    // Sanity check:
    for (int src = 0; src < deps.size(); src++) {
      if (deps.get(src) <= NO_ID)
        throw new RuntimeException(String.format("Word at %d dependent of %d\n", src, deps.get(src)));
    }
  }

  @Override
  public boolean ignore(WordAlignment sent, int f1, int f2, int e1, int e2) {
    boolean ignore = ignorePhrase(sent, -1, -1, e1, e2);
    if(DEBUG && ignore) System.err.printf("ignore: %s\n", sent.e().subsequence(e1, e2+1));
    return ignore;
  }

  private boolean ignorePhrase(WordAlignment sent, int f1, int f2, int e1, int e2) {

    if(deps.isEmpty()) {
      System.err.println("warning: dependencies missing!");
      return false;
    }

    int headIdx = NO_ID;
    int headAttachCount = 0;

    for(int si=e1; si<=e2; ++si) {
      int ti = deps.get(si);
      //System.err.printf("%d:%d s=%d(%s) t=%d(%s)\n", e1, e2, si, sent.e().get(si), ti, (ti>=0) ? sent.e().get(ti) : "");
      if(e1 <= ti && ti <= e2)
        continue;
      if(headIdx == NO_ID) {
        headIdx = ti;
        ++headAttachCount;
      } else {
        if(headIdx == ti) {
          ++headAttachCount;
        } else {
          return true;
        }
      }
    }

    if(headAttachCount <= 0)
      throw new RuntimeException(String.format("Head word %d without dependents\n", headIdx));

    if(headAttachCount > 1 && !EXTRACT_MODIFIER_PHRASES) {
      if(DEBUG) System.err.printf("ignore modifier phrase: %s\n", sent.e().subsequence(e1, e2+1));
      return true;
    }
    return false;
  }

}
