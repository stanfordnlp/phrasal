package edu.stanford.nlp.mt.classifyde;

import java.util.*;

import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.trees.international.pennchinese.ChineseGrammaticalStructure;
import edu.stanford.nlp.trees.tregex.TreeMatcher;
import edu.stanford.nlp.trees.tregex.TreePattern;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Filters;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.mt.train.transtb.AlignmentUtils;
import edu.stanford.nlp.mt.train.transtb.TranslationAlignment;
import edu.stanford.nlp.mt.train.transtb.TreePair;


/**
 * a wrapper class to add DE information to TreePairs
 *
 * @author Pi-Chuan Chang
 */

public class AnnotatedTreePair {
  private TreePair treepair_;
  public Map<Integer, String> NPwithDEs_categories;
  public Map<Integer, Pair<Integer,Integer>> NPwithDEs_deIdx;
  public TreeSet<Integer> NPwithDEs_deIdx_set;
  public Map<Integer, Pair<Integer,Integer>> parsedNPwithDEs_deIdx;
  private Map<Pair<Integer,Integer>, List<Pair<Integer,Integer>>> NPwithDEs;

  public AnnotatedTreePair(TreePair tp) {
    this.treepair_ = tp;
    this.NPwithDEs_categories = new TreeMap<Integer, String>();
    computeNPwithDEs();
    computeParsedNPwithDEs();
  }

  public List<Pair<Integer,Integer>> getNPEnglishTranslation(int deIdx) {
    Pair<Integer,Integer> chNPrange = NPwithDEs_deIdx.get(deIdx);
    return NPwithDEs.get(chNPrange);
  }

  public int numNPwithDE() {
    if (NPwithDEs.size() != NPwithDEs_deIdx.size() ||
        NPwithDEs_deIdx.size() != NPwithDEs_deIdx_set.size() ||
        (parsedNPwithDEs_deIdx != null && NPwithDEs_deIdx_set.size() != parsedNPwithDEs_deIdx.size())) {
      System.err.println("NPwithDEs.size="+NPwithDEs.size());
      System.err.println("NPwithDEs_categories.size="+NPwithDEs_categories.size());
      System.err.println("NPwithDEs_deIdx.size="+NPwithDEs_deIdx.size());
      System.err.println("NPwithDEs_deIdx_set.size="+NPwithDEs_deIdx_set.size());
      System.err.println("parsedNPwithDEs_deIdx.size.size="+parsedNPwithDEs_deIdx.size());
      throw new RuntimeException();
    }

    return NPwithDEs.size();
  }

  private void computeParsedNPwithDEs() {
    List<Tree> parsedChineseTrees = treepair_.chParsedTrees();

    if (parsedChineseTrees == null || parsedChineseTrees.isEmpty()) {
      System.err.println("Warning: NO Chinese parsed trees info present. Use gold trees instead.");
      parsedChineseTrees = treepair_.chTrees();
    }

    if (parsedNPwithDEs_deIdx==null) {
      parsedNPwithDEs_deIdx = new TreeMap<Integer, Pair<Integer,Integer>>();
      Tree parsedTree = parsedChineseTrees.get(0);
      for(int i : NPwithDEs_deIdx_set) {
        Pair<Integer,Integer> range = ExperimentUtils.getNPwithDERangeFromIdx(parsedTree, i);
        parsedNPwithDEs_deIdx.put(i, range);
      }
    }
  }

