package edu.stanford.nlp.mt.base;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;

public class DiskLM implements LanguageModel<IString> {
  protected final String name;
  //protected final Map<Integer,Integer> ngramOrder;
  //protected final Map<String, ProbBowPair> ngramProbBows;
  protected final RandomAccessFile lmfh;
  protected final long entries;
  protected final int buckets;
  protected final int order;
  static public final boolean verbose = false;
    
  protected final BitSet[] usedPositions;
  MappedByteBuffer[] tablebb;
  MappedByteBuffer[] symbolbb;
  final float[] prob;
  @SuppressWarnings("rawtypes")
  final Sequence[] entry;
  
  long calls = 0;
  long hits = 0;
  long lineHits = 0;
  
  
  public static final long MAGIC_NUMBER = 0xDA11CE4111L;
  
  public static final int ENTRY_SIZE = 24;
  public static final int HEADER_SIZE = 24;
  public static final int ENTRIES_PER_BUCKET = Integer.MAX_VALUE/ENTRY_SIZE;
  public static final double CACHE_FRACTION = 0.10;
  public static final int MAX_CACHE_SIZE    = 100000; // 100,000 n-grams 
  
  // Header: 
  //   magic number (long)  (8)
  //   version (int)        (4)
  //   order   (int)        (4)
  //   Entries (long)       (8)
  //
  // Array of []
  //    extended hashCode  : long  (8)
  //    ptrSymbol : long (8)
  //    float : prob     (4)
  //    float : bow      (4)
  //                     24
  // Symbols
  //
  // UTF-8 encoded strings
  // ....
  
  public DiskLM(String filename) throws IOException {
    lmfh = new RandomAccessFile(filename, "r");
    long magicNumber = lmfh.readLong();
    if (magicNumber != MAGIC_NUMBER) {
      throw new RuntimeException(String.format("Bad LM (magic number %x != %x)\n", magicNumber, MAGIC_NUMBER));  
    }
    int version = lmfh.readInt(); 
    if (version != 1) throw new RuntimeException(String.format("Unknown version: %d != 1\n", version));
    order = lmfh.readInt();
    entries = lmfh.readLong();
    int cachePositions = (int)(entries * CACHE_FRACTION);
    if (cachePositions > MAX_CACHE_SIZE) cachePositions = MAX_CACHE_SIZE;
    
    prob = new float[cachePositions];
    entry = new Sequence[cachePositions];
    
    // each bucket describes no more than Integer.MAX_VALUE / ENTRY_SIZE entries
    buckets = (int)(entries/ENTRIES_PER_BUCKET)+1;
    usedPositions = new BitSet[buckets];
    tablebb = new MappedByteBuffer[buckets];
    FileChannel fc = lmfh.getChannel();
    for (int i = 0; i < usedPositions.length; i++) {
      if (i + 1 < usedPositions.length) {
        usedPositions[i] = new BitSet(ENTRIES_PER_BUCKET);
        tablebb[i] = fc.map(FileChannel.MapMode.READ_ONLY, HEADER_SIZE +  (i*1L) * Integer.MAX_VALUE, Integer.MAX_VALUE);
      } else {
        int lastBucketPositions = (int)(entries - (buckets-1L)*ENTRIES_PER_BUCKET);
        usedPositions[i] = new BitSet(lastBucketPositions);
        tablebb[i] = fc.map(FileChannel.MapMode.READ_ONLY, HEADER_SIZE  + (i*1L) * Integer.MAX_VALUE, lastBucketPositions * ENTRY_SIZE);
      }
    }
    
    long symbolTableStart = HEADER_SIZE + entries * ENTRY_SIZE;
    long symbolTableSize = lmfh.length() - symbolTableStart;
    
    symbolbb = new MappedByteBuffer[1+(int)(symbolTableSize/Integer.MAX_VALUE)];
    for (int i = 0; i < symbolbb.length; i++) {
      if (i + 1 < symbolbb.length) {
        symbolbb[i] = fc.map(FileChannel.MapMode.READ_ONLY, symbolTableStart + (i*1L) * Integer.MAX_VALUE, Integer.MAX_VALUE);
      } else {
        symbolbb[i] = fc.map(FileChannel.MapMode.READ_ONLY, symbolTableStart + (i*1L) * Integer.MAX_VALUE, 
            lmfh.length() - symbolTableStart - (i*1L)*Integer.MAX_VALUE);
      }
    }
    
    this.name = String.format("DiskLM(%s)", filename);
    
    System.err.printf("Initializing %s\n", filename);
    
    long setPositions = 0;
    for (long i = 0; i < entries; i++) {
      //lmfh.seek(24L*i + 24);
      //long extendedHashCode = lmfh.readLong();
      long entryPos = i * ENTRY_SIZE;
      int bucket = getBucket(entryPos);
      int bucketPos = getBucketPos(entryPos);
      tablebb[bucket].position(bucketPos);
      long extendedHashCode = tablebb[bucket].getLong();
      if (extendedHashCode > 0) {
        usedPositions[bucket].set((int)(i % ENTRIES_PER_BUCKET));
        setPositions++;
      }
    }
    System.err.printf("Done (%s, non-zero entries: %d).\n", filename, setPositions);
  }
  
