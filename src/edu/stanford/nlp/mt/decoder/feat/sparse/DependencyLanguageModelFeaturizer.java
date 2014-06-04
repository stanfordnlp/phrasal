package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import edu.stanford.nlp.mt.base.CoreNLPCache;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.decoder.feat.NeedsCloneable;
import edu.stanford.nlp.mt.lm.LMState;
import edu.stanford.nlp.mt.lm.LanguageModel;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;
import edu.stanford.nlp.mt.pt.ConcreteRule;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.mt.util.TargetClassMap;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;


/**
 * Featurizer for n-gram dependency language models.
 * 
 * @author Sebastian Schuster
 */
public class DependencyLanguageModelFeaturizer extends DerivationFeaturizer<IString, String> implements NeedsCloneable<IString, String> {

  public static String FEAT_NAME_WORD_PENALTY = "DEPLM_WORD_PENALTY";
  public static String FEAT_NAME = "DEPLM";
  private static LanguageModel<IString> leftLM ;
  private static LanguageModel<IString> rightLM;
  private static LanguageModel<IString> rootLM;

  private boolean useClasses = false;
  
  private TargetClassMap targetClassMap;

  
  private int maxDepth = 0;
  private HashMap<Integer, Set<Integer>> reachableNodesCache;
  private HashMap<Integer, HashSet<Integer>> forwardDependencies;
  private HashMap<Integer, Integer> reverseDependencies;
  
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
    
    if (!options.containsKey("annotations")) {
      throw new RuntimeException(
            this.getClass().getName() + ": ERROR No dependency annotations file was specified!");
    }
    
    if (options.contains("maxDepth")) {
      this.maxDepth = Integer.parseInt(options.getProperty("maxDepth"));
    }
    
    this.useClasses = PropertiesUtils.getBool(options, "classBased", false);
    this.targetClassMap = useClasses ? TargetClassMap.getInstance() : null;
    
     
    