  public static void annotateNPwithDEs(
    List<Pair<String,String>> categories, AnnotatedTreePair atp) {
    int[] deIndices = ArrayUtils.asPrimitiveIntArray(atp.NPwithDEs_deIdx_set);
    int offset = -1;

    if (deIndices.length <= 0) {
      // no labeling needed
      return;
    }

    // First, find the offset of the first NP in the TreePair
    // relative to the categories for the whole file
    String currentNP = atp.oracleChNPwithDE(deIndices[0]).trim();
    // we go through all the categories for the file (which contains this TreePair)
    for (int i = 0; i < categories.size(); i++) {
      Pair<String,String> category = categories.get(i);
      // if the "offset" (means the position of first NP in this TreePair relatively
      //                  in the categories for categories of this whole file.
      if (offset < 0) {
        if (category.second().trim().equals(currentNP)) {
          // check next NP as sanity check!
          // (there might be multiple NPs that have the same
          // Chinese words in one file, if we just match one,
          // we might get a wrong offset.
          // This might still potentiall cause problems,
          // if there are two subsequent NPs that are duplicates.
          // But for now the NP data we have don't have the problem.
          String nextCatNP = "";
          String nextNP = "";
          if (deIndices.length > 1) {
            nextNP = atp.oracleChNPwithDE(deIndices[1]).trim();
          }
          if (i+1 < categories.size()) {
            nextCatNP = categories.get(i+1).second().trim();
          }
          if (nextCatNP.equals(nextNP) ||
              nextNP.equals("")) { // if there's no nextNP in TreePair, that's ok too.
            offset = i;
            break;
          }
        }
      }
    }
    if (offset < 0) throw new RuntimeException("couldn't find offset in annotateNPwithDEs");
    // starting from categories[offset], label all NPs in TreePair
    for (int idx = 0; idx < deIndices.length; idx++) {
      if (idx+offset >= categories.size()) {
        throw new RuntimeException();
      }
      int deIdxInSent = deIndices[idx];
      Pair<String,String> category = categories.get(idx+offset);
      String np = atp.oracleChNPwithDE(deIdxInSent).trim();
      if (category.second.trim().equals(np)) {
        //System.err.println("Put in to atp.NPwithDEs_categories!");
        atp.NPwithDEs_categories.put(deIdxInSent, category.first());
      } else {
        throw new RuntimeException("error labeling NPs");
      }
    }
  }

  public String oracleChNPwithDE(int idx) {
    Pair<Integer,Integer> ip = NPwithDEs_deIdx.get(idx);
    StringBuilder sb = new StringBuilder();
    if (NPwithDEs.keySet().contains(ip)) {
      for(int i = ip.first; i <= ip.second; i++)
        sb.append(treepair_.alignment().source_[i]).append(' ');
      return sb.toString();
    } else {
      throw new RuntimeException();
      //return null;
    }
  }

  private void printMarkedChineseSentence() {
    String[] chSent = treepair_.alignment().source_;
    for(int i = 0; i < chSent.length; i++) {
      int markBegin = 0;
      int markEnd = 0;
      for (Pair<Integer,Integer> ip : NPwithDEs.keySet()) {
        if (ip.first == i) markBegin++;
        if (ip.second == i) markEnd++;
      }
      while (markBegin>0) {
        System.out.print("<b><u> [[ ");
        markBegin--;
      }
      System.out.print(chSent[i]);
      System.out.print(' ');
      while (markEnd>0) {
        System.out.print(" ]] </u></b>");
        markEnd--;
      }
    }
    System.out.println("<br />");
  }

  private void printMarkedEnglishSentence() {
    String[] enSent = treepair_.alignment().translation_;
    Set<Integer> markedwords = new TreeSet<Integer>();
    for (Pair<Integer,Integer> key : NPwithDEs.keySet()) {
      List<Pair<Integer,Integer>> ips = NPwithDEs.get(key);
      for (Pair<Integer,Integer> ip : ips) {
        for (int i = ip.first; i <= ip.second; i++) {
          markedwords.add(i);
        }
      }
    }

    for(int i = 0; i < enSent.length; i++) {
      if (markedwords.contains(i)) {
        System.out.print("<b><u>");
      }
      System.out.print(enSent[i]);
      System.out.print(' ');
      if (markedwords.contains(i)) {
        System.out.print("</u></b>");
      }
    }
    System.out.println("<br />");
  }

