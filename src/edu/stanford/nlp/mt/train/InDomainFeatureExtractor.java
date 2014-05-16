package edu.stanford.nlp.mt.train;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.util.Generics;

/**
 * Extractor for marking in-domain rules given a zero-indexed file of lineids.
 * 
 * @author Spence Green
 */
public class InDomainFeatureExtractor extends AbstractFeatureExtractor {

  /**
   * A log transformation will be applied when the phrase table is
   * written to disk.
   */
  private static final double EXP_INDICATOR = Math.exp(1);

  // Threadsafe sets that marks both the indomain sets and indomain rules.
  private final List<Set<Integer>> inDomainSetList;
  private final List<Set<Integer>> inDomainKeyList;
  private final int numDomains;

  /**
   * Constructor.
   * 
   * @param args
   */
  public InDomainFeatureExtractor(String...args) {
    if (args.length == 0) {
      throw new RuntimeException("Format: zero-indexed line id file");
    }
    inDomainSetList = Generics.newArrayList(args.length);
    inDomainKeyList = Generics.newArrayList(args.length);
    for (String filename : args) {
      Set<Integer> inDomainSet = load(filename);
      inDomainSetList.add(inDomainSet);
      inDomainKeyList.add(Collections.synchronizedSet(new HashSet<Integer>(inDomainSet.size()*10)));
      System.err.printf("%s: Loaded domain list %s%n", this.getClass().getName(), filename);
    }
    numDomains = args.length;
  }

  /**
   * Load the zero-indexed list of in-domain bitext lines.
   * 
   * @param filename
   * @return
   */
  private static Set<Integer> load(String filename) {
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    Set<Integer> set = Collections.synchronizedSet(new HashSet<Integer>(20000));
    try {
      for (String line; (line = reader.readLine()) != null;) {
        set.add(Integer.parseInt(line.trim()));
      }
      reader.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return set;
  }

  @Override
  public void featurizePhrase(AlignmentTemplateInstance alTemp,
      AlignmentGrid alGrid) {
    int lineId = alTemp.getWordAlignment().getId();
    for (int i = 0; i < numDomains; ++i) {
      Set<Integer> domainSet = inDomainSetList.get(i);
      if (domainSet.contains(lineId)) {
        inDomainKeyList.get(i).add(alTemp.getKey());
      }
    }
  }

  @Override
  public Object score(AlignmentTemplate alTemp) {
    double[] scores = new double[numDomains];
    for (int i = 0; i < numDomains; ++i) {
      scores[i] = inDomainKeyList.get(i).contains(alTemp.getKey()) ? EXP_INDICATOR : 1.0;
    }
    return scores;
  }

  @Override
  public int getRequiredPassNumber() {
    return 1;
  }
}
