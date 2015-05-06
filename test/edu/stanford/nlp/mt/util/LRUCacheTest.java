package edu.stanford.nlp.mt.util;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.stanford.nlp.mt.util.LRUCache.ArrayKey;

/**
 * Test for a least recently used cache.
 * 
 * @author Spence Green
 *
 */
public class LRUCacheTest {

  @Test
  public void testMaxSize() {
    LRUCache<ArrayKey, Integer> lruCache = new LRUCache<>(3);
    
    int[][] keys = {{0,1,2},
                    {3,4,5},
                    {6,7,8},
                    {0,1,2},
                    {2,3,4}};
    
    lruCache.put(new ArrayKey(keys[0]), 1);
    assertEquals(1, lruCache.size());
    lruCache.put(new ArrayKey(keys[1]), 2);
    assertEquals(2, lruCache.size());
    lruCache.put(new ArrayKey(keys[2]), 3);
    assertEquals(3, lruCache.size());
    lruCache.put(new ArrayKey(keys[3]), 4);
    assertEquals(3, lruCache.size());
    assertEquals(4, (int) lruCache.get(new ArrayKey(keys[0])));
    lruCache.put(new ArrayKey(keys[4]), 5);
    assertEquals(3, lruCache.size());
  } 
}
