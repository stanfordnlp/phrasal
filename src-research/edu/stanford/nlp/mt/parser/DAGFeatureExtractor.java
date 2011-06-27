package edu.stanford.nlp.mt.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.logging.Logger;

import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LeftChildrenNodeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.mt.parser.Actions.ActionType;
import edu.stanford.nlp.util.Pair;

public class DAGFeatureExtractor {

  // sk : k-th item in the stack from the top (s1, s2, ...)
  // qk : k-th item from the first element of queue (q1, q2, ...) q1: first item in the queue, q2: previous word of q1, q3: previous word of q2
  // tk : k-th item in the queue (t1, t2, ...) : q1 == t1 -> don't use t1
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
  private static final boolean useActionType = true;  // the current action type (for labeling)

  private static final boolean useS1Q1word = true;
  private static final boolean useS1Q1POS = true;
  private static final boolean useS1Q1WordPOS = true;

  private static final boolean useQ1Q2word = true;
  private static final boolean useQ1Q2POS = true;
  private static final boolean useQ1Q2WordPOS = true;

  private static final boolean useS1S2word = true;
  private static final boolean useS1S2POS = true;
  private static final boolean useS1S2WordPOS = true;

  // temporary features for analysis - begin

  private static final boolean useRightFeature = true;

  private static final boolean useT2Word = true;
  private static final boolean useT2Lemma = true;
  private static final boolean useT2POS = true;
  private static final boolean useT2WordPOS = true;

  private static final boolean useT3Word = true;
  private static final boolean useT3Lemma = true;
  private static final boolean useT3POS = true;
  private static final boolean useT3WordPOS = true;

  private static final boolean useT1T2word = true;
  private static final boolean useT1T2POS = true;
  private static final boolean useT1T2WordPOS = true;

  // temporary features for analysis - end


  // TODO : add flags for new features


