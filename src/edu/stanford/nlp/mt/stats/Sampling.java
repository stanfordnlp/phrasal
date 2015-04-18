package edu.stanford.nlp.mt.stats;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * Helper functions for sampling.
 * 
 * @author Spence Green
 *
 */
public final class Sampling {

  private Sampling() {}
  
  /**
   * Generates a stream of random integers sampled from a range without replacement.
   * 
   * @param origin
   * @param boundExclusive
   * @param numSamples
   * @return
   */
  public static IntStream sampleWithoutReplacement(int origin, int boundExclusive, int numSamples) {
    int[] range = IntStream.range(origin, boundExclusive).toArray();
    Random r = new Random();
    for (int i = range.length-1; i > 0; --i) {
      int j = r.nextInt(i+1);
      int tmp = range[i];
      range[i] = range[j];
      range[j] = tmp;
    }
    return StreamSupport.intStream(Arrays.spliterator(range, 0, numSamples), false);
  }
  
  /**
   * Draw a random sample (without replacement) from a list.
   * 
   * @param samples
   * @param sampleSize
   * @return
   */
  public static <V> List<V> randomSample(List<V> samples, int sampleSize) {
    if (sampleSize < samples.size()) return samples;
    return sampleWithoutReplacement(0, samples.size(), sampleSize).
        mapToObj(i -> samples.get(i)).collect(Collectors.toList());
  }

}
