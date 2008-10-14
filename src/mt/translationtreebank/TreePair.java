package mt.translationtreebank;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;
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

  public void printTree(Tree t) {
    System.out.println("<pre>");
    t.pennPrint(System.out);
    System.out.println("</pre>");
  }
  
  public static void printAll(List<TreePair> tps) {
    TranslationAlignment.printAlignmentGridHeader();
    for(int i = 0; i < tps.size(); i++) {
      System.out.printf("[ <a href=#%d>%d</a> ]\t", i+1, i+1);
      TreePair tp = tps.get(i);
      System.out.println(StringUtils.join(tp.chTrees.get(0).yield(), " ")+"<br />");
    }
    int counter = 1;
    for(TreePair tp : tps) {
      System.out.println("<hr>");
      System.out.printf("<a name=%d>\n", counter);
      System.out.printf("<h2>Sentence %d</h2>\n", counter);
      tp.print();
      counter++;
    }
    TranslationAlignment.printAlignmentGridBottom();
  }

  public void print() {
    // Chinese Tree
    System.out.println("<h3> Chinese Tree </h3>");
    printTree(chTrees.get(0));
    System.out.println("<h3> English Tree </h3>");
    for (Tree t : enTrees) {
      printTree(t);
    }
    System.out.println("<h3> Alignment Grid </h3>");
    TranslationAlignment.printAlignmentGrid(alignment);
  }
}
