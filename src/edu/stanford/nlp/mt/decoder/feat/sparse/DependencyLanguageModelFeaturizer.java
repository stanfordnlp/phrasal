package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.decoder.feat.NeedsCloneable;
import edu.stanford.nlp.mt.lm.LMState;
import edu.stanford.nlp.mt.lm.LanguageModel;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tools.deplm.DependencyUtils;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.mt.util.TargetClassMap;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;


/**
 * Featurizer for n-gram dependency language models.
 * 
 * @author Sebastian Schuster
 */
public class DependencyLanguageModelFeaturizer extends DerivationFeaturizer<IString, String> implements NeedsCloneable<IString, String> {

  public static String FEAT_NAME_FRAG_PENALTY = "DEPLM_FRAG_PENALTY";
  public static String FEAT_NAME_WORD_PENALTY = "DEPLM_WORD_PENALTY";
  public static String FEAT_NAME = "DEPLM";
  private static LanguageModel<IString> leftLM ;
  private static LanguageModel<IString> rightLM;
  private static LanguageModel<IString> rootLM;

  private boolean useClasses = false;
  private boolean useFragPenalty = false;
  
  private TargetClassMap targetClassMap;

  
  private static Map<Integer, Map<Integer, Set<Integer>>> reachableNodesCache;
  private static Map<Integer, Map<Integer, HashSet<Integer>>> forwardDependenciesCache;
  private static Map<Integer, Map<Integer,Integer>> reverseDependenciesCache;
  
  private Map<Integer, HashSet<Integer>> head2Dependent;
  private Map<Integer,Integer> dependent2Head;
  private Map<Integer, Set<Integer>> reachableNodes;

  private static String HEAD_SUFFIX = "<HEAD>";
  private static String ROOT_SUFFIX = "<ROOT>";
  private static String FRAG_SUFFIX = "<FRAG>";

  
  public DependencyLanguageModelFeaturizer() {
  
  }
  
  public DependencyLanguageModelFeaturizer(String...args) throws IOException {
    Properties options = FeatureUtils.argsToProperties(args);
    if (!options.containsKey("leftLM")) {
        throw new RuntimeException(
              this.getClass().getName() + ": ERROR No left dependency LM file was specified!");
    }

    if (!options.containsKey("rightLM")) {
      throw new RuntimeException(
            this.getClass().getName() + ": ERROR No right dependency LM file was specified!");
    }
    
    if (!options.containsKey("rootLM")) {
      throw new RuntimeException(
            this.getClass().getName() + ": ERROR No root dependency LM file was specified!");
    }
    
    if (!options.containsKey("parses")) {
      throw new RuntimeException(
            this.getClass().getName() + ": ERROR No dependency parses file was specified!");
    }
    
    this.useClasses = PropertiesUtils.getBool(options, "classBased", false);
    this.useFragPenalty = PropertiesUtils.getBool(options, "fragPenalty", false);
    this.targetClassMap = useClasses ? TargetClassMap.getInstance() : null;
    
     
    
    assert leftLM == null;
    leftLM = LanguageModelFactory.load(options.getProperty("leftLM"));
    assert rightLM == null;
    rightLM = LanguageModelFactory.load(options.getProperty("rightLM"));
    assert rootLM == null;
    rootLM = LanguageModelFactory.load(options.getProperty("rootLM"));

    loadDependencies(options.getProperty("parses"));

  }
  
  
  private void loadDependencies(String filename) throws IOException {
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    forwardDependenciesCache = new HashMap<Integer, Map<Integer, HashSet<Integer>>>();
    reverseDependenciesCache = new HashMap<Integer, Map<Integer, Integer>>();
    reachableNodesCache = new HashMap<Integer, Map<Integer, Set<Integer>>>();

    
    HashMap<Integer, Pair<String, List<Integer>>> deps;
    int i = 0;
    while ((deps = DependencyUtils.getDependenciesFromCoNLLFileReader(reader, true)) != null) {
      reverseDependenciesCache.put(i,DependencyUtils.getReverseDependencies(deps));
      Map<Integer, HashSet<Integer>> forwardDeps = new HashMap<Integer, HashSet<Integer>>();
      for (Integer gov : deps.keySet()) {
        List<Integer> children = deps.get(gov).second;
        forwardDeps.put(gov, new HashSet<Integer>());
        for (Integer child : children) {
          forwardDeps.get(gov).add(child);
        }
      }
      forwardDependenciesCache.put(i, forwardDeps);
      i++;
    }
    
    reader.close();
  }
  
