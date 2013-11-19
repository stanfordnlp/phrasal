/**
 * 
 */
package edu.stanford.nlp.mt.neural;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.ejml.simple.SimpleMatrix;

import org.junit.Test;

import edu.stanford.nlp.util.Generics;

/**
 * @author Minh-Thang Luong <lmthang@stanford.edu>, created on Nov 16, 2013
 *
 */
public class BilingualEmbeddingTest {
  public static final String PREFIX = "projects/mt/"; //"";
  public static final String phraseTableFile = PREFIX + "test/inputs/embedding.table.zh-en";
  public static final String srcWordFile = PREFIX + "test/inputs/embedding.zh.words";
  public static final String srcVectorFile = PREFIX + "test/inputs/embedding.zh.We";
  public static final String tgtWordFile = PREFIX + "test/inputs/embedding.en.words";
  public static final String tgtVectorFile = PREFIX + "test/inputs/embedding.en.We";
  
  @Test
  public void testScoreEntry1() throws IOException {
    BilingualEmbedding biEmbedding = new BilingualEmbedding(srcWordFile, srcVectorFile, tgtWordFile, tgtVectorFile);
    
    String line = "投资 和 ||| over investment and over ||| (1) (2) ||| () (0) (1) () ||| 0.0";
    assertEquals(0.9098953738850512, biEmbedding.scorePhraseEntry(line, 0, 0), 1e-10); // sum, cosine
    assertEquals(60.49552089004014, biEmbedding.scorePhraseEntry(line, 0, 1), 1e-10); // sum, dot
    assertEquals(1.9742464857550281, biEmbedding.scorePhraseEntry(line, 1, 0), 1e-10); // alignment, cosine
    assertEquals(22.671727993190707, biEmbedding.scorePhraseEntry(line, 1, 1), 1e-10); // alignemt, dot
  }

  @Test
  public void testScoreEntry2() throws IOException {
    System.err.println("testScoreEntry2");
    double[][] values = new double[1][50];

    Map<String, SimpleMatrix> srcWordVectors = Generics.newTreeMap();
    values[0] = new double[]{0.12153, -0.81558, -0.4153, -0.59989, -0.20671, -0.44668, 0.71568, -0.24647, -0.5301, -0.0081304, -0.22624, -0.43984, -0.069139, -0.31909, 0.10316, 1.2096, 0.54021, -1.1589, 0.082936, 0.079201, 0.96654, 0.43052, -0.69995, -0.59791, -0.88103, 0.14726, -1.0737, -0.72765, -0.2512, -0.26905, -0.048704, -0.22528, -0.44037, 0.86639, -0.65976, -0.15912, -0.41047, 0.42126, 0.046179, 0.34737, 1.4583, 0.11204, -0.90819, -0.18211, -0.55173, -0.94106, -0.36523, 0.32685, 0.076892, -0.8231};
    srcWordVectors.put("用", (new SimpleMatrix(values)).transpose());
    srcWordVectors.put("UNK", (new SimpleMatrix(values)).transpose());

    Map<String, SimpleMatrix> tgtWordVectors = Generics.newTreeMap();
    values[0] = new double[]{-0.15667, -0.12861, -0.43491, -0.50228, 0.072108, 0.029807, 0.32976, -0.5116, 0.13201, 0.12218, -0.4536, 0.05831, -0.21321, -0.1231, 0.61563, 0.18507, 0.56737, -0.74451, -0.31762, 0.14573, -0.23856, 0.24076, 0.13654, -0.37566, -0.26511, 0.18118, -0.032189, -0.53265, 0.18245, -0.2119, -0.065214, 0.19514, 0.14149, 0.60183, -0.53336, 0.27318, -0.67016, -0.54059, -0.28265, -0.056866, 0.98519, -0.5733, -0.066288, -0.80522, 0.0057698, -0.047176, 0.36627, 0.08115, -0.27188, -0.29251};
    tgtWordVectors.put("use", (new SimpleMatrix(values)).transpose());
    tgtWordVectors.put("UNK", (new SimpleMatrix(values)).transpose());
    BilingualEmbedding biEmbedding = new BilingualEmbedding(srcWordVectors, tgtWordVectors);
    
    String line = "用 ||| use ||| (0) ||| (0) ||| 0.0"; 
    assertEquals(0.49876565864807204, biEmbedding.scorePhraseEntry(line, 0, 0), 1e-4); // sum, cosine
    assertEquals(5.573680241039998, biEmbedding.scorePhraseEntry(line, 0, 1), 1e-4); // sum, dot
    assertEquals(0.49876565864807204, biEmbedding.scorePhraseEntry(line, 1, 0), 1e-4); // alignment, cosine
    assertEquals(5.573680241039998, biEmbedding.scorePhraseEntry(line, 1, 1), 1e-4); // alignemt, dot
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
