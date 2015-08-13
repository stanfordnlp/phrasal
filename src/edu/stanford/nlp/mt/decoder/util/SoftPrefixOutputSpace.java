package edu.stanford.nlp.mt.decoder.util;

import java.util.List;

import edu.stanford.nlp.mt.decoder.AbstractInferer;
import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.TranslationModelFeaturizer;
import edu.stanford.nlp.mt.tm.CombinedTranslationModel;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.CompiledPhraseTable;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.tm.Rule;
import edu.stanford.nlp.mt.tm.TranslationModel;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.InputProperties;

/**
 * Constrained output space for prefix decoding. Uses the phrase table
 * to allow alternate translations for prefixes.
 * 
 * 
 * @author Spence Green
 *
 * @param <IString>
 * @param <String>
 */
public class SoftPrefixOutputSpace implements OutputSpace<IString, String> {

  // Hyperparameters for constructing synthetic rules
  private static final String PHRASE_TABLE_NAME = "SoftTargetGenerator";
  private static final double SYNTHETIC_ISOLATION_SCORE = -199.0;
  private static final PhraseAlignment ALIGNMENT = PhraseAlignment.getPhraseAlignment("(0)");
  private static final int NUM_SYNTHETIC_SCORES = 4;
  private static String[] PHRASE_SCORE_NAMES;
  private static float[] PHRASE_SCORES;
  static {
    PHRASE_SCORE_NAMES = new String[NUM_SYNTHETIC_SCORES];
    PHRASE_SCORES = new float[NUM_SYNTHETIC_SCORES];
    for (int i = 0; i < NUM_SYNTHETIC_SCORES; ++i) {
      // TODO(spenceg) This ignores the phrase penalty. Bad for now! But this is another
      // reason why the phrase penalty should be a separate featurizer.
      // Emulate the FlatPhraseTable feature naming convention
      PHRASE_SCORE_NAMES[i] = String.format("%s.%d", CompiledPhraseTable.DEFAULT_FEATURE_PREFIX, i);
      PHRASE_SCORES[i] = -99.0f;
    }
  }
  private static final RuleFeaturizer<IString,String> featurizer = 
      new TranslationModelFeaturizer(NUM_SYNTHETIC_SCORES);

  private Sequence<IString> sourceSequence;
  private final Sequence<IString> allowablePrefix;
  private final int allowablePrefixLength;
  private final int sourceInputId;

  /**
   * Constructor.
   * 
   * @param sourceSequence
   * @param allowablePrefix
   * @param sourceInputId
   */
  public SoftPrefixOutputSpace(Sequence<IString> allowablePrefix, int sourceInputId) {
    this.allowablePrefix = allowablePrefix;
    this.allowablePrefixLength = allowablePrefix.size();
    this.sourceInputId = sourceInputId;
  }


  @Override
  public void setSourceSequence(Sequence<IString> sourceSequence) {
    this.sourceSequence = sourceSequence;
  }

  @Override
  public void filter(RuleGrid<IString, String> ruleGrid, 
      AbstractInferer<IString, String> inferer) {
    filter(ruleGrid, inferer, null);
  }
  
