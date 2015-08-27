package edu.stanford.nlp.mt.benchmark;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;

/**
 * Benchmark various hash-based histogram implementations.
 * 
 * @author Spence Green
 *
 */
public class HistogramTest {

  private static String randomString(int length) throws UnsupportedEncodingException {
    Random random = new Random();
    byte[] arr = new byte[length];
    for (int i=0; i<arr.length; i++) {
      int r = random.nextInt(128);
      arr[i] = (byte)(r & 0x7f);
    }
    return new String(arr, "UTF-8");
  }
  
  public static void main(String[] args) {
    int numInsertions = 10000000;
    System.out.printf("#insertions: %d%n", numInsertions);
    
    Random random = new Random();
    TimeKeeper timer = TimingUtils.start();
    Map<Long,AtomicInteger> hashMap = new HashMap<>();
    for (int i = 0; i < numInsertions; ++i) {
      long k = random.nextLong();
      AtomicInteger v = hashMap.get(k);
      if (v == null) {
        hashMap.putIfAbsent(k, new AtomicInteger());
        v = hashMap.get(k);
      }
      v.incrementAndGet();
    }
    System.out.println("Done HashMap A");
    timer.mark("HashMap A");
    
    hashMap = new HashMap<>();
    for (int i = 0; i < numInsertions; ++i) {
      long key = random.nextLong();
      hashMap.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }
    System.out.println("Done HashMap B");
    timer.mark("HashMap B");

    hashMap = new ConcurrentHashMap<>();
    for (int i = 0; i < numInsertions; ++i) {
      long k = random.nextLong();
      AtomicInteger v = hashMap.get(k);
      if (v == null) {
        hashMap.putIfAbsent(k, new AtomicInteger());
        v = hashMap.get(k);
      }
      v.incrementAndGet();
    }
    System.out.println("Done ConcurrentHashMap A");
    timer.mark("ConcurrentHashMap A");
    
    hashMap = new ConcurrentHashMap<>();
    for (int i = 0; i < numInsertions; ++i) {
      long key = random.nextLong();
      hashMap.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }
    System.out.println("Done ConcurrentHashMap B");
    timer.mark("ConcurrentHashMap B");

    System.out.println("Timing: " + timer.toString());
  }

}
