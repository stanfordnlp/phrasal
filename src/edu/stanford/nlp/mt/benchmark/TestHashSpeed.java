package edu.stanford.nlp.mt.benchmark;

import java.nio.charset.Charset;

import edu.stanford.nlp.mt.util.MurmurHash3;


/**
 * @author yonik
 */
public class TestHashSpeed {
  static  Charset utf8Charset = Charset.forName("UTF-8");

  public static void main(String[] args) throws Exception {
    int arg = 0;
    int size = Integer.parseInt(args[arg++]);
    int iter = Integer.parseInt(args[arg++]);
    String method = args[arg++];

    byte[] arr = new byte[size];
    for (int i=0; i<arr.length; i++) {
      arr[i] = (byte)(i & 0x7f);
    }
    String s = new String(arr, "UTF-8");

    int ret = 0;
    long start = System.currentTimeMillis();

    if (method == null || method.equals("murmur32"))  {
      for (int i = 0; i<iter; i++) {
        // change offset and len so internal conditionals aren't predictable
        int offset = ret & 0x03;
        int len = arr.length - offset - ((ret>>3)&0x03);
        ret += MurmurHash3.murmurhash3_x86_32(arr, offset, len, i);
      }
    } else if (method.equals("slow_string")) {
      for (int i = 0; i<iter; i++) {
        // change offset and len so internal conditionals aren't predictable
        int offset = ret & 0x03;
        int len = arr.length - offset - ((ret>>3)&0x03);
        byte[] utf8 = s.getBytes(utf8Charset);
        ret += MurmurHash3.murmurhash3_x86_32(utf8, offset, len, i);
      }
    } else if (method.equals("fast_string")) {
      for (int i = 0; i<iter; i++) {
        // change offset and len so internal conditionals aren't predictable
        int offset = ret & 0x03;
        int len = arr.length - offset - ((ret>>3)&0x03);
        ret += MurmurHash3.murmurhash3_x86_32(s, offset, len, i);
      }
    } else {
      throw new RuntimeException("Unknown method " + method);
    }

    long end = System.currentTimeMillis();

    System.out.println("method="+method + " result="+ ret + " throughput=" + 1000 * ((double)size)*iter/(end-start) );

  }

}
