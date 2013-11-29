package edu.stanford.nlp.mt.train;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Set;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.util.Generics;

/**
 * Extractor for marking in-domain rules given a zero-indexed file of lineids.
 * 
 * @author Spence Green
 */
public class InDomainFeatureExtractor extends AbstractFeatureExtractor {

  private static final double EXP_M1 = Math.exp(-1);

  private final Set<Integer> inDomainSet;
  private final Set<Integer> inDomainKeys;

  public InDomainFeatureExtractor(String...args) {
    if (args.length != 1) {
      throw new RuntimeException("Format: zero-indexed line id file");
    }
    inDomainSet = load(args[0]);
    inDomainKeys = Generics.newHashSet(inDomainSet.size()*5);
  }

  private static Set<Integer> load(String filename) {
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    Set<Integer> set = Generics.newHashSet();
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
    return new double[] { inDomainKeys.contains(alTemp.getKey()) ? EXP_M1 : 1.0 };
  }

  @Override
  public int getRequiredPassNumber() {
    return 1;
  }
}
