/**
 * 
 */
package edu.stanford.nlp.mt.neural;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.List;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.mt.base.FlatPhraseTable;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.neural.Utils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Score phrase table entries using word embeddings.
 * 
 * @author Minh-Thang Luong <lmthang@stanford.edu>, created on Nov 11, 2013
 *
 */
public class PhraseTableNeuralScores {
  /**
   * Load the phrase table from file. 
   * 
   * @param f
   * @param reverse
   * @return
   * @throws IOException
   */
  public static void score(String inPhraseTableFile, Embedding srcEmbedding, Embedding tgtEmbedding, 
      String outPhraseTableFile, int option) throws IOException {
    Runtime rt = Runtime.getRuntime();
    long prePhraseTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    final long startTime = System.nanoTime();

    LineNumberReader reader = IOTools.getReaderFromFile(new File(inPhraseTableFile));
    PrintStream writer = IOTools.getWriterFromFile(new File(outPhraseTableFile));
    int numScores = -1;
    int srcEmbeddingSize = srcEmbedding.getEmbeddingSize();
    int tgtEmbeddingSize = tgtEmbedding.getEmbeddingSize();
    
    int count = 0;
    for (String line; (line = reader.readLine()) != null;) {
      List<List<String>> fields = StringUtils.splitFieldsFast(line, FlatPhraseTable.FIELD_DELIM);
      
      // The standard format has five fields
      assert fields.size() == 5 : String.format("n-best list line %d has %d fields", 
          reader.getLineNumber(), fields.size());
      Sequence<IString> source = IStrings.toIStringSequence(fields.get(0));
      Sequence<IString> target = IStrings.toIStringSequence(fields.get(1));
//      String sourceConstellation = fields[2];
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

      
      double score = Double.NaN;
      if (option==0){ // average word vectors on each side and compute a similarity scores
        SimpleMatrix srcVector = new SimpleMatrix(srcEmbeddingSize, 1);
        for(IString src : source){
          srcVector = srcVector.plus(srcEmbedding.get(src.word()));
//          System.err.println(src.word() + "\t" + srcEmbedding.get(src.word()).transpose());
        }
        
        SimpleMatrix tgtVector = new SimpleMatrix(tgtEmbeddingSize, 1);
        for(IString tgt : target){
          tgtVector = tgtVector.plus(tgtEmbedding.get(tgt.word()));
//          System.err.println(tgt.word() + "\t" + tgtEmbedding.get(tgt.word()).transpose());
        }

        score = Utils.cosine(srcVector, tgtVector);
      } else if (option==1) {
        String targetConstellation = StringUtils.join(fields.get(3));
        score = 0;
//        System.err.println(source + "\t" + target + "\t" + targetConstellation + "\t" + scoreList);
        if (!targetConstellation.equals("")) {
          PhraseAlignment tgtAlignment = PhraseAlignment.getPhraseAlignment(targetConstellation);
          for(int i=0; i<target.size(); i++){
            int[] alignments = tgtAlignment.t2s(i);
            
            // sum cosine scores of aligned words
            if (alignments!=null && alignments.length>0){
              SimpleMatrix tgtVector = tgtEmbedding.get(target.get(i).word());
//              System.err.println(target.get(i).word() + "\t" + tgtVector.transpose());
              for (int alignment : alignments){ // sum cosine(srcVector, tgtVector)
//                System.err.println(source.get(alignment).word() + "\t" + srcEmbedding.get(source.get(alignment).word()).transpose());
                score += Utils.cosine(srcEmbedding.get(source.get(alignment).word()), tgtVector);
              }
            }
          }
        }
      }
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
    if (args.length != 7) {
      System.err.println("Usage:\n\tjava ...PhraseTableWithNLMScores (inPhraseTableFile) "
              + "(srcWordFile) (srcVectorFile) (tgtWordFile) (tgtVectorFile) (outPhraseTableFile) (scoreOption)");
      System.err.println("\t\tscoreOption: 0 sum vectors on each side and compute cosine similarity (default)"
          + ", 1 -- sum cosine scores of aligned words");
      
      System.exit(-1);
    }

    String inPhraseTableFile = args[0];
    String srcWordFile = args[1];
    String srcVectorFile = args[2];
    String tgtWordFile = args[3];
    String tgtVectorFile = args[4];
    String outPhraseTableFile = args[5];
    int option = Integer.parseInt(args[6]);
    
    Embedding srcEmbedding = new Embedding(srcWordFile, srcVectorFile);
    Embedding tgtEmbedding = new Embedding(tgtWordFile, tgtVectorFile);
    
    long startTimeMillis = System.currentTimeMillis();
    
    System.err.printf("# Loading phrase table ...\n  phrase table file = %s\n", inPhraseTableFile);
    PhraseTableNeuralScores.score(inPhraseTableFile, srcEmbedding, tgtEmbedding, outPhraseTableFile, option);
    
    long totalMemory = Runtime.getRuntime().totalMemory() / (1L << 20);
    long freeMemory = Runtime.getRuntime().freeMemory() / (1L << 20);
    double totalSecs = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
    System.err.printf("secs = %.3f, totalmem = %dm, freemem = %dm\n", totalSecs, totalMemory, freeMemory);

  }

}