  /*
   * Returns true if a source token was already translated
   * and has been scored
   */
  private boolean isSourceTokenScorable(int sourceHeadIndex, 
      int tgtIndex, Featurizable<IString, String> f,
      CoverageSet alignedSourceIndices) {
    
    if ( ! f.derivation.sourceCoverage.get(sourceHeadIndex))
      return false;
    
    if (alignedSourceIndices.get(sourceHeadIndex))
      return true;
    

    final int tgtLength = f.targetPhrase.size();
    final int srcLength = f.sourcePhrase.size();

    //check if the source head is outside of the bounds of the current rule
    if (sourceHeadIndex < f.sourcePosition || sourceHeadIndex >= (f.sourcePosition + srcLength)) {
      //not in coverage set and not within the current rule, 
      //so it cannot be scorable at this point.
      return false;
    }
    
    final int ruleTgtIndex = tgtIndex - f.targetPosition;
    final int ruleSrcIndex = sourceHeadIndex - f.sourcePosition;
    
    List<Set<Integer>> s2t = Generics.newArrayList(srcLength);
    for (int i = 0; i < srcLength; ++i) {
      s2t.add(new HashSet<Integer>());
    }
    
    PhraseAlignment alignment =  f.rule.abstractRule.alignment;

    for (int i = 0; i < tgtLength; ++i) {
      int[] alignments = alignment.t2s(i);
      if (alignments != null) {
        for (int j : alignments) {
          s2t.get(j).add(i);
        }
      }
    }

    if (ruleSrcIndex < 0) {
      s2t.get(0);
    }
    
    if (s2t.get(ruleSrcIndex).isEmpty()) {
        //Cache this result in alignedSourceIndices
        alignedSourceIndices.set(sourceHeadIndex);
        return true;
    } else {
      for (int j : s2t.get(ruleSrcIndex)) {
        if (j < ruleTgtIndex)
          return true;
      }
    }
    
    return false;
  }
  
  private Set<Integer> reachableNodes(int root, int depth) {
    Set<Integer> reachableNodes = Generics.newHashSet();
    reachableNodes.add(root);
    Set<Integer> children = this.head2Dependent.get(root);
    if (children == null)
      return reachableNodes;
    
    for (int i : children) {
      reachableNodes.addAll(reachableNodes(i, depth + 1));
    }
 
    return reachableNodes;
    
  }
  
  private Set<Integer> reachableNodes(int root) {
    if (!reachableNodes.containsKey(root)) {
      reachableNodes.put(root, this.reachableNodes(root, 0));
    }
    
    return reachableNodes.get(root);
  }
  

