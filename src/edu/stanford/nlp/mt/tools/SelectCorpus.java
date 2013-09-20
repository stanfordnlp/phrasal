package edu.stanford.nlp.mt.tools;

import static java.lang.System.*;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import edu.stanford.nlp.mt.base.LineIndexedCorpus;

/**
 * Select a subset of lines from a corpus.
 *   
 * @author Thang Luong <lmthang@stanford.edu>
 *
 */
public class SelectCorpus {
   static public void usage() {
      err.println("Usage:\n\tjava ...SelectCorpus (line.file) (in.corpus) (out.corpus) [offset]");
      err.println("  offset: to change from 0-based (default) indexing to offset-based indexing.");
   }
   
   static public void main(String[] args) throws IOException {
      if (args.length != 3 && args.length != 4) {
         usage();
         System.exit(-1);
      }
      
      String lineFn = args[0];
      String inCorpusFn = args[1];
      String outCorpusFn = args[2];
      int offset = (args.length==4) ? Integer.parseInt(args[3]) : 0;
      
      // in corpus
      err.printf("# Opening %s\n", inCorpusFn);
      LineIndexedCorpus inCorpus = new LineIndexedCorpus(inCorpusFn);
      
      // out corpus
      PrintWriter outPW = new PrintWriter(new OutputStreamWriter(
        new FileOutputStream(outCorpusFn), "UTF-8"));
      
      BufferedReader br = new BufferedReader(new FileReader(lineFn));
      String line;
      while ((line=br.readLine())!=null){
        outPW.write(inCorpus.get(Integer.parseInt(line)-offset) + "\n");
      }
      br.close();
      outPW.close();
   }
}