  public void computeNPwithDEs() {
    if (NPwithDEs == null) {
      NPwithDEs = new TreeMap<Pair<Integer,Integer>, List<Pair<Integer,Integer>>>();
      NPwithDEs_deIdx = new TreeMap<Integer, Pair<Integer,Integer>>();
      NPwithDEs_deIdx_set = new TreeSet<Integer>();
      Tree chTree = treepair_.chTrees().get(0);

      Set<Tree> deTrees = getNPwithDESubTrees(chTree);
      Set<Pair<Integer,Integer>> deSpans = getSpans(deTrees, chTree);

      for (Pair<Integer,Integer> deSpan : deSpans) {
        TreeSet<Integer> enSpan = treepair_.alignment().mapChineseToEnglish(deSpan);
        TreeSet<Integer> nullSpan = treepair_.alignment().mapChineseToEnglish_FillGap(deSpan, enSpan);
        // merge these 2
        enSpan.addAll(nullSpan);

        // fill in English gaps where the English word aligns to null in Chinese
        if (enSpan.size() > 0)
          for(int eidx = enSpan.first(); eidx <= enSpan.last(); eidx++) {
            if (!enSpan.contains(eidx)) {
              // if this index wasn't aligned to any thing (except NULL)
              boolean notAligned = true;
              for (int cidx = 1; cidx < treepair_.alignment().matrix_[eidx].length; cidx++) {
                if (treepair_.alignment().matrix_[eidx][cidx] > 0) { notAligned = false; break; }
              }
              if (notAligned) { enSpan.add(eidx); }
            }
          }

        // compute Pair<Integer,Integer>
        int prevI = -1;
        int start = -1;
        List<Pair<Integer,Integer>> enTranslation = new ArrayList<Pair<Integer,Integer>>();
        int last = -1;
        for (int en : enSpan) {
          if (start == -1) {
            start = en;
          }
          if (prevI != -1 && en > prevI+1) {
            enTranslation.add(new Pair<Integer,Integer>(start, prevI));
            start = en;
          }
          prevI = en;
          last = en;
        }
        // add last one
        if (start != -1) {
          enTranslation.add(new Pair<Integer,Integer>(start, last));
        }

        NPwithDEs.put(deSpan, enTranslation);

        // Also, fill the position of the DE in the NPwithDE
        Tree sentTree = treepair_.chTrees().get(0);
        Tree chNP = AlignmentUtils.getTreeWithEdges(sentTree, deSpan.first, deSpan.second+1);
        if (chNP==null) {
          sentTree.pennPrint(System.err);
          System.err.println("range="+deSpan);
          throw new RuntimeException();
        }
        int startIdx = Trees.leftEdge(chNP, sentTree);
        int deIdx = startIdx + ExperimentUtils.getDEIndex(chNP);
        if (NPwithDEs_deIdx_set.contains(deIdx)) {
          throw new RuntimeException("multi idx on DE");
        } else {
          NPwithDEs_deIdx_set.add(deIdx);
        }
        NPwithDEs_deIdx.put(deIdx, deSpan);

        /*
        Sentence<Word> s = sentTree.yield();
        System.err.print(deIdx+"\t");
        for(int i = deSpan.first; i < deSpan.second+1; i++) {
          Word w = s.get(i);
          System.err.print(w+" ");
        }
        System.err.print("\t"+sentTree);
        System.err.println();
        */

      }
    }
  }

  // return the number of NP with DEs in this list of TreePair
  public static int printAllwithDE(List<AnnotatedTreePair> atps) {
    int numNPwithDE = 0;

    AlignmentUtils.printAlignmentGridHeader();
    // List<Set<Tree>> deTreesList = new ArrayList<Set<Tree>>();
    // List<Set<Pair<Integer,Integer>>> deSpansList = new ArrayList<Set<Pair<Integer,Integer>>>();

    for(int i = 0; i < atps.size(); i++) {
      // Print Header of the HTML
      System.out.printf("[ <a href=#%d>%d</a> ]<br />", i+1, i+1);
      AnnotatedTreePair atp = atps.get(i);

      atp.printMarkedChineseSentence();
      atp.printMarkedEnglishSentence();

    }


    int counter = 1;
    for (AnnotatedTreePair atp : atps) {
      System.out.println("<hr>");
      System.out.printf("<a name=%d>\n", counter);
      System.out.printf("<h2>Sentence %d</h2>\n", counter);
      //atp.printTreePair();
      atp.printTreePairWithDeps();
      atp.printNPwithDEs();
      atp.printAlignmentGrid();
      counter++;
    }
    AlignmentUtils.printAlignmentGridBottom();
    return numNPwithDE;
  }

