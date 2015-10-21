package edu.stanford.nlp.mt.metrics;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.ArraySequence;
import edu.stanford.nlp.mt.util.TokenUtils;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * N-gram recall of the reference following a fixed prefix. This metric is used
 * to compare systems that may use a reference prefix as a decoding hint.
 * References that do not start with the supplied prefix are ignored.
 * <p>
 * The file containing the prefixes is passed as the first argument
 * (i.e. the first reference).
 * <p>
 * This metric is well-defined for multiple references, but makes the most sense
 * when used with only a single reference that always matches the prefix.
 *
 * @author John DeNero
 */
public class BLEUAfterPrefixMetric<FV> extends BLEUMetric<IString, FV> {

  /**
   * The implementation just doctors the references to block everything not in
   * the prefix. For example:
   * <p>
   * Ref 1: I love dogs so much .
   * Ref 2: I like dogs a lot .
   * Ref 3: I love dogs a lot .
   * <p>
   * Pref:  I love dogs
   * <p>
   * With order=2 gets converted to...
   * <p>
   * Ref 1: _ _ dogs so much .
   * Ref 3: _ _ dogs a lot .
   * <p>
   * So the following hypothesis has these n-gram matches.
   * <p>
   * Hyp 1: I love dogs so damn much .
   * Unigram matches: (dogs) (so)  (much)  (.)
   * Bigram matches: (dogs so) (much .)
   * <p>
   * If a unigram is repeated in both the suffix and prefix of the reference,
   * it is retained in the masked reference.
   */
  public BLEUAfterPrefixMetric(List<List<Sequence<IString>>> referencesList, int order) {
    super(excludePrefix(referencesList, order), order);
    if (referencesList == null ||
            referencesList.size() < 1 ||
            referencesList.get(0).size() < 2) {
      throw new RuntimeException(
              "BLEUAfterPrefixMetric requires at least two arguments: the prefix file and one reference.");
    }
  }

  public BLEUAfterPrefixMetric(List<List<Sequence<IString>>> referencesList) {
    this(referencesList, DEFAULT_MAX_NGRAM_ORDER);
  }

  private static List<List<Sequence<IString>>> excludePrefix(List<List<Sequence<IString>>> referencesList, int order) {
    return referencesList.stream().map(refs -> {
      if (refs.size() < 2) {
        throw new RuntimeException("BLEUAfterPrefixMetric requires prefixes!");
      }
      Sequence<IString> prefix = refs.get(0);
      return refs.stream().skip(1)  // Skip the prefix
              .filter(ref -> ref.startsWith(prefix)) // Discard non-matches
              .map(ref -> {
                        Sequence<IString> masked = new ArraySequence<>(ref);
                        IString[] elements = masked.elements();
                        int exclude_up_to = Math.max(0, prefix.size()-order+1);
                        for (int i = 0; i < exclude_up_to; i++) {
                          if(!ref.subsequence(prefix.size(), ref.size()).contains(ref.subsequence(i,i+1)))
                            elements[i] = TokenUtils.NULL_TOKEN;
                        }
                        return (Sequence<IString>) new ArraySequence<>(true, elements);
                      }
              ).collect(toList());
    }).collect(toList());
  }

  @Override
  public BLEUIncrementalMetric getIncrementalMetric() {
    return new Incremental();
  }

  private class Incremental extends BLEUIncrementalMetric {
    @Override
    public double score() {
      return 2.0 * super.score();
    }
  }
}
