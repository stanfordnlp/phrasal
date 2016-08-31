package edu.stanford.nlp.mt.train;

import java.util.*;

/**
 * Implementation of phrase-extract that runs in time O(m * s * t), where m is
 * the maximum phrase length, and s and t are respectively the lengths of the
 * source and target sentence.
 * 
 * @author Michel Galley
 */
public class FlatPhraseExtractor extends AbstractPhraseExtractor {

  
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  public FlatPhraseExtractor(Properties prop, AlignmentTemplates alTemps,
      List<AbstractFeatureExtractor> extractors) {
    super(prop, alTemps, extractors);
  }

  @Override
  public void extractPhrases(WordAlignment sent) {
    if (addPhrasesToIndex(sent))
      featurize(sent);
  }

  public static final String DEBUG_PROPERTY = "DebugFlatPhraseExtractor";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  protected boolean addPhrasesToIndex(WordAlignment sent) {

    int fsize = sent.f().size();
    int esize = sent.e().size();

    alGrid.init(sent);

    if (fsize > MAX_SENT_LEN || esize > MAX_SENT_LEN) {
      System.err.println("Warning: skipping too long sentence. Length: f="
          + fsize + " e=" + esize);
      return false;
    }

    if (fsize < PRINT_GRID_MAX_LEN && esize < PRINT_GRID_MAX_LEN)
      alGrid.printAlTempInGrid("line: " + sent.getId(), null, System.err);

    // Sentence boundaries:
    if (extractBoundaryPhrases) {
      // make sure we can always translate <s> as <s> and </s> as </s>:
      addPhraseToIndex(sent, 0, 0, 0, 0, true, 1.0f);
      addPhraseToIndex(sent, fsize - 1, fsize - 1, esize - 1, esize - 1, true,
          1.0f);
    }

    // For each English phrase:
    for (int e1 = 0; e1 < esize; ++e1) {

      int f1 = Integer.MAX_VALUE;
      int f2 = Integer.MIN_VALUE;
      int lastE = Math.min(esize, e1 + maxPhraseLenE) - 1;

      for (int e2 = e1; e2 <= lastE; ++e2) {

        // Find range of f aligning to e1...e2:
        SortedSet<Integer> fss = sent.e2f(e2);
        if (!fss.isEmpty()) {
          int fmin = fss.first();
          int fmax = fss.last();
          if (fmin < f1)
            f1 = fmin;
          if (fmax > f2)
            f2 = fmax;
        }

        // Phrase too long:
        if (f2 - f1 >= maxPhraseLenF)
          continue;

        // No word alignment within that range, or phrase too long?
        if (NO_EMPTY_ALIGNMENT && f1 > f2)
          continue;

        // Check if range [e1-e2] [f1-f2] is admissible:
        boolean admissible = true;
        for (int fi = f1; fi <= f2; ++fi) {
          SortedSet<Integer> ess = sent.f2e(fi);
          if (!ess.isEmpty())
            if (ess.first() < e1 || ess.last() > e2) {
              admissible = false;
              break;
            }
        }
        if (!admissible)
          continue;

        // See how much we can expand the source span to cover unaligned words
        int F1 = f1, F2 = f2;
        int lastF1 = Math.max(0, f2 - maxPhraseLenF + 1);
        while (F1 > lastF1 && sent.f2e(F1 - 1).isEmpty()) {
          --F1;
        }
        int lastF2 = Math.min(fsize - 1, f1 + maxPhraseLenF - 1);
        while (F2 < lastF2 && sent.f2e(F2 + 1).isEmpty()) {
          ++F2;
        }

        for (int i = F1; i <= f1; ++i) {
          int lasti = Math.min(F2, i + maxPhraseLenF - 1);
          for (int j = f2; j <= lasti; ++j) {
            assert (j - i < maxPhraseLenF);
            addPhraseToIndex(sent, i, j, e1, e2, true, 1.0f);
          }
        }
      }
    }

    return true;
  }
}
