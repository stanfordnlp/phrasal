package edu.stanford.nlp.mt.tools;

import static java.lang.System.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

import edu.stanford.nlp.mt.base.LineIndexedCorpus;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Triple;

/**
 * Feature Decay Algorithm (FDA) bi-text selection.
 * 
 * Based on the selection technique presented in Bicici and Yuret's 
 * "Instance Selection for Machine Translation using feature Decay Algorithms" 
 * (WMT2011)  
 * 
 * @author daniel cer (http://dmcer.net)
 *
 */
public class FDACorpusSelection {
   static final int NGRAM_ORDER = 5; // Bicici and Yuret found that 
                                     // using bi-grams was sufficient   
   static final boolean VERBOSE = false; 
   static final boolean LENGTH_NORM = true;

   final Set<String> F;
   final Counter<String> cntfU;
   final Counter<String> cntfL;
   final int sizeU;
   final PriorityQueue<Integer> Q;
   final double score[];
   final Counter<String> fvalue;
   final LineIndexedCorpus bitextFr;
   final LineIndexedCorpus bitextEn;
   
   static public void usage() {
      err.println("Usage:\n\tjava ...FDACorpusSelection (selection size) " +
          "(bitext.en) (bitext.fr) (test.fr) (selected.en) (selected.fr) " +
          "[selected.ln]");
   }
   
   class SentenceScoreComparator implements Comparator<Integer> {
      @Override
      public int compare(Integer o1, Integer o2) {         
         return (int)Math.signum(score[o2]-score[o1]);
      }      
   }

   // Bicici and Yuret found that log inverse initialization, log(|U|/cnt(f,U)),
   // improved run time performance by decreasing the number of tied segment 
   // scores
   private double init(String f) {
      return Math.log(sizeU) - Math.log(cntfU.getCount(f));  
   }
   
   // Bicici and Yuret found that reducing the feature weights by 1/n produced 
   // the best expected test set coverage
   private double decay(String f) {
      return init(f)/(1+cntfL.getCount(f));
   }
   
   public FDACorpusSelection(LineIndexedCorpus bitextEn, LineIndexedCorpus 
     bitextFr, LineIndexedCorpus testFr) {
      this.bitextEn = bitextEn;
      this.bitextFr = bitextFr;      
      
      // construct F
      Counter<String> testFrNgramCounts = new ClassicCounter<String>();
      for (String line : testFr) {
         if (VERBOSE) {
            err.println("test line:" + line);
         }
         CoverageChecker.countNgrams(line, testFrNgramCounts, null, NGRAM_ORDER);
      }
      F = new HashSet<String>(testFrNgramCounts.keySet());
      
      // collect cnt(f,U) values and |U|
      cntfU = new ClassicCounter<String>();
      int sizeU = 0;
      for (String line : bitextFr) {
         CoverageChecker.countNgrams(line, cntfU, F, NGRAM_ORDER);
         sizeU += line.split("\\s+").length;
      }
      this.sizeU = sizeU;
      
      // initial feature weights
      fvalue = new ClassicCounter<String>();
      if (VERBOSE) {
         err.printf("Initial Feature Weights\n");
      }
      for (String f : F) {         
         fvalue.setCount(f, init(f));
         if (VERBOSE) {           
            err.printf("\t%s: %f cnt(f,U): %f\n", f, fvalue.getCount(f), cntfU.getCount(f));
         }
      }
      
      // score sentences using initial feature weights and place them in the PriorityQueue
      score = new double[bitextFr.size()];
      Q = new PriorityQueue<Integer>(bitextFr.size(), new SentenceScoreComparator());
      for (int i = 0; i < score.length; i++) {
         Counter<String> lineNgramCounts = new ClassicCounter<String>();
         String line = bitextFr.get(i);
         CoverageChecker.countNgrams(line, lineNgramCounts, F, NGRAM_ORDER);        
         for (String f : lineNgramCounts.keySet()) {
            score[i] += fvalue.getCount(f)*lineNgramCounts.getCount(f);
         }
         
         if (LENGTH_NORM) {
            score[i] /= line.split("\\s+").length;
         }
         
         // err.printf("init score: %d %.3f\n", i, score[i]);
         Q.add(i);
      }
      
      // We start with zero counts for everything in L
      cntfL = new ClassicCounter<String>();
   }
   
