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
      boolean markBegin = false;
      boolean markEnd = false;
      for (IntPair ip : deSpans) {
        if (ip.getSource() == i) markBegin = true;
        if (ip.getTarget() == i) markEnd = true;
      }
      if (markBegin) System.out.print("<b><u>");
      System.out.print(yield.get(i)+" ");
      if (markEnd) System.out.print("</u></b>");
    }
    System.out.println("<br />");
  }

  // print from the alignment.translation_ for now.
  // the trees haven't been fixed for cases like "can not" and "china 's"
  private static void printMarkedEnglishSentence(String[] enSent, TreeSet<Integer> marked) {
    for(int i = 0; i < enSent.length; i++) {
      if (marked.contains(i)) {
        System.out.print("<b><u>");
      }
      System.out.print(enSent[i]+" ");
      if (marked.contains(i)) {
        System.out.print("</u></b>");
      }
    }
    System.out.println("<br />");
  }
  
  public static void printAll(List<TreePair> tps) {
    TranslationAlignment.printAlignmentGridHeader();
    List<Set<Tree>> deTreesList = new ArrayList<Set<Tree>>();
    List<Set<IntPair>> deSpansList = new ArrayList<Set<IntPair>>();

    for(int i = 0; i < tps.size(); i++) {
      // Print Header of the HTML
      System.out.printf("[ <a href=#%d>%d</a> ]<br />", i+1, i+1);
      TreePair tp = tps.get(i);
      Tree chTree = tp.chTrees.get(0);
      Set<Tree> deTrees = getNPwithDESubTrees(chTree);
      Set<IntPair> deSpans = getSpans(deTrees, chTree);
      printMarkedChineseSentence(chTree.yield(), deSpans);

      TreeSet<Integer> englishMappedSpans = new TreeSet<Integer>();
      for (IntPair deSpan : deSpans) {
        TreeSet<Integer> enSpan = tp.alignment.mapChineseToEnglish(deSpan);
        englishMappedSpans.addAll(enSpan);
      }
      printMarkedEnglishSentence(tp.alignment.translation_, englishMappedSpans);

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
  }

  static IntPair getSpan(Tree subT, Tree allT) {
    IntPair ip;
    Sentence<HasWord> subyield = subT.yield();
    Sentence<HasWord> allyield = allT.yield();
    for(int i = 0; i < allyield.size(); i++) {
      if (allyield.get(i).equals(subyield.get(0))) {
        int length = subyield.size();
        if (allyield.get(i+length-1).equals(subyield.get(length-1))) {
          ip = new IntPair(i, i+length-1);
          return ip;
        }
      }
    }
    return null;
  }

  public static Set<IntPair> getSpans(Set<Tree> deTrees, Tree mainT) {
    Set<IntPair> ips = new HashSet<IntPair>();
    for (Tree deT : deTrees) {
      ips.add(getSpan(deT, mainT));
    }
    return ips;
  }
  
  public static Set<Tree> getNPwithDESubTrees(Tree t) {
    //List<IntPair> spans = new ArrayList<IntPair>();
    TreePattern p = TreePattern.compile("NP << (/P$/ < DEG|DEC)");
    System.err.println("Parsed pattern: " + p.pattern());
    TreeMatcher match = p.matcher(t);
    Set<Tree> matchedTrees = new HashSet<Tree>();
    while(match.find()) {
      matchedTrees.add(match.getMatch());
    }
    return matchedTrees;
    /*
    int count = 1;
    for (Tree mt : matchedTrees) {
      System.out.println("Tree #"+count);
      IntPair ip = getSpan(mt, t);
      System.out.println(ip);
      spans.add(ip);
      mt.pennPrint();
      count++;
    }
    System.out.println("</pre>");
    System.out.println("</font>");
    return spans;
    */
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
