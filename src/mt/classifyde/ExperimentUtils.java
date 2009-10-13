package mt.classifyde;

import mt.train.transtb.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import java.io.*;
import java.util.*;

public class ExperimentUtils {
  static int TOPICALITY_SENT_WINDOW_SIZE = 2;

  static TreePattern dec = TreePattern.compile("DEC < 的");
  static TreePattern deg = TreePattern.compile("DEG < 的");
  static TreePattern de = TreePattern.compile("DEG|DEC < 的");
  static TreePattern va1 = TreePattern.compile("CP <, (IP <- (VP <: VA)) <- (DEC < 的)");
  static TreePattern va2 = TreePattern.compile("CP <, (IP <- (VP <, (ADVP $+ (VP <: VA)))) <- (DEC < 的)");
  static TreePattern adjpdeg = TreePattern.compile("DNP <, ADJP <- (DEG < 的)");
  static TreePattern qpdeg = TreePattern.compile("DNP <, QP <- (DEG < 的)");
  static TreePattern nppndeg = TreePattern.compile("DNP <, (NP < PN) <- (DEG < 的)");
  
  static void ReverseSublist(List<String> list, int start, int end) {
    if (start < 0 || start >= list.size() ||
        end < 0 || end >= list.size() ||
        start > end) {
      //System.err.println("Warning: No reverse");
      return;
    }

    while(start < end) {
      Swap(list, start, end);
      start++;
      end--;
    }
  }

  private static void Swap(List<String> list, int p1, int p2) {
    String tmp = list.get(p1);
    list.set(p1, list.get(p2));
    list.set(p2, tmp);
  }

  static Set<String> treeToSetWords(Tree tree) {
    Sentence<Word> sent = tree.yield();
    Set<String> sow = new HashSet<String>();
    for (Word w : sent) {
      sow.add(w.value());
    }
    return sow;
  }

  static Set<String> mergeAllSets(Queue<Set<String>> q) {
    Set<String> sow = new HashSet<String>();
    for (Set<String> set : q) {
      sow.addAll(set);
    }
    return sow;
  }

  public static Pair<Integer, Integer> getNPwithDERangeFromIdx(Tree tree, int deIdx) {
    Tree preT = Trees.getPreTerminal(tree, deIdx);
    if (preT==null) return new Pair<Integer,Integer>(-1, -1);
    Tree DNPorCP = preT.parent(tree);
    if (DNPorCP==null) return new Pair<Integer,Integer>(-1, -1);
    Tree theNP = DNPorCP.parent(tree);
    if (theNP==null) return new Pair<Integer,Integer>(-1, -1);
    int leftE = Trees.leftEdge(theNP, tree);
    int rightE = Trees.rightEdge(theNP, tree)-1;
    Pair<Integer,Integer> range = new Pair<Integer,Integer>(leftE, rightE);
    return range;
  }

  /* this is supposed to be a better reordering range.
     instead of everything before NP, only take DNP or CP
  */
  public static Pair<Integer, Integer> getNPwithDERangeFromIdx_DNPorCP(Tree tree, int deIdx) {
    Tree preT = Trees.getPreTerminal(tree, deIdx);
    Tree DNPorCP = preT.parent(tree);
    Tree theNP = DNPorCP.parent(tree);
    int leftE;
    int rightE = Trees.rightEdge(theNP, tree)-1;
    //System.err.println(DNPorCP.label());
    if (DNPorCP.label().toString().equals("DNP") ||DNPorCP.label().toString().equals("CP")) {
      //System.err.println("DNP or CP");
      leftE = Trees.leftEdge(DNPorCP, tree);
    } else {
      //System.err.println("NOT");
      leftE = Trees.leftEdge(theNP, tree);
    }
    Pair<Integer,Integer> range = new Pair<Integer,Integer>(leftE, rightE);
    return range;
  }

  public static void markRotatingDNPorCPinNP(Tree tree, int deIdx) {
    Tree preT = Trees.getPreTerminal(tree, deIdx);
    Tree DNPorCP = preT.parent(tree);
    Tree theNP = DNPorCP.parent(tree);
    if (!theNP.label().toString().equals("NP")) {
      return;
    }
    /*
    if (!DNPorCP.label().toString().equals("DNP") &&
        !DNPorCP.label().toString().equals("CP")) {
      return;
    }
    */

    StringBuilder newLabelV = new StringBuilder();
    newLabelV.append(DNPorCP.label().toString()).append("r");
    DNPorCP.label().setValue(newLabelV.toString());
  }

