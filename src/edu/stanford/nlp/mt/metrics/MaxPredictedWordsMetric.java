package edu.stanford.nlp.mt.metrics;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.State;
import edu.stanford.nlp.mt.util.NBestListContainer;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Metric that counts the average number of correctly predicted words following
 * a prefix of the reference translation. This metric differs from
 * MaxPredictedWordsMetric because it does not assume that the hypothesis begins
 * with the designated prefix (but still only gives credit for matching words
 * after the prefix in the reference).
 * <p>
 * The file containing the prefixes is passed as the first argument
 * (i.e. the first reference).
 *
 * @param <TK>
 * @author John DeNero
 */
public class MaxPredictedWordsMetric<TK, FV> extends AbstractMetric<TK, FV> {
  final List<List<Sequence<TK>>> referencesList;

  public MaxPredictedWordsMetric(List<List<Sequence<TK>>> referencesList) {
    if (referencesList == null ||
            referencesList.size() < 1 ||
            referencesList.get(0).size() < 2)
      throw new RuntimeException(
              "MaxPredictedWordsMetric requires at least two arguments: the prefix file and one reference.");

    this.referencesList = referencesList;
  }

  @Override
  public MaxPredictedWordsIncrementalMetric getIncrementalMetric() {
    return new MaxPredictedWordsIncrementalMetric();
  }

  @Override
  public MaxPredictedWordsIncrementalMetric getIncrementalMetric(
          NBestListContainer<TK, FV> nbestList) {
    throw new UnsupportedOperationException();
  }

  @Override
  public RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> getIncrementalMetricRecombinationFilter() {
    throw new UnsupportedOperationException();
  }

  public class MaxPredictedWordsIncrementalMetric implements
          IncrementalEvaluationMetric<TK, FV> {

    private int totalPredictedWords = 0;
    private List<Integer> predictedWords = new ArrayList<>();

    MaxPredictedWordsIncrementalMetric() {
    }

    /**
     * Copy constructor.
     */
    private MaxPredictedWordsIncrementalMetric(MaxPredictedWordsIncrementalMetric m) {
      this.predictedWords = new ArrayList<>(m.predictedWords);
      this.totalPredictedWords = m.totalPredictedWords;
    }

    @Override
    public int size() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(IncrementalEvaluationMetric<TK, FV> o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int depth() {
      throw new UnsupportedOperationException();
    }

    @Override
    public State<IncrementalEvaluationMetric<TK, FV>> parent() {
      throw new UnsupportedOperationException();
    }

    @Override
    public double partialScore() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
      super.clone();
      return new MaxPredictedWordsIncrementalMetric(this);
    }

    @Override
    public double maxScore() {
      return Integer.MAX_VALUE;
    }

    @Override
    public String scoreDetails() {
      return "Total predicted words: " + totalPredictedWords + "\n lines: " + predictedWords.size();
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
            ScoredFeaturizedTranslation<TK, FV> tran) {
      return add(tran == null ? null : tran.translation);
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> add(Sequence<TK> tran) {
      if (referencesList.size() <= predictedWords.size())
        throw new RuntimeException(
                "MaxPredictedWordsMetric: insufficient number of references.");

      // No credit for null translations.
      if (tran == null) {
        predictedWords.add(0);
        return this;
      }

      final int id = predictedWords.size();
      predictedWords.add(getMaxPredictedWords(tran, referencesList.get(id), id));
      totalPredictedWords += predictedWords.get(id);
      return this;
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int id, ScoredFeaturizedTranslation<TK, FV> trans) {
      totalPredictedWords -= predictedWords.get(id);
      predictedWords.set(id, getMaxPredictedWords(trans.translation, referencesList.get(id), id));
      totalPredictedWords += predictedWords.get(id);
      return this;
    }

    @Override
    public double score() {
      double rv = (double) totalPredictedWords / (double) predictedWords.size();
      // will be multiplied by 100 in edu.stanford.nlp.mt.tools.Evaluate
      return rv / 100;
    }
  }

  public static <TK> int getMaxPredictedWords(Sequence<TK> tran, List<Sequence<TK>> references, int id) {
    // The first reference is actually the prefix
    assert (references.size() > 1);

    Sequence<TK> prefix = references.get(0);

    boolean foundRef = false;
    int maxPredictedWords = 0;

    for (int i = 1; i < references.size(); ++i) {
      Sequence<TK> ref = references.get(i);
      if (!ref.startsWith(prefix))
        continue;

      foundRef = true;
      final int start = prefix.size();
      int predictedWords = 0;
      for (int end = start + 1; end <= ref.size(); end++) {
        Sequence<TK> extension = ref.subsequence(start, end);
        if (!ref.contains(extension)) {
          break;
        }
        predictedWords++;
      }
      maxPredictedWords = Math.max(maxPredictedWords, predictedWords);
    }

    if (!foundRef)
      throw new RuntimeException(
              "MaxPredictedWordsMetric: No reference found with correct prefix in line " + (id + 1));

    return maxPredictedWords;
  }

  @Override
  public double maxScore() {
    return Integer.MAX_VALUE;
  }

  public static class Local<TK, FV> implements SentenceLevelMetric<TK, FV> {

    public Local() {
    }

    @Override
    public double score(int sourceId, Sequence<TK> source,
                        List<Sequence<TK>> references, Sequence<TK> translation) {
      return getMaxPredictedWords(translation, references, sourceId);
    }

    @Override
    public void update(int sourceId, List<Sequence<TK>> references,
                       Sequence<TK> translation) {
    }

    @Override
    public boolean isThreadsafe() {
      return true;
    }

  }
}
