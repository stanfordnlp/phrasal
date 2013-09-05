package edu.stanford.nlp.mt.decoder.efeat;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Generics;

/**
 * Single dense feature count of target function words inserted
 * by a rule.
 * 
 * The counts file is of the format created by SRILM ngram-count, e.g.:
 * 
 *   ngram-count -order 1 -sort -text myfile.txt -write myfile.counts
 * 
 * You should create the counts file on the target side of the bitext or
 * some other suitably large monolingual data set.
 * 
 * @author Spence Green
 *
 */
public class TargetFunctionWordInsertion implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "TWFN";
  
  private static final int DEFAULT_RANK_CUTOFF = 35;
  
  private final Set<IString> functionWordSet;
  private final int rankCutoff;
  
  public TargetFunctionWordInsertion(String...opts) {
    if (opts.length == 0) {
      throw new RuntimeException("Must specify unigram counts file");
    }
    String filename = opts[0];
    rankCutoff = opts.length > 1 ? Integer.valueOf(opts[1]) : DEFAULT_RANK_CUTOFF;
    functionWordSet = loadCountsFile(filename);
  }
  
  private Set<IString> loadCountsFile(String filename) {
    Counter<IString> counter = new ClassicCounter<IString>();
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    try {
      for (String line; (line = reader.readLine()) != null;) {
        String[] fields = line.trim().split("\\s+");
        if (fields.length == 2) {
          counter.setCount(new IString(fields[0]), Double.valueOf(fields[1]));
        } else {
          System.err.printf("%s: Discarding line %s%n", this.getClass().getName(), line);
        }
      }
      reader.close();
      return Generics.newHashSet(Counters.topKeys(counter, rankCutoff));
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    int numTargetFunctionWords = 0;
    for (IString token : f.targetPhrase) {
      if (functionWordSet.contains(token)) {
        ++numTargetFunctionWords;
      }
    }
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(FEATURE_NAME, numTargetFunctionWords));
    return features;
  }
}