  static Tree processInternalDEReordering(Tree t) {
    // TODO: rotate DE and stuff
    //if (!t.value().endsWith("Pr") &&
    //    !t.value().endsWith("Pr")) {
    if (t.isLeaf() || !t.value().endsWith("r")) {
      throw new RuntimeException("t="+t+", only *Pr should be processed");
    }
    String tag = t.value();
    tag = tag.replaceAll("r$", "");
    t.label().setValue(tag);

    Tree newT = t.deepCopy();
    List<Tree> children = t.getChildrenAsList();
    int moveIdx = -1;
    for(int i = 0 ; i < children.size(); i++) {
      if (children.get(i).isPreTerminal()) {
        children.get(i).firstChild().value().startsWith("的_");
      }
      moveIdx = i;
    }
    if (moveIdx < 0) throw new RuntimeException("no internal 的_ ?");
    List<Tree> newChildren = new ArrayList<Tree>();
    newChildren.add(children.get(moveIdx));
    for(int i = 0 ; i < children.size(); i++) {
      if (i==moveIdx) continue;
      newChildren.add(children.get(i));
    }
    newT.setChildren(newChildren);
    return newT;
  }

  static String getNPwithDE_DNPorCPLabel(Tree tree, int deIdx) {
    Tree preT = Trees.getPreTerminal(tree, deIdx);
    Tree DNPorCP = preT.parent(tree);
    return DNPorCP.label().value();
  }

  static String getNPwithDE_rootLabel(Tree tree, int deIdx) {
    Tree preT = Trees.getPreTerminal(tree, deIdx);
    Tree DNPorCP = preT.parent(tree);
    Tree theNP = DNPorCP.parent(tree);
    return theNP.label().value();
  }

  static Tree getNPwithDE_rootTree(Tree tree, int deIdx) {
    Tree preT = Trees.getPreTerminal(tree, deIdx);
    Tree DNPorCP = preT.parent(tree);
    Tree theNP = DNPorCP.parent(tree);
    return theNP;
  }

  static Tree getNPwithDE_leftTree(Tree tree, int deIdx) {
    Tree preT = Trees.getPreTerminal(tree, deIdx);
    Tree DNPorCP = preT.parent(tree);
    if (DNPorCP.numChildren() != 2) return null;
    return DNPorCP.firstChild();
  }


  static List<Integer> getDEIndices(List<HasWord> sent) {
    List<Integer> des = new ArrayList<Integer>();
    for(int i = 0; i < sent.size(); i++) {
      if (sent.get(i).word().equals("的")) {
        des.add(i);
      }
    }
    return des;
  }

  static List<Integer> getMarkedDEIndices(List<HasWord> sent) {
    List<Integer> des = new ArrayList<Integer>();
    for(int i = 0; i < sent.size(); i++) {
      if (sent.get(i).word().startsWith("的_")) {
        des.add(i);
      }
    }
    return des;
  }

  /**
   * From a given tree, get the DE position by checking immediate children of the tree.
   * if there are multiple ones, the last one will be chosen
   */
  public static int getDEIndex(Tree t) {
    Tree[] children = t.children();
    int deIdx = -1;
    for (Tree c : children) {
      Sentence<Word> words = c.yield();
      String lastW = words.get(words.size()-1).word();
      if (lastW.equals("的")) {
        if (deIdx != -1) {
          //System.err.println("multi-DEs: ");
          //t.pennPrint(System.err);
        } else 
          deIdx = Trees.rightEdge(c, t)-1;
      }
    }
    //System.err.println("DEIDX="+deIdx+"\t"+t.toString());
    return deIdx;
  }


  public static Tree maskIrrelevantDEs(Tree tree, int deInTree) {
    Tree newTree = tree.deeperCopy();

    List<Tree> leaves = newTree.getLeaves();

    List<HasWord> words = new ArrayList<HasWord>();
    //for (Tree leaf : leaves) {
    if (!"的".equals(leaves.get(deInTree).value())) {
      newTree.pennPrint(System.err);
      System.err.println("deInTree = "+deInTree);
      System.err.println("leaves.get(deInTree).value()="+leaves.get(deInTree).value());
      throw new RuntimeException("deInTree should be a DE");
    }

    for (int idx = 0; idx < leaves.size(); idx++) {
      //if (idx==deInTree) continue;
      Tree leaf = leaves.get(idx);

      if (idx!=deInTree && "的".equals(leaf.value())) {
        words.add(new Word("X"));
      } else {
        words.add(new Word(leaf.value()));
      }
    }
    
    for (int i = 0; i < leaves.size(); i++) {
      leaves.get(i).setValue(words.get(i).word());
    }
    return newTree;
  }
  
  static boolean hasVApattern(Tree t) {
    TreeMatcher va1M = va1.matcher(t);
    TreeMatcher va2M = va2.matcher(t);
    return (va1M.find() || va2M.find());
  }

  static boolean hasADJPpattern(Tree t) {
    TreeMatcher adjpdegM = adjpdeg.matcher(t);
    return adjpdegM.find();
  }
  
  static boolean hasQPpattern(Tree t) {
    TreeMatcher qpdegM = qpdeg.matcher(t);
    return qpdegM.find();
  }

  static boolean hasNPPNpattern(Tree t) {
    TreeMatcher nppndegM = nppndeg.matcher(t);
    return nppndegM.find();
  }
  
  static boolean hasDEC(Tree npT, Tree wholeT, int deIdx) {
    return hasDE(npT, wholeT, deIdx, "DEC");
  }

  static boolean hasDEG(Tree npT, Tree wholeT, int deIdx) {
    return hasDE(npT, wholeT, deIdx, "DEG");
  }

  private static boolean hasDE(Tree npT, Tree wholeT, int deIdx, String dePat) {
    Sentence<TaggedWord> tws = wholeT.taggedYield();
    TaggedWord tw = tws.get(deIdx);
    if (tw.tag().startsWith("DE")) {
      if (tw.tag().equals(dePat)) return true;
      else return false;
    } else {
      System.err.println(tw + " (" + deIdx + ") in " + tws + " is not a DE");
      return false;
    }
  }

  static int countDE(Tree t) {
    TreeMatcher deM = de.matcher(t);
    int deCount = 0;
    while(deM.find()) {
      deCount++;
    }
    return deCount;
  }

  
	static List<Pair<String, String>>[] readFinalCategories(String categoryFile, String npFile, String fileidFile, String npidFile) throws IOException{
    String content = StringUtils.slurpFileNoExceptions(categoryFile);
    String[] categories = content.split("\\n");

    content = StringUtils.slurpFileNoExceptions(npFile);
    String[] nps = content.split("\\n");

    content = StringUtils.slurpFileNoExceptions(fileidFile);
    String[] fileids = content.split("\\n");

    content = StringUtils.slurpFileNoExceptions(npidFile);
    String[] npids = content.split("\\n");

    //List<Pair<String, String>>[][] result = new List[326][];
    @SuppressWarnings("unchecked")
    List<Pair<String, String>>[] result = new List[326];

    if (categories.length != nps.length ||
        nps.length != fileids.length ||
        fileids.length != npids.length)
      throw new RuntimeException("should have 4 equal length files");

    /*
    for(int i = 0; i < categories.length; i++) {
      int fileid = Integer.parseInt(fileids[i]);
      int npid = Integer.parseInt(npids[i]);
      if (maxNP[fileid] < npid) {
        maxNP[fileid] = npid;
      }
    }
    
    for(int i = 1; i <= 325; i++) {
      result[i] = new List[maxNP[i]+1];
      for(int j = 1; j <= maxNP[fileid]; j++) 
        result[i][j] = new ArrayList<Pair<String, String>>();
    }
    */
    for(int i = 1; i <= 325; i++) {
      result[i] = new ArrayList<Pair<String, String>>();
    }

    for(int i = 0; i < categories.length; i++) {
      Pair<String, String> pair = new Pair<String, String>(categories[i], nps[i]);
      int fileid = Integer.parseInt(fileids[i]);
      //result[fileid][npid].add(pair);
      result[fileid].add(pair);
    }
    return result;
  }


  static List<Pair<String, String>>[] readFinalCategories(String allFile) {
    return readFinalCategories(allFile, true);
  }