    assert leftLM == null;
    leftLM = LanguageModelFactory.load(options.getProperty("leftLM"));
    assert rightLM == null;
    rightLM = LanguageModelFactory.load(options.getProperty("rightLM"));
    assert rootLM == null;
    rootLM = LanguageModelFactory.load(options.getProperty("rootLM"));

    
    CoreNLPCache.loadSerialized(options.getProperty("annotations"));

    

  }
       
  /*
   * returns true if token is a word 
   * (starts with a letter or a digit)
   */
  private boolean isWord(String token) {
    return Character.isAlphabetic(token.charAt(0)) || Character.isDigit(token.charAt(0));
  }
  

 
  
  /*
   * Returns true if a source token was already translated
   * and has been scored
   */
  private boolean isSourceTokenTranslated(int sourceIndex, 
      int currentTargetIndex, Featurizable<IString, String> f,
      SortedSet<Integer> alignedSourceIndices) {
    
    if (!f.derivation.sourceCoverage.get(sourceIndex))
      return false;
    
    if (alignedSourceIndices.contains(sourceIndex))
      return true;
    
    //check if the sourceIndex lies within the boundaries
    //of the current rule
    //if not, then the token was already translated as
    //it is in the source coverage set
    if (sourceIndex < f.sourcePosition || sourceIndex >= (f.sourcePosition + f.sourcePhrase.size()))
      return true;
    
    
    int ruleTargetIndex = currentTargetIndex - f.targetPosition;
    
    //the target index token should always be aligned
    //if this is not the case, return false
    if (f.rule.abstractRule.alignment.t2s(ruleTargetIndex) == null 
        || f.rule.abstractRule.alignment.t2s(ruleTargetIndex).length < 1)
    return false;
     
   
   int currentSourceIndex = f.rule.abstractRule.alignment.t2s(ruleTargetIndex)[0];
   
   if (sourceIndex <= currentSourceIndex)
     return true;
   
   return false;

  }
  
  private Set<Integer> reachableNodes(int root, int depth) {
    Set<Integer> reachableNodes = Generics.newHashSet();
    reachableNodes.add(root);
    Set<Integer> children = this.forwardDependencies.get(root);
    if (children == null || depth > this.maxDepth)
      return reachableNodes;
    
    for (int i : children) {
      reachableNodes.addAll(reachableNodes(i, depth + 1));
    }
 
    return reachableNodes;
    
  }
  
  private Set<Integer> reachableNodes(int root) {
    if (!this.reachableNodesCache.containsKey(root)) {
      this.reachableNodesCache.put(root, this.reachableNodes(root, 0));
    }
    
    return this.reachableNodesCache.get(root);
  }
  


  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString, String>> ruleList, Sequence<IString> source) {
    SemanticGraph semanticGraph = CoreNLPCache.get(sourceInputId).get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    this.forwardDependencies = new HashMap<Integer, HashSet<Integer>>();
    this.reverseDependencies = new HashMap<Integer, Integer>() ;
    this.reachableNodesCache = new HashMap<Integer, Set<Integer>>();
    for (TypedDependency dep : semanticGraph.typedDependencies()) {
      int govIndex = dep.gov().index() - 1;
      int depIndex = dep.dep().index() - 1;
      if (!this.forwardDependencies.containsKey(govIndex)) {
        this.forwardDependencies.put(govIndex, new HashSet<Integer>());
      }
      this.forwardDependencies.get(govIndex).add(depIndex);
      this.reverseDependencies.put(depIndex, govIndex);
    }
  }

  
  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    DepLMState prevState = f.prior == null ? null : (DepLMState) f.prior.getState(this);
    DepLMState state = prevState == null ? new DepLMState() : prevState.clone();
    
    int targetLength = f.targetPhrase.size();
    PhraseAlignment alignment = f.rule.abstractRule.alignment;
    tokfor: for (int i = 0; i < targetLength; i++) {
      int currentTargetIndex = i + f.targetPosition;
      IString token = f.targetPhrase.get(i);
      if (token.word().length() < 1 || !isWord(token.word()))
        continue;
      if (alignment.t2s(i) == null || alignment.t2s(i).length < 1)
        continue;
      
      
      if (this.useClasses)
        token = this.targetClassMap.get(token);
      
      
      Integer sourceGovIndex = null;
      int sourceDepIndex = -1;
      for (int j = 0; j < alignment.t2s(i).length; j++) {
        int k = alignment.t2s(i)[j] + f.sourcePosition;
        if (sourceGovIndex == null &&  this.reverseDependencies.get(k) != null) {
          sourceGovIndex = this.reverseDependencies.get(k);
          sourceDepIndex = k;
        } else if (this.reverseDependencies.get(k) != null) {
          if (sourceGovIndex == k) {
            sourceGovIndex = this.reverseDependencies.get(k);
            sourceDepIndex = k;
          }
        }
      }
      
      //check if the current word has a head
      if (sourceGovIndex == null)
        continue; 

      //force 1:1 alignments, if the source token is already aligned to some other target token
      //then ignore the current token
      if (state.alignedSourceIndices.contains(sourceDepIndex)) {
        continue;
      }
      
      //mark the source token as being aligned
      state.alignedSourceIndices.add(sourceDepIndex);
      
      //check for root
      if (sourceGovIndex == -1) {
        Sequence<IString> seq = new SimpleSequence<IString>(token);
        double rootScore = rootLM.score(seq, 0, null).getScore();
        features.add(new FeatureValue<String>(FEAT_NAME, rootScore));
        features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, -1.0));
      } else {
        //check if head was already put down
        if (isSourceTokenTranslated(sourceGovIndex, currentTargetIndex, f, state.alignedSourceIndices)) {
          //try to score right
          DepLMSubState oSubState = state.getSubState(sourceGovIndex);
          DepLMSubState subState = oSubState;
          
          //if the substate exists but has no head token, then the head is not aligned
          //try to align it to the parent of the head until you reach the root or
          //maximum depth
          int depth = 0;
          while (subState == null || subState.headToken == null) {
            if (depth >= this.maxDepth) {
              if (oSubState != null) {
                oSubState.getLeftChildren().clear();
                state.setSubState(sourceGovIndex, null);
              }
              continue tokfor;
            }
            depth++;
            
            sourceGovIndex = this.reverseDependencies.get(sourceGovIndex);
            //score all left children as roots
            if (sourceGovIndex == -1) {
              if (oSubState != null && oSubState.getLeftChildren().size() > 0) {
                for (IString child : oSubState.getLeftChildren()) {
                  Sequence<IString> seq = new SimpleSequence<IString>(child);
                  double rootScore = rootLM.score(seq, 0, null).getScore();
                  features.add(new FeatureValue<String>(FEAT_NAME, rootScore));
                  features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, -1.0));
                }
                oSubState.getLeftChildren().clear();
              }
              
              //Score current token as a root
              
              
              Sequence<IString> seq = new SimpleSequence<IString>(token);
              double rootScore = rootLM.score(seq, 0, null).getScore();
              features.add(new FeatureValue<String>(FEAT_NAME, rootScore));
              features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, -1.0));

              
              //go to next token
              continue tokfor;
            } else {
              subState = state.getSubState(sourceGovIndex);
              //check if the transitive head was already put down
              if (isSourceTokenTranslated(sourceGovIndex, currentTargetIndex, f, state.alignedSourceIndices)) {
                if (subState == null || subState.headToken == null) {
                  //also the parent is unaligned, go one level further up the tree
                  continue;
                }
                
                
                //if left children exist, score them before breaking
                //they will now be right tokens relative to the new head 
                //under the assumption that there are no crossing arcs
                if (oSubState != null && !oSubState.getLeftChildren().isEmpty()) {
                  Sequence<IString> seq = new SimpleSequence<IString>(oSubState.getLeftChildren());
                  int start = 0;
                  if (subState.getRightLMState() == null) {
                    seq = Sequences.wrapStart(seq, new IString(subState.headToken.word() + "<HEAD>"));
                    seq = Sequences.wrapStart(seq, rightLM.getStartToken());
                    start = 1;
                  }
                  LMState lmState = rightLM.score(seq, start, subState.getRightLMState());
                  double rightScore = lmState.getScore();
                  subState.setRightLMState(lmState);
                  features.add(new FeatureValue<String>(FEAT_NAME, rightScore));
                  features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, -1.0));
                  oSubState.getLeftChildren().clear();
                }
                
                
                //exit loop to score the current token as a right token
                break;
                
                
              } else {
                //add it to the list of left children of the parent
                if (oSubState != null) {
                  if (subState == null) {
                    subState = state.addSubState(sourceGovIndex);
                  }
                  subState.getLeftChildren().addAll(oSubState.getLeftChildren());
                  subState.getLeftChildren().add(token);
                  //go to next token
                  continue tokfor;
                }
              }
            }
          }
      
          
          //score current token as a right token
          Sequence<IString> seq = new SimpleSequence<IString>(token);
          //add start and end tokens
          int start = 0;
          if (subState.getRightLMState() == null) {
            seq = Sequences.wrapStart(seq, new IString(subState.headToken.word() + "<HEAD>"));
            seq = Sequences.wrapStart(seq, rightLM.getStartToken());
            start = 1;
          }
          LMState lmState = rightLM.score(seq, start, subState.getRightLMState());
          double rightScore = lmState.getScore();
          subState.setRightLMState(lmState);
          features.add(new FeatureValue<String>(FEAT_NAME, rightScore));
          features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, -1.0));

        } else {
          //add the current token to the list that should be scored on the left
          DepLMSubState subState =  state.getSubState(sourceGovIndex);
          if (subState == null) {
            subState = state.addSubState(sourceGovIndex);
          }
          subState.getLeftChildren().add(token);
        }
          
      }
      
      //score left children of current token
      DepLMSubState subState =  state.getSubState(sourceDepIndex);
      if (subState != null) {
        if (subState.getLeftChildren().size() > 0) {
          Collections.reverse(subState.getLeftChildren());
          Sequence<IString> seq = new SimpleSequence<IString>(subState.getLeftChildren());
          seq = Sequences.wrapStart(seq, new IString(token.word() + "<HEAD>"));
          seq = Sequences.wrapStartEnd(seq, leftLM.getStartToken(), leftLM.getEndToken());
          double leftScore = leftLM.score(seq, 1, null).getScore();
          features.add(new FeatureValue<String>(FEAT_NAME, leftScore));
          features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, -1.0 * subState.getLeftChildren().size()));
          subState.getLeftChildren().clear();
        }
      } else if (this.forwardDependencies.get(sourceDepIndex) != null && !this.forwardDependencies.get(sourceDepIndex).isEmpty()) {
        subState = state.addSubState(sourceDepIndex);
      }
      
      if (subState != null) {
        subState.setHeadToken(token);
      }     
    }

    //check which substates can be deleted
    for (Integer i: state.getSubStates().keySet()) {
      if (state.getSubState(i) == null)
        continue;
      HashSet<Integer> deps = this.forwardDependencies.get(i);
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
    private SortedSet<Integer> alignedSourceIndices;
    
    public DepLMState() {
      this.subStates = new HashMap<Integer, DepLMSubState>();
      this.alignedSourceIndices = Generics.newTreeSet();
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
      
      if (oState.alignedSourceIndices.size() != this.alignedSourceIndices.size())
        return false;

      for (Integer i : this.alignedSourceIndices) {
        if (!oState.alignedSourceIndices.contains(i))
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
      copy.alignedSourceIndices.addAll(this.alignedSourceIndices);
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