   public Triple<String,String,Integer> getNextBest() {
      while (true) {
         if (Q.size() == 0) return null;
         
         // remove the best scoring segment from the priority queue         
         int id = Q.remove();
         if (VERBOSE) {
            err.printf("checking: %d %f\n", id, score[id]);
            err.printf("  src: %s\n", bitextFr.get(id));
            err.printf("  trg: %s\n", bitextEn.get(id));
         }
         Counter<String> lineNgramCounts = new ClassicCounter<String>();
         String line = bitextFr.get(id);
         CoverageChecker.countNgrams(line, lineNgramCounts, F, NGRAM_ORDER);
         
         // re-compute the score for the segment
         double priorScoreId = score[id];
         score[id] = 0;
         for (String f : lineNgramCounts.keySet()) {
            score[id] += fvalue.getCount(f)*lineNgramCounts.getCount(f);
         }
         
         if (LENGTH_NORM) {
            score[id] /= line.split("\\s+").length;
         }
         
         
         // check to see if there is anything left in the queue after
         // the current item - if there is, we'll need to double check
         // that the current item is still the best choice         
         if (Q.size() == 0) {
            return new Triple<String,String,Integer>(line,bitextEn.get(id),id);
         }
         
         // compare the re-computed score with the score
         // assigned to the next item on the top of the priority Q
         // 
         // only return the current item if it's still better than
         // the next best item, otherwise reinsert the current item
         // into the queue
         int nextId = Q.peek();
         if (score[id] == priorScoreId || score[id] >= score[nextId]) {
            cntfL.addAll(lineNgramCounts);
            // decay feature values
            for (String f : lineNgramCounts.keySet()) {
               fvalue.setCount(f, decay(f));
            }
            if (VERBOSE) {
               err.printf(" - accepting: %d %f\n", id, score[id]);
            }
            return new Triple<String,String,Integer>(line,bitextEn.get(id),id);
         } else {
            if (VERBOSE) {
              err.printf(" - rejecting: %d %f < %f\n", id, score[id], score[nextId]);
            }
            Q.add(id);
         }         
      }
   }
   
   static public void main(String[] args) throws IOException {
      if (args.length != 7 && args.length != 6) {
         usage();
         System.exit(-1);
      }
      
      int selectionSize = Integer.parseInt(args[0]);
      String bitextEnFn = args[1];
      String bitextFrFn = args[2];
      String testFn = args[3];
      String selectedEnFn = args[4];
      String selectedFrFn = args[5];
      String selectedLines = (args.length == 7 ? args[6] : null);
      
      err.printf("Opening %s\n", bitextEnFn);
      LineIndexedCorpus bitextEn = new LineIndexedCorpus(bitextEnFn);
      err.printf("Opening %s\n", bitextFrFn);
      LineIndexedCorpus bitextFr = new LineIndexedCorpus(bitextFrFn);
      err.printf("Opening %s\n", testFn);
      LineIndexedCorpus testFr = new LineIndexedCorpus(testFn);
      if (bitextEn.size() != bitextFr.size()) {
         err.printf("Bitext files %s and %s are of different lengths (%d vs %d)", 
               bitextEnFn, bitextFrFn, bitextEn.size(), bitextFr.size());
      }
      selectionSize = Math.min(selectionSize, bitextEn.size());
      PrintWriter selectedEn = new PrintWriter(new OutputStreamWriter(
        new FileOutputStream(selectedEnFn), "UTF-8"));
      PrintWriter selectedFr = new PrintWriter(new OutputStreamWriter(
        new FileOutputStream(selectedFrFn), "UTF-8"));
      PrintWriter selectedLn = (selectedLines == null ? null : 
        new PrintWriter(new OutputStreamWriter(new FileOutputStream(
            selectedLines), "UTF-8")));
      FDACorpusSelection fsacs = new FDACorpusSelection(bitextEn, bitextFr, 
         testFr);
      for (int n = 0; n < selectionSize; n++) {
         Triple<String,String,Integer> frEn = fsacs.getNextBest();
         selectedFr.println(frEn.first());
         selectedEn.println(frEn.second());
         if (selectedLn != null) selectedLn.println(frEn.third());
      }
      selectedFr.close();
      selectedEn.close();      
      if (selectedLn != null) selectedLn.close();
   }

}
