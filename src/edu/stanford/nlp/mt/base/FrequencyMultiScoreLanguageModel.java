package edu.stanford.nlp.mt.base;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import edu.stanford.nlp.mt.misc.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.Sink;

import edu.stanford.nlp.util.Pair;

/**
 * N-gram frequency feature function language model back by
 * a Bloom filter as seen in Talbot and Osborne's 
 * Tera-Scale LMs on the Cheap (EMNLP 2007).
 * 
 * @author daniel cer
 *
 * @param <T>
 */
public class FrequencyMultiScoreLanguageModel implements MultiScoreLanguageModel<IString>, Serializable {
   
    private static final long serialVersionUID = 1L;
    
    final BloomFilter<Pair<String,Integer>> bloomFilter;
    final double logBase;
    final int order;
   
    public static final boolean VERBOSE = false;
    
    protected final String name;
    public static final double EXPECTED_COLLISIONS = 0.0001;
    public static final IString START_TOKEN = new IString("<s>");
    public static final IString END_TOKEN = new IString("</s>");
    public static final IString UNK_TOKEN = new IString("<unk>");
    
    int logQuantize(long x) {
      return (int) Math.round(Math.log(x)/Math.log(logBase));
    }


    private void put(String ngram, long count) {
      int lquant = logQuantize(count);
      if (VERBOSE) {
        System.err.printf("INSERT: ngram: %s count: %d lquant: %d\n", ngram, count, lquant);
      }
      for (int i = 0; i < lquant+1; i++) {
        Pair<String,Integer> p = new Pair<String,Integer>(ngram,i);
        bloomFilter.put(p);
        if (VERBOSE) {
          System.err.printf("(%d) mightContain: %b\n", i, bloomFilter.mightContain(p));
        }
      }
    }
    
    private int getLQScore(String ngram) {
      if (VERBOSE) {
        System.err.printf("ngram: %s\n", ngram);
      }
      for (int i = 0; i < Integer.MAX_VALUE; i++) {
        Pair<String,Integer> p = new Pair<String,Integer>(ngram,i);
        // System.err.printf("GET (%d) mightContain: %b\n", i, bloomFilter.mightContain(p));
        if (!bloomFilter.mightContain(p)) {
          return i-1;
        }
      }
      return Integer.MAX_VALUE;
    }
    
    public FrequencyMultiScoreLanguageModel(String name, long expectedInstances, double logBase, int order, Iterable<Pair<String,Long>> ngrams) {
      bloomFilter = BloomFilter.create(new StringIntegerPairFunnel(), (int)expectedInstances, EXPECTED_COLLISIONS); 
      this.logBase = logBase;
      this.order = order;
      this.name = name;
      for (Pair<String,Long> ngram : ngrams) {
          put(ngram.first, ngram.second);      
      }
    }
    
    public void save(String filename) throws IOException  {
      ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename));
      oos.writeObject(this);
      oos.close();
    }
    


    public static FrequencyMultiScoreLanguageModel load(String filename) throws IOException {
      ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename));
      try {
        FrequencyMultiScoreLanguageModel fmslm = (FrequencyMultiScoreLanguageModel)ois.readObject();
        return fmslm;
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public double score(Sequence<IString> sequence) {
      throw new UnsupportedOperationException();
    }

    @Override
    public IString getStartToken() {
      return START_TOKEN;
    }

    @Override
    public IString getEndToken() {
      return END_TOKEN;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public int order() {
      return order;
    }

    @Override
    public boolean releventPrefix(Sequence<IString> sequence) {
      Pair<String,Integer> zeroPair = new Pair<String,Integer>(sequence.toString(" "), 0);
      return bloomFilter.mightContain(zeroPair);
    }

    @Override
    public double[] multiScore(Sequence<IString> ngram) {
      double[] scores = new double[order];
      int sz = ngram.size();
      if (sz > order) {
        ngram = ngram.subsequence(sz - order, sz);
        sz = order;
      }
      for (int i = 0; i < order; i++) {
        scores[i] = Double.NEGATIVE_INFINITY;
      }
      for (int i = 0; i < order; i++) {
        if (i >= sz) break;
         Sequence<IString> s = ngram.subsequence(sz-1-i, sz);
         int lqScore = getLQScore(s.toString(" "));
         if (lqScore == -1) break;
         scores[i] = lqScore * Math.log(logBase);
      }
      return scores;
    }
    
}

class StringIntegerPairFunnel implements Funnel<Pair<String,Integer>>, Serializable {

  private static final long serialVersionUID = 1L;

  @Override
  public void funnel(Pair<String, Integer> from, Sink into) {
    into.putString(from.first);
    into.putInt(from.second);
  }
}