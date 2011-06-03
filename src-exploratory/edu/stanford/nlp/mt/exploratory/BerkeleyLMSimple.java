package edu.stanford.nlp.mt.exploratory;

/**
* Simplest possible invocation of BerkeleyLM
*
* @author Daniel Cer (http://dmcer.net)
*/


import java.util.List;
import java.util.Arrays;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import edu.berkeley.nlp.lm.StringWordIndexer;
import edu.berkeley.nlp.lm.BackoffLm;
import edu.berkeley.nlp.lm.WordIndexer;
import edu.berkeley.nlp.lm.io.LmReaders;
import edu.berkeley.nlp.lm.map.ConfigOptions;

import static java.lang.System.in;
import static java.lang.System.out;
import static java.lang.System.err;
import static java.lang.System.exit;

public class BerkeleyLMSimple {
  static public void main(String[] args) throws Exception {
    if (args.length != 2) {
      out.println("Usage:\n\tjava BerkeleyLMSimple (ARPA LM) (order)\n");
      exit(-1); 
    }

    String filename = args[0];
    int order = Integer.parseInt(args[1]);
    System.gc();
    Runtime rt = Runtime.getRuntime();
    long initMemUsage = rt.totalMemory() - rt.freeMemory();
    long startMillis = System.currentTimeMillis();
    BackoffLm<String> lm = LmReaders.readArpaLmFile(new ConfigOptions(), args[0], order, new StringWordIndexer());
    long loadTime = System.currentTimeMillis() - startMillis;
    long lmMemUsage = (rt.totalMemory() - rt.freeMemory()) - initMemUsage;
    out.printf("Load time: %.3f s Memory Usage: %d MiB\n", loadTime/1000.,
       lmMemUsage/(1024*1024));
    BufferedReader reader = new BufferedReader(new InputStreamReader(in));   
    for (String line = reader.readLine(); 
                line != null; 
                line = reader.readLine()) {
       List<String> sequence = Arrays.asList(line.split("\\s+"));
       double score = lm.scoreSequence(sequence);
       out.printf("score: %.3f\n", score);
    }
  }
}
