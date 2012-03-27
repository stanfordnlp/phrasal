/* Derived from com.google.common.hash.BloomFilterStrategies
 * 
 * Copyright (C) 2011 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package edu.stanford.nlp.mt.misc;


import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.hash.Funnel;
import com.google.common.hash.Hashing;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Collections of strategies of generating the k * log(M) bits required for an element to
 * be mapped to a BloomFilter of M bits and k hash functions. These
 * strategies are part of the serialized form of the Bloom filters that use them, thus they must be
 * preserved as is (no updates allowed, only introduction of new versions).
 *
 * Important: the order of the constants cannot change, and they cannot be deleted - we depend
 * on their ordinal for BloomFilter serialization.
 *
 * @author Dimitris Andreou
 */
enum BloomFilterStrategies implements BloomFilter.Strategy {
  /**
   * See "Less Hashing, Same Performance: Building a Better Bloom Filter" by Adam Kirsch and
   * Michael Mitzenmacher. The paper argues that this trick doesn't significantly deteriorate the
   * performance of a Bloom filter (yet only needs two 32bit hash functions).
   */
  MURMUR128_MITZ_64() {
    @Override public <T> boolean put(T object, Funnel<? super T> funnel,
        int numHashFunctions, BitArray bits) {
      // TODO(user): when the murmur's shortcuts are implemented, update this code
      /*
      long hash64 = Hashing.murmur3_128().newHasher().putObject(object, funnel).hash().asLong();
      int hash1 = (int) hash64;
      int hash2 = (int) (hash64 >>> 32); 
      */
      
      ByteBuffer bufH = ByteBuffer.wrap(Hashing.murmur3_128().newHasher().putObject(object, funnel).hash().asBytes());
      long hash1 = bufH.getLong();
      long hash2 = bufH.getLong();
    
      boolean bitsChanged = false;
      for (int i = 1; i <= numHashFunctions; i++) {
        long nextHash = hash1 + i * hash2;
        if (nextHash < 0) {
          nextHash = ~nextHash;
        }
        bitsChanged |= bits.set(nextHash % bits.size());
      }
      return bitsChanged;
    }

    @Override public <T> boolean mightContain(T object, Funnel<? super T> funnel,
        int numHashFunctions, BitArray bits) {
      /* long hash64 = Hashing.murmur3_128().newHasher().putObject(object, funnel).hash().asLong();
      int hash1 = (int) hash64;
      int hash2 = (int) (hash64 >>> 32); */
    
      ByteBuffer bufH = ByteBuffer.wrap(Hashing.murmur3_128().newHasher().putObject(object, funnel).hash().asBytes());
      long hash1 = bufH.getLong();
      long hash2 = bufH.getLong();
    
      for (int i = 1; i <= numHashFunctions; i++) {
        long nextHash = hash1 + i * hash2;
        if (nextHash < 0) {
          nextHash = ~nextHash;
        }
        if (!bits.get(nextHash % bits.size())) {
          return false;
        }
      }
      return true;
    }
  };

  // Note: We use this instead of java.util.BitSet because we need access to the long[] data field
  static class BitArray {
    final long[][] data;

    BitArray(long bits) {
      data = new long[1+(int)(bits>>36)][];
      for (int i = 0; i < data.length-1; i++) {
         data[i] = new long[1<<30];
      }
      data[data.length-1] = new long[(int)(bits & ((1<<30)-1))];
    }

    // Used by serialization
    BitArray(long[][] data) {
      checkArgument(data.length > 0, "data length is zero!");
      this.data = data;
    }

    /** Returns true if the bit changed value. */
    boolean set(long index) {
      boolean wasSet = get(index);
      data[(int)(index>>36)][(int)((index >> 6) & ((1<<30)-1))] |= (1L << index);
      return !wasSet;
    }

    boolean get(long index) {
      return (data[(int)(index>>36)][(int)((index >> 6) & ((1<<30)-1))] & (1L << index)) != 0;
    }

    /** Number of bits */
    long size() {
      return (data.length - 1) * (1<<30) * Long.SIZE +
             data[data.length -1].length * Long.SIZE;
    }

    BitArray copy() {
      return new BitArray(data.clone());
    }

    @Override public boolean equals(Object o) {
      if (o instanceof BitArray) {
        BitArray bitArray = (BitArray) o;
        return Arrays.equals(data, bitArray.data);
      }

      return false;
    }

    @Override public int hashCode() {
      return Arrays.hashCode(data);
    }
  }
}
