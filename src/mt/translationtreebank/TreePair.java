package mt.translationtreebank;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.ling.*;
import java.util.*;

class TreePair {
  TranslationAlignment alignment;
  List<Tree> enTrees;
  List<Tree> chTrees;
  Map<IntPair, List<IntPair>> NPwithDEs;

  public TreePair(TranslationAlignment alignment, List<Tree> enTrees, List<Tree> chTrees) {
    this.alignment = alignment;
    this.enTrees = enTrees;
    this.chTrees = chTrees;
    computeNPwithDEs();
  }

  public static void printTree(Tree t) {
    System.out.println("<pre>");
    t.pennPrint(System.out);
    System.out.println("</pre>");
  }

  private void printMarkedChineseSentence() {
    String[] chSent = alignment.source_;
    for(int i = 0; i < chSent.length; i++) {
      int markBegin = 0;
      int markEnd = 0;
      for (IntPair ip : NPwithDEs.keySet()) {
        if (ip.getSource() == i) markBegin++;
        if (ip.getTarget() == i) markEnd++;
      }
      while (markBegin>0) {
        System.out.print("<b><u> [[ ");
        markBegin--;
      }
      System.out.print(chSent[i]+" ");
      while (markEnd>0) {
        System.out.print(" ]] </u></b>");
        markEnd--;
      }
    }
    System.out.println("<br />");
  }

  private void printMarkedEnglishSentence() {
    String[] enSent = alignment.translation_;
    Set<Integer> markedwords = new HashSet<Integer>();
    for (IntPair key : NPwithDEs.keySet()) {
      List<IntPair> ips = NPwithDEs.get(key);
      for (IntPair ip : ips) {
        for (int i = ip.getSource(); i <= ip.getTarget(); i++) {
          markedwords.add(i);
        }
      }
    }

    for(int i = 0; i < enSent.length; i++) {
      if (markedwords.contains(i)) {
        System.out.print("<b><u>");
      }
      System.out.print(enSent[i]+" ");
      if (markedwords.contains(i)) {
        System.out.print("</u></b>");
      }
    }
    System.out.println("<br />");
  }

  public void computeNPwithDEs() {
    if (NPwithDEs == null) {
      NPwithDEs = new HashMap<IntPair, List<IntPair>>();
      Tree chTree = chTrees.get(0);
      Set<Tree> deTrees = getNPwithDESubTrees(chTree);
      Set<IntPair> deSpans = getSpans(deTrees, chTree);

      for (IntPair deSpan : deSpans) {
        TreeSet<Integer> enSpan = alignment.mapChineseToEnglish(deSpan);
        TreeSet<Integer> nullSpan = alignment.mapChineseToEnglish_FillGap(deSpan, enSpan);
        // merge these 2
        enSpan.addAll(nullSpan);

        // compute IntPair
        int prevI = -1;
        int start = -1;
        List<IntPair> enTranslation = new ArrayList<IntPair>();
        int last = -1;
        for (int en : enSpan) {
          if (start == -1) {
            start = en;
          }
          if (prevI != -1 && en > prevI+1) {
            enTranslation.add(new IntPair(start, prevI));
            start = en;
          }
          prevI = en;
          last = en;
        }
        // add last one
        if (start != -1) {
          enTranslation.add(new IntPair(start, last));
        }

        NPwithDEs.put(deSpan, enTranslation);
      }
    }
  }

  
  // return the number of NP with DEs in this list of TreePair
  public static int printAllwithDE(List<TreePair> tps) {
    int numNPwithDE = 0;

    TranslationAlignment.printAlignmentGridHeader();
    List<Set<Tree>> deTreesList = new ArrayList<Set<Tree>>();
    List<Set<IntPair>> deSpansList = new ArrayList<Set<IntPair>>();

    for(int i = 0; i < tps.size(); i++) {
      // Print Header of the HTML
      System.out.printf("[ <a href=#%d>%d</a> ]<br />", i+1, i+1);
      TreePair tp = tps.get(i);

      tp.printMarkedChineseSentence();
      tp.printMarkedEnglishSentence();

    }


    int counter = 1;
    for(int i = 0; i < tps.size(); i++) {
      TreePair tp = tps.get(i);
      System.out.println("<hr>");
      System.out.printf("<a name=%d>\n", counter);
      System.out.printf("<h2>Sentence %d</h2>\n", counter);
      tp.printTreePair();
      tp.printNPwithDEs();
      tp.printAlignmentGrid();
      counter++;
    }
    TranslationAlignment.printAlignmentGridBottom();
    return numNPwithDE;
  }

  static IntPair getSpan(Tree subT, Tree allT) {
    IntPair ip = new IntPair(
      Trees.leftEdge(subT, allT),
      Trees.rightEdge(subT, allT)-1);
    return ip;
  }

  public static Set<IntPair> getSpans(Set<Tree> deTrees, Tree mainT) {
    Set<IntPair> ips = new HashSet<IntPair>();
    for (Tree deT : deTrees) {
      ips.add(getSpan(deT, mainT));
    }
    return ips;
  }
  
  public static Set<Tree> getNPwithDESubTrees(Tree t) {
    TreePattern p = TreePattern.compile("NP <, (/P$/ < (DEG|DEC < 的))");
    TreeMatcher match = p.matcher(t);
    Set<Tree> matchedTrees = new HashSet<Tree>();
    while(match.find()) {
      matchedTrees.add(match.getMatch());
    }
    return matchedTrees;
  }

  public void printNPwithDEs() {
    // print out NPs
    int npcount = 1;
    for (IntPair NPwithDE : NPwithDEs.keySet()) {
      List<IntPair> englishNP = NPwithDEs.get(NPwithDE);
      System.out.printf("NP #%d:\t", npcount++);
      if (englishNP.size()==1) {
        System.out.printf("<font color=\"blue\">[Contiguous]</font>\t");
      } else {
        System.out.printf("<font color=\"red\">[Fragmented]</font>\t");
      }
      for(int chi = NPwithDE.getSource(); chi <= NPwithDE.getTarget(); chi++) {
        System.out.print(alignment.source_[chi]+" ");
      }
      System.out.println("<br />");
    }
  }

  public void printTreePair() {
    // (1.1) Chinese Tree
    System.out.println("<h3> Chinese Tree </h3>");
    printTree(chTrees.get(0));
    
    // TODO: this information doesn't exist in TreePair for now
    /*
    // (1.2) print DE trees
    int count = 1;
    System.out.println("<font color=\"red\">");
    System.out.println("<pre>");
    for (Tree mt : deTrees) {
      System.out.println("Tree #"+count);
      mt.pennPrint();
      count++;
    }
    System.out.println("</pre>");
    System.out.println("</font>");
    */

    // (2) English Tree
    System.out.println("<h3> English Tree </h3>");
    for (Tree t : enTrees) {
      printTree(t);
    }

  }

  public void printAlignmentGrid() {
    System.out.println("<h3> Alignment Grid </h3>");
    TranslationAlignment.printAlignmentGrid(alignment);
  }
}
