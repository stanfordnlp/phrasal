/**
 * 
 */
package edu.stanford.nlp.mt.neural;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.List;

import edu.stanford.nlp.mt.base.FlatPhraseTable;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.util.StringUtils;

/**
 * Score phrase table entries using word embeddings.
 * 
 * @author Minh-Thang Luong <lmthang@stanford.edu>, created on Nov 11, 2013
 *
 */
public class PhraseTableNeuralScores {
  /**
   * Add a neural score to a phrase table
   * 
   * @param inPhraseTableFile
   * @param biEmbedding
   * @param outPhraseTableFile
   * @param option: 0 -- sum phrase vector, 1 -- use alignment info
   * @param operator: 0 -- cosine, 1 -- dot product
   * @throws IOException
   */
  public static void score(String inPhraseTableFile, BilingualEmbedding biEmbedding, 
      String outPhraseTableFile, int option, int operator) throws IOException {
    Runtime rt = Runtime.getRuntime();
    long prePhraseTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    final long startTime = System.nanoTime();

    LineNumberReader reader = IOTools.getReaderFromFile(new File(inPhraseTableFile));
    PrintStream writer = IOTools.getWriterFromFile(new File(outPhraseTableFile));
    int numScores = -1;
    
    int count = 0;
    for (String line; (line = reader.readLine()) != null;) {
      List<List<String>> fields = StringUtils.splitFieldsFast(line, FlatPhraseTable.FIELD_DELIM);
      
      // The standard format has five fields
      assert fields.size() == 5 : String.format("n-best list line %d has %d fields", 
          reader.getLineNumber(), fields.size());
      Sequence<IString> source = IStrings.toIStringSequence(fields.get(0));
      Sequence<IString> target = IStrings.toIStringSequence(fields.get(1));
//      String sourceConstellation = fields[2];
      String targetConstellation = StringUtils.join(fields.get(3));
      List<String> scoreList = fields.get(4);

      // Ensure that all rules in the phrase table have the same number of scores
      if (numScores < 0) {
        numScores = scoreList.size();
      } else if (numScores != scoreList.size()) {
        throw new RuntimeException(
            String.format(
                    "Error (line %d): Each entry must have exactly the same number of translation\n"
                        + "scores per line. Prior entries had %d, while the current entry has %d:",
                    reader.getLineNumber(), numScores, scoreList.size()));
      }

      
      double score = biEmbedding.scorePhraseEntry(source, target, targetConstellation, option, operator);
      writer.println(line + " " + score);
      
      count++;
      if(count % 100000 == 0){
        System.err.print(" (" + count/1000 + "K) ");
      }
    }
    
    reader.close();
    writer.close();
    
    // print some status information
    long postPhraseTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    double elapsedTime = ((double) System.nanoTime() - startTime) / 1e9;
    System.err.printf("Done loading phrase table: num phrase pairs = %d, mem used = %d MiB, time = %.3f s\n",
            count, (postPhraseTableLoadMemUsed - prePhraseTableLoadMemUsed) / (1024 * 1024), elapsedTime);
  }
  
  
  /**
   * @param args
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 6) {
      System.err.println("Usage:\n\tjava ...PhraseTableNeuralScores (inPhraseTableFile) "
              + "(srcWordFile) (srcVectorFile) (tgtWordFile) (tgtVectorFile) (outPhraseTableFile)"); // (scoreOption) (operatorOption)");
      //System.err.println("\t\tscoreOption: 'all' -- sum vectors on each side and compute cosine similarity (default)"
      //    + ", 'align' -- sum scores of aligned word pairs");
      //System.err.println("\t\toperatorOption: 0 -- cosine, 1 -- dot product");
      
      System.exit(-1);
    }

    String inPhraseTableFile = args[0];
    String srcWordFile = args[1];
    String srcVectorFile = args[2];
    String tgtWordFile = args[3];
    String tgtVectorFile = args[4];
    String outPhraseTableFile = args[5];
    int option = 0; //Integer.parseInt(args[6]);
    int operator = 0; //Integer.parseInt(args[7]);
    
    BilingualEmbedding biEmbedding = new BilingualEmbedding(srcWordFile, srcVectorFile, tgtWordFile, tgtVectorFile);
    long startTimeMillis = System.currentTimeMillis();
    
    System.err.printf("# Loading phrase table ...\n  phrase table file = %s\n", inPhraseTableFile);
    PhraseTableNeuralScores.score(inPhraseTableFile, biEmbedding, outPhraseTableFile, option, operator);
    
    long totalMemory = Runtime.getRuntime().totalMemory() / (1L << 20);
    long freeMemory = Runtime.getRuntime().freeMemory() / (1L << 20);
    double totalSecs = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
    System.err.printf("secs = %.3f, totalmem = %dm, freemem = %dm\n", totalSecs, totalMemory, freeMemory);

  }

}
