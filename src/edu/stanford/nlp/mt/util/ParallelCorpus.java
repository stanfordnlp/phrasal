package edu.stanford.nlp.mt.util;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Parallel corpus.
 * 
 * NOTE: The fields are public, non-final for fast serialization/deserialization.
 * 
 * @author Spence Green
 *
 */
public class ParallelCorpus implements Iterable<AlignedSentence>, Serializable {
  
  private static final long serialVersionUID = 5837610708369154242L;

  private static transient final Logger logger = LogManager.getLogger(ParallelCorpus.class);

  private static final int DEFAULT_CAPACITY = 10000;
  
  protected List<AlignedSentence> corpus;
  protected Vocabulary index;
  protected int numSourcePos = 0;
  protected int numTargetPos = 0;
  
  /**
   * Constructor.
   */
  public ParallelCorpus() {
    this(DEFAULT_CAPACITY);
  }
  
  /**
   * Constructor.
   * 
   * @param initialCapacity
   */
  public ParallelCorpus(int initialCapacity) {
    this(DEFAULT_CAPACITY, false);
  }
  
  /**
   * Constructor.
   * 
   * @param initialCapacity
   * @param isDecoderLocal
   */
  public ParallelCorpus(int initialCapacity, boolean isDecoderLocal) {
    corpus = new ArrayList<>(initialCapacity);
    boolean isSystemIndex = ! isDecoderLocal;
    index = new Vocabulary(initialCapacity, isSystemIndex);
  }

  /**
   * Add an aligned sentence to the corpus.
   * 
   * @param source
   * @param target
   * @param align
   * @return
   */
  public AlignedSentence add(String source, String target, String align) {
    int[] f = stringToArray(source);
    numSourcePos += f.length;
    int[] e = stringToArray(target);
    numTargetPos += e.length;
    Alignment a = extractAlignment(align, f.length, e.length);
    AlignedSentence s = new AlignedSentence(f, e, a.f2e, a.e2f);
    corpus.add(s);
    return s;
  }
  
  private int[] stringToArray(String string) {
    return Arrays.asList(string.trim().split("\\s+")).stream().mapToInt(i -> index.add(i)).toArray();
  }
  
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static Alignment extractAlignment(String alignStr, int sourceLen, int targetLen) {
    Set[] f2e = new HashSet[sourceLen];
    Set[] e2f = new HashSet[targetLen];
    String[] alignmentPoints = alignStr.split("\\s+");
    for (String point : alignmentPoints) {
      final int splitIdx = point.indexOf("-");
      int srcPos = Integer.parseInt(point.substring(0, splitIdx));
      if (srcPos < 0 || srcPos >= sourceLen) {
        throw new ArrayIndexOutOfBoundsException(String.format("Source length: %d  source index: %d", sourceLen, srcPos));
      }
      if (f2e[srcPos] == null) f2e[srcPos] = new HashSet<>();
      for (String tgtStr : point.substring(splitIdx+1, point.length()).split(",")) {
        int tgtPos = Integer.parseInt(tgtStr);
        if (tgtPos < 0 || tgtPos >= targetLen) {
          throw new ArrayIndexOutOfBoundsException(String.format("Target length: %d  target index: %d", targetLen, tgtPos));          
        }
        f2e[srcPos].add(tgtPos);
        if (e2f[tgtPos] == null) e2f[tgtPos] = new HashSet<>();
        e2f[tgtPos].add(srcPos);
      }
    }
    return new Alignment(f2e, e2f);
  }

  /**
   * Container class for alignments.
   * 
   * @author Spence Green
   *
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static class Alignment {
    public int[][] f2e;
    public int[][] e2f;
    public Alignment(Set[] f2e, Set[] e2f) {
      this.f2e = flatten(f2e);
      this.e2f = flatten(e2f);
    }
    private static int[][] flatten(Set[] f2e) {
      int[][] f2eArr = new int[f2e.length][];
      for (int i = 0; i < f2e.length; ++i) {
        f2eArr[i] = f2e[i] == null ? new int[0] :
          f2e[i].stream().mapToInt(x -> (int) x).sorted().toArray();
      }
      return f2eArr;
    }
  }
  
  /**
   * Get the sentence from the corpus.
   * 
   * @param sourceId
   * @return
   */
  public AlignedSentence get(int sourceId) {
    if (sourceId < 0 || sourceId >= corpus.size()) {
      throw new ArrayIndexOutOfBoundsException();
    }
    return corpus.get(sourceId);
  }

  /**
   * Number of segments in the corpus.
   * 
   * @return
   */
  public int size() { return corpus.size(); }
  
  /**
   * Number of source corpus positions.
   * 
   * @return
   */
  public int numSourcePositions() {
    return numSourcePos;
  }
  
  /**
   * Number of target corpus positions.
   * 
   * @return
   */
  public int numTargetPositions() {
    return numTargetPos;
  }
  
  @Override
  public Iterator<AlignedSentence> iterator() {
    return corpus.iterator();
  }
  
  /**
   * Load a corpus from an aligned bitext.
   * 
   * @param sourceFile
   * @param targetFile
   * @param alignmentFile
   * @param expectedSize
   * @param isDecoderLocal 
   * @return
   * @throws IOException
   */
  public static ParallelCorpus loadCorpusFromFiles(String sourceFile, String targetFile,
      String alignmentFile, int expectedSize, boolean isDecoderLocal) throws IOException {
    ParallelCorpus corpus = new ParallelCorpus(expectedSize, isDecoderLocal);
    LineNumberReader srcReader = IOTools.getReaderFromFile(sourceFile);
    LineNumberReader tgtReader = IOTools.getReaderFromFile(targetFile);
    LineNumberReader algnReader = IOTools.getReaderFromFile(alignmentFile);
    for (String srcLine; (srcLine = srcReader.readLine()) != null;) {
      String tgtLine = tgtReader.readLine();
      String algnLine = algnReader.readLine();
      corpus.add(srcLine, tgtLine, algnLine);
    }
    srcReader.close();
    tgtReader.close();
    algnReader.close();
    return corpus;
  }
}