  public static Set<Tree> getNPwithDESubTrees(Tree t) {
    TreePattern p = TreePattern.compile("NP < (/P$/ < (DEG|DEC < 的))");
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
    for (Pair<Integer,Integer> NPwithDE : NPwithDEs.keySet()) {
      List<Pair<Integer,Integer>> englishNP = NPwithDEs.get(NPwithDE);
      System.out.printf("NP #%d:\t", npcount++);
      if (englishNP.size()==1) {
        System.out.printf("<font color=\"blue\">[Contiguous]</font>\t");
      } else {
        System.out.printf("<font color=\"red\">[Fragmented]</font>\t");
      }
      for(int chi = NPwithDE.first; chi <= NPwithDE.second; chi++) {
        System.out.print(treepair_.alignment().source_[chi]);
        System.out.print(' ');
      }
      System.out.println("<br />");
    }
  }

  static Pair<Integer,Integer> getSpan(Tree subT, Tree allT) {
    Pair<Integer,Integer> ip = new Pair<Integer,Integer>(
      Trees.leftEdge(subT, allT),
      Trees.rightEdge(subT, allT)-1);
    return ip;
  }

  public static Set<Pair<Integer,Integer>> getSpans(Set<Tree> deTrees, Tree mainT) {
    Set<Pair<Integer,Integer>> ips = new HashSet<Pair<Integer,Integer>>();
    for (Tree deT : deTrees) {
      ips.add(getSpan(deT, mainT));
    }
    return ips;
  }

  // forwarding other calls to TreePair
  public void printTreePair() { treepair_.printTreePair(); }
  public void printAlignmentGrid() { treepair_.printAlignmentGrid(); }
  public void setEnTrees(List<Tree> ts) { treepair_.setEnTrees(ts); }
  public List<Tree> enTrees() { return treepair_.enTrees(); }

  public void setChTrees(List<Tree> ts) { treepair_.setChTrees(ts); }
  public List<Tree> chTrees() { return treepair_.chTrees(); }

  public void setChParsedTrees(List<Tree> ts) { treepair_.setChParsedTrees(ts); }
  public List<Tree> chParsedTrees() { return treepair_.chParsedTrees(); }

  public void setAlignment(TranslationAlignment a) { treepair_.setAlignment(a); }
  public TranslationAlignment alignment() { return treepair_.alignment(); }

  public void setFileID(int fileid) { treepair_.setFileID(fileid); }
  public int getFileID() { return treepair_.getFileID(); }

  public void printTreePairWithDeps() {
    Tree chT = treepair_.chTrees().get(0);
    List<Tree> enTrees = treepair_.enTrees();

    // (1.1) Chinese Tree
    System.out.println("<h3> Chinese Tree </h3>");
    TreePair.printTree(chT);
    Filter<String> puncWordFilter = Filters.acceptFilter();
    System.out.println("<h3> Chinese Deps </h3>");
    GrammaticalStructure gs = new ChineseGrammaticalStructure(chT, puncWordFilter);
    Collection<TypedDependency> deps = gs.allTypedDependencies();
    System.out.println("<pre>");
    for (TypedDependency dep : deps) {
      System.out.println(dep);
    }
    System.out.println("</pre>");

    //GrammaticalRelation.clearMap();

    // (2) English Tree
    //for (Tree t : enTrees) {
    for (int ti = 0; ti < enTrees.size(); ti++) {
      Tree enT = enTrees.get(ti);
      System.out.printf("<h3> English Tree(%d) </h3>\n", ti);
      TreePair.printTree(enT);
      System.err.println("=====================");
      enT.pennPrint(System.err);
      System.err.println("=====================");
      System.err.println("enT.size() = "+enT.size());
      System.err.println("enT.toString() = "+enT.toString());
      /*
      GrammaticalStructure gsE = new EnglishGrammaticalStructure(enT,puncWordFilter);

      Collection<TypedDependency> depsE = gsE.allTypedDependencies();
      System.out.printf("<h3> English Deps(%d) </h3>\n", ti);
      System.out.println("<pre>");
      for (TypedDependency dep : depsE) {
        System.out.println(dep);
      }
      System.out.println("</pre>");
      */
    }

  }
}