  // TODO(spenceg) This won't work with the CompiledPhraseTable anymore!!
  @SuppressWarnings({ "rawtypes", "unchecked" })
  @Override
  public void filter(RuleGrid<IString, String> ruleGrid, 
      AbstractInferer<IString, String> inferer, InputProperties inputProperties) {
    List<TranslationModel<IString,String>> models = ((CombinedTranslationModel) inferer.phraseGenerator).getModels();
    final boolean isDynamicTM = models.get(0) instanceof DynamicTranslationModel;
    if (isDynamicTM) {
      // We're going to add rules here, so enable sorting on lookup
      ruleGrid.setLazySorting(true);
      
      // New strategy with the dynamic TM
      DynamicTranslationModel<String> backgroundModel = (DynamicTranslationModel<String>) models.get(0);
      DynamicTranslationModel<String> foregroundModel = models.size() == 2 ? (DynamicTranslationModel<String>) models.get(1)
          : null;
      
      // These settings shouldn't matter for scoring
      final String phraseTableName = backgroundModel.getName();
      final String[] featureNames = backgroundModel.getFeatureNames().stream().toArray(String[]::new);
      
      // Target OOVs, Target insertions, target unigrams
      for (int j = 0, tgtLength = allowablePrefix.size(); j < tgtLength; ++j) {
        IString targetQuery = allowablePrefix.get(j);
        int tgtIdBackground = backgroundModel.getTMVocabularyId(targetQuery);
        int tgtIdForeground = foregroundModel == null ? -1 : foregroundModel.getTMVocabularyId(targetQuery);
        final int cnt_e = backgroundModel.coocTable.getTgtMarginal(tgtIdBackground)
            + (foregroundModel == null ? 0 : foregroundModel.coocTable.getTgtMarginal(tgtIdForeground));
        boolean isTargetOOV = cnt_e == 0;
        final Sequence<IString> target = allowablePrefix.subsequence(j, j+1);
        
        for (int i = 0, srcLength = sourceSequence.size(); i < srcLength; ++i) {
          // if (isTargetOOV) break;
          IString sourceQuery = sourceSequence.get(i);
          int srcIdBack = backgroundModel.getTMVocabularyId(sourceQuery);
          int srcIdFore = foregroundModel == null ? -1 : foregroundModel.getTMVocabularyId(sourceQuery);
          int cnt_f = backgroundModel.coocTable.getSrcMarginal(srcIdBack) +
              (foregroundModel == null ? 0 : foregroundModel.coocTable.getSrcMarginal(srcIdFore));
          final boolean isSourceOOV = cnt_f == 0;
          final Sequence<IString> source = sourceSequence.subsequence(i,i+1);
          
          if (isTargetOOV 
              || isSourceOOV) { // the system OOV model can not handle this unless the identical word is present in the target prefix    
            // EMNLP14 algorithm. Should be replaced with insertion or deletion logic below.
            ConcreteRule<IString,String> syntheticRule = makeDummyRule(source, 
                target, i, inputProperties);
            ruleGrid.addEntry(syntheticRule);
          
          } else {
            int cnt_joint = backgroundModel.coocTable.getJointCount(srcIdBack, tgtIdBackground)
                + (foregroundModel == null ? 0 : foregroundModel.coocTable.getJointCount(srcIdFore, tgtIdForeground));
            // TODO(spenceg) Smooth for now. This should encourage infrequent words to align with infrequent words.
            // Use the rule insertion logic below when the interaction with
            // CubePruningDecoder is better understood.
            if (cnt_joint == 0) cnt_joint += 1;
            CoverageSet sourceCoverage = new CoverageSet(srcLength);
            sourceCoverage.set(i);
            ConcreteRule<IString,String> syntheticRule = makeSyntheticRule(source, target, 
                sourceCoverage, phraseTableName, featureNames, inferer.scorer, inferer.featurizer, 
                cnt_joint, cnt_e, cnt_f, inputProperties);
            ruleGrid.addEntry(syntheticRule);
          } 
        }
      }
      
    } else {
      // EMNLP14 strategy for compiled TMs
      // Allow any target word to map anywhere into the source, but with high
      // cost so that only OOVs and words outside the distortion limit will
      // be used.
      for (int i = 0, limit = sourceSequence.size(); i < limit; ++i) {
        final Sequence<IString> source = sourceSequence.subsequence(i,i+1);
        for (int j = 0, size = allowablePrefix.size(); j < size; ++j) {
          ConcreteRule<IString,String> syntheticRule = makeDummyRule(source, 
              allowablePrefix.subsequence(j, j+1), i, inputProperties);
          ruleGrid.addEntry(syntheticRule);
        }
      }
    }
  }

