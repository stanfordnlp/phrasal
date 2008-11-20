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
  List<Tree> chParsedTrees;
  private Map<Pair<Integer,Integer>, List<Pair<Integer,Integer>>> NPwithDEs;
  Map<Integer, String> NPwithDEs_categories;
  Map<Integer, Pair<Integer,Integer>> NPwithDEs_deIdx;
  TreeSet<Integer> NPwithDEs_deIdx_set;
  Map<Integer, Pair<Integer,Integer>> parsedNPwithDEs_deIdx;

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

  public static void annotateNPwithDEs(List<Pair<String,String>> categories, List<TreePair> treepairs_inFile) {
    // check if total number of NPs is correct
    int totalNPs = 0;
    for (TreePair tp : treepairs_inFile) {
      totalNPs += tp.NPwithDEs.size();
    }
    if (categories.size() != totalNPs)
      throw new RuntimeException(categories.size()+" != "+totalNPs);

    int catIdx = 0;
    for(TreePair tp : treepairs_inFile) {
      //for(Map.Entry<Pair<Integer,Integer>, List<Pair<Integer,Integer>>> e : tp.NPwithDEs.entrySet()) {
      for(int deIdxInSent : tp.NPwithDEs_deIdx_set) {
        Pair<Integer, Integer> ip = tp.NPwithDEs_deIdx.get(deIdxInSent);
        String np = tp.oracleChNPwithDE(deIdxInSent);
        np = np.trim();
        if (!categories.get(catIdx).second().endsWith(np)) {
          System.err.println("CMP1:\t"+categories.get(catIdx).second());
          System.err.println("CMP2:\t"+np);
        }
        tp.NPwithDEs_categories.put(deIdxInSent, categories.get(catIdx).first());
        catIdx++;
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
