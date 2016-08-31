package edu.stanford.nlp.mt.util;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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

  private static final Logger logger = LogManager.getLogger(ParallelCorpus.class);

  public static final int MAX_SENTENCE_LENGTH = AlignedSentence.MAX_SENTENCE_LENGTH;
  private static final int DEFAULT_CAPACITY = 10000;
  
  protected List<AlignedSentence> segments;
  protected Vocabulary vocabulary;
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
    segments = new ArrayList<>(initialCapacity);
    vocabulary = new Vocabulary(initialCapacity);
  }

  /**
   * Get the vocabulary.
   * 
   * @return
   */
  public Vocabulary getVocabulary() { return vocabulary; }
  
  /**
   * Add an aligned sentence to the corpus.
   * 
   * @param source
   * @param target
   * @param align
   * @return
   */
  public boolean add(String source, String target, String align) {
    AlignedSentence s = getSentence(source, target, align);
    if (s == null) {
      logger.warn("Cannot add unaligned sentence to corpus: {} ||| {} ||| {}", source, target, align);
      return false;
    } else {
      segments.add(s);
      return true;
    }
  }
  
  /**
   * Create an {@link AlignedSentence} from a parallel input.
   * 
   * @param source
   * @param target
   * @param align
   * @return The sentence object, or null if the input exceeds the length constraints.
   */
  public AlignedSentence getSentence(String source, String target, String align) {
    if (align.trim().length() == 0) return null; // The segment is unaligned, so reject it.
    int[] f = stringToArray(source);
    int[] e = stringToArray(target);
    if (f.length > MAX_SENTENCE_LENGTH || 
        e.length > MAX_SENTENCE_LENGTH) {
      return null;
    }
    numSourcePos += f.length;
    numTargetPos += e.length;
    Alignment a = extractAlignment(align, f.length, e.length);
    AlignedSentence s = new AlignedSentence(f, e, a.f2e, a.e2f);
    return s;
  }
  
  /**
   * Convert a string to its integer representation.
   * 
   * @param string
   * @return
   */
  private int[] stringToArray(String string) {
    return Arrays.asList(string.trim().split("\\s+")).stream().mapToInt(i -> vocabulary.add(i)).toArray();
  }
  
  @SuppressWarnings("unchecked")
  public static Alignment extractAlignment(String alignStr, int sourceLen, int targetLen) {
    Set<Integer>[] f2e = new TreeSet[sourceLen];
    Set<Integer>[] e2f = new TreeSet[targetLen];
    String[] alignmentPoints = alignStr.split("\\s+");
    for (String point : alignmentPoints) {
      final int splitIdx = point.indexOf("-");
      int srcPos = Integer.parseInt(point.substring(0, splitIdx));
      if (srcPos < 0 || srcPos >= sourceLen) {
        throw new ArrayIndexOutOfBoundsException(String.format("Source length: %d  source index: %d", sourceLen, srcPos));
      }
      if (f2e[srcPos] == null) f2e[srcPos] = new TreeSet<>();
      Arrays.stream(point.substring(splitIdx+1, point.length()).split(","))
      .mapToInt(tgtStr -> Integer.parseInt(tgtStr)).sorted().forEach(tgtPos -> {
        if (tgtPos < 0 || tgtPos >= targetLen) {
          logger.error("Target length: {}  target index: {} alignmentStr: {}", targetLen, tgtPos, alignStr);
          throw new ArrayIndexOutOfBoundsException();
        }
        f2e[srcPos].add(tgtPos);
        if (e2f[tgtPos] == null) e2f[tgtPos] = new TreeSet<>();
        e2f[tgtPos].add(srcPos);
      });
    }
    return new Alignment(f2e, e2f);
  }

  /**
   * Container class for alignments.
   * 
   * @author Spence Green
   *
   */
  public static class Alignment {
    public Set<Integer>[] f2e;
    public Set<Integer>[] e2f;
    public Alignment(Set<Integer>[] f2e, Set<Integer>[] e2f) {
      this.f2e = f2e;
      this.e2f = e2f;
    }
  }
  
  /**
   * Get the sentence from the corpus.
   * 
   * @param sourceId
   * @return
   */
  public AlignedSentence get(int sourceId) {
    if (sourceId < 0 || sourceId >= segments.size()) {
      throw new ArrayIndexOutOfBoundsException();
    }
    return segments.get(sourceId);
  }

  /**
   * Number of segments in the corpus.
   * 
   * @return
   */
  public int size() { return segments.size(); }
  
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
    return segments.iterator();
  }
  
  /**
   * Get an unmodifiable view of the underlying list of segments.
   * 
   * @return
   */
  public List<AlignedSentence> getSegments() {
    return Collections.unmodifiableList(segments);
  }
  
  /**
   * Load a corpus from an aligned bitext.
   * 
   * @param sourceFile
   * @param targetFile
   * @param alignmentFile
   * @param expectedSize
   * @return
   * @throws IOException
   */
  public static ParallelCorpus loadCorpusFromFiles(String sourceFile, String targetFile,
      String alignmentFile, int expectedSize) throws IOException {
    try(LineNumberReader srcReader = IOTools.getReaderFromFile(sourceFile);
        LineNumberReader tgtReader = IOTools.getReaderFromFile(targetFile);
        LineNumberReader algnReader = IOTools.getReaderFromFile(alignmentFile)) {
      ParallelCorpus corpus = new ParallelCorpus(expectedSize);
      for (String srcLine; (srcLine = srcReader.readLine()) != null;) {
        if (srcReader.getLineNumber() % 10000 == 0) 
          logger.info("Reading corpus line {}...", srcReader.getLineNumber());
        String tgtLine = tgtReader.readLine();
        String algnLine = algnReader.readLine();
        corpus.add(srcLine, tgtLine, algnLine);
      }
      return corpus;
    }
  }
}