  public static List<Pair<String, String>>[] readFinalCategories(String allFile, Boolean useReducedCategories) {
    String content = StringUtils.slurpFileNoExceptions(allFile);
    String[] lines = content.split("\\n");

    @SuppressWarnings("unchecked")
    List<Pair<String, String>>[] result = new List[326];

    for(int i = 1; i <= 325; i++) {
      result[i] = new ArrayList<Pair<String, String>>();
    }

    for(int i = 0; i < lines.length; i++) {
      String[] fields = lines[i].split("\\t");
      if (fields.length != 4) {
        throw new RuntimeException("finalCategories_all.txt should have 4 fields: "+lines[i]);
      }
      String fileidStr = fields[0];
      String categoriesStr = fields[2];
      String npStr = fields[3];

      if (useReducedCategories) {
        categoriesStr = normCategory(categoriesStr);
      }
      
      Pair<String, String> pair = new Pair<String, String>(categoriesStr, npStr);
      fileidStr = fileidStr.replaceAll("[^\\d]","");
      int fileid = Integer.parseInt(fileidStr);
      //result[fileid][npid].add(pair);
      result[fileid].add(pair);
    }
    return result;
  }

  static String normCategory(String cat) {
    if (cat.equals("B of A")) {
      return "B prep A";
    }
    return cat;
  }

    
  static void resultSummary(TwoDimensionalCounter<String,String> confusionMatrix) {
    double totalNum = 0;
    double totalDenom = confusionMatrix.totalCount();
    for (String k : confusionMatrix.firstKeySet()) {
      double denom = confusionMatrix.totalCount(k);
      double num = confusionMatrix.getCount(k, k);
      totalNum += num;
      System.out.printf("#[ %s ] = %d |\tAcc:\t%.2f\n", k, (int)denom, 100.0*num/denom);
    }
    System.out.printf("#total = %d |\tAcc:\t%f\n", (int)totalDenom, 100.0*totalNum/totalDenom);    
  }

  public static String coarseCategory(String cat) {
    String normcat;
    if (cat.startsWith("B") || cat.equals("relative clause") ||
        cat.equals("swapped")) {
      normcat = "swapped";
    } else if (cat.startsWith("A") || cat.equals("no B") ||
               cat.equals("ordered")) {
      normcat = "ordered";
    } else if (cat.equals("multi-DEs") || cat.equals("other")) {
      normcat = "other";
    } else {
      throw new RuntimeException("Can't find coarse category for " + cat);
    }
    return normcat;
  }

  public static boolean is6class(String cat) {
    if ("no B".equals(cat)) return true;
    return ExperimentUtils.is5class(cat);
  }

  public static boolean is5class(String cat) {
    if ("A 's B".equals(cat) ||
        "A B".equals(cat) ||
        "A prep B".equals(cat) ||
        "B prep A".equals(cat) ||
        "relative clause".equals(cat)) {
      return true;
    }
    if ("no B".equals(cat) ||
        "multi-DEs".equals(cat) ||
        "other".equals(cat)) {
      return false;
    }
    throw new RuntimeException("the category ["+cat+"] is not valid in 'is5class'");
  }

  public static String short5class(String cat) {
    if ("no B".equals(cat)) 
      throw new RuntimeException("the category ["+cat+"] is not valid in 'is5class'");
    return ExperimentUtils.short6class(cat);
  }

  public static String short6class(String cat) {
    if ("A 's B".equals(cat)) return "AsB";
    if ("A B".equals(cat)) return "AB";
    if ("A prep B".equals(cat)) return "AprepB";
    if ("B prep A".equals(cat)) return "BprepA";
    if ("relative clause".equals(cat)) return "relc";
    if ("no B".equals(cat)) return "noB";
    throw new RuntimeException("the category ["+cat+"] is not valid in 'is6class'");
  }
    


  static void resultCoarseSummary(TwoDimensionalCounter<String,String> confusionMatrix) {
    TwoDimensionalCounter<String,String> cc = new TwoDimensionalCounter<String,String> ();
    
    for (Map.Entry<String,ClassicCounter<String>> k : confusionMatrix.entrySet()) {
      String k1 = k.getKey();
      ClassicCounter<String> k2 = k.getValue();
      String normK1 = coarseCategory(k1);
      for (String val : k2) {
        String normval = coarseCategory(val);
        double count = confusionMatrix.getCount(k1, val);
        cc.incrementCount(normK1, normval, count);
      }
    }
    
    resultSummary(cc);
  }

  public static List<String> readTrainDevTest(String trainDevTestFile) {
    String content = StringUtils.slurpFileNoExceptions(trainDevTestFile);
    String[] lines = content.split("\\n");
    List<String> ans = new ArrayList<String>();
    for (String line : lines)
      ans.add(line);

    return ans;
  }


  /** the default is to use
   * useReducedCategories=true (means "B of A" is maped to "B prep A"), and
   * useNonOracleTrees=false (means features are extracted from parsed trees, not gold-standard trees)
   **/
  public static List<AnnotatedTreePair> readAnnotatedTreePairs() throws IOException {
    return readAnnotatedTreePairs(true, false);
  }

  public static List<AnnotatedTreePair> readAnnotatedTreePairs(
    Boolean useReducedCategories) throws IOException {
    return readAnnotatedTreePairs(useReducedCategories, false);
  }

  public static List<AnnotatedTreePair> readAnnotatedTreePairs(
    Boolean useReducedCategories,
    Boolean useNonOracleTrees) throws IOException {
    List<TreePair> treepairs = TransTBUtils.readAnnotatedTreePairs(useReducedCategories, useNonOracleTrees);
    List<AnnotatedTreePair> atps = new ArrayList<AnnotatedTreePair>();

    // Open the hand-annotate file
    //String finalCategoriesFile = "C:\\cygwin\\home\\Pichuan Chang\\javanlp\\projects\\mt\\src\\mt\\classifyde\\data\\finalCategories_all.txt";

    String finalCategoriesFile =
      System.getenv("JAVANLP_HOME")+
      "/projects/mt/src/mt/classifyde/data/finalCategories_all.txt";

    List<Pair<String, String>>[] finalCategories = 
      ExperimentUtils.readFinalCategories(finalCategoriesFile, 
                                          useReducedCategories);

    for (TreePair tp : treepairs) {
      // Important: Read the categories of each NPwithDEs
      int fileid = tp.getFileID();
      AnnotatedTreePair atp = new AnnotatedTreePair(tp);
      AnnotatedTreePair.annotateNPwithDEs(finalCategories[fileid], atp);
      atps.add(atp);
    }
    return atps;
  }

  public static Tree getTreeWithEdges(Tree root, int leftEdge, int rightEdge) {
    Queue<Tree> queue = new LinkedList<Tree>();
    queue.add(root);
    if (leftEdge == 0 && rightEdge == root.yield().size()) {
      return root;
    }

    while(queue.size() > 0) {
      Tree t = queue.remove();
      Tree[] children = t.children();
      for (Tree c : children) {
        int left = Trees.leftEdge(c, root);
        int right = Trees.rightEdge(c, root);
        if (c.numChildren()==1) c = c.firstChild();

        if (left==leftEdge && right==rightEdge) {
          return c;
        }
        if (left <= leftEdge || right >= rightEdge) {
          queue.add(c);
        }
      }
    }
    return null;
  }

  static Tree getTreeWithEdges(List<Tree> ts, int leftEdge, int rightEdge) {
    int[] startIndices = new int[ts.size()];

    for(int i = 0; i < ts.size(); i++) {
      if (i==0) startIndices[i] = 0;
      else {
        startIndices[i] = startIndices[i-1]+ts.get(i-1).yield().size();
      }
    }

    int pickTreeIdx = -1;
    
    for(int i = 0; i < startIndices.length; i++) {
      if (leftEdge >= startIndices[i] && 
          (i==startIndices.length-1 || rightEdge <= startIndices[i+1])) {
        pickTreeIdx = i;
      }
    }
    if (pickTreeIdx == -1) {
      return null;
    }

    Tree root = ts.get(pickTreeIdx);
    Queue<Tree> queue = new LinkedList<Tree>();
    queue.add(root);

    while(queue.size() > 0) {
      Tree t = queue.remove();
      Tree[] children = t.children();
      for (Tree c : children) {
        int left = Trees.leftEdge(c, root);
        int right = Trees.rightEdge(c, root);

        if (left==leftEdge-startIndices[pickTreeIdx] && 
            right==rightEdge-startIndices[pickTreeIdx]) {
          return c;
        }
        if (left <= leftEdge-startIndices[pickTreeIdx] ||
            right >= rightEdge-startIndices[pickTreeIdx]) {
          queue.add(c);
        }
      }
    }
    return null;
  }

}
