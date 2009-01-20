package mt.train.transtb;

import mt.classifyde.*;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.ling.*;
import java.util.*;

public class TreePair {
  public TranslationAlignment alignment;
  public List<Tree> enTrees;
  public List<Tree> chTrees;
  public List<Tree> chParsedTrees;
  public Map<Integer, String> NPwithDEs_categories;
  public Map<Integer, Pair<Integer,Integer>> NPwithDEs_deIdx;
  public TreeSet<Integer> NPwithDEs_deIdx_set;
  public Map<Integer, Pair<Integer,Integer>> parsedNPwithDEs_deIdx;

  private int fileid; // the original file ID in CTB and E-CTB
  private Map<Pair<Integer,Integer>, List<Pair<Integer,Integer>>> NPwithDEs;


  public void setFileID(int fileid) {
    this.fileid = fileid;
  }

  public int getFileID() {
    return fileid;
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

  public TreePair(TranslationAlignment alignment, List<Tree> enTrees, List<Tree> chTrees) {
    this.alignment = alignment;
    this.enTrees = enTrees;
    this.chTrees = chTrees;
    this.NPwithDEs_categories = new TreeMap<Integer, String>();

    computeNPwithDEs();
  }

  public TreePair(TranslationAlignment alignment, List<Tree> enTrees, List<Tree> chTrees, List<Tree> chPT) {
    this.alignment = alignment;
    this.enTrees = enTrees;
    this.chTrees = chTrees;
    this.chParsedTrees = chPT;
    this.NPwithDEs_categories = new TreeMap<Integer, String>();

    computeNPwithDEs();
    computeParsedNPwithDEs();
  }

  private void computeParsedNPwithDEs() {
    if (parsedNPwithDEs_deIdx==null) {
      parsedNPwithDEs_deIdx = new TreeMap<Integer, Pair<Integer,Integer>>();
      //System.err.println("TP:chParsedTrees.size="+chParsedTrees.size());
      Tree parsedTree = chParsedTrees.get(0);
      for(int i : NPwithDEs_deIdx_set) {
        Pair<Integer,Integer> range = ExperimentUtils.getNPwithDERangeFromIdx(parsedTree, i);
        parsedNPwithDEs_deIdx.put(i, range);
      }
    }
  }

  public static void printTree(Tree t) {
    System.out.println("<pre>");
    t.pennPrint(System.out);
    System.out.println("</pre>");
  }

  public static void annotateNPwithDEs(List<Pair<String,String>> categories, TreePair tp) {
    Integer[] deIndices = tp.NPwithDEs_deIdx_set.toArray(new Integer[tp.NPwithDEs_deIdx_set.size()]);
    int offset = -1;

    if (deIndices.length <= 0) {
      // no labeling needed
      return;
    }
    
    // First, find the offset of the first NP in the TreePair
    // relative to the categories for the whole file
    String currentNP = tp.oracleChNPwithDE((int)deIndices[0]).trim();
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
            nextNP = tp.oracleChNPwithDE((int)deIndices[1]).trim();
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
      String np = tp.oracleChNPwithDE(deIdxInSent).trim();
      if (category.second.trim().equals(np)) {
        tp.NPwithDEs_categories.put(deIdxInSent, category.first());
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
        sb.append(alignment.source_[i]).append(" ");
      return sb.toString();
    } else {
      throw new RuntimeException();
      //return null;
    }
  }

  private void printMarkedChineseSentence() {
    String[] chSent = alignment.source_;
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
      System.out.print(enSent[i]+" ");
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
      Tree chTree = chTrees.get(0);
      Set<Tree> deTrees = getNPwithDESubTrees(chTree);
      Set<Pair<Integer,Integer>> deSpans = getSpans(deTrees, chTree);

      for (Pair<Integer,Integer> deSpan : deSpans) {
        TreeSet<Integer> enSpan = alignment.mapChineseToEnglish(deSpan);
        TreeSet<Integer> nullSpan = alignment.mapChineseToEnglish_FillGap(deSpan, enSpan);
        // merge these 2
        enSpan.addAll(nullSpan);

        // fill in English gaps where the English word aligns to null in Chinese
        if (enSpan.size() > 0)
          for(int eidx = enSpan.first(); eidx <= enSpan.last(); eidx++) {
            if (!enSpan.contains(eidx)) {
              // if this index wasn't aligned to any thing (except NULL)
              boolean notAligned = true;
              for (int cidx = 1; cidx < alignment.matrix_[eidx].length; cidx++) {
                if (alignment.matrix_[eidx][cidx] > 0) { notAligned = false; break; }
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
        Tree sentTree = chTrees.get(0);
        Tree chNP = TranslationAlignment.getTreeWithEdges(sentTree, deSpan.first, deSpan.second+1);
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
  public static int printAllwithDE(List<TreePair> tps) {
    int numNPwithDE = 0;

    TranslationAlignment.printAlignmentGridHeader();
    List<Set<Tree>> deTreesList = new ArrayList<Set<Tree>>();
    List<Set<Pair<Integer,Integer>>> deSpansList = new ArrayList<Set<Pair<Integer,Integer>>>();

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
        System.out.print(alignment.source_[chi]+" ");
      }
      System.out.println("<br />");
    }
  }

  public void printTreePair() {
    // (1.1) Chinese Tree
    System.out.println("<h3> Chinese Tree </h3>");
    printTree(chTrees.get(0));
    
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
