package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.decoder.feat.NeedsCloneable;
import edu.stanford.nlp.mt.lm.LMState;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tools.deplm.DependencyUtils;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TargetClassMap;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

public abstract class AbstractDependencyLanguageModelFeaturizer extends DerivationFeaturizer<IString, String>  implements NeedsCloneable<IString, String> {

  public static String FEAT_NAME_FRAG_PENALTY = "DEPLM_FRAG_PENALTY";
  public static String FEAT_NAME_WORD_PENALTY = "DEPLM_WORD_PENALTY";
  public static String FEAT_NAME = "DEPLM";


  public boolean useClasses = false;
  public boolean useFragPenalty = false;
  public boolean disableTransitivity = false;
  
  public TargetClassMap targetClassMap;

  
  public static Map<Integer, Map<Integer, Set<Integer>>> reachableNodesCache;
  public static Map<Integer, Map<Integer, HashSet<Integer>>> forwardDependenciesCache;
  public static Map<Integer, Map<Integer,Integer>> reverseDependenciesCache;
  
  public Map<Integer, HashSet<Integer>> head2Dependent;
  public Map<Integer,Integer> dependent2Head;
  public Map<Integer, Set<Integer>> reachableNodes;

  public static String HEAD_SUFFIX = "<HEAD>";
  public static String ROOT_SUFFIX = "<ROOT>";
  public static String FRAG_SUFFIX = "<FRAG>";

  
  public abstract void scoreLeft(List<FeatureValue<String>> features, IString token, DepLMSubState subState);
  
  public abstract void scoreRight(List<FeatureValue<String>> features, IString token, DepLMSubState subState);

  public abstract void scoreRightEnd(List<FeatureValue<String>> features, DepLMSubState subState);
  
  public abstract void scoreUnaligned(List<FeatureValue<String>> features, IString token);
  
  public abstract void scoreFrag(List<FeatureValue<String>> features, IString token, boolean scoreEmptyChildren);
  
  public abstract void scoreRoot(List<FeatureValue<String>> features, IString token);
  
