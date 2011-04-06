package edu.stanford.nlp.mt.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.CoreAnnotations.*;

public class DAGFeatureExtractor {
  
  // sk : k-th item in the stack from the top (s1, s2, ...)
  // qk : k-th item in the queue (q1, q2, ...)
  // flags
  private static final boolean useS1Word = true;
  private static final boolean useS1Lemma = true;
  private static final boolean useS1POS = true;
  private static final boolean useS1WordPOS = true;
  private static final boolean useS1NumChild = true;
  private static final boolean useS1LeftChildPOS = true;
  private static final boolean useS1LeftChildRel = true;
  private static final boolean useS1RightChildPOS = true;
  private static final boolean useS1RightChildRel = true;
  private static final boolean useS1PreviousTokenPOS = true;
  
  private static final boolean useS2Word = true;
  private static final boolean useS2Lemma = true;
  private static final boolean useS2POS = true;
  private static final boolean useS2WordPOS = true;
  private static final boolean useS2NumChild = true;
  private static final boolean useS2LeftChildPOS = true;
  private static final boolean useS2LeftChildRel = true;
  private static final boolean useS2RightChildPOS = true;
  private static final boolean useS2RightChildRel = true;
  private static final boolean useS2NextTokenPOS = true;
  
  private static final boolean useS3Word = true;
  private static final boolean useS3Lemma = true;
  private static final boolean useS3POS = true;
  private static final boolean useS3WordPOS = true;
  
  private static final boolean useQ1Word = true;
  private static final boolean useQ1Lemma = true;
  private static final boolean useQ1POS = true;
  private static final boolean useQ1WordPOS = true;
  
  private static final boolean useQ2Word = true;
  private static final boolean useQ2Lemma = true;
  private static final boolean useQ2POS = true;
  private static final boolean useQ2WordPOS = true;
  
  private static final boolean useQ3Word = true;
  private static final boolean useQ3Lemma = true;
  private static final boolean useQ3POS = true;
  private static final boolean useQ3WordPOS = true;
  
  private static final boolean usePreAction = true;

  private static final boolean useS1Q1word = true;
  private static final boolean useS1Q1POS = true;
  private static final boolean useS1Q1WordPOS = false;
  
  private static final boolean useQ1Q2word = true;
  private static final boolean useQ1Q2POS = true;
  private static final boolean useQ1Q2WordPOS = false;
  
  private static final boolean useS1S2word = true;
  private static final boolean useS1S2POS = true;
  private static final boolean useS1S2WordPOS = false;
  
  
  // TODO : add flags for new features  


