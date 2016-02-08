package edu.stanford.nlp.mt.metrics;

import java.util.*;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.State;
import edu.stanford.nlp.mt.util.NBestListContainer;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 *
 * Implementation of the repetition rate measure 
 * (Nicola Bertoldi, Mauro Cettolo, Marcello Federico: 
 * Cache-based Online Adaptation for Machine Translation Enhanced Computer Assisted Translation,
 * MT Summit XIV, 2013)
 * 
 * also refer to (Cettolo et al., AMTA 2014)
 *
 * @author Joern Wuebker
 *
 * @param <TK>
 */
public class RepetitionRate<TK, FV> extends AbstractMetric<TK, FV> {
  public static final int DEFAULT_MAX_NGRAM_ORDER = 4;
  public static final int SLIDING_WINDOW_SIZE = 1000;
  private List<Sequence<TK>> corpus;

  /**
   *
   */
  public RepetitionRate() {
  }

  @Override
  public RepetitionRateIncrementalMetric getIncrementalMetric() {
    return new RepetitionRateIncrementalMetric();
  }

  @Override
  public RepetitionRateIncrementalMetric getIncrementalMetric(
      NBestListContainer<TK, FV> nbestList) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> getIncrementalMetricRecombinationFilter() {
    throw new UnsupportedOperationException();
  }

  public class RepetitionRateIncrementalMetric implements
        IncrementalEvaluationMetric<TK, FV> {
    
    final int maxNgramOrder = DEFAULT_MAX_NGRAM_ORDER;
    
    private List<Integer> totalNonSingletonNgrams = new ArrayList<Integer>();
    private List<Integer> totalNgrams = new ArrayList<Integer>();
    
    private List<Integer> windowNonSingletonNgrams = new ArrayList<Integer>();
    private List<Integer> windowNgrams = new ArrayList<Integer>();
    
    int slidingWindowSize = 0;
    int windowBeginSent = 0;
    int windowBeginWord = 0;
    
    Counter<Sequence<TK>> ngrams;

    RepetitionRateIncrementalMetric() {
      ngrams = new ClassicCounter<Sequence<TK>>();
      corpus = new ArrayList<Sequence<TK>>();
      
      for(int i = 0; i < maxNgramOrder; ++i) {
        totalNonSingletonNgrams.add(0);
        totalNgrams.add(0);
        windowNonSingletonNgrams.add(0);
        windowNgrams.add(0);
      }
      
    }
    
    /**
     *
     */
    private RepetitionRateIncrementalMetric(RepetitionRateIncrementalMetric m) {
      this.totalNonSingletonNgrams = new ArrayList<Integer>(m.totalNonSingletonNgrams);
      this.totalNgrams = new ArrayList<Integer>(m.totalNgrams);

      this.windowNonSingletonNgrams = new ArrayList<Integer>(m.windowNonSingletonNgrams);
      this.windowNgrams = new ArrayList<Integer>(m.windowNgrams);
      
      slidingWindowSize = m.slidingWindowSize;
      windowBeginSent = m.windowBeginSent;
      windowBeginWord = m.windowBeginWord;
      
      ngrams.addAll(m.ngrams);
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
      return new RepetitionRateIncrementalMetric(this);
    }
    
    @Override
    public double maxScore() {
      return 1;
    }

    @Override
    public String scoreDetails() {
      return "Total non-singleton n-grams: " + totalNonSingletonNgrams + "\n total n-grams: " + totalNgrams;
    }

    
    
    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        ScoredFeaturizedTranslation<TK, FV> tran) {
      return add(tran == null ? null : tran.translation);
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        Sequence<TK> tran) {
      
      if(tran == null)
        return this;
      
      corpus.add(tran);
      
      for(int i = 0; i < tran.size(); ++i) {
        for(int j = 0; j < maxNgramOrder; ++j)
          if(i >= j)
            addNgram(tran.subsequence(i-j, i + 1));

        if(slidingWindowSize < SLIDING_WINDOW_SIZE)
          slidingWindowSize++;
        else {
          for(int j = 0; j < maxNgramOrder; ++j)
            if(windowBeginWord + j < corpus.get(windowBeginSent).size())
              removeNgram(corpus.get(windowBeginSent).subsequence(windowBeginWord, windowBeginWord + j + 1));
          
          windowBeginWord++;
          
          if(windowBeginWord >= corpus.get(windowBeginSent).size()) {
            windowBeginSent++;
            windowBeginWord = 0;
          }
        }
          
        updateStats();
      }
      return this;
    }
    
    private void addNgram(Sequence<TK> ngram) {
      ngrams.incrementCount(ngram, 1);
      
      int n = ngram.size() - 1;
      
      if(ngrams.getCount(ngram) == 1)
        windowNgrams.set(n, windowNgrams.get(n) + 1);
      else if(ngrams.getCount(ngram) == 2)
        windowNonSingletonNgrams.set(n, windowNonSingletonNgrams.get(n) + 1);
    }
    
    private void removeNgram(Sequence<TK> ngram) {
      ngrams.decrementCount(ngram, 1);
      
      int n = ngram.size() - 1;
      
      if(ngrams.getCount(ngram) == 1)
        windowNonSingletonNgrams.set(n, windowNonSingletonNgrams.get(n) - 1);
      else if(ngrams.getCount(ngram) < 1)
        windowNgrams.set(n, windowNgrams.get(n) - 1);
    }
    
    private void updateStats() {      
      if(slidingWindowSize < SLIDING_WINDOW_SIZE) {
        for(int j = 0; j < maxNgramOrder; ++j) {
          totalNonSingletonNgrams.set(j, windowNonSingletonNgrams.get(j));
          totalNgrams.set(j, windowNgrams.get(j));
        }
      }
      else {
        for(int j = 0; j < maxNgramOrder; ++j) {
          totalNonSingletonNgrams.set(j, totalNonSingletonNgrams.get(j) + windowNonSingletonNgrams.get(j));
          totalNgrams.set(j, totalNgrams.get(j) + windowNgrams.get(j));
        }
      }
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int id,
        ScoredFeaturizedTranslation<TK, FV> trans) { 
      throw new UnsupportedOperationException();
    }

    @Override
    public double score() {
      double rv = 1.0f;
      for(int i = 0; i < maxNgramOrder; ++i)
        rv *= (double) totalNonSingletonNgrams.get(i) / (double) totalNgrams.get(i);
      
      rv = Math.pow(rv, 1.0f / (double) maxNgramOrder);
      // will be multiplied by 100 in edu.stanford.nlp.mt.tools.Evaluate
      return rv;
    }
  }
  
  @Override
  public double maxScore() {
    return 1;
  }
}
