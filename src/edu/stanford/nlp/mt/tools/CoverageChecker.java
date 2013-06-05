package edu.stanford.nlp.mt.tools;

import static java.lang.System.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import edu.stanford.nlp.mt.base.LineIndexedCorpus;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * Data set coverage checking tool. 
 * 
 * This tool checks and reports the number of n-grams present in an evaluation set that are also 
 * present in a training set.
 * 
 * This tool can be used to check both the source and target side of the development and tuning data
 * with this information then being used to guide selection of the best training set.
 * 
 * However, when this tool is being used to guide training data selection, it should only be used to 
 * check the <strong>source</strong> side of true test set data. Using coverage information from the
 * target side of the test data to select training data will produce biased experimental results.
 * 
 * @author daniel cer (http://dmcer.net)
 *
 */
public class CoverageChecker {
   
   static final boolean VERBOSE = true; 
   
   static public void usage() {
      err.println("Usage:\n\tjava ...CoverageChecker (n-gram order) (training data) (test data)\n");      
   }
   
   static public void main(String[] args) throws Exception {
      if (args.length != 3) {
         usage();
         exit(-1);
      }
      int maxNgramOrder = Integer.parseInt(args[0]);
      String trainingDataFn = args[1];
      String testDataFn = args[2];
      
      if (VERBOSE) {
         err.printf("Opening: %s\n", trainingDataFn);
      }
      LineIndexedCorpus trainingData = new LineIndexedCorpus(trainingDataFn);      
      
      if (VERBOSE) {
         err.printf("Opening: %s\n", testDataFn);
      }
      LineIndexedCorpus testData = new LineIndexedCorpus(testDataFn);
      
      List<Counter<String>> ngramCounts = new ArrayList<Counter<String>>(maxNgramOrder);
      for (int i = 0; i < maxNgramOrder; i++) {
         ngramCounts.add(new ClassicCounter<String>()); 
      }
      for (String line : testData) {
         Counter<String> testLineNgrams = new ClassicCounter<String>();
         countNgrams(line, testLineNgrams, null, maxNgramOrder);
         for (String ngram : testLineNgrams.keySet()) {
            int ngramOrder = ngram.split("\\s+").length;
            ngramCounts.get(ngramOrder-1).incrementCount(ngram, testLineNgrams.getCount(ngram));
         }
      }
      
      List<Counter<String>> coveredNgramCounts = new ArrayList<Counter<String>>(maxNgramOrder);
      for (int i = 0; i < maxNgramOrder; i++) {
         coveredNgramCounts.add(new ClassicCounter<String>()); 
      }
      int lineno = 0;
      for (String line : trainingData) { 
         if (VERBOSE && lineno % 10000 == 0) {
            err.printf("train line processed > %d\n", lineno);
         }
         lineno++;
         Counter<String> trainLineNgrams = new ClassicCounter<String>();
         countNgrams(line, trainLineNgrams, null, maxNgramOrder);
         for (String ngram : trainLineNgrams.keySet()) {
            int ngramOrder = ngram.split("\\s+").length;
            if (ngramCounts.get(ngramOrder-1).containsKey(ngram) &&
                !coveredNgramCounts.get(ngramOrder-1).containsKey(ngram)) {
               coveredNgramCounts.get(ngramOrder-1).setCount(ngram, ngramCounts.get(ngramOrder-1).getCount(ngram));
            }
         }
      }
      
      
      out.print("N-gram Coverage\n");
      out.print("Order\t% Tok\t% Type\n");
      for (int ngramOrder = 0; ngramOrder < maxNgramOrder; ngramOrder++) {
          double tokCoverage = coveredNgramCounts.get(ngramOrder).totalCount()/ngramCounts.get(ngramOrder).totalCount();
          double typeCoverage = coveredNgramCounts.get(ngramOrder).size()/(double)ngramCounts.get(ngramOrder).size();
          out.printf("%d\t%.3f (%d/%d)\t%.3f (%d/%d)\n", ngramOrder+1, 
                tokCoverage*100, 
                (int)coveredNgramCounts.get(ngramOrder).totalCount(), (int)ngramCounts.get(ngramOrder).totalCount(),
                typeCoverage*100,
                coveredNgramCounts.get(ngramOrder).size(), ngramCounts.get(ngramOrder).size());
      }
   }

   static public void countNgrams(String line, Counter<String> ngramCounts, Set<String> limitSet, int order) {
      String[] toks = line.split("\\s");
      for (int i = 0; i < toks.length; i++) {
         for (int j = 0; j < order && j+i < toks.length ; j++) {
            String[] ngramArr = Arrays.copyOfRange(toks, i, i+j+1);
            String ngram = StringUtils.join(ngramArr, " ");
            if (limitSet == null || limitSet.contains(ngram)) {
               ngramCounts.incrementCount(ngram);
            }
         }
      }	   
   }
}
