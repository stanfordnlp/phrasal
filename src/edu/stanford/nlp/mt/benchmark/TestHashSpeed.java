package edu.stanford.nlp.mt.benchmark;

import java.nio.charset.Charset;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import edu.stanford.nlp.mt.util.MurmurHash;
import edu.stanford.nlp.mt.util.MurmurHash3;
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;

/**
 * Adapted from Yonik's speed test.
 * 
 * @see {@link https://github.com/JohnLangford/vowpal_wabbit/wiki/murmur2-vs-murmur3}
 * 
 * @author Spence Green
 *
 */
public class TestHashSpeed {
  static  Charset utf8Charset = Charset.forName("UTF-8");

  public static void main(String[] args) throws Exception {
    int size = 1000;
    int iter = 1000000;

    byte[] arr = new byte[size];
    for (int i=0; i<arr.length; i++) {
      arr[i] = (byte)(i & 0x7f);
    }
    String s = new String(arr, "UTF-8");

    int ret = 0;

    TimeKeeper timer = TimingUtils.start();
    
    for (int i = 0; i<iter; i++) {
      // change offset and len so internal conditionals aren't predictable
      int offset = ret & 0x03;
      int len = arr.length - offset - ((ret>>3)&0x03);
      ret += MurmurHash.hash32(arr, len, i);
    }
    timer.mark("MurmurHash2");

    for (int i = 0; i<iter; i++) {
      // change offset and len so internal conditionals aren't predictable
      int offset = ret & 0x03;
      int len = arr.length - offset - ((ret>>3)&0x03);
      ret += MurmurHash.hash32(s);
    }
    timer.mark("MurmurHash2 string");
    
    for (int i = 0; i<iter; i++) {
      // change offset and len so internal conditionals aren't predictable
      int offset = ret & 0x03;
      int len = arr.length - offset - ((ret>>3)&0x03);
      ret += MurmurHash3.murmurhash3_x86_32(arr, offset, len, i);
    }
    timer.mark("MurmurHash3");

    HashFunction hf = Hashing.murmur3_32();
    for (int i = 0; i<iter; i++) {
      // change offset and len so internal conditionals aren't predictable
      int offset = ret & 0x03;
      int len = arr.length - offset - ((ret>>3)&0x03);
      ret += hf.hashBytes(arr, offset, len).asInt();
    }
    timer.mark("MurmurHash3 Google");
    
    for (int i = 0; i<iter; i++) {
      // change offset and len so internal conditionals aren't predictable
      int offset = ret & 0x03;
      int len = arr.length - offset - ((ret>>3)&0x03);
      byte[] utf8 = s.getBytes(utf8Charset);
      ret += MurmurHash3.murmurhash3_x86_32(utf8, offset, len, i);
    }
    timer.mark("MurmurHash3 slow string");

    for (int i = 0; i<iter; i++) {
      // change offset and len so internal conditionals aren't predictable
      int offset = ret & 0x03;
      int len = arr.length - offset - ((ret>>3)&0x03);
      ret += MurmurHash3.murmurhash3_x86_32(s, offset, len, i);
    }
    timer.mark("MurmurHash3 fast string");
        
    System.out.println("Timing: " + timer.toString());
  }
}
