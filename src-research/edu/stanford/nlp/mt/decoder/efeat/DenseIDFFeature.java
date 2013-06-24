package edu.stanford.nlp.mt.decoder.efeat;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.CacheableFeatureValue;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.mt.tools.ComputeBitextIDF;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;

/**
 * Chris' suggestion! Computes the sum of IDFs on either side of a rule,
 * and then fires a feature for the squared sum. The goal is to discourage
 * content word deletion.
 * 
 * @author Spence Green
 *
 */
public class DenseIDFFeature implements 
    IsolatedPhraseFeaturizer<IString, String> {

  private static final String FEATURE_PREFIX = "DenseIDF";
  
  private Counter<String> sourceIDF;
  private Counter<String> targetIDF;
  
  public DenseIDFFeature(String...args) {
    if (args.length != 2) throw new RuntimeException("Arguments: source-idf-file, target-idf-file");
    
    sourceIDF = loadIDFFile(args[0]);
    targetIDF = loadIDFFile(args[1]);
  }
  
  private Counter<String> loadIDFFile(String filename) {
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    Counter<String> idfFile = new ClassicCounter<String>(1000000);
    try {
      for (String line; (line = reader.readLine()) != null;) {
        String[] fields = line.trim().split("\\s+");
        assert fields.length == 2;
        idfFile.setCount(fields[0], Double.parseDouble(fields[1]));
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    // Sanity check
    assert idfFile.totalCount() > 0;
    return idfFile;
  }

  @Override
  public List<FeatureValue<String>> phraseListFeaturize(
      Featurizable<IString, String> f) {
    double sourceSum = computeIDFSum(f.sourcePhrase, sourceIDF);
    double targetSum = computeIDFSum(f.targetPhrase, targetIDF);
    double difference = sourceSum - targetSum;
    List<FeatureValue<String>> featureList = null;
    if (difference > 0.0) {
      // Source content words aligned to target function words
      featureList = new LinkedList<FeatureValue<String>>();
      difference *= difference;
      featureList.add(new CacheableFeatureValue<String>(FEATURE_PREFIX + ":tgt", difference));
    } else if (difference < 0.0) {
      // Source function words aligned to target content words
      featureList = new LinkedList<FeatureValue<String>>();
      difference *= difference;
      featureList.add(new CacheableFeatureValue<String>(FEATURE_PREFIX + ":src", difference));
    } 
    return featureList;
  }

  private double computeIDFSum(Sequence<IString> sourcePhrase,
      Counter<String> idfList) {
    double sourceSum = 0.0;
    for (IString token : sourcePhrase) {
      String tokenStr = token.toString();
      sourceSum += idfList.containsKey(tokenStr) ? 
          idfList.getCount(tokenStr) : idfList.getCount(ComputeBitextIDF.UNK_TOKEN);
    }
    return sourceSum;
  }

  @Override
  public void initialize(Index<String> featureIndex) {
  }

  @Override
  public FeatureValue<String> phraseFeaturize(Featurizable<IString, String> f) {
    return null;
  }
}
