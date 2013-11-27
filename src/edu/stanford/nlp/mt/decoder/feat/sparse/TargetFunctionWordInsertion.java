package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TokenUtils;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.NeedsCloneable;
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
 * NOTE: This implementation does not treat punctuation and digits as function
 * words.
 * 
 * @author Spence Green
 *
 */
public class TargetFunctionWordInsertion extends DerivationFeaturizer<IString, String> implements NeedsCloneable<IString,String> {

  private static final String FEATURE_NAME = "TWFN";
  
  private static final int DEFAULT_RANK_CUTOFF = 35;
  
  private final Set<IString> sourceFunctionWordSet;
  private final Set<IString> targetFunctionWordSet;
  private final int rankCutoff;

  private int numSourceFunctionTokens;
  
  public TargetFunctionWordInsertion(String...args) {
    if (args.length < 2) {
      throw new RuntimeException("Must specify source and target unigram counts files");
    }
    System.err.println("Loading TargetFunctionWordInsertion template...");
    String sourceFilename = args[0];
    String targetFilename = args[1];
    rankCutoff = args.length > 2 ? Integer.valueOf(args[2]) : DEFAULT_RANK_CUTOFF;
    System.err.println("Source words:");
    sourceFunctionWordSet = loadCountsFile(sourceFilename);
    System.err.println("Target words:");
    targetFunctionWordSet = loadCountsFile(targetFilename);
  }
  
  private Set<IString> loadCountsFile(String filename) {
    Counter<IString> counter = new ClassicCounter<IString>();
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    try {
      for (String line; (line = reader.readLine()) != null;) {
        String[] fields = line.trim().split("\\s+");
        if (fields.length == 2) {
          String wordType = fields[0];
          if ( ! (TokenUtils.isNumericOrPunctuationOrSymbols(wordType) ||
                  wordType.equals(TokenUtils.START_TOKEN.toString()) ||
                  wordType.equals(TokenUtils.END_TOKEN.toString()))) {
            counter.setCount(new IString(wordType), Double.valueOf(fields[1]));
          }
        } else {
          System.err.printf("%s: Discarding line %s%n", this.getClass().getName(), line);
        }
      }
      reader.close();
      Set<IString> set = Generics.newHashSet(Counters.topKeys(counter, rankCutoff));
      for (IString word : set) {
        System.err.printf(" %s%n", word);
      }
      return set;
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
  
  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString, String>> ruleList, Sequence<IString> source) {
    numSourceFunctionTokens = 0;
    for (IString token : source) {
      if (sourceFunctionWordSet.contains(token)) {
        ++numSourceFunctionTokens;
      }
    }
  }

  @Override
  public List<FeatureValue<String>> featurize(
      Featurizable<IString, String> f) {
    if (numSourceFunctionTokens == 0) return null;
    int numTargetFunctionTokens = 0;
    for (IString token : f.targetPhrase) {
      if (targetFunctionWordSet.contains(token)) {
        ++numTargetFunctionTokens;
      }
    }
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(FEATURE_NAME, (double) numTargetFunctionTokens / (double) numSourceFunctionTokens));
    return features;
  }
}