  /**
   * Create a synthetic translation rule.
   * 
   * @param source
   * @param target
   * @param sourceIndex
   * @param phraseScoreNames
   * @return
   */
  private ConcreteRule<IString, String> makeSyntheticRule(Sequence<IString> source, Sequence<IString> target, 
      CoverageSet sourceCoverage, String phraseTableName, String[] phraseScoreNames, Scorer<String> scorer,
      FeatureExtractor<IString,String> featurizer,
      int cnt_f_e, int cnt_e, int cnt_f, InputProperties inputProperties) {
    //  [0] := phi_f_e
    //  [1] := lex_f_e
    //  [2] := phi_e_f
    //  [3] := lex_e_f
    float[] scores = new float[phraseScoreNames.length];
    scores[0] = (float) (Math.log(cnt_f_e) - Math.log(cnt_e));
    scores[1] = scores[0];
    scores[2] = (float) (Math.log(cnt_f_e) - Math.log(cnt_f));
    scores[3] = scores[2];
    
    for(int i = 4; i < scores.length; ++i)
      scores[i] = -99.0f;
    
    Rule<IString> abstractRule = new Rule<IString>(scores, phraseScoreNames,
        target, source, ALIGNMENT);
    ConcreteRule<IString,String> rule = new ConcreteRule<IString,String>(abstractRule,
        sourceCoverage, featurizer, scorer, sourceSequence, 
        phraseTableName, sourceInputId, inputProperties);
    return rule;
  }
  
  /**
   * Create a synthetic translation rule.
   * 
   * @param source
   * @param target
   * @param sourceIndex
   * @param phraseScoreNames
   * @return
   */
  private ConcreteRule<IString, String> makeDummyRule(Sequence<IString> source, Sequence<IString> target, 
      int sourceIndex, InputProperties inputProperties) {
    // Downweight the TM features
    Rule<IString> abstractRule = new Rule<IString>(PHRASE_SCORES, PHRASE_SCORE_NAMES,
        target, source, ALIGNMENT);

    CoverageSet sourceCoverage = new CoverageSet();
    sourceCoverage.set(sourceIndex);
    ConcreteRule<IString,String> rule = new ConcreteRule<IString,String>(abstractRule,
        sourceCoverage, featurizer, null, sourceSequence, 
        PHRASE_TABLE_NAME, sourceInputId, inputProperties);
    
    // Deterministically set the isolation score since we didn't provide a scorer to the
    // ConcreteRule constructor.
    rule.isolationScore = SYNTHETIC_ISOLATION_SCORE;
    return rule;
  }

  @Override
  public boolean allowableContinuation(Featurizable<IString, String> featurizable,
      ConcreteRule<IString, String> rule) {
    final Sequence<IString> prefix = featurizable == null ? null : featurizable.targetPrefix;
    return exactMatch(prefix, rule.abstractRule.target);
  }

  private boolean exactMatch(Sequence<IString> prefix, Sequence<IString> rule) {
    if (prefix == null) {
      return allowablePrefix.size() > rule.size() ? allowablePrefix.startsWith(rule) :
        rule.startsWith(allowablePrefix);

    } else {
      int prefixLength = prefix.size();
      int upperBound = Math.min(prefixLength + rule.size(), allowablePrefixLength);
      for (int i = 0; i < upperBound; i++) {
        IString next = i >= prefixLength ? rule.get(i-prefixLength) : prefix.get(i);
        if ( ! allowablePrefix.get(i).equals(next)) {
          return false;
        }
      }
      return true;
    }
  }

  @Override
  public boolean allowableFinal(Featurizable<IString, String> featurizable) {
    // Allow everything except for the NULL hypothesis
    return featurizable != null;
  }

  @Override
  public List<Sequence<IString>> getAllowableSequences() {
    // null has the semantics of the full (unconstrained) target output space.
    // This is what we want for prefix decoding because we don't pruning to happen
    // at the point of the phrase table query.
    return null;
  }
  
  @Override 
  public int getPrefixLength() {
    return allowablePrefixLength;
  }
  
}
