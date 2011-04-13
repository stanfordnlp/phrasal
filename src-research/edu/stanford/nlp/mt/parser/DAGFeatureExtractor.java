package edu.stanford.nlp.mt.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.logging.Logger;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

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
  private static final boolean useS1Q1WordPOS = true;
  
  private static final boolean useQ1Q2word = true;
  private static final boolean useQ1Q2POS = true;
  private static final boolean useQ1Q2WordPOS = true;
  
  private static final boolean useS1S2word = true;
  private static final boolean useS1S2POS = true;
  private static final boolean useS1S2WordPOS = true;
  
  
  // TODO : add flags for new features  


  public static List<List<Integer>> extractFeatures(Structure struc, Index<String> strIndex) {
    List<List<Integer>> features = new ArrayList<List<Integer>>();
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
    
    String preActionStr = "##"+struc.getActionTrace().get(struc.getActionTrace().size()-1).toString();
    
    List<IndexedWord> s1Children = null;
    List<IndexedWord> s2Children = null;
    
    if(s1!=null && struc.getDependencyGraph().vertexSet().contains(s1)) s1Children = struc.getDependencyGraph().getChildList(s1);
    if(s2!=null && struc.getDependencyGraph().vertexSet().contains(s2)) s2Children = struc.getDependencyGraph().getChildList(s2);
    
    // add strings to strIndex
    strIndex.addAll(Arrays.asList(s1Word, s2Word, s3Word, q1Word, q2Word, q3Word, s1POS, s2POS, s3POS, q1POS, q2POS, q3POS, 
        s1Lemma, s2Lemma, s3Lemma, q1Lemma, q2Lemma, q3Lemma, preActionStr
    ));

    if(usePreAction) features.add(Arrays.asList(strIndex.indexOf(preActionStr), strIndex.indexOf("preAct")));
    
    if(useS1Word && s1!=null) features.add(Arrays.asList(strIndex.indexOf(s1Word), strIndex.indexOf("S1Word")));
    if(useS2Word && s2!=null) features.add(Arrays.asList(strIndex.indexOf(s2Word), strIndex.indexOf("S2Word")));
    if(useS3Word && s3!=null) features.add(Arrays.asList(strIndex.indexOf(s3Word), strIndex.indexOf("S3Word")));
    if(useQ1Word && q1!=null) features.add(Arrays.asList(strIndex.indexOf(q1Word), strIndex.indexOf("Q1Word")));
    if(useQ2Word && q2!=null) features.add(Arrays.asList(strIndex.indexOf(q2Word), strIndex.indexOf("Q2Word")));
    if(useQ3Word && q3!=null) features.add(Arrays.asList(strIndex.indexOf(q3Word), strIndex.indexOf("Q3Word")));

    if(useS1POS && s1!=null) features.add(Arrays.asList(strIndex.indexOf(s1POS), strIndex.indexOf("S1POS")));
    if(useS2POS && s2!=null) features.add(Arrays.asList(strIndex.indexOf(s2POS), strIndex.indexOf("S2POS")));
    if(useS3POS && s3!=null) features.add(Arrays.asList(strIndex.indexOf(s3POS), strIndex.indexOf("S3POS")));
    if(useQ1POS && q1!=null) features.add(Arrays.asList(strIndex.indexOf(q1POS), strIndex.indexOf("Q1POS")));
    if(useQ2POS && q2!=null) features.add(Arrays.asList(strIndex.indexOf(q2POS), strIndex.indexOf("Q2POS")));
    if(useQ3POS && q3!=null) features.add(Arrays.asList(strIndex.indexOf(q3POS), strIndex.indexOf("Q3POS")));
    
    if(useS1WordPOS && s1!=null) features.add(Arrays.asList(strIndex.indexOf(s1Word), strIndex.indexOf(s1POS), strIndex.indexOf("S1WordPOS")));
    if(useS2WordPOS && s2!=null) features.add(Arrays.asList(strIndex.indexOf(s2Word), strIndex.indexOf(s2POS), strIndex.indexOf("S2WordPOS")));
    if(useS3WordPOS && s3!=null) features.add(Arrays.asList(strIndex.indexOf(s3Word), strIndex.indexOf(s3POS), strIndex.indexOf("S3WordPOS")));
    if(useQ1WordPOS && q1!=null) features.add(Arrays.asList(strIndex.indexOf(q1Word), strIndex.indexOf(q1POS), strIndex.indexOf("Q1WordPOS")));
    if(useQ2WordPOS && q2!=null) features.add(Arrays.asList(strIndex.indexOf(q2Word), strIndex.indexOf(q2POS), strIndex.indexOf("Q2WordPOS")));
    if(useQ3WordPOS && q3!=null) features.add(Arrays.asList(strIndex.indexOf(q3Word), strIndex.indexOf(q3POS), strIndex.indexOf("Q3WordPOS")));   
    
    if(useS1Lemma && s1!=null) features.add(Arrays.asList(strIndex.indexOf(s1Lemma), strIndex.indexOf("S1Lemma")));
    if(useS2Lemma && s2!=null) features.add(Arrays.asList(strIndex.indexOf(s2Lemma), strIndex.indexOf("S2Lemma")));
    if(useS3Lemma && s3!=null) features.add(Arrays.asList(strIndex.indexOf(s3Lemma), strIndex.indexOf("S3Lemma")));
    if(useQ1Lemma && q1!=null) features.add(Arrays.asList(strIndex.indexOf(q1Lemma), strIndex.indexOf("Q1Lemma")));
    if(useQ2Lemma && q2!=null) features.add(Arrays.asList(strIndex.indexOf(q2Lemma), strIndex.indexOf("Q2Lemma")));
    if(useQ3Lemma && q3!=null) features.add(Arrays.asList(strIndex.indexOf(q3Lemma), strIndex.indexOf("Q3Lemma")));
    
    if(useS1Q1word && s1!=null && q1!=null) features.add(Arrays.asList(strIndex.indexOf(s1Word), strIndex.indexOf(q1Word), strIndex.indexOf("S1Q1Word")));
    if(useQ1Q2word && q1!=null && q2!=null) features.add(Arrays.asList(strIndex.indexOf(q1Word), strIndex.indexOf(q2Word), strIndex.indexOf("Q1Q2Word")));
    if(useS1S2word && s1!=null && s2!=null) features.add(Arrays.asList(strIndex.indexOf(s1Word), strIndex.indexOf(s2Word), strIndex.indexOf("S1S2Word")));
    
    if(useS1Q1POS && s1!=null && q1!=null) features.add(Arrays.asList(strIndex.indexOf(s1POS), strIndex.indexOf(q1POS), strIndex.indexOf("S1Q1POS")));
    if(useQ1Q2POS && q1!=null && q2!=null) features.add(Arrays.asList(strIndex.indexOf(q1POS), strIndex.indexOf(q2POS), strIndex.indexOf("Q1Q2POS")));
    if(useS1S2POS && s1!=null && s2!=null) features.add(Arrays.asList(strIndex.indexOf(s1POS), strIndex.indexOf(s2POS), strIndex.indexOf("S1S2POS")));
    
    if(useS1Q1WordPOS && s1!=null && q1!=null) features.add(Arrays.asList(strIndex.indexOf(s1Word), strIndex.indexOf(s1POS), strIndex.indexOf(q1Word), strIndex.indexOf(q1POS), strIndex.indexOf("S1Q1WordPOS")));
    if(useQ1Q2WordPOS && q1!=null && q2!=null) features.add(Arrays.asList(strIndex.indexOf(q1Word), strIndex.indexOf(q1POS), strIndex.indexOf(q2Word), strIndex.indexOf(q2POS), strIndex.indexOf("Q1Q2WordPOS")));
    if(useS1S2WordPOS && s1!=null && s2!=null) features.add(Arrays.asList(strIndex.indexOf(s1Word), strIndex.indexOf(s1POS), strIndex.indexOf(s2Word), strIndex.indexOf(s2POS), strIndex.indexOf("S1S2WordPOS")));
    
    if(useS1NumChild && s1Children!=null) {
      String childrenSize = "#"+s1Children.size(); 
      strIndex.add(childrenSize);
      features.add(Arrays.asList(strIndex.indexOf(childrenSize), strIndex.indexOf("S1ChildNum")));
    }
    if(useS2NumChild && s2Children!=null) {
      String childrenSize = "#"+s2Children.size(); 
      strIndex.add(childrenSize);
      features.add(Arrays.asList(strIndex.indexOf(childrenSize), strIndex.indexOf("S2ChildNum")));
    }
    
    if(s1Children!=null) {
      int s1ChildNum = s1Children.size();
      if(useS1LeftChildPOS && s1ChildNum > 0) {
        String leftChildPOS = s1Children.get(0).get(PartOfSpeechAnnotation.class);
        strIndex.add(leftChildPOS);
        features.add(Arrays.asList(strIndex.indexOf(leftChildPOS), strIndex.indexOf("S1LeftChildPOS")));
      }
      if(useS1LeftChildRel && s1ChildNum > 0) {
        String leftChildRel = struc.getDependencyGraph().reln(s1, s1Children.get(0)).toString();
        strIndex.add(leftChildRel);
        features.add(Arrays.asList(strIndex.indexOf(leftChildRel), strIndex.indexOf("S1LeftChildRel")));
      }
      if(useS1RightChildPOS && s1ChildNum>1) {
        String rightChildPOS = s1Children.get(s1ChildNum-1).get(PartOfSpeechAnnotation.class);
        strIndex.add(rightChildPOS);
        features.add(Arrays.asList(strIndex.indexOf(rightChildPOS), strIndex.indexOf("S1RightChildPOS")));
      }
      if(useS1RightChildRel && s1ChildNum>1) {
        String rightChildRel = struc.getDependencyGraph().reln(s1, s1Children.get(s1ChildNum-1)).toString();
        strIndex.add(rightChildRel);
        features.add(Arrays.asList(strIndex.indexOf(rightChildRel), strIndex.indexOf("S1RightChildRel")));
      }
    }
    if(s2Children!=null) {
      int s2ChildNum = s2Children.size();
      if(useS2LeftChildPOS && s2ChildNum > 0) {
        String leftChildPOS = s2Children.get(0).get(PartOfSpeechAnnotation.class);
        strIndex.add(leftChildPOS);
        features.add(Arrays.asList(strIndex.indexOf(leftChildPOS), strIndex.indexOf("S2LeftChildPOS")));
      }
      if(useS2LeftChildRel && s2ChildNum > 0) {
        String leftChildRel = struc.getDependencyGraph().reln(s2, s2Children.get(0)).toString();
        strIndex.add(leftChildRel);
        features.add(Arrays.asList(strIndex.indexOf(leftChildRel), strIndex.indexOf("S2LeftChildRel")));
      }
      if(useS2RightChildPOS && s2ChildNum>1) {
        String rightChildPOS = s2Children.get(s2ChildNum-1).get(PartOfSpeechAnnotation.class);
        strIndex.add(rightChildPOS);
        features.add(Arrays.asList(strIndex.indexOf(rightChildPOS), strIndex.indexOf("S2RightChildPOS")));
      }
      if(useS2RightChildRel && s2ChildNum>1) {
        String rightChildRel = struc.getDependencyGraph().reln(s2, s2Children.get(s2ChildNum-1)).toString();
        strIndex.add(rightChildRel);
        features.add(Arrays.asList(strIndex.indexOf(rightChildRel), strIndex.indexOf("S2RightChildRel")));
      }
    }

    if(useS1PreviousTokenPOS && s1!=null) {
      int preTokenIdx = s1.get(IndexAnnotation.class) - 2;
      if(preTokenIdx >= 0) {
        String preTokenPOS = struc.getInput().get(preTokenIdx).get(PartOfSpeechAnnotation.class);
        strIndex.add(preTokenPOS);
        features.add(Arrays.asList(strIndex.indexOf(preTokenPOS), strIndex.indexOf("S1PreTokenPOS")));
      }
    }
    if(useS2NextTokenPOS && s2!=null) {
      int nextTokenIdx = s2.get(IndexAnnotation.class);
      String nextTokenPOS = struc.getInput().get(nextTokenIdx).get(PartOfSpeechAnnotation.class);
      strIndex.add(nextTokenPOS);
      features.add(Arrays.asList(strIndex.indexOf(nextTokenPOS), strIndex.indexOf("S2NextTokenPOS")));
    }
    
    
    // TODO add features
    
    return features;
  }


  public static void setStrIndex(Index<String> strIndex) {
    strIndex.addAll(Arrays.asList(
        "preAct",
        "S1ChildNum", "S2ChildNum",
        "S1LeftChildPOS", "S1LeftChildRel", "S1RightChildPOS", "S1RightChildRel",
        "S2LeftChildPOS", "S2LeftChildRel", "S2RightChildPOS", "S2RightChildRel",
        "S1PreTokenPOS", "S2NextTokenPOS",
        "S1Word", "S2Word", "S3Word",
        "Q1Word", "Q2Word", "Q3Word",
        "S1POS", "S2POS", "S3POS",
        "Q1POS", "Q2POS", "Q3POS",
        "S1Lemma", "S2Lemma", "S3Lemma",
        "Q1Lemma", "Q2Lemma", "Q3Lemma",
        "S1WordPOS", "S2WordPOS", "S3WordPOS",
        "Q1WordPOS", "Q2WordPOS", "Q3WordPOS",
        "S1Q1Word", "Q1Q2Word", "S1S2Word",
        "S1Q1POS", "Q1Q2POS", "S1S2POS",
        "S1Q1WordPOS", "Q1Q2WordPOS", "S1S2WordPOS"
        ));
  }


  public static void printFeatureFlags(Logger logger) {
    if(useS1Word) logger.fine("useS1Word on"); else logger.fine("useS1Word off");
    if(useS1Lemma) logger.fine("useS1Lemma on"); else logger.fine("useS1Lemma off");
    if(useS1POS) logger.fine("useS1POS on"); else logger.fine("useS1POS off");
    if(useS1WordPOS) logger.fine("useS1WordPOS on"); else logger.fine("useS1WordPOS off");
    if(useS1NumChild) logger.fine("useS1NumChild on"); else logger.fine("useS1NumChild off");
    if(useS1LeftChildPOS) logger.fine("useS1LeftChildPOS on"); else logger.fine("useS1LeftChildPOS off");
    if(useS1LeftChildRel) logger.fine("useS1LeftChildRel on"); else logger.fine("useS1LeftChildRel off");
    if(useS1RightChildPOS) logger.fine("useS1RightChildPOS on"); else logger.fine("useS1RightChildPOS off");
    if(useS1RightChildRel) logger.fine("useS1RightChildRel on"); else logger.fine("useS1RightChildRel off");
    if(useS1PreviousTokenPOS) logger.fine("useS1PreviousTokenPOS on"); else logger.fine("useS1PreviousTokenPOS off");
    if(useS2Word) logger.fine("useS2Word on"); else logger.fine("useS2Word off");
    if(useS2Lemma) logger.fine("useS2Lemma on"); else logger.fine("useS2Lemma off");
    if(useS2POS) logger.fine("useS2POS on"); else logger.fine("useS2POS off");
    if(useS2WordPOS) logger.fine("useS2WordPOS on"); else logger.fine("useS2WordPOS off");
    if(useS2NumChild) logger.fine("useS2NumChild on"); else logger.fine("useS2NumChild off");
    if(useS2LeftChildPOS) logger.fine("useS2LeftChildPOS on"); else logger.fine("useS2LeftChildPOS off");
    if(useS2LeftChildRel) logger.fine("useS2LeftChildRel on"); else logger.fine("useS2LeftChildRel off");
    if(useS2RightChildPOS) logger.fine("useS2RightChildPOS on"); else logger.fine("useS2RightChildPOS off");
    if(useS2RightChildRel) logger.fine("useS2RightChildRel on"); else logger.fine("useS2RightChildRel off");
    if(useS2NextTokenPOS) logger.fine("useS2NextTokenPOS on"); else logger.fine("useS2NextTokenPOS off");
    if(useS3Word) logger.fine("useS3Word on"); else logger.fine("useS3Word off");
    if(useS3Lemma) logger.fine("useS3Lemma on"); else logger.fine("useS3Lemma off");
    if(useS3POS) logger.fine("useS3POS on"); else logger.fine("useS3POS off");
    if(useS3WordPOS) logger.fine("useS3WordPOS on"); else logger.fine("useS3WordPOS off");
    if(useQ1Word) logger.fine("useQ1Word on"); else logger.fine("useQ1Word off");
    if(useQ1Lemma) logger.fine("useQ1Lemma on"); else logger.fine("useQ1Lemma off");
    if(useQ1POS) logger.fine("useQ1POS on"); else logger.fine("useQ1POS off");
    if(useQ1WordPOS) logger.fine("useQ1WordPOS on"); else logger.fine("useQ1WordPOS off");
    if(useQ2Word) logger.fine("useQ2Word on"); else logger.fine("useQ2Word off");
    if(useQ2Lemma) logger.fine("useQ2Lemma on"); else logger.fine("useQ2Lemma off");
    if(useQ2POS) logger.fine("useQ2POS on"); else logger.fine("useQ2POS off");
    if(useQ2WordPOS) logger.fine("useQ2WordPOS on"); else logger.fine("useQ2WordPOS off");
    if(useQ3Word) logger.fine("useQ3Word on"); else logger.fine("useQ3Word off");
    if(useQ3Lemma) logger.fine("useQ3Lemma on"); else logger.fine("useQ3Lemma off");
    if(useQ3POS) logger.fine("useQ3POS on"); else logger.fine("useQ3POS off");
    if(useQ3WordPOS) logger.fine("useQ3WordPOS on"); else logger.fine("useQ3WordPOS off");
    if(usePreAction) logger.fine("usePreAction on"); else logger.fine("usePreAction off");
    if(useS1Q1word) logger.fine("useS1Q1word on"); else logger.fine("useS1Q1word off");
    if(useS1Q1POS) logger.fine("useS1Q1POS on"); else logger.fine("useS1Q1POS off");
    if(useS1Q1WordPOS) logger.fine("useS1Q1WordPOS on"); else logger.fine("useS1Q1WordPOS off");
    if(useQ1Q2word) logger.fine("useQ1Q2word on"); else logger.fine("useQ1Q2word off");
    if(useQ1Q2POS) logger.fine("useQ1Q2POS on"); else logger.fine("useQ1Q2POS off");
    if(useQ1Q2WordPOS) logger.fine("useQ1Q2WordPOS on"); else logger.fine("useQ1Q2WordPOS off");
    if(useS1S2word) logger.fine("useS1S2word on"); else logger.fine("useS1S2word off");
    if(useS1S2POS) logger.fine("useS1S2POS on"); else logger.fine("useS1S2POS off");
    if(useS1S2WordPOS) logger.fine("useS1S2WordPOS on"); else logger.fine("useS1S2WordPOS off");
  }
}