  private void scoreFrag(List<FeatureValue<String>> features, IString token, boolean scoreEmptyChildren) {
    String str = token.word() + FRAG_SUFFIX;
    Sequence<IString> seq = new SimpleSequence<IString>(new IString(str));
    seq = Sequences.wrapStartEnd(seq, rootLM.getStartToken(), rootLM.getEndToken());
    double rootScore = rootLM.score(seq, 1, null).getScore();
    features.add(new FeatureValue<String>(FEAT_NAME, rootScore));
    features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, -1.0));
    if (this.useFragPenalty)
      features.add(new FeatureValue<String>(FEAT_NAME_FRAG_PENALTY, -1.0));

    if (scoreEmptyChildren) {
      String headStr = token.word() + HEAD_SUFFIX;
      Sequence<IString> childSeq = new SimpleSequence<IString>(new IString(headStr));
      childSeq = Sequences.wrapStartEnd(childSeq, rootLM.getStartToken(), rootLM.getEndToken());
      double leftScore = leftLM.score(childSeq, 2, null).getScore();
      double rightScore = rightLM.score(childSeq, 2, null).getScore();
      features.add(new FeatureValue<String>(FEAT_NAME, leftScore));
      features.add(new FeatureValue<String>(FEAT_NAME, rightScore));
    }
  }

  private void scoreRight(List<FeatureValue<String>> features, IString token, DepLMSubState subState) {
    Sequence<IString> seq = new SimpleSequence<IString>(token);
    int start = 0;
    if (subState.getRightLMState() == null) {
      seq = Sequences.wrapStart(seq, new IString(subState.headToken.word() + HEAD_SUFFIX));
      seq = Sequences.wrapStart(seq, rightLM.getStartToken());
      start = 2;
    }
    LMState lmState = rightLM.score(seq, start, subState.getRightLMState());
    double rightScore = lmState.getScore();
    subState.setRightLMState(lmState);
    features.add(new FeatureValue<String>(FEAT_NAME, rightScore));
    if ( ! token.equals(rightLM.getEndToken()))
        features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, -1.0));
  }
  
  /**
   * Clean up the state by scoring or attaching all left children of a substate
   * that corresponds to an unaligned source token
   */
  
  private void cleanUp(DepLMState state, Featurizable<IString, String> f, int tgtIndex, 
      List<FeatureValue<String>> features) {
    for (int j = f.sourcePosition; j < f.sourcePhrase.size(); j++) {
      DepLMSubState orphanedSubState = state.getSubState(j);
      if (orphanedSubState != null  
          && orphanedSubState.headToken == null 
          && ! orphanedSubState.leftChildren.isEmpty()
          && isSourceTokenScorable(j, tgtIndex, f, state.alignedSourceIndices)) {
      
        DepLMSubState subState = null;
        Integer sourceHeadIndex = dependent2Head.get(j);
        
        boolean isRoot = false;
        boolean foundLeftHead = false;
        boolean foundRightHead = false;

        while (!isRoot && !foundLeftHead && !foundRightHead) {
          sourceHeadIndex = this.dependent2Head.get(sourceHeadIndex);
          if (sourceHeadIndex == null || sourceHeadIndex < 0) {
            isRoot = true;
          } else {
            if (!isSourceTokenScorable(sourceHeadIndex, tgtIndex, f, state.alignedSourceIndices)) {
              foundRightHead = true;
            } else {
              subState = state.getSubState(sourceHeadIndex);
              foundLeftHead = (subState != null && subState.headToken != null);
            }
          }
        }
        for (IString child : orphanedSubState.leftChildren) {
          if (isRoot) {
            scoreFrag(features, child, false);
          } else if (foundLeftHead) {
            scoreRight(features, child, subState);
          } else {
            subState = state.getSubState(sourceHeadIndex);
            if (subState == null)
              subState = state.addSubState(sourceHeadIndex);
            subState.leftChildren.add(child);
          }
        }
        
        orphanedSubState.leftChildren.clear();
        state.setSubState(j, null);
        
      }
    }
  }
  
  @Override
  public void initialize(int sourceInputId, List<ConcreteRule<IString, String>> ruleList, Sequence<IString> source) {
    this.head2Dependent = forwardDependenciesCache.get(sourceInputId);
    this.dependent2Head = reverseDependenciesCache.get(sourceInputId);
    if (reachableNodesCache.get(sourceInputId) == null)
      reachableNodesCache.put(sourceInputId, new HashMap<Integer, Set<Integer>>());
    this.reachableNodes = reachableNodesCache.get(sourceInputId);
  }
  
  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    
    // Lookup the state
    DepLMState prevState = f.prior == null ? null : (DepLMState) f.prior.getState(this);
    DepLMState state = prevState == null ? new DepLMState() : prevState.clone();
    
    PhraseAlignment alignment = f.rule.abstractRule.alignment;
    for (int i = 0, targetLength = f.targetPhrase.size(); i < targetLength; i++) {
      int tgtIndex = i + f.targetPosition;
      
      //clean up orphaned substates that are a result of
      //unaligned source tokens
      cleanUp(state, f, tgtIndex, features);
      
      
      IString tgtToken = f.targetPhrase.get(i);
      if (tgtToken.word().length() == 0 || TokenUtils.isPunctuation(tgtToken.word()))
        continue;
      if (alignment.t2s(i) == null || alignment.t2s(i).length < 1) {
        // Unaligned
        scoreFrag(features, tgtToken, true);
        continue;
      }
      
      if (this.useClasses) {
        tgtToken = this.targetClassMap.get(tgtToken);
      }
      
      Integer sourceHeadIndex = null;
      int sourceDepIndex = -1;
      for (int j = 0; j < alignment.t2s(i).length; j++) {
        int srcIndex = alignment.t2s(i)[j] + f.sourcePosition;
        // Heuristic: choose the leftmost aligned token in the case of multiple alignments
        // TODO: Is this the best/right heuristic?
        if (sourceHeadIndex == null && this.dependent2Head.get(srcIndex) != null) {
          sourceHeadIndex = this.dependent2Head.get(srcIndex);
          sourceDepIndex = srcIndex;
        } else if (this.dependent2Head.get(srcIndex) != null) {
          if (sourceHeadIndex == srcIndex) {
            // Special case: target aligned to both dependent and head
            sourceHeadIndex = this.dependent2Head.get(srcIndex);
            sourceDepIndex = srcIndex;
          }
        }
      }
      
      // True if a target content word is aligned to source punctuation
      if (sourceHeadIndex == null) {
        scoreFrag(features, tgtToken, true);
        continue;
      }

      //Special case: force 1:1 alignments, if the source token is already aligned to some other target token
      //then ignore the current token
      if (state.alignedSourceIndices.get(sourceDepIndex)) {
        scoreFrag(features, tgtToken, true);
        continue;
      }
      
      //mark the source token as being aligned
      state.alignedSourceIndices.set(sourceDepIndex);
      
      
      
      //check for root
      if (sourceHeadIndex < 0) {
        String str = tgtToken.word() + ROOT_SUFFIX;
        Sequence<IString> seq = new SimpleSequence<IString>(new IString(str));
        seq = Sequences.wrapStartEnd(seq, rootLM.getStartToken(), rootLM.getEndToken());
        double rootScore = rootLM.score(seq, 1, null).getScore();
        features.add(new FeatureValue<String>(FEAT_NAME, rootScore));
        features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, -1.0));
      
      } else {
        //check if head was already put down
        if (isSourceTokenScorable(sourceHeadIndex, tgtIndex, f, state.alignedSourceIndices)) {

          
          
          
          //try to score right
          DepLMSubState subState = state.getSubState(sourceHeadIndex);
          if (subState != null && subState.headToken != null) {
            scoreRight(features, tgtToken, subState);
          } else {
            
            // Walk up the source dependency tree until
            // 1) we reach the root (isRoot),
            // 2) we find a token that has not been put down yet (foundLeftHead),
            // 3) or we find an aligned token (foundRightHead)
            
            boolean isRoot = false;
            boolean foundLeftHead = false;
            boolean foundRightHead = false;

            while (!isRoot && !foundLeftHead && !foundRightHead) {
              sourceHeadIndex = this.dependent2Head.get(sourceHeadIndex);
              if (sourceHeadIndex == null || sourceHeadIndex < 0) {
                isRoot = true;
              } else {
                if (!isSourceTokenScorable(sourceHeadIndex, tgtIndex, f, state.alignedSourceIndices)) {
                  foundRightHead = true;
                } else {
                  subState = state.getSubState(sourceHeadIndex);
                  foundLeftHead = (subState != null && subState.headToken != null);
                }
              }
            }
            
            if (isRoot) {
              scoreFrag(features, tgtToken, false);
            } else if (foundLeftHead) {
              scoreRight(features, tgtToken, subState);
            } else {
              subState = state.getSubState(sourceHeadIndex);
              if (subState == null)
                subState = state.addSubState(sourceHeadIndex);
              subState.leftChildren.add(tgtToken);
            }
            
          }
          
        } else {
          DepLMSubState subState = state.getSubState(sourceHeadIndex);
          if (subState == null)
            subState = state.addSubState(sourceHeadIndex);
          subState.leftChildren.add(tgtToken);
        }
      }
          
       
      DepLMSubState subState = state.getSubState(sourceDepIndex);
      if (subState == null) {
        subState = state.addSubState(sourceDepIndex);
      }
      subState.setHeadToken(tgtToken);

      
     
      
      
      //score left children of current token
      Collections.reverse(subState.getLeftChildren());
      Sequence<IString> seq = new SimpleSequence<IString>(subState.getLeftChildren());
      seq = Sequences.wrapStart(seq, new IString(tgtToken.word() + "<HEAD>"));
      seq = Sequences.wrapStartEnd(seq, leftLM.getStartToken(), leftLM.getEndToken());
      double leftScore = leftLM.score(seq, 2, null).getScore();
      features.add(new FeatureValue<String>(FEAT_NAME, leftScore));
      features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, -1.0 * subState.getLeftChildren().size()));
      subState.getLeftChildren().clear();
    }

    //check which substates can be deleted
    for (Integer i : state.getSubStates().keySet()) {
      if (state.getSubState(i) == null)
        continue;
      HashSet<Integer> deps = this.head2Dependent.get(i);
      boolean del = f.derivation.sourceCoverage.get(i);
      if (del && deps != null) {
        for (Integer j : this.reachableNodes(i)) {
          if (!f.derivation.sourceCoverage.get(j)) {
            del = false;
            break;
          }
        }
      }
      if (del) {
        if (state.getSubState(i).headToken != null) {
          //score right end token
          scoreRight(features, rightLM.getEndToken(), state.getSubState(i));
        } else {
          //clean up
          cleanUp(state, f, f.targetPosition + f.targetPhrase.size(), features);
        }
        //remove this head index from state
        state.setSubState(i, null);
      }
    }
    
    f.setState(this, state);
    
    return features;
  }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
  
  public static class DepLMState extends FeaturizerState {

    private HashMap<Integer, DepLMSubState> subStates;
    private CoverageSet alignedSourceIndices;
    
    public DepLMState() {
      this.subStates = new HashMap<Integer, DepLMSubState>();
      this.alignedSourceIndices = new CoverageSet();
    }
    
    @Override
    public boolean equals(Object other) {
      if (other == null)
        return false;
      if (other == this)
        return true;
      if (!(other instanceof DepLMState))
        return false;

      DepLMState oState = (DepLMState) other;
      
      if (oState.alignedSourceIndices.cardinality() != this.alignedSourceIndices.cardinality())
        return false;

      if ( ! oState.alignedSourceIndices.equals(this.alignedSourceIndices)) {
        return false;
      }
      
      for (Integer i : this.subStates.keySet()) {
        if ((oState.getSubState(i) == null || this.getSubState(i) == null)) {
          if (oState.getSubState(i) != this.getSubState(i))
            return false;
        } else {
          if (!this.subStates.get(i).equals(oState.getSubState(i)))
            return false;
        }
      }
      
      return true;
    }

    public HashMap<Integer, DepLMSubState> getSubStates() {
      return this.subStates;
    }

    public DepLMSubState getSubState(int i) {
      return this.subStates.get(i);
    }
    
    public DepLMSubState addSubState(int i) {
      this.subStates.put(i, new DepLMSubState());
      return this.subStates.get(i);
    }

    public void setSubState(int i, DepLMSubState subState) {
      this.subStates.put(i, subState);
    }

    
   
    
    
    @Override
    public int hashCode() {
      SortedSet<Integer> keys = new TreeSet<Integer>(this.getSubStates().keySet());
      int sz = this.alignedSourceIndices.size();
      for (int i : keys) {
        if (this.getSubState(i) != null)
          sz++;
      }

      if (sz == 0)
        return 0;
      int arr[] = new int[sz];
      int j = 0;
      for (int i: this.alignedSourceIndices) {
        arr[j] = i;
        j++;
      }
      for (int i : keys) {
        if (this.getSubState(i) != null) {
          arr[j] = this.getSubState(i).hashCode();
          j++;
        }
      }
      return arr.hashCode();
    }
    
    public DepLMState clone() {
      DepLMState copy = new DepLMState();
      for (Integer i : this.subStates.keySet()) {
        copy.setSubState(i, this.subStates.get(i) != null ? this.subStates.get(i).clone() : null);
      }
      copy.alignedSourceIndices.or(this.alignedSourceIndices);
      return copy;
    }
  }
  
  private static class DepLMSubState {
    
    private LMState rightLMState;
    private List<IString> leftChildren;
    private IString headToken;
    
    
    public DepLMSubState() {
      this.rightLMState = null;
      this.leftChildren = new LinkedList<IString>();
      this.headToken = null;
      
    }
    
    public void setHeadToken(IString token) {
      this.headToken = token;
    }
    
    public IString getHeadToken() {
      return this.headToken;
    }
    
    public void setRightLMState(LMState rightLMState){
      this.rightLMState = rightLMState;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other == null)
        return false;
      if (other == this)
        return true;
      if (!(other instanceof DepLMSubState))
        return false;
      
      DepLMSubState oState = (DepLMSubState) other;
      
      if (rightLMState != null &&  oState.rightLMState != null && 
           !rightLMState.equals(oState.getRightLMState()))
        return false;
      
      if ((rightLMState == null || oState.rightLMState == null) &&
          oState.rightLMState != rightLMState)
        return false;

      int lChildrenCount = oState.getLeftChildren().size();
      if (this.leftChildren.size() != lChildrenCount)
        return false;
      
      for (int i = 0; i < lChildrenCount; i++) {
        if (!this.getLeftChildren().get(i).equals(oState.getLeftChildren().get(i)))
          return false;
      }
      
      return true;
    }
   
    //fix hash function such that it returns the same
    //val if equals returns true
    //arrays.hashCode
    
    public int hashCode() {
      int sz = 2 + this.getLeftChildren().size();
      int arr[] = new int[sz];
      arr[0] = this.headToken != null ? this.headToken.id : 0;
      arr[1] = this.rightLMState != null ? this.rightLMState.hashCode() : 0;
      int i = 2;
      for (IString c : this.getLeftChildren()) {
        arr[i] = c.id;
        i++;
      }
      return arr.hashCode();
    }
    
    public LMState getRightLMState() {
      return this.rightLMState;
    }
    
    public List<IString> getLeftChildren() {
      return this.leftChildren;
    }
    
    public DepLMSubState clone() {
      DepLMSubState copy = new DepLMSubState();
      copy.setRightLMState(this.rightLMState);
      copy.getLeftChildren().addAll(this.getLeftChildren());
      copy.setHeadToken(this.headToken);
      return copy;
    }
    
  }
}
