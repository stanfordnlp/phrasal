package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.NeedsCloneable;
import edu.stanford.nlp.mt.pt.ConcreteRule;
import edu.stanford.nlp.mt.pt.FlatPhraseTable;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.SourceClassMap;
import edu.stanford.nlp.mt.util.TargetClassMap;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Fire PTM features during tuning and testing!
 * 
 * Depends on <code>MakePTMPhrasalInput</code> and <code>SplitByInterfaceCondition</code>.
 * 
 * @author Spence Green
 *
 */
public class PTMFeaturizer extends DerivationFeaturizer<IString, String> implements NeedsCloneable<IString,String> {

  public static final String FEATURE_PREFIX = "PTM";
  private static final String OOV_BLANKET_FEATURE = String.format("%s.oovb", FEATURE_PREFIX);
  private static final String ALIGNMENT_FEATURE = String.format("%s.algn", FEATURE_PREFIX);
  
  private static List<UserDerivation> sourceIdToDerivation;
  private static SourceClassMap sourceMap;
  private static TargetClassMap targetMap;
  private static boolean isTestMode;
  private static boolean oovBlanket;
  private static boolean alignmentFeature;

  // TODO(spenceg): This will shape the space during tuning. Bind this to IMT features so that post-edit can't
  // do it?
  private static boolean recombinationFeature;
  
  // Fields to be cloned
  private UserDerivation derivation;
  private BitSet isSourceOOV;

  /**
   * Constructor.
   * 
   * @param args
   */
  public PTMFeaturizer(String...args) {
    Properties options = FeatureUtils.argsToProperties(args);
    String derivationFile = options.getProperty("file", null);
    if (derivationFile == null) {
      throw new RuntimeException("Derivation file parameter is required");
    }
    File derivFile = new File(derivationFile);
    if ( ! derivFile.exists()) {
      System.err.println("PTMFeaturizer: derivation file unspecified; entering test mode");
      isTestMode = true;
      return;
    }
    sourceIdToDerivation = Collections.unmodifiableList(load(derivationFile));
    System.err.printf("PTMFeaturizer: Loaded %d derivations%n", sourceIdToDerivation.size());
    isTestMode = PropertiesUtils.getBool(options, "testMode", false);
    System.err.println("PTMFeaturizer mode: " + (isTestMode ? "test" : "tune"));
    oovBlanket = PropertiesUtils.getBool(options, "oovBlanket", false);
    alignmentFeature = PropertiesUtils.getBool(options, "alignmentFeature", false);
    recombinationFeature = PropertiesUtils.getBool(options, "recombinationFeature", false);
    if (oovBlanket || alignmentFeature) {
      targetMap = TargetClassMap.getInstance();
      sourceMap = SourceClassMap.getInstance();
    }
  }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
  
  /**
   * Load the ".user" output of <code>SplitInterfaceByCondition</code>.
   * 
   * @param derivationFile
   * @return
   */
  private List<UserDerivation> load(String derivationFile) {
    LineNumberReader reader = IOTools.getReaderFromFile(derivationFile);
    List<UserDerivation> idToDerivation = Generics.newArrayList();
    try {
      for (String line; (line = reader.readLine()) != null;) {
        String[] fields = line.trim().split("\t");
        if (line.trim().length() == 0) {
          // No logged derivation
          idToDerivation.add(null);
        
        } else {
          String mt = fields[1];
          String user = fields[2];
          String s2mt = fields[3];
          String src = fields[4];
          UserDerivation d = new UserDerivation(mt, user, s2mt, src);
          idToDerivation.add(d);
        }
      }
      reader.close();

    } catch (IOException e) {
      throw new RuntimeException("Could not load derivation file: " + derivationFile);
    }
    return idToDerivation;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString, String>> ruleList, Sequence<IString> source) {
    if (isTestMode) return;
    derivation = sourceIdToDerivation.get(sourceInputId);
    if (derivation != null) {
      // Mark source OOVs
      isSourceOOV = new BitSet();
      int[] srcArr = new int[1];
      for (int i = 0, sz = source.size(); i < sz; ++i) {
        srcArr[0] = source.get(i).id;
        if (FlatPhraseTable.sourceIndex.indexOf(srcArr) < 0) {
          isSourceOOV.set(i);
        }
      }
    }
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    if (isTestMode) return features;
    List<Set<Integer>> s2t = null;
    
    // TODO: Didn't record data for some derivations
    // Could still use the string features for recombination
    if (derivation == null) return features;
    
//    try {
    if (oovBlanket) {
      if (s2t == null) s2t = s2tFromRule(f.rule);
      features.addAll(oovBlanketFeatures(f, s2t));
    }
    if (alignmentFeature) {
      if (s2t == null) s2t = s2tFromRule(f.rule);
      features.addAll(alignmentFeatures(f, s2t));
    }
    if (recombinationFeature) {
      // TODO
    }
//    } catch (Exception e) {
//      e.printStackTrace();
//      throw new RuntimeException(e);
//    }
    return features;
  }