  public static List<String> extractFeatures(Structure struc) {
    List<String> features = new ArrayList<String>();
    Stack<IndexedWord> stack = struc.getStack();
    List<IndexedWord> inputQueue = struc.getInput();
    if(stack.size()==0) return features;   // empty stack: always SHIFT
    int curQueue = struc.getCurrentInputIndex();
    int stackSize = stack.size();
    
    IndexedWord s1 = struc.getStack().peek();
    IndexedWord s2 = (stackSize > 1)? struc.getStack().get(stackSize-2) : null;
    IndexedWord s3 = (stackSize > 2)? struc.getStack().get(stackSize-3) : null;
    IndexedWord q1 = (inputQueue.size() > curQueue)? inputQueue.get(curQueue) : null;
    IndexedWord q2 = (inputQueue.size() > curQueue+1)? inputQueue.get(curQueue+1) : null;
    IndexedWord q3 = (inputQueue.size() > curQueue+2)? inputQueue.get(curQueue+2) : null;
    
    String s1Word = (s1==null)? null : s1.get(TextAnnotation.class);
    String s2Word = (s2==null)? null : s2.get(TextAnnotation.class);
    String s3Word = (s3==null)? null : s3.get(TextAnnotation.class);
    String q1Word = (q1==null)? null : q1.get(TextAnnotation.class);
    String q2Word = (q2==null)? null : q2.get(TextAnnotation.class);
    String q3Word = (q3==null)? null : q3.get(TextAnnotation.class);
    
    String s1POS = (s1==null)? null : s1.get(PartOfSpeechAnnotation.class);
    String s2POS = (s2==null)? null : s2.get(PartOfSpeechAnnotation.class);
    String s3POS = (s3==null)? null : s3.get(PartOfSpeechAnnotation.class);
    String q1POS = (q1==null)? null : q1.get(PartOfSpeechAnnotation.class);
    String q2POS = (q2==null)? null : q2.get(PartOfSpeechAnnotation.class);
    String q3POS = (q3==null)? null : q3.get(PartOfSpeechAnnotation.class);
    
    String s1Lemma = (s1==null)? null : s1.get(LemmaAnnotation.class);
    String s2Lemma = (s2==null)? null : s2.get(LemmaAnnotation.class);
    String s3Lemma = (s3==null)? null : s3.get(LemmaAnnotation.class);
    String q1Lemma = (q1==null)? null : q1.get(LemmaAnnotation.class);
    String q2Lemma = (q2==null)? null : q2.get(LemmaAnnotation.class);
    String q3Lemma = (q3==null)? null : q3.get(LemmaAnnotation.class);
    
    List<IndexedWord> s1Children = null;
    List<IndexedWord> s2Children = null;
    
    if(s1!=null && struc.getDependencyGraph().vertexSet().contains(s1)) s1Children = struc.getDependencyGraph().getChildList(s1);
    if(s2!=null && struc.getDependencyGraph().vertexSet().contains(s2)) s2Children = struc.getDependencyGraph().getChildList(s2);

    if(usePreAction) features.add(struc.getActionTrace().get(struc.getActionTrace().size()-1).toString()+"-preAct");
    
    if(useS1Word && s1!=null) features.add(s1Word+"-S1Word");
    if(useS2Word && s2!=null) features.add(s2Word+"-S2Word");
    if(useS3Word && s3!=null) features.add(s3Word+"-S3Word");
    if(useQ1Word && q1!=null) features.add(q1Word+"-Q1Word");
    if(useQ2Word && q2!=null) features.add(q2Word+"-Q2Word");
    if(useQ3Word && q3!=null) features.add(q3Word+"-Q3Word");

    if(useS1POS && s1!=null) features.add(s1POS+"-S1POS");
    if(useS2POS && s2!=null) features.add(s2POS+"-S2POS");
    if(useS3POS && s3!=null) features.add(s3POS+"-S3POS");
    if(useQ1POS && q1!=null) features.add(q1POS+"-Q1POS");
    if(useQ2POS && q2!=null) features.add(q2POS+"-Q2POS");
    if(useQ3POS && q3!=null) features.add(q3POS+"-Q3POS");
    
    if(useS1WordPOS && s1!=null) features.add(s1Word+"_"+s1POS+"-S1WordPOS");
    if(useS2WordPOS && s2!=null) features.add(s2Word+"_"+s2POS+"-S2WordPOS");
    if(useS3WordPOS && s3!=null) features.add(s3Word+"_"+s3POS+"-S3WordPOS");
    if(useQ1WordPOS && q1!=null) features.add(q1Word+"_"+q1POS+"-Q1WordPOS");
    if(useQ2WordPOS && q2!=null) features.add(q2Word+"_"+q2POS+"-Q2WordPOS");
    if(useQ3WordPOS && q3!=null) features.add(q3Word+"_"+q3POS+"-Q3WordPOS");   
    
    if(useS1Lemma && s1!=null) features.add(s1Lemma+"-S1Lemma");
    if(useS2Lemma && s2!=null) features.add(s2Lemma+"-S2Lemma");
    if(useS3Lemma && s3!=null) features.add(s3Lemma+"-S3Lemma");
    if(useQ1Lemma && q1!=null) features.add(q1Lemma+"-Q1Lemma");
    if(useQ2Lemma && q2!=null) features.add(q2Lemma+"-Q2Lemma");
    if(useQ3Lemma && q3!=null) features.add(q3Lemma+"-Q3Lemma");
    
    if(useS1Q1word && s1!=null && q1!=null) features.add(s1Word+"_"+q1Word+"-S1Q1Word");
    if(useQ1Q2word && q1!=null && q2!=null) features.add(q1Word+"_"+q2Word+"-Q1Q2Word");
    if(useS1S2word && s1!=null && s2!=null) features.add(s1Word+"_"+s2Word+"-S1S2Word");
    
    if(useS1Q1POS && s1!=null && q1!=null) features.add(s1POS+"_"+q1POS+"-S1Q1POS");
    if(useQ1Q2POS && q1!=null && q2!=null) features.add(q1POS+"_"+q2POS+"-Q1Q2POS");
    if(useS1S2POS && s1!=null && s2!=null) features.add(s1POS+"_"+s2POS+"-S1S2POS");
    
    if(useS1Q1WordPOS && s1!=null && q1!=null) features.add(s1Word+"_"+s1POS+"_"+q1Word+"_"+q1POS+"-S1Q1WordPOS");
    if(useQ1Q2WordPOS && q1!=null && q2!=null) features.add(q1Word+"_"+q1POS+"_"+q2Word+"_"+q2POS+"-Q1Q2WordPOS");
    if(useS1S2WordPOS && s1!=null && s2!=null) features.add(s1Word+"_"+s1POS+"_"+s2Word+"_"+s2POS+"-S1S2WordPOS");
    
    if(useS1NumChild && s1Children!=null) features.add(s1Children.size()+"-S1ChildNum");
    if(useS2NumChild && s2Children!=null) features.add(s2Children.size()+"-S2ChildNum");
    
    if(s1Children!=null) {
      int s1ChildNum = s1Children.size();
      if(useS1LeftChildPOS && s1ChildNum > 0) features.add(s1Children.get(0).get(PartOfSpeechAnnotation.class)+"-S1LeftChildPOS");
      if(useS1LeftChildRel && s1ChildNum > 0) features.add(struc.getDependencyGraph().reln(s1, s1Children.get(0))+"-S1LeftChildRel");
      if(useS1RightChildPOS && s1ChildNum>1) features.add(s1Children.get(s1ChildNum-1).get(PartOfSpeechAnnotation.class)+"-S1RightChildPOS");
      if(useS1RightChildRel && s1ChildNum>1) features.add(struc.getDependencyGraph().reln(s1, s1Children.get(s1ChildNum-1))+"-S1RightChildRel");
    }
    if(s2Children!=null) {
      int s2ChildNum = s2Children.size();
      if(useS2LeftChildPOS && s2ChildNum > 0) features.add(s2Children.get(0).get(PartOfSpeechAnnotation.class)+"-S2LeftChildPOS");
      if(useS2LeftChildRel && s2ChildNum > 0) features.add(struc.getDependencyGraph().reln(s2, s2Children.get(0))+"-S2LeftChildRel");
      if(useS2RightChildPOS && s2ChildNum>1) features.add(s2Children.get(s2ChildNum-1).get(PartOfSpeechAnnotation.class)+"-S2RightChildPOS");
      if(useS2RightChildRel && s2ChildNum>1) features.add(struc.getDependencyGraph().reln(s2, s2Children.get(s2ChildNum-1))+"-S2RightChildRel");
    }

    if(useS1PreviousTokenPOS && s1!=null) {
      int preTokenIdx = s1.get(IndexAnnotation.class) - 2;
      if(preTokenIdx >= 0) features.add(struc.getInput().get(preTokenIdx).get(PartOfSpeechAnnotation.class)+"-S1PreTokenPOS");
    }
    if(useS2NextTokenPOS && s2!=null) {
      int nextTokenIdx = s2.get(IndexAnnotation.class);
      features.add(struc.getInput().get(nextTokenIdx).get(PartOfSpeechAnnotation.class)+"-S2NextTokenPOS");
    }
    
    
    // TODO add features
    
    return features;
  }
}
