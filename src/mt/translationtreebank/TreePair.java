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

  public TreePair(TranslationAlignment alignment, List<Tree> enTrees, List<Tree> chTrees) {
    this.alignment = alignment;
    this.enTrees = enTrees;
    this.chTrees = chTrees;
  }

  public static void printTree(Tree t) {
    System.out.println("<pre>");
    t.pennPrint(System.out);
    System.out.println("</pre>");
  }

  private static void printMarkedChineseSentence(Sentence<HasWord> yield, Set<IntPair> deSpans) {
    for(int i = 0; i < yield.size(); i++) {
      int markBegin = 0;
      int markEnd = 0;
      for (IntPair ip : deSpans) {
        if (ip.getSource() == i) markBegin++;
        if (ip.getTarget() == i) markEnd++;
      }
      while (markBegin>0) {
        System.out.print("<b><u> [[ ");
        markBegin--;
      }
      System.out.print(yield.get(i)+" ");
      while (markEnd>0) {
        System.out.print(" ]] </u></b>");
        markEnd--;
      }
    }
    System.out.println("<br />");
  }

  private static void printMarkedEnglishSentence(String[] enSent, TreeSet<Integer> marked, TreeSet<Integer> nullAggregated) {
    for(int i = 0; i < enSent.length; i++) {
      if (marked.contains(i) || nullAggregated.contains(i)) {
        System.out.print("<b><u>");
      }
      if (nullAggregated.contains(i)) {
        System.out.print("<font style=\"BACKGROUND-COLOR: lightgreen\">");
      }
      System.out.print(enSent[i]+" ");
      if (nullAggregated.contains(i)) {
        System.out.print("</font>");
      }
      if (marked.contains(i) || nullAggregated.contains(i)) {
        System.out.print("</u></b>");
      }
    }
    System.out.println("<br />");
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
      Tree chTree = tp.chTrees.get(0);
      Set<Tree> deTrees = getNPwithDESubTrees(chTree);
      numNPwithDE += deTrees.size();
      Set<IntPair> deSpans = getSpans(deTrees, chTree);
      printMarkedChineseSentence(chTree.yield(), deSpans);

      TreeSet<Integer> englishMappedSpans = new TreeSet<Integer>();
      TreeSet<Integer> nullAggregatedSpan = new TreeSet<Integer>();
      for (IntPair deSpan : deSpans) {
        TreeSet<Integer> enSpan = tp.alignment.mapChineseToEnglish(deSpan);
        TreeSet<Integer> nullSpan = tp.alignment.mapChineseToEnglish_FillGap(deSpan, enSpan);
        englishMappedSpans.addAll(enSpan);
        nullAggregatedSpan.addAll(nullSpan);
      }
      printMarkedEnglishSentence(tp.alignment.translation_, englishMappedSpans, nullAggregatedSpan);

      // for later using when printing each TreePair
      deTreesList.add(deTrees);
      deSpansList.add(deSpans);
    }
    int counter = 1;
    for(int i = 0; i < tps.size(); i++) {
      TreePair tp = tps.get(i);
      Set<Tree> deTrees = deTreesList.get(i);
      Set<IntPair> deSpans = deSpansList.get(i);
      System.out.println("<hr>");
      System.out.printf("<a name=%d>\n", counter);
      System.out.printf("<h2>Sentence %d</h2>\n", counter);
      printTreePair(tp, deTrees, deSpans);
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
    TreePattern p = TreePattern.compile("NP <, (/P$/ < DEG|DEC )");
    //System.err.println("Parsed pattern: " + p.pattern());
    TreeMatcher match = p.matcher(t);
    Set<Tree> matchedTrees = new HashSet<Tree>();
    while(match.find()) {
      matchedTrees.add(match.getMatch());
    }
    return matchedTrees;
  }

  public static void printTreePair(TreePair tp, Set<Tree> deTrees, Set<IntPair> deSpans) {
    // (1.1) Chinese Tree
    System.out.println("<h3> Chinese Tree </h3>");
    printTree(tp.chTrees.get(0));
    
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

    // (2) English Tree
    System.out.println("<h3> English Tree </h3>");
    for (Tree t : tp.enTrees) {
      printTree(t);
    }

    // (3) Alignment Grid
    System.out.println("<h3> Alignment Grid </h3>");
    TranslationAlignment.printAlignmentGrid(tp.alignment);

  }
}