  public static List<List<String>> extractActFeatures(Structure struc, int offset) {
    List<List<String>> features = new ArrayList<List<String>>();
    LinkedStack<CoreLabel> stack = struc.getStack();
    LinkedStack<CoreLabel> inputQueue = struc.getInput();
    if(stack.size()==0) return features;   // empty stack: always SHIFT
    int stackSize = stack.size();

    Object[] stackTopN = struc.getStack().peekN(3);
    CoreLabel s1 = (CoreLabel) stackTopN[0];
    CoreLabel s2 = (stackSize > 1)? (CoreLabel) stackTopN[1] : null;
    CoreLabel s3 = (stackSize > 2)? (CoreLabel) stackTopN[2] : null;
    int peekLen = inputQueue.size() - s1.get(IndexAnnotation.class) + 2;
    Object[] queueNWords = struc.getInput().peekN(peekLen);
    CoreLabel q1 = (queueNWords.length > 0)? (CoreLabel) queueNWords[offset-1] : null;
    CoreLabel q2 = (queueNWords.length > 1)? (CoreLabel) queueNWords[offset] : null;
    CoreLabel q3 = (queueNWords.length > 2)? (CoreLabel) queueNWords[offset+1] : null;

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

    // temporary features for analysis - begin

    if(useRightFeature){
      CoreLabel t2 = null;
      CoreLabel t3 = null;

      if(offset > 1) {
        t2 = (CoreLabel) queueNWords[offset-2];
        if(offset > 2) t3 = (CoreLabel) queueNWords[offset-3];
      }
      String t2Word = (t2==null)? null : t2.get(TextAnnotation.class);
      String t3Word = (t3==null)? null : t3.get(TextAnnotation.class);
      String t2POS = (t2==null)? null : t2.get(PartOfSpeechAnnotation.class);
      String t3POS = (t3==null)? null : t3.get(PartOfSpeechAnnotation.class);
      String t2Lemma = (t2==null)? null : t2.get(LemmaAnnotation.class);
      String t3Lemma = (t3==null)? null : t3.get(LemmaAnnotation.class);

      if(useT2Word && t2!=null) features.add(Arrays.asList(t2Word, "T2Word"));
      if(useT3Word && t3!=null) features.add(Arrays.asList(t3Word, "T3Word"));

      if(useT2POS && t2!=null) features.add(Arrays.asList(t2POS, "T2POS"));
      if(useT3POS && t3!=null) features.add(Arrays.asList(t3POS, "T3POS"));

      if(useT2Lemma && t2!=null) features.add(Arrays.asList(t2Lemma, "T2Lemma"));
      if(useT3Lemma && t3!=null) features.add(Arrays.asList(t3Lemma, "T3Lemma"));

      if(useT1T2word && q1!=null && t2!=null) features.add(Arrays.asList(q1Word, t2Word, "T1T2Word"));
      if(useT1T2POS && q1!=null && t2!=null) features.add(Arrays.asList(q1POS, t2POS, "T1T2POS"));
      if(useT1T2WordPOS && q1!=null && t2!=null) features.add(Arrays.asList(q1Word, q1POS, t2Word, t2POS, "T1T2WordPOS"));
    }
    // temporary features for analysis - end

    String preActionStr = "##"+struc.getActionTrace().peek().toString();

    SortedSet<Pair<CoreLabel, String>> s1Children = null;
    SortedSet<Pair<CoreLabel, String>> s2Children = null;

    if(s1 != null) s1Children = s1.get(LeftChildrenNodeAnnotation.class);
    if(s2 != null) s2Children = s2.get(LeftChildrenNodeAnnotation.class);

    if(usePreAction) features.add(Arrays.asList(preActionStr, "preAct"));

    if(useS1Word && s1!=null) features.add(Arrays.asList(s1Word, "S1Word"));
    if(useS2Word && s2!=null) features.add(Arrays.asList(s2Word, "S2Word"));
    if(useS3Word && s3!=null) features.add(Arrays.asList(s3Word, "S3Word"));
    if(useQ1Word && q1!=null) features.add(Arrays.asList(q1Word, "Q1Word"));
    if(useQ2Word && q2!=null) features.add(Arrays.asList(q2Word, "Q2Word"));
    if(useQ3Word && q3!=null) features.add(Arrays.asList(q3Word, "Q3Word"));

    if(useS1POS && s1!=null) features.add(Arrays.asList(s1POS, "S1POS"));
    if(useS2POS && s2!=null) features.add(Arrays.asList(s2POS, "S2POS"));
    if(useS3POS && s3!=null) features.add(Arrays.asList(s3POS, "S3POS"));
    if(useQ1POS && q1!=null) features.add(Arrays.asList(q1POS, "Q1POS"));
    if(useQ2POS && q2!=null) features.add(Arrays.asList(q2POS, "Q2POS"));
    if(useQ3POS && q3!=null) features.add(Arrays.asList(q3POS, "Q3POS"));

    if(useS1WordPOS && s1!=null) features.add(Arrays.asList(s1Word, s1POS, "S1WordPOS"));
    if(useS2WordPOS && s2!=null) features.add(Arrays.asList(s2Word, s2POS, "S2WordPOS"));
    if(useS3WordPOS && s3!=null) features.add(Arrays.asList(s3Word, s3POS, "S3WordPOS"));
    if(useQ1WordPOS && q1!=null) features.add(Arrays.asList(q1Word, q1POS, "Q1WordPOS"));
    if(useQ2WordPOS && q2!=null) features.add(Arrays.asList(q2Word, q2POS, "Q2WordPOS"));
    if(useQ3WordPOS && q3!=null) features.add(Arrays.asList(q3Word, q3POS, "Q3WordPOS"));

    if(useS1Lemma && s1!=null) features.add(Arrays.asList(s1Lemma, "S1Lemma"));
    if(useS2Lemma && s2!=null) features.add(Arrays.asList(s2Lemma, "S2Lemma"));
    if(useS3Lemma && s3!=null) features.add(Arrays.asList(s3Lemma, "S3Lemma"));
    if(useQ1Lemma && q1!=null) features.add(Arrays.asList(q1Lemma, "Q1Lemma"));
    if(useQ2Lemma && q2!=null) features.add(Arrays.asList(q2Lemma, "Q2Lemma"));
    if(useQ3Lemma && q3!=null) features.add(Arrays.asList(q3Lemma, "Q3Lemma"));

    if(useS1Q1word && s1!=null && q1!=null) features.add(Arrays.asList(s1Word, q1Word, "S1Q1Word"));
    if(useQ1Q2word && q1!=null && q2!=null) features.add(Arrays.asList(q1Word, q2Word, "Q1Q2Word"));
    if(useS1S2word && s1!=null && s2!=null) features.add(Arrays.asList(s1Word, s2Word, "S1S2Word"));

    if(useS1Q1POS && s1!=null && q1!=null) features.add(Arrays.asList(s1POS, q1POS, "S1Q1POS"));
    if(useQ1Q2POS && q1!=null && q2!=null) features.add(Arrays.asList(q1POS, q2POS, "Q1Q2POS"));
    if(useS1S2POS && s1!=null && s2!=null) features.add(Arrays.asList(s1POS, s2POS, "S1S2POS"));

    if(useS1Q1WordPOS && s1!=null && q1!=null) features.add(Arrays.asList(s1Word, s1POS, q1Word, q1POS, "S1Q1WordPOS"));
    if(useQ1Q2WordPOS && q1!=null && q2!=null) features.add(Arrays.asList(q1Word, q1POS, q2Word, q2POS, "Q1Q2WordPOS"));
    if(useS1S2WordPOS && s1!=null && s2!=null) features.add(Arrays.asList(s1Word, s1POS, s2Word, s2POS, "S1S2WordPOS"));

    if(useS1NumChild && s1Children!=null) {
      String childrenSize = "#"+s1Children.size();
      features.add(Arrays.asList(childrenSize, "S1ChildNum"));
    }
    if(useS2NumChild && s2Children!=null) {
      String childrenSize = "#"+s2Children.size();
      features.add(Arrays.asList(childrenSize, "S2ChildNum"));
    }

    if(s1Children!=null) {
      int s1ChildNum = s1Children.size();
      if(useS1LeftChildPOS && s1ChildNum > 0) {
        String leftChildPOS = s1Children.first().first().get(PartOfSpeechAnnotation.class);
        features.add(Arrays.asList(leftChildPOS, "S1LeftChildPOS"));
      }
      if(useS1LeftChildRel && s1ChildNum > 0) {
        String leftChildRel = s1Children.first().second();
        features.add(Arrays.asList(leftChildRel, "S1LeftChildRel"));
      }
      if(useS1RightChildPOS && s1ChildNum>1) {
        String rightChildPOS = s1Children.last().first().get(PartOfSpeechAnnotation.class);
        features.add(Arrays.asList(rightChildPOS, "S1RightChildPOS"));
      }
      if(useS1RightChildRel && s1ChildNum>1) {
        String rightChildRel = s1Children.last().second();
        features.add(Arrays.asList(rightChildRel, "S1RightChildRel"));
      }
    }
    if(s2Children!=null) {
      int s2ChildNum = s2Children.size();
      if(useS2LeftChildPOS && s2ChildNum > 0) {
        String leftChildPOS = s2Children.first().first().get(PartOfSpeechAnnotation.class);
        features.add(Arrays.asList(leftChildPOS, "S2LeftChildPOS"));
      }
      if(useS2LeftChildRel && s2ChildNum > 0) {
        String leftChildRel = s2Children.first().second();
        features.add(Arrays.asList(leftChildRel, "S2LeftChildRel"));
      }
      if(useS2RightChildPOS && s2ChildNum>1) {
        String rightChildPOS = s2Children.last().first().get(PartOfSpeechAnnotation.class);
        features.add(Arrays.asList(rightChildPOS, "S2RightChildPOS"));
      }
      if(useS2RightChildRel && s2ChildNum>1) {
        String rightChildRel = s2Children.last().second();
        features.add(Arrays.asList(rightChildRel, "S2RightChildRel"));
      }
    }

    if(useS1PreviousTokenPOS && s1!=null && queueNWords[peekLen-1] != null) {
      CoreLabel sn = (CoreLabel) queueNWords[peekLen-1];
      String preTokenPOS = sn.get(PartOfSpeechAnnotation.class);
      features.add(Arrays.asList(preTokenPOS, "S1PreTokenPOS"));
    }
    if(useS2NextTokenPOS && s2!=null) {
      // TODO: fix this to make it more efficient
      int nextTokenIdx = s2.get(IndexAnnotation.class);
      String nextTokenPOS;
      int inputSz = struc.getInput().size();
      if (inputSz-nextTokenIdx < queueNWords.length) {
        nextTokenPOS = ((CoreLabel)queueNWords[inputSz-nextTokenIdx]).get(PartOfSpeechAnnotation.class);
      } else {
        Object[] inputArr = struc.getInput().peekN(inputSz-nextTokenIdx);
        nextTokenPOS = ((CoreLabel)inputArr[inputArr.length-1]).get(PartOfSpeechAnnotation.class);
      }
      features.add(Arrays.asList(nextTokenPOS, "S2NextTokenPOS"));
    }


    // TODO add features


    return features;
  }

  /** Extracting features for labelClassifier:
   *    use all features of actClassifier and one additional feature, arcDirection
   *    */
  public static List<List<String>> extractLabelFeatures(
      ActionType action, Datum<ActionType, List<String>> actDatum, Structure s, int offset) {
    List<List<String>> features = new ArrayList<List<String>>();
    features.addAll(actDatum.asFeatures());
    features.add(Arrays.asList("##"+action.toString(), "actionType"));

    return features;
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
    if(useActionType) logger.fine("useActionType on"); else logger.fine("useActionType off");
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