  private List<FeatureValue<String>> alignmentFeatures(
      Featurizable<IString, String> f, List<Set<Integer>> s2t) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    PhraseAlignment alignment = f.rule.abstractRule.alignment;

    // Source-side alignments
    for (int i = 0, srcLength = f.sourcePhrase.size(); i < srcLength; ++i) {
      Set<Integer> alignments = s2t.get(i);
      IString srcWord = f.sourcePhrase.get(i);
      if (alignments.size() > 0) {
        // Use sets so that multiple alignments aren't counted twice
        Set<IString> alignedTargetWords = Generics.newHashSet();
        Set<IString> alignedSourceWords = Generics.newHashSet();
        alignedSourceWords.add(srcWord);
        for (int j : alignments) {
          alignedTargetWords.add(f.targetPhrase.get(j));
          int[] jAlignments = alignment.t2s(j);
          if (jAlignments != null & jAlignments.length > 0) {
            int[] srcIndices = alignment.t2s(j);
            for (int sIndex : srcIndices) {
              IString srcToken = f.sourcePhrase.get(sIndex);
              alignedSourceWords.add(srcToken);
            }
          }
        }

        // Extract user aligned tokens
        // TODO(spenceg): Many-to-one right now, but there could be many-to-many alignments
        Set<IString> userAlignedTokens = Generics.newHashSet();
        int srcIndex = f.sourcePosition + i;
        for (int jUser : derivation.s2t.f2e(srcIndex)) {
          userAlignedTokens.add(derivation.mt.get(jUser));
        }
       
        // Fire features
        if ( ! isTestMode) {
          // Tuning
          alignedTargetWords.retainAll(userAlignedTokens);
        }
        String tgtAligned = setToString(alignedTargetWords);
        String tgtClassAligned = targetSetToString(alignedTargetWords, true);
        IString oovSource = f.sourceSentence.get(srcIndex);
        String srcToken = oovSource.toString();
        IString oovClass = sourceMap.get(oovSource);
        String srcTokenClass = oovClass.toString();
        features.add(new FeatureValue<String>(String.format("%s.%s>%s", ALIGNMENT_FEATURE, srcToken, tgtAligned), 1.0));
        features.add(new FeatureValue<String>(String.format("%sc.%s>%s", ALIGNMENT_FEATURE, srcTokenClass, tgtClassAligned), 1.0));
      }
    }
    return features;
  }

  /**
   * Get s2t alignments from a rule.
   * 
   * @param rule
   * @return
   */
  private List<Set<Integer>> s2tFromRule(ConcreteRule<IString, String> rule) {
    PhraseAlignment a = rule.abstractRule.alignment;
    List<Set<Integer>> s2t = Generics.newArrayList();
    for (int i = 0, sz = rule.abstractRule.source.size(); i < sz; ++i) {
      s2t.add(new HashSet<Integer>());
    }
    for (int j = 0, sz = rule.abstractRule.target.size(); j < sz; ++j) {
      int[] t2s = a.t2s(j);
      if (t2s != null) {
        for (int srcidx : t2s) {
          s2t.get(srcidx).add(j);
        }
      }
    }
    return s2t;
  }

  /**
   * Add the target blanket around OOV source words.
   * 
   * @param f
   * @param s2t
   * @return
   */
  private List<FeatureValue<String>> oovBlanketFeatures(Featurizable<IString, String> f, List<Set<Integer>> s2t) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    final int srcStart = f.sourcePosition;
    for (int i = 0, sz = f.sourcePhrase.size(); i < sz; ++i) {
      int srcIndex = srcStart + i;
      if (isSourceOOV.get(srcIndex)) {
        // Extract user features
        Set<IString> leftUserContext = Generics.newHashSet();
        Set<IString> userAlignedTokens = Generics.newHashSet();
        Set<IString> rightUserContext = Generics.newHashSet();
        for (int jUser : derivation.s2t.f2e(srcIndex)) {
          userAlignedTokens.add(derivation.mt.get(jUser));
          if (jUser-1 >= 0) {
            leftUserContext.add(derivation.mt.get(jUser-1));
          } else {
            leftUserContext.add(TokenUtils.START_TOKEN);
          }
          if (jUser+1 < derivation.mt.size()) {
            rightUserContext.add(derivation.mt.get(jUser+1));
          } else {
            rightUserContext.add(TokenUtils.END_TOKEN);
          }
        }
        
        // Extract machine features
        Set<Integer> tgtAlignments = s2t.get(i);
        Set<IString> leftMTContext = Generics.newHashSet();
        Set<IString> mtAlignedTokens = Generics.newHashSet();
        Set<IString> rightMTContext = Generics.newHashSet();
        if (tgtAlignments.size() > 0) {
          // Aligned
          for (int jMT : tgtAlignments) {
            jMT += f.targetPosition;
            mtAlignedTokens.add(f.targetPrefix.get(jMT));
            if (jMT-1 >= 0) {
              leftMTContext.add(f.targetPrefix.get(jMT-1));
            } else {
              leftMTContext.add(TokenUtils.START_TOKEN);
            }
            if (jMT+1 < f.targetPrefix.size()) {
              rightMTContext.add(f.targetPrefix.get(jMT+1));
            } else if (f.done) {
              rightMTContext.add(TokenUtils.END_TOKEN);
            }
          }
        }
        
        if ( ! isTestMode) {
          // Tuning
          leftMTContext.retainAll(leftUserContext);
          mtAlignedTokens.retainAll(userAlignedTokens);
          rightMTContext.retainAll(rightUserContext);
        }
        
        // Extract features
        String leftContext = setToString(leftMTContext);
        String leftClassContext = targetSetToString(leftMTContext, true);
        String rightContext = setToString(rightMTContext);
        String rightClassContext = targetSetToString(rightMTContext, true);
        IString oovSource = f.sourceSentence.get(srcIndex);
        String srcToken = oovSource.toString();
        IString oovClass = sourceMap.get(oovSource);
        String srcTokenClass = oovClass.toString();
        
        if (leftContext.length() > 0) {
          features.add(new FeatureValue<String>(String.format("%s.%s<%s", OOV_BLANKET_FEATURE, srcToken, leftContext), 1.0));
          features.add(new FeatureValue<String>(String.format("%sc.%s<%s", OOV_BLANKET_FEATURE, srcTokenClass, leftClassContext), 1.0));
        }
        if (rightContext.length() > 0) {
          features.add(new FeatureValue<String>(String.format("%s.%s>%s", OOV_BLANKET_FEATURE, srcToken, rightContext), 1.0));
          features.add(new FeatureValue<String>(String.format("%sc.%s>%s", OOV_BLANKET_FEATURE, srcTokenClass, rightClassContext), 1.0));
        }
        if (leftContext.length() > 0 || rightContext.length() > 0) {
          features.add(new FeatureValue<String>(String.format("%s.%s<%s>%s", OOV_BLANKET_FEATURE, leftContext, srcToken, rightContext), 1.0));
          features.add(new FeatureValue<String>(String.format("%sc.%s<%s>%s", OOV_BLANKET_FEATURE, leftClassContext, srcTokenClass, rightClassContext), 1.0));
        }
      }
    }
    return features;
  }

  private String setToString(Set<IString> set) {
    return targetSetToString(set, false);
  }

  private String targetSetToString(Set<IString> set, boolean classBased) {
    List<String> tokens = Generics.newArrayList(set.size());
    for (IString token : set) tokens.add(classBased ? targetMap.get(token).toString() : token.toString());
    Collections.sort(tokens);
    StringBuilder sb = new StringBuilder();
    for (String s : tokens) {
      if (sb.length() > 0) sb.append("_");
      sb.append(s);
    }
    return sb.toString();
  }

  private static class UserDerivation {
    public final Sequence<IString> mt;
    public final Sequence<IString> user;
    public final SymmetricalWordAlignment s2t;
    public final Sequence<IString> src;
    public UserDerivation(String mt, String user, String s2t, String src) {
      this.user = IStrings.tokenize(user);
      this.s2t = new SymmetricalWordAlignment(src, mt, s2t);
      this.src = this.s2t.f();
      this.mt = this.s2t.e();
    }
    @Override
    public String toString() {
      return String.format("%s\t%s\t%s\t%s", mt.toString(), user.toString(), s2t.toString(), src.toString());
    }
  }
}
