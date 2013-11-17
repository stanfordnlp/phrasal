/**
 * 
 */
package edu.stanford.nlp.mt.neural;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

/**
 * @author Minh-Thang Luong <lmthang@stanford.edu>, created on Nov 16, 2013
 *
 */
public class BilingualEmbeddingTest {
  public static final String PREFIX = "";
  public static final String phraseTableFile = PREFIX + "test/inputs/embedding.table.zh-en";
  public static final String srcWordFile = PREFIX + "test/inputs/embedding.zh.words";
  public static final String srcVectorFile = PREFIX + "test/inputs/embedding.zh.We";
  public static final String tgtWordFile = PREFIX + "test/inputs/embedding.en.words";
  public static final String tgtVectorFile = PREFIX + "test/inputs/embedding.en.We";
  
  @Test
  public void testScoreEntry() throws IOException {
    BilingualEmbedding biEmbedding = new BilingualEmbedding(srcWordFile, srcVectorFile, tgtWordFile, tgtVectorFile);
    
    String line = "投资 和 ||| over investment and over ||| (1) (2) ||| () (0) (1) () ||| 0.0";
    assertEquals(0.9098953738850512, biEmbedding.scorePhraseEntry(line, 0, 0), 1e-10); // sum, cosine
    assertEquals(60.49552089004014, biEmbedding.scorePhraseEntry(line, 0, 1), 1e-10); // sum, dot
    assertEquals(1.9742464857550281, biEmbedding.scorePhraseEntry(line, 1, 0), 1e-10); // alignment, cosine
    assertEquals(22.671727993190707, biEmbedding.scorePhraseEntry(line, 1, 1), 1e-10); // alignemt, dot
  }

  @Test
  public void testScoreTable() throws IOException {
    BilingualEmbedding biEmbedding = new BilingualEmbedding(srcWordFile, srcVectorFile, tgtWordFile, tgtVectorFile);
    
    // sum, cosine
    List<Double> scores = biEmbedding.scorePhraseTable(phraseTableFile, 0, 0);
    assertEquals(0.9540520929469261, scores.get(0), 1e-10);
    assertEquals(0.9098953738850512, scores.get(1), 1e-10);
    assertEquals(0.9900173299542396, scores.get(2), 1e-10);
    assertEquals(0.9540520929469261, scores.get(3), 1e-10);
    assertEquals(0.9885280502889845, scores.get(4), 1e-10);
    
    // sum, dot
    scores = biEmbedding.scorePhraseTable(phraseTableFile, 0, 1);
    assertEquals(48.03966008497559, scores.get(0), 1e-10);
    assertEquals(60.49552089004014, scores.get(1), 1e-10);
    assertEquals(35.58379927991105, scores.get(2), 1e-10);
    assertEquals(48.03966008497559, scores.get(3), 1e-10);
    assertEquals(14.945825441133227, scores.get(4), 1e-10);
    
    // alignment, cosine
    scores = biEmbedding.scorePhraseTable(phraseTableFile, 1, 0);
    assertEquals(1.9742464857550281, scores.get(0), 1e-10);
    assertEquals(1.9742464857550281, scores.get(1), 1e-10);
    assertEquals(1.9742464857550281, scores.get(2), 1e-10);
    assertEquals(1.9742464857550281, scores.get(3), 1e-10);
    assertEquals(0.9885280502889845, scores.get(4), 1e-10);
    
    scores = biEmbedding.scorePhraseTable(phraseTableFile, 1, 1);
    assertEquals(22.671727993190707, scores.get(0), 1e-10);
    assertEquals(22.671727993190707, scores.get(1), 1e-10);
    assertEquals(22.671727993190707, scores.get(2), 1e-10);
    assertEquals(22.671727993190707, scores.get(3), 1e-10);
    assertEquals(14.945825441133227, scores.get(4), 1e-10);
  }
}
