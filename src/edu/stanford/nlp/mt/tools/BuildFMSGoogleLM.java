package edu.stanford.nlp.mt.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import edu.stanford.nlp.mt.base.FrequencyMultiScoreLanguageModel;
import edu.stanford.nlp.util.Pair;

/**
 * Command line utility for building a FrequenceMultiScoreLanguageModel over the
 * Google n-gram corpus (Web 1T 5-gram Version 1 - LDC2006T13).
 * 
 * @author daniel cer
 *
 */
public class BuildFMSGoogleLM {
   static public final String GoogleLMNamePrefix = "GLM";
    
   public static void main(String[] args) throws IOException {
      if (args.length != 4 && args.length != 5) {
         System.err.println("Usage:\n\tjava ...BuildFMSGoogleLM (Corpus Directory) (Order) (logBase) (save-to) [sampling]");
         System.exit(-1);
      }
      String corpusPath = args[0];
      int order = Integer.parseInt(args[1]);
      double logBase = Double.parseDouble(args[2]);
      String saveTo = args[3];
      
      double sampling;
      if (args.length == 5) {
        sampling = Double.parseDouble(args[4]);
      } else {
        sampling = 1.0;
      }
      
      if (order < 1) {
        System.err.printf("Order must be >= 1");
      }
      
      List<String> files = new LinkedList<String>();
      if (corpusPath.startsWith("FILE:")) {
        files.add(corpusPath.substring("FILE:".length()));  
      } else {
        files.add(corpusPath + File.separator + "1gms" + File.separator + "vocab");
        for (int i = 2; i <= order; i++) {
          File dir = new File(corpusPath + File.separator + i +"gms");
          for (String file : dir.list()) {
            if (file.startsWith(String.format("%dgm-", i))) {
              files.add(dir.getAbsolutePath() + File.separator + file);
            }
          }
        }
      }      
      String name = String.format("%s.%d.%e.s=%e", GoogleLMNamePrefix, order, logBase, sampling);
      long expectedInstances = 0;
      
      System.err.printf("Counting types at sampling level %e", sampling);
      long freqSum = 0; 
      for (Pair<String,Long> ngram : new FileListNgramCounts(files, true,  sampling)) {
        expectedInstances++;
        freqSum += ngram.second;
      }
      double avgFreq = freqSum/expectedInstances;
      System.err.printf("\nTypes found: %d Total Freq: %d AvgFreq: %.2f\n", expectedInstances, freqSum, avgFreq);
      long insertEstimate = (long)(expectedInstances*Math.log(avgFreq)/Math.log(logBase));
      System.err.printf("Insert Estimate: %d (%d * %.3f / %.3f)\n", insertEstimate, expectedInstances, Math.log(avgFreq), Math.log(logBase));
      FrequencyMultiScoreLanguageModel fmslm = new 
          FrequencyMultiScoreLanguageModel(name, insertEstimate, (int) logBase, order, 
             new FileListNgramCounts(files, true,  sampling));
      
      fmslm.save(saveTo);
   }
}

class FileListNgramCounts implements Iterable<Pair<String,Long>> {
  final List<String> files;
  final boolean lowercase;
  final double sample;
  final Random r = new Random(1);
  
  public FileListNgramCounts(List<String> files, boolean lowercase, double sample) {
     this.files = new ArrayList<String>(files);
     this.lowercase = lowercase;
     this.sample = sample;
  }
  
  static final Pattern unkMatch = Pattern.compile("\b<UNK>\b");
  static final Pattern sBgnMatch = Pattern.compile("\b<S>\b");
  static final Pattern sEndMatch = Pattern.compile("\b</S>\b");

  @Override
  public Iterator<Pair<String, Long>> iterator() {
    return new Iterator<Pair<String,Long>>() {
      static final int MAX_PRELOAD = 10000;
      Iterator<String> fileIter = files.iterator();
      Iterator<Pair<String,Long>> currentFileNgramIter = refreshCurrentFileNgrams().iterator();
      LineNumberReader reader;
      
      private List<Pair<String,Long>> refreshCurrentFileNgrams() {
        try {
          List<Pair<String,Long>> currentFileNgrams = new LinkedList<Pair<String,Long>>();
          cfnLoop: do {
            String line = (reader == null ? null : reader.readLine());
            while (line == null) {
               if (!fileIter.hasNext()) {
                 break cfnLoop;
               }
               String filename = fileIter.next();
               System.err.printf("Doing: %s\n", filename);
               
               if (filename.endsWith(".gz")) {
                 reader = new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename)))); 
               } else {
                 reader = new LineNumberReader(new FileReader(filename)); 
               }
               line = reader.readLine();     
            }
            
            double rn = r.nextDouble();
            if (rn > sample) continue;
            
            StringTokenizer tok = new StringTokenizer(line, "\t");
            String ngram = tok.nextToken();
          
            // normalize beginning and end of sentence markers as well as unknown word tokens
            
            unkMatch.matcher(ngram).replaceAll("<unk>");
            sBgnMatch.matcher(ngram).replaceAll("<s>");
            sEndMatch.matcher(ngram).replaceAll("</s>");
            
            if (lowercase) {
              ngram = ngram.toLowerCase(Locale.ENGLISH);
            }
            
            long count = Long.parseLong(tok.nextToken());
            currentFileNgrams.add(new Pair<String,Long>(ngram, count));  
          } while (currentFileNgrams.size() < MAX_PRELOAD);
          return currentFileNgrams;
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      
      @Override
      public boolean hasNext() {
        if (currentFileNgramIter.hasNext()) return true;
        currentFileNgramIter = refreshCurrentFileNgrams().iterator();
        return currentFileNgramIter.hasNext();
      }

      @Override
      public Pair<String, Long> next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return currentFileNgramIter.next();
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }
  
}