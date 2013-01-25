package edu.stanford.nlp.mt.decoder.efeat;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.AlignmentFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;

/**
 * 
 * 
 * @author Spence Green
 *
 */
public class DiscriminativeAlignmentFeaturizer implements AlignmentFeaturizer,
    IncrementalFeaturizer<IString, String> {

  private static final String ALIGN_FEAT = "ALN";
  private static final String UNALIGN_TGT = "ALNT";
  private static final String UNALIGN_SRC = "ALNS";
  
  private final boolean addUnalignedSourceWords;
  private final boolean addUnalignedTargetWords;
  
  public DiscriminativeAlignmentFeaturizer() { 
    addUnalignedSourceWords = false;
    addUnalignedTargetWords = false;
  }
  
  public DiscriminativeAlignmentFeaturizer(String...args) {
    addUnalignedSourceWords = (args.length > 0) ? Boolean.parseBoolean(args[0]) : false;
    addUnalignedTargetWords = (args.length > 1) ? Boolean.parseBoolean(args[1]) : false;
  }
  
  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    PhraseAlignment align = f.option.abstractOption.alignment;
    final int eLength = f.translatedPhrase.size();
    boolean[] fIsAligned = new boolean[f.foreignPhrase.size()];
    List<FeatureValue<String>> features = new LinkedList<FeatureValue<String>>();

    // Add alignment and target features
    for (int i = 0; i < eLength; ++i) {
      int[] fIndices = align.e2f(i);
      String eWord = f.translatedPhrase.get(i).toString();
      if (fIndices == null) {
        // Unaligned target word
        if (addUnalignedTargetWords) {
          String feature = UNALIGN_TGT + ":" + eWord;
          features.add(new FeatureValue<String>(feature, 1.0));
        }
      } else {
        // Aligned target word
        String[] tokens = new String[1+fIndices.length];
        int tokId = 0;
        tokens[tokId++] = eWord;
        for (int fIndex : fIndices) {
          fIsAligned[fIndex] = true;
          tokens[tokId++] = f.foreignPhrase.get(fIndex).toString();
        }
        Arrays.sort(tokens);
        StringBuilder sb = new StringBuilder();
        sb.append(ALIGN_FEAT).append(":");
        for (int j = 0; j < tokens.length; ++j) {
          if (j > 0) sb.append("-");
          sb.append(tokens[j]);
        }
        features.add(new FeatureValue<String>(sb.toString(), 1.0));
      }
    }
    
    // Add source features
    if (addUnalignedSourceWords) {
      for (int fIndex = 0; fIndex < fIsAligned.length; ++fIndex) {
        if ( ! fIsAligned[fIndex]) {
          String fWord = f.foreignPhrase.get(fIndex).toString();
          String feature = UNALIGN_SRC + ":" + fWord;
          features.add(new FeatureValue<String>(feature, 1.0));
        }
      }
    }
    
    return features;
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options,
      Sequence<IString> foreign) {}

  @Override
  public void reset() {}
}
