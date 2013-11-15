/**
 * 
 */
package edu.stanford.nlp.mt.neural;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.List;

import edu.stanford.nlp.mt.base.FlatPhraseTable;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.StringUtils;

/**
 * @author Minh-Thang Luong <lmthang@stanford.edu>, created on Nov 11, 2013
 *
 */
public class PhraseTableNeuralScores<FV> extends FlatPhraseTable<FV> {
  /**
   * @param filename
   * @throws IOException
   */
  public PhraseTableNeuralScores(String filename) throws IOException {
    super(filename);
    // TODO Auto-generated constructor stub
  }


  /**
   * @param phraseFeaturizer
   * @param filename
   * @throws IOException
   */
  public PhraseTableNeuralScores(
      RuleFeaturizer<IString, FV> phraseFeaturizer,
      String filename) throws IOException {
    // default is not to do log rithm on the scores
    super(phraseFeaturizer, filename, false);
  }
  
  /**
   * Load the phrase table from file. 
   * 
   * @param f
   * @param reverse
   * @return
   * @throws IOException
   */
  protected int init(File f, boolean reverse) throws IOException {
    Runtime rt = Runtime.getRuntime();
    long prePhraseTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    final long startTime = System.nanoTime();

    LineNumberReader reader = IOTools.getReaderFromFile(f);
    int numScores = -1;
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
      
      if (reverse) {
        Sequence<IString> tmp = source;
        source = target;
        target = tmp;
      }

      // Ensure that all rules in the phrase table have the same number of scores
      if (numScores < 0) {
        numScores = scoreList.size();
      } else if (numScores != scoreList.size()) {
        throw new RuntimeException(
            String
                .format(
                    "Error (line %d): Each entry must have exactly the same number of translation\n"
                        + "scores per line. Prior entries had %d, while the current entry has %d:",
                    reader.getLineNumber(), numScores, scoreList.size()));
      }
      float[] scores;
      try {
        scores = IOTools.stringListToNumeric(scoreList);
      } catch (NumberFormatException e) {
        e.printStackTrace();
        throw new RuntimeException(String.format("Number format error on line %d",
            reader.getLineNumber()));
      }

      if (targetConstellation.equals("")) {
        addEntry(source, target, null, scores);
      } else {
        addEntry(source, target,
            PhraseAlignment.getPhraseAlignment(targetConstellation), scores);
      }

      if (source.size() > longestSourcePhrase) {
        longestSourcePhrase = source.size();
      }
      if (target.size() > longestTargetPhrase) {
        longestTargetPhrase = target.size();
      }

      System.err.println(source + "\n" + target + "\n" + targetConstellation + "\n" + scoreList);
      break;
    }
    reader.close();

    // print some status information
    long postPhraseTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    double elapsedTime = ((double) System.nanoTime() - startTime) / 1e9;
    System.err
        .printf(
            "Done loading phrase table: %s (mem used: %d MiB time: %.3f s)%n",
            f.getAbsolutePath(),
            (postPhraseTableLoadMemUsed - prePhraseTableLoadMemUsed)
                / (1024 * 1024), elapsedTime);
    System.err.println("Longest foreign phrase: " + longestSourcePhrase);
    System.err.printf("Phrase table signature: %d%n", getSignature());
    return numScores;
  }
  
  
  /**
   * @param args
   */
  public static void main(String[] args) throws Exception {
    // TODO Auto-generated method stub
    if (args.length != 1) {
      System.out
          .println("Usage:\n\tjava ...PhraseTableWithNLMScores (phrasetable file)");
      System.exit(-1);
    }

    String model = args[0];
    //String phrase = args[1];
    long startTimeMillis = System.currentTimeMillis();
    System.out.printf("Loading phrase table: %s\n", model);
    FlatPhraseTable.createIndex(false);
    PhraseTableNeuralScores<String> ppt = new PhraseTableNeuralScores<String>(null, model);
    long totalMemory = Runtime.getRuntime().totalMemory() / (1L << 20);
    long freeMemory = Runtime.getRuntime().freeMemory() / (1L << 20);
    double totalSecs = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
    System.err.printf(
        "size = %d, secs = %.3f, totalmem = %dm, freemem = %dm\n",
        FlatPhraseTable.sourceIndex.size(), totalSecs, totalMemory, freeMemory);

  }

}
