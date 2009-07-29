package mt.train;

import java.util.*;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import edu.stanford.nlp.util.Pair;


/**
 * Same as LinearTimePhraseExtractor, but restricts phrases according to consituencies defined by dependencies
 * read from an info file.
 *
 * @author Michel Galley
 */
public class ConstituentPhraseExtractor extends DependencyPhraseExtractor {

  private static final int NO_ID = -2;
  private static final int ROOT_ID = -1;

  public ConstituentPhraseExtractor(Properties prop, AlignmentTemplates alTemps, List<AbstractFeatureExtractor> extractors) {
    super(prop, alTemps, extractors);
    System.err.println("Constituent phrase extractor.");
  }

  final Set<Pair<Integer,Integer>> spans = new HashSet<Pair<Integer,Integer>>();

  @Override
  @SuppressWarnings("unchecked")
  public void setSentenceInfo(String infoStr) {
    super.setSentenceInfo(infoStr);

    int[] startP = new int[deps.size()];
    Arrays.fill(startP, Integer.MAX_VALUE);
    int[] endP = new int[deps.size()];
    Arrays.fill(endP, NO_ID);

    // Find all spans:
    for (int i=0; i<deps.size(); ++i) {
      int hi = i;
      while(hi >= 0) {
        if(startP[hi] > i)
          startP[hi] = i;
        if(endP[hi] < i)
          endP[hi] = i;
        hi = deps.get(hi);
      }

      assert(hi == ROOT_ID); // should end with root node
    }

    // Store them in "spans":
    spans.clear();
    for(int i=0; i<startP.length; ++i)
      spans.add(new Pair<Integer,Integer>(startP[i],endP[i]));
  }

  @Override
  public boolean ignore(WordAlignment sent, int f1, int f2, int e1, int e2) {
    boolean ignore = ignorePhrase(sent, -1, -1, e1, e2);
    if(ignore) System.err.printf("ignore: %s\n", sent.e().subsequence(e1, e2+1));
    return ignore;
  }

  private boolean ignorePhrase(WordAlignment sent, int f1, int f2, int e1, int e2) {

    if(deps.isEmpty() || spans.isEmpty()) {
      System.err.println("warning: dependencies/constituents missing!");
      return false;
    }

    return !spans.contains(new Pair<Integer,Integer>(e1,e2));
  }

}