  private final int getBucket(long pos) {
    return (int)(pos>>31);
  }
  
  private final int getBucketPos(long pos) {
    return (int)(pos & (long)Integer.MAX_VALUE);
  }
  
  @Override
  public IString getStartToken() {
    return TokenUtils.START_TOKEN;
  }

  @Override
  public IString getEndToken() {
    return TokenUtils.END_TOKEN;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int order() {
    return order;
  }

  // Use the same algorithm as String.hashCode(), 
  // but with 64bit longs rather than an 32bit ints
  public static long extendedHashCode(String string) {
    long hashCode = 1;
    int strLength = string.length();
    for (int i = 0; i < strLength; i++) {
      hashCode = 31*hashCode + string.charAt(i);
    }
    return (hashCode >= 0 ? hashCode : -hashCode);
  }
  
  
  @Override
  synchronized public double score(Sequence<IString> sequence) {
    if (ARPALanguageModel.isBoundaryWord(sequence))
      return 0.0;
    Sequence<IString> ngramSeq;
    int sequenceSz = sequence.size();
    int maxOrder = (order < sequenceSz ? order : sequenceSz);

    if (sequenceSz == maxOrder) {
      ngramSeq = sequence;
    } else {
      ngramSeq = sequence.subsequence(sequenceSz - maxOrder, sequenceSz);
    }

    if (verbose) {
      if (calls % 10000 == 0)
          System.err.printf("Calls %d Hits: %d Frac: %f Line Hits: %d Frac: %f\n", 
              calls, hits, hits*1.0/calls, lineHits, lineHits*1.0/calls);
    }
    calls++;
    
    int h = ngramSeq.hashCode();
    int pos = h % prob.length;
    if (pos < 0) pos = -pos;
    if (entry[pos] != null) {
      lineHits++;
    }
    if (ngramSeq.equals(entry[pos])) {
      //System.err.println("cache hit!");
      hits++;
      return prob[pos];
    }
    
    String ngram = ngramSeq.toString(" ");
    float score = scoreR(ngram);
    entry[pos] = new RawSequence<IString>(ngramSeq);
    prob[pos] = score;
    
    if (verbose)
      System.err.printf("score: seq: %s logp: %f [%f]\n", sequence.toString(),
          score, score / Math.log(10));
    return score;
  }
  
  public static long getPutPosition(RandomAccessFile rfh, String entry, long entries) throws IOException {
    long extendedHashCode = extendedHashCode(entry);
    long startIdx = extendedHashCode % entries;
    for (long i = 0; i < entries; i++) {
      long checkPosition = (i+startIdx) % entries;
      long seekPos = 24*checkPosition+24;
      //System.err.printf("extenedHashCode: %d startIdx %d seekPos: %d\n", extendedHashCode, startIdx, seekPos);
      rfh.seek(seekPos);
      long entryHash = rfh.readLong();
      if (entryHash == 0 || entryHash == extendedHashCode) return checkPosition;
    }
    return -1;
  }
  
  
  public ProbBowPair getProbBow(String entry)  {
    try {
    long extendedHashCode = extendedHashCode(entry);
    long startIdx = extendedHashCode % entries;
    
    for (long i = 0; i < entries; i++) {
       long checkIndex = (i+startIdx) % entries;
       long checkPosition = checkIndex*ENTRY_SIZE;
       int bucket = getBucket(checkPosition);
       int bucketPos = getBucketPos(checkPosition);
       if (!usedPositions[bucket].get((int)(checkIndex % ENTRIES_PER_BUCKET))) return null;
       tablebb[bucket].position(bucketPos);
       long entryHash = tablebb[bucket].getLong();
       
       if (entryHash == 0) throw new RuntimeException();
       if (entryHash != extendedHashCode) continue;
       
       if (verbose) {
         System.err.printf("hash entry(%b:%s): %s (%s)\n", 
             usedPositions[bucket].get((int)(checkIndex / ENTRIES_PER_BUCKET)), checkPosition, entryHash, extendedHashCode);
       }
       long symbolPosition = tablebb[bucket].getLong();
       
       float prob = tablebb[bucket].getFloat();
       float bow  = tablebb[bucket].getFloat();
       
       if (verbose) {
         System.err.printf("symbol pos: %d\n", symbolPosition);
       }
       // lmfh.seek(symbolPosition);
       long relSymPosition = symbolPosition - HEADER_SIZE - ENTRY_SIZE*entries;
       int symbolBucket = getBucket(relSymPosition);
       symbolbb[symbolBucket].position(getBucketPos(relSymPosition));
       int bsize = symbolbb[symbolBucket].getShort();
       byte[] bytes = new byte[bsize];
       symbolbb[symbolBucket].get(bytes);
       String s = new String(bytes);
       if (verbose) {
         System.err.printf("found %s  (%s)", s, entry);
       }
       if (s.equals(entry)) {
         return new ProbBowPair(prob, bow);
       }
    }
    return null;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  /**
   * 
   * From CMU language model headers:
   * ------------------------------------------------------------------
   * 
   * This file is in the ARPA-standard format introduced by Doug Paul.
   * 
   * p(wd3|wd1,wd2)= if(trigram exists) p_3(wd1,wd2,wd3) else if(bigram w1,w2
   * exists) bo_wt_2(w1,w2)*p(wd3|wd2) else p(wd3|w2)
   * 
   * p(wd2|wd1)= if(bigram exists) p_2(wd1,wd2) else bo_wt_1(wd1)*p_1(wd2)
   * 
   */
  protected float scoreR(String ngram) {
    if (verbose) {
      System.err.printf("Looking up %s\n", ngram);
    }
    
    ProbBowPair pbp = getProbBow(ngram);
   
    if (pbp != null) { // match found
      if (verbose)
        System.err.printf("EM: scoreR: seq: %s logp: %f\n", ngram, pbp.p);
      return pbp.p;
    }
    
    int nxtSpace = ngram.indexOf(" ");
    if (nxtSpace < 0) {
      return Float.NEGATIVE_INFINITY; // OOV
    }
    int rnxtSpace = ngram.lastIndexOf(" ");
    String prefix = ngram.substring(0, rnxtSpace);
    ProbBowPair prefixPbp = getProbBow(prefix);
    double bow;
    if (prefixPbp == null) {
      bow = 0.0;
    } else {
      bow = prefixPbp.bow;
    }
    
    double p = bow + scoreR(ngram.substring(nxtSpace+1));
  
    if (verbose)
      System.err.printf("scoreR: seq: %s logp: %f [%f] bow: %f\n",
         ngram, p, p / Math.log(10), bow);
  
    return (float)p;
  }
  
  @Override
  public boolean relevantPrefix(Sequence<IString> sequence) {
    return getProbBow(sequence.toString(" ")) != null;
  }
  
  
  public static void ARPAtoDISKLM(String arpaLM, String diskLM) throws IOException {
    File arpaf = new File(arpaLM);
    File diskLMf = new File(diskLM);
    if (diskLMf.exists()) {
      diskLMf.delete();
    }
    
    Map<Integer,Integer> ngramOrder = new HashMap<Integer,Integer>();
    
    LineNumberReader reader = (arpaLM.endsWith(".gz") ? new LineNumberReader(
        new InputStreamReader(
            new GZIPInputStream(new FileInputStream(arpaLM))))
        : new LineNumberReader(new FileReader(arpaf)));

    // skip everything until the line that begins with '\data\'
    while (!ARPALanguageModel.readLineNonNull(reader).startsWith("\\data\\")) {
    }

    
    String inline;
    int maxOrder = 0;
    int totalNgrams = 0;
    while ((inline = ARPALanguageModel.readLineNonNull(reader)).startsWith("ngram")) {
      inline = inline.replaceFirst("ngram\\s+", "");
      String[] fields = inline.split("=");
      int ngramOrderN = Integer.parseInt(fields[0]);
      int ngramOrderCnt = Integer.parseInt(fields[1].replaceFirst(
          "[^0-9].*$", ""));
      ngramOrder.put(ngramOrderN, ngramOrderCnt);
      totalNgrams += ngramOrderCnt;
      maxOrder++;
    }

    float log10LogConstant = (float) Math.log(10);

    RandomAccessFile lmfh = new RandomAccessFile(diskLM, "rw");
    lmfh.writeLong(MAGIC_NUMBER);
    lmfh.writeInt(1); 
    lmfh.writeInt(ngramOrder.size());
    lmfh.writeLong(totalNgrams*3L);
    
    if (totalNgrams*3L > Integer.MAX_VALUE) throw new RuntimeException();

    long symbolTablePos = 24 + totalNgrams*3L*24;
    lmfh.setLength(symbolTablePos);
    // read in the n-gram tables one by one
    for (int order = 0; order < maxOrder; order++) {
      System.err.printf("Reading %d %d-grams...\n", ngramOrder.get(order+1),
          order + 1);
      String nextOrderHeader = String.format("\\%d-grams:", order + 1);

      // skip all material upto the next n-gram table header
      while (!ARPALanguageModel.readLineNonNull(reader).startsWith(nextOrderHeader)) {
      }
      // read in table
      while (!(inline = ARPALanguageModel.readLineNonNull(reader)).equals("")) {
        // during profiling, 'split' turned out to be a bottle neck
        // and using StringTokenizer is about twice as fast
        StringTokenizer tok = new StringTokenizer(inline);
        float prob = Float.parseFloat(tok.nextToken()) * log10LogConstant;

        StringBuilder ngramBuilder = new StringBuilder();
        
        for (int i = 0; i <= order; i++) {
          ngramBuilder.append(tok.nextToken());
          if (i != order) ngramBuilder.append(" ");
        }
       
        String ngram = ngramBuilder.toString();
        
        float bow = (tok.hasMoreElements() ? Float.parseFloat(tok.nextToken())
            * log10LogConstant : 0);
        
        long pos = getPutPosition(lmfh, ngram, totalNgrams*3); 
        lmfh.seek(24*pos + 24);
        lmfh.writeLong(extendedHashCode(ngram));
        lmfh.writeLong(symbolTablePos);
        lmfh.writeFloat(prob);
        lmfh.writeFloat(bow);
        lmfh.seek(symbolTablePos);
        lmfh.writeUTF(ngram);
        symbolTablePos = lmfh.getFilePointer();
      }
    }
    lmfh.close();
  }
  
  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.err.printf("Usage:\n\t java ...DiskLM (arpalm) (new_disklm)\n");
      System.exit(-1);
    }
    
    String arpaLM = args[0];
    String diskLM = args[1];
    ARPAtoDISKLM(arpaLM, diskLM);
  }
}

class ProbBowPair {
  public final float p;
  public final float bow;
  public ProbBowPair(float p, float bow) {
    this.p = p;
    this.bow = bow;
  }
}