  /*
   * Returns true if a source token was already translated
   * and has been scored
   */
  public boolean isSourceTokenScorable(int sourceHeadIndex, 
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
  

  public Set<Integer> reachableNodes(int root, int depth) {
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
  
  public Set<Integer> reachableNodes(int root) {
    if (!reachableNodes.containsKey(root)) {
      reachableNodes.put(root, this.reachableNodes(root, 0));
    }
    
    return reachableNodes.get(root);
  }
  
  
  public void loadDependencies(String filename) throws IOException {
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    forwardDependenciesCache = new HashMap<Integer, Map<Integer, HashSet<Integer>>>();
    reverseDependenciesCache = new HashMap<Integer, Map<Integer, Integer>>();
    reachableNodesCache = new HashMap<Integer, Map<Integer, Set<Integer>>>();

    
    HashMap<Integer, Pair<IndexedWord, List<Integer>>> deps;
    int i = 0;
    while ((deps = DependencyUtils.getDependenciesFromCoNLLFileReader(reader, true, false)) != null) {
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
  
  /**
   * Clean up the state by scoring or attaching all left children of a substate
   * that corresponds to an unaligned source token
   */
  
  private void cleanUp(DepLMState state, Featurizable<IString, String> f, int tgtIndex, 
      List<FeatureValue<String>> features) {
    
    final int startPos = f.sourcePosition;
    final int endPos = startPos + f.sourcePhrase.size();
    
    for (int j = startPos; j < endPos; j++) {
      DepLMSubState orphanedSubState = state.getSubState(j);
      if (orphanedSubState != null  
          && orphanedSubState.getHeadToken() == null 
          && ! orphanedSubState.getLeftChildren().isEmpty()
          && isSourceTokenScorable(j, tgtIndex, f, state.getAlignedSourceIndices())) {
      
        DepLMSubState subState = null;
        Integer sourceHeadIndex = dependent2Head.get(j);
        
        boolean isRoot = ! this.disableTransitivity; // in case transitive attachments are disabled, go directly to the root
        boolean foundLeftHead = false;
        boolean foundRightHead = false;

        while (!isRoot && !foundLeftHead && !foundRightHead) {
          sourceHeadIndex = this.dependent2Head.get(sourceHeadIndex);
          if (sourceHeadIndex == null || sourceHeadIndex < 0) {
            isRoot = true;
          } else {
            if (!isSourceTokenScorable(sourceHeadIndex, tgtIndex, f, state.getAlignedSourceIndices())) {
              foundRightHead = true;
            } else {
              subState = state.getSubState(sourceHeadIndex);
              foundLeftHead = (subState != null && subState.getHeadToken() != null);
            }
          }
        }
        for (IString child : orphanedSubState.getLeftChildren()) {
          if (isRoot) {
            scoreFrag(features, child, false);
          } else if (foundLeftHead) {
            scoreRight(features, child, subState);
          } else {
            subState = state.getSubState(sourceHeadIndex);
            if (subState == null)
              subState = state.addSubState(sourceHeadIndex);
            subState.getLeftChildren().add(child);
          }
        }
        
        orphanedSubState.getLeftChildren().clear();
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
        scoreUnaligned(features, tgtToken);
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
        scoreUnaligned(features, tgtToken);
        continue;
      }

      //Special case: force 1:1 alignments, if the source token is already aligned to some other target token
      //then ignore the current token
      if (state.getAlignedSourceIndices().get(sourceDepIndex)) {
        scoreUnaligned(features, tgtToken);
        continue;
      }
      
      //mark the source token as being aligned
      state.getAlignedSourceIndices().set(sourceDepIndex);
      
      
      
      //check for root
      if (sourceHeadIndex < 0) {
       scoreRoot(features, tgtToken);
      } else {
        //check if head was already put down
        if (isSourceTokenScorable(sourceHeadIndex, tgtIndex, f, state.getAlignedSourceIndices())) {

          //try to score right
          DepLMSubState subState = state.getSubState(sourceHeadIndex);
          if (subState != null && subState.getHeadToken() != null) {
            scoreRight(features, tgtToken, subState);
          } else {
            
            // Walk up the source dependency tree until
            // 1) we reach the root (isRoot),
            // 2) we find a token that has not been put down yet (foundLeftHead),
            // 3) or we find an aligned token (foundRightHead)
            // In case transitivity is disabled, then we score it as a 
            // fragment immediately.
            
            boolean isRoot = ! this.disableTransitivity;
            boolean foundLeftHead = false;
            boolean foundRightHead = false;

            while (!isRoot && !foundLeftHead && !foundRightHead) {
              sourceHeadIndex = this.dependent2Head.get(sourceHeadIndex);
              if (sourceHeadIndex == null || sourceHeadIndex < 0) {
                isRoot = true;
              } else {
                if (!isSourceTokenScorable(sourceHeadIndex, tgtIndex, f, state.getAlignedSourceIndices())) {
                  foundRightHead = true;
                } else {
                  subState = state.getSubState(sourceHeadIndex);
                  foundLeftHead = (subState != null && subState.getHeadToken() != null);
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
              subState.getLeftChildren().add(tgtToken);
            }
            
          }
          
        } else {
          DepLMSubState subState = state.getSubState(sourceHeadIndex);
          if (subState == null)
            subState = state.addSubState(sourceHeadIndex);
          subState.getLeftChildren().add(tgtToken);
        }
      }
          
       
      DepLMSubState subState = state.getSubState(sourceDepIndex);
      if (subState == null) {
        subState = state.addSubState(sourceDepIndex);
      }
      subState.setHeadToken(tgtToken);

      
     
      
      
      //score left children of current token
      scoreLeft(features, tgtToken, subState);
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
        if (state.getSubState(i).getHeadToken() != null) {
          //score right end token
          scoreRightEnd(features, state.getSubState(i));
        }
        //remove this head index from state
        state.setSubState(i, null);
      }
    }
    
    //clean up
    cleanUp(state, f, f.targetPosition + f.targetPhrase.size(), features);

    
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
      this.setAlignedSourceIndices(new CoverageSet());
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
      
      if (oState.getAlignedSourceIndices().cardinality() != this.getAlignedSourceIndices().cardinality())
        return false;

      if ( ! oState.getAlignedSourceIndices().equals(this.getAlignedSourceIndices())) {
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
      int sz = this.getAlignedSourceIndices().size();
      for (int i : keys) {
        if (this.getSubState(i) != null)
          sz++;
      }

      if (sz == 0)
        return 0;
      int arr[] = new int[sz];
      int j = 0;
      for (int i: this.getAlignedSourceIndices()) {
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
      copy.getAlignedSourceIndices().or(this.getAlignedSourceIndices());
      return copy;
    }

    public CoverageSet getAlignedSourceIndices() {
      return alignedSourceIndices;
    }

    public void setAlignedSourceIndices(CoverageSet alignedSourceIndices) {
      this.alignedSourceIndices = alignedSourceIndices;
    }
  }
  
  
  protected static class DepLMSubState {
    
    private LMState rightLMState;
    private IString rightSibling;

    private List<IString> leftChildren;
    private IString headToken;
    
    
    public DepLMSubState() {
      this.rightLMState = null;
      this.leftChildren = new LinkedList<IString>();
      this.headToken = null;
      this.rightSibling = null;
      
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
      
      
      if (headToken != null && oState.headToken != null &&
          headToken.id != oState.headToken.id)
        return false;
      
      if ((headToken == null || oState.headToken == null) &&
          headToken != oState.headToken)
        return false;
      
      if (rightSibling != null && oState.rightSibling != null &&
          rightSibling.id != oState.rightSibling.id)
        return false;
      
      if ((rightSibling == null || oState.rightSibling == null) &&
          rightSibling != oState.rightSibling)
        return false;
      
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
      int sz = 3 + this.getLeftChildren().size();
      int arr[] = new int[sz];
      arr[0] = this.headToken != null ? this.headToken.id : 0;
      arr[1] = this.rightLMState != null ? this.rightLMState.hashCode() : 0;
      arr[2] = this.rightSibling != null ? this.rightSibling.id : 0;
      int i = 3;
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
      copy.setRightSibling(this.rightSibling);
      return copy;
    }

    public IString getRightSibling() {
      return rightSibling;
    }

    public void setRightSibling(IString rightSibling) {
      this.rightSibling = rightSibling;
    }
    
  }
  
}
