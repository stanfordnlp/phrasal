package edu.stanford.nlp.mt.train;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.mt.base.IOTools;

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

  private final Set<Integer> inDomainSet;
  private final Set<Integer> inDomainKeys;

  /**
   * Constructor.
   * 
   * @param args
   */
  public InDomainFeatureExtractor(String...args) {
    if (args.length != 1) {
      throw new RuntimeException("Format: zero-indexed line id file");
    }
    inDomainSet = load(args[0]);
    inDomainKeys = Collections.synchronizedSet(new HashSet<Integer>(inDomainSet.size()*10));
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
    if (inDomainSet.contains(lineId)) {
      inDomainKeys.add(alTemp.getKey());
    }
  }

  @Override
  public Object score(AlignmentTemplate alTemp) {
    return new double[] { inDomainKeys.contains(alTemp.getKey()) ? EXP_INDICATOR : 1.0 };
  }

  @Override
  public int getRequiredPassNumber() {
    return 1;
  }
}
