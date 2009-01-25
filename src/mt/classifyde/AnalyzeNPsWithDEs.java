package mt.classifyde;

import mt.train.transtb.*;
import edu.stanford.nlp.util.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;
import java.util.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.parser.lexparser.*;
import edu.stanford.nlp.process.*;

class AnalyzeNPsWithDEs {

  private static boolean DEBUG = false;

  private static String analyzeNPwithDE(
    int deIdxInSent, 
    AnnotatedTreePair atp, 
    PrintWriter pw) throws IOException {

    List<Pair<Integer,Integer>> englishNP = atp.getNPEnglishTranslation(deIdxInSent);
    Pair<Integer,Integer> np = atp.NPwithDEs_deIdx.get(deIdxInSent);

    //List<Pair<Integer,Integer>> englishNP = atp.NPwithDEs.get(np);
    if (englishNP.size() != 1) {
      return "fragmented";
    }

    Tree chTree = atp.chTrees().get(0);
    Tree chNPTree = ExperimentUtils.getTreeWithEdges(chTree,np.first, np.second+1);
    if (chNPTree == null) {
      throw new RuntimeException("chNPTree shouldn't be null");
    }

    Tree enNPTree = ExperimentUtils.getTreeWithEdges(atp.enTrees(), englishNP.get(0).first, englishNP.get(0).second+1);
    if (enNPTree == null) {
      System.err.println("enNPTree: NULL");
    } else {
      System.err.println("enNPTree: Found");
      //enNPTree.pennPrint(System.err);
    }

    // if there's only one chunk of English, get the submatrix and subsource & subtranslation
    Pair<Integer,Integer> ennp = englishNP.get(0);
    int nplength = np.second-np.first+1;
    int ennplength = ennp.second-ennp.first+1;
    String[] subsource      = new String[nplength];
    String[] subtranslation = new String[ennplength];
    int[][] submatrix = new int[ennplength][nplength];

    for(int tidx = ennp.first; tidx <= ennp.second; tidx++) {
      for(int sidx = np.first; sidx <= np.second; sidx++) {
        // This really can be improved. Note that in matrix_, 0 --> NULL
        submatrix[tidx-ennp.first][sidx-np.first] = atp.alignment().matrix_[tidx+1][sidx+1];
      }
    }

    //locate the "的"
    for(int tidx = ennp.first; tidx <= ennp.second; tidx++) {
      subtranslation[tidx-ennp.first] = atp.alignment().translation_[tidx];
    }
    List<Integer> deIndices = new ArrayList<Integer>();
    for(int sidx = np.first; sidx <= np.second; sidx++) {
      subsource[sidx-np.first] = atp.alignment().source_[sidx];
      if (atp.alignment().source_[sidx].equals("的") ||
          atp.alignment().source_[sidx].equals("之")) {
        // TODO: should get rid of the second condition
        deIndices.add(sidx-np.first);
      }
    }

    if (deIndices.size() > 1) {
      return "multi-DEs";
    }
    if (deIndices.size() == 0) {
      return "no DE?";
    }

    // Now it's the case with only one DE
    // find the mapping to English of the first part, 
    // and find the mapping to the 2nd part
    int deIdx = deIndices.get(0);


    // The manual alignment like to align the determiners with the noun,
    // which cause lots of "undecided" type. Try to eliminate this case
    fixDeterminerOrOfOrWith(subtranslation, subsource, submatrix);

    // for "A de B"
    // get the translation range of A
    Pair<Integer,Integer> rangeA = getRangeA(subtranslation, subsource, submatrix, deIdx);
    Pair<Integer,Integer> rangeB = getRangeB(subtranslation, subsource, submatrix, deIdx);
    int deEidx = getDEeidx(subtranslation, subsource, submatrix, deIdx);
    // based one the range, fix 'which' clause, and update rangeA & rangeB
    fixWhichWhere(subtranslation, subsource, submatrix, rangeA, rangeB, deIndices);
    rangeA = getRangeA(subtranslation, subsource, submatrix, deIdx);
    rangeB = getRangeB(subtranslation, subsource, submatrix, deIdx);

    // From here, we have 'rangeA' and 'rangeB'
    //printRangeAandB(rangeA, rangeB, subtranslation, subsource);

    // Print out the sub-grid to file
    printGridNoNull(subtranslation, subsource, submatrix, pw);
    // Print related chNPTree & enNPTree, if exist
    printNPTree(chNPTree, pw);
    printNPTree(enNPTree, pw);


    // if there is A and B,
    // but either no rangeA or rangeB
    // then return "other - A/B not aligned"
    if (deIdx > 0 && deIdx < subsource.length-1) {
      if (rangeA.second==-1) { return "other - A not aligned"; }
      if (rangeB.second==-1) { return "other - B not aligned"; }
    }

    if (deIdx == subsource.length - 1) {
      if (enNPTree != null && enNPTree.value().equals("VP")) { return "no B - VP"; }
      return "no B";
    }

    if (rangeA.second==-1) { return "other - no en range A"; }
    if (rangeB.second==-1) { return "other - no en range B"; }


    if (rangeA.second < rangeB.first) {
      // starting from the end of rangeA, because 's could be on the edge
      for(int eidx = rangeA.second; eidx <= rangeB.first-1; eidx++) {
        if (submatrix[eidx][deIdx] > 0) {
          String deWord = subtranslation[eidx];
          if (deWord.equals("'s")) {
            return "A 's B";
          }
        }
      }

      // check if A becomes an adjective
      if (checkSecondVP(rangeA, rangeB, enNPTree)) return "other - VP";

      // For the following cases, we have to first check that
      // DE does not align to something inside (not on edge) rangeA & rangeB
      if (deEidx == -1 ||
          ((onRangeEdge(deEidx, rangeA) || !inRange(deEidx, rangeA)) &&
           (onRangeEdge(deEidx, rangeB) || !inRange(deEidx, rangeB)))) {
        if (deEidx != -1 && subtranslation[deEidx].equals("of")) return "A of B";
        if (deEidx != -1 && isPrep(subtranslation[deEidx])) return "A prep B";
        if (checkOrderedAdjective(rangeA, rangeB, enNPTree)) return "A B (adj)";
        if (checkOrderedAdverb(rangeA, rangeB, enNPTree)) return "A B (adv)";
        String mod = checkOrderedOtherModifier(rangeA, rangeB, enNPTree);
        if (mod != null) return "A B ("+mod.toLowerCase()+")";
        if (checkOrderedNoun(rangeA, rangeB, enNPTree)) return "A B (n)";
      } else {
        return "other - mixed";
      }

      // if no enNPTRee, give it a "ordered - no enNPtree" category
      if (enNPTree==null) return "ordered - no enNPtree";
      return "ordered";
    }

    if (rangeB.second < rangeA.first) {
      String boundaryWord = subtranslation[rangeA.first];
      if (boundaryWord.equals("of") ||
          boundaryWord.equals("to")) {
        return "B "+boundaryWord+" A";
      }
      if (isPrep(boundaryWord)) {
        return "B prep A";
      }
      if (boundaryWord.equals("that") || boundaryWord.equals("which")) {
        return "relative clause";
      }

      String deMappedWord = null;
      // check if deIdx aligns to somewhere in between max(rangeB) and min(rangeA)
      for(int eidx = rangeB.second+1; eidx <= rangeA.first-1; eidx++) {
        if (submatrix[eidx][deIdx] > 0) {
          String deWord = subtranslation[eidx];
          if (deWord.equals("of") ||
              deWord.equals("to")) {
            return "B "+deWord+" A";
          }
          if (isPrep(deWord)) {
            return "B prep A";
          }
          if (deWord.equals("that") || deWord.equals("which")) {
            return "relative clause";
          }
          deMappedWord = deWord;
          break;
        }
      }

      // This is flipped case, but we don't know what it is yet.
      // If enNPTree exists, 
      // we can check if range A is a VP and range B is an NP
      if (checkFlippedRelativeClause(rangeA, rangeB, enNPTree)) return "relative clause";//"flipped (relc)";

      return "flipped";
    }
    return "undecided";
  }


  // testing only
  public static void main(String[] args) throws IOException {
    int validAlignments = 0;
    int numtreepairs = 0;
    int numNPwithDE = 0;
    int numNPwithDE_contiguous = 0;
    int numNPwithDE_fragmented = 0;
    int FIDX = Integer.parseInt(args[0]);
    Properties props =StringUtils.argsToProperties(args);
    Counter typeCounter = new ClassicCounter<String>();

    // For this to run on both NLP machine and my computer
    String dirname = "/u/nlp/scr/data/ldc/LDC2006E93/GALE-Y1Q4/word_alignment/data/chinese/nw/";
    File dir = new File(dirname);
    if (!dir.exists()) {
      dirname = "C:\\cygwin\\home\\Pichuan Chang\\data\\LDC2006E93\\GALE-Y1Q4\\word_alignment\\data\\chinese\\nw\\";
      dir = new File(dirname);
      if (!dir.exists()) {

        throw new RuntimeException("LDC2006E93 doesn't exist in either of the hard-coded locations.");
      }
    }

    for(int fileidx = FIDX; fileidx <= FIDX; fileidx++) {
      // (1) Read alignment files
      String aname = String.format("%schtb_%03d.txt", dirname, fileidx);
      File file = new File(aname);
      List<TranslationAlignment> alignment_list = new ArrayList<TranslationAlignment>();
      if (file.exists()) {
        System.err.println("Processing  "+fileidx);
        alignment_list = TranslationAlignment.readFromFile(file);
      } else {
        System.err.println("Skip "+fileidx);
      }

      // (2) Read Chinese Trees
      ChineseTreeReader ctr = new ChineseTreeReader();
      String ctbdirname = "/afs/ir/data/linguistic-data/Chinese-Treebank/6/data/utf8/bracketed/";
      File ctbdir = new File(ctbdirname);
      if (!ctbdir.exists()) {
        ctbdirname = "C:\\cygwin\\home\\Pichuan Chang\\data\\CTB6\\data\\utf8\\bracketed\\";
        ctbdir = new File(ctbdirname);
        if (!ctbdir.exists())
          throw new RuntimeException("CTB6.0 doesn't exist in either of the hard-coded locations.");
      }
      String ctbname =
        String.format("%schtb_%04d.fid", ctbdirname, fileidx);
      ctr.readMoreTrees(ctbname);

      // (3) Read English Trees
      EnglishTreeReader etr = new EnglishTreeReader();
      String ectbdirname = "/u/nlp/scr/data/ldc/LDC2007T02-EnglishChineseTranslationTreebankV1.0/data/pennTB-style-trees/";
      File ectbdir = new File(ectbdirname);
      if (!ectbdir.exists()) {
        ectbdirname = "C:\\cygwin\\home\\Pichuan Chang\\data\\LDC2007T02-EnglishChineseTranslationTreebankV1.0\\data\\pennTB-style-trees\\";
        ectbdir = new File(ectbdirname);
        if (!ectbdir.exists()) {
          throw new RuntimeException("EnglishChineseTranslationTreebankV1.0 doesn't exist in either of the hard-coded locations.");
        }
      }
      String ename =
        String.format("%schtb_%03d.mrg.gz",
                      ectbdirname, fileidx);
      etr.readMoreTrees(ename);

      // (4) Going through entries in (1) and check if they exist in (2)
      // (5) Going through entries in (1) and check if they exist in (3)
      // (6) also, if the tests passed, output various information

      List<AnnotatedTreePair> atreepairs = new ArrayList<AnnotatedTreePair>();

      // open output file for NP list. This format is easy to import to Excel
      String npOutputDir = props.getProperty("npOutputDir", null);
      PrintWriter npPW = null;
      PrintWriter npgridPW = null;
      if (npOutputDir != null) {
        String filename = npOutputDir+"/"+fileidx+".np";
        System.err.println("Output NPs to "+filename);
        npPW= new PrintWriter(new BufferedWriter(new FileWriter(filename)));

        filename = npOutputDir+"/grids/"+fileidx+".npgrid.html";
        System.err.println("Output NP Grids to "+filename);
        npgridPW= new PrintWriter(new BufferedWriter(new FileWriter(filename)));
      }
      // open output file for orginal Translation Alignment file.
      // In this format, every token is as defined in the manual word_alignment file.
      // So, Chinese tokens are characters.
      // The reason of having this option is mostly for debugging.
      // If you find any final grid in the tree-aligned version somewhat weird,
      // you can print out the original grid and see if it makes sense.
      String origTAdir = props.getProperty("origTAdir", null);
      PrintWriter origtaPW = null;
      if (origTAdir != null) {
        String filename = origTAdir+"/"+fileidx+".origta.html";
        System.err.println("Output original grids to "+filename);
        origtaPW = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
      }

      int npCount = 1;
      if (origtaPW != null) {AlignmentUtils.printAlignmentGridHeader(origtaPW);}
      if (npgridPW != null) {AlignmentUtils.printAlignmentGridHeader(npgridPW);}
      for (TranslationAlignment ta : alignment_list) {
        if (origtaPW != null) {
          AlignmentUtils.printAlignmentGrid(ta, origtaPW);
        }

        List<Tree> chTrees = ctr.getTreesWithWords(ta.source_);
        if (chTrees.size() == 0) {
          System.err.printf("i=%d: Can't find tree in CTB: %s\n", fileidx, StringUtils.join(ta.source_, " "));
          System.err.println(StringUtils.join(ta.source_, " "));

          continue;
          // skip for now
        } else if (chTrees.size() > 1) {
          //System.err.printf("i=%d: Multiple trees: %s\n", fileidx, StringUtils.join(ta.source_, " "));
          throw new RuntimeException("i="+fileidx+": Multiple trees.");
        }
        
        List<Tree> enTrees = etr.getTreesWithWords(ta.translation_);
        if (enTrees.size() == 0) {
          System.err.printf("i=%d: Can't find tree in PTB: %s\n", fileidx, StringUtils.join(ta.translation_, " "));
          System.err.println(StringUtils.join(ta.translation_, " "));
          continue;
          // skip for now
        } else if (enTrees.size() > 1) {
          System.err.printf("i=%d: Multiple trees: %s\n", fileidx, StringUtils.join(ta.translation_, " "));
        }
        
        // Fix the Translation Alignment before adding to the TreePair
        if (DEBUG) System.err.println("i="+fileidx);

        ta = AlignmentUtils.fixAlignmentGridWithChineseTree(ta, chTrees);
        ta = AlignmentUtils.fixAlignmentGridMergingChinese(ta, chTrees);
        ta = AlignmentUtils.fixAlignmentGridWithEnglishTree(ta, enTrees);
        ta = AlignmentUtils.fixAlignmentGridMergingEnglish(ta, enTrees);
        AlignmentUtils.checkTranslationAlignmentAndEnTrees(ta, enTrees);
        AlignmentUtils.checkTranslationAlignmentAndChTrees(ta, chTrees);
        TreePair tp = new TreePair(ta, enTrees, chTrees);
        AnnotatedTreePair atp = new AnnotatedTreePair(tp);
        //numNPwithDE += tp.NPwithDEs.size();
        numNPwithDE += atp.numNPwithDE();
        
        //for (Pair<Integer,Integer> NPwithDE : atp.NPwithDEs.keySet()) {
        for(int deIdxInSent : atp.NPwithDEs_deIdx_set) {
          Pair<Integer,Integer> NPwithDE = atp.NPwithDEs_deIdx.get(deIdxInSent);
          //List<Pair<Integer,Integer>> englishNP = atp.NPwithDEs.get(NPwithDE);
          List<Pair<Integer,Integer>> englishNP = atp.getNPEnglishTranslation(deIdxInSent);
          if (englishNP.size()==1) {
            numNPwithDE_contiguous++;
          } else {
            numNPwithDE_fragmented++;
          }
          
          if (npPW != null) {
            AlignmentUtils.printAlignmentGridHeader(npgridPW);
            String type = analyzeNPwithDE(deIdxInSent, atp, npgridPW);
            typeCounter.incrementCount(type);
            AlignmentUtils.printAlignmentGridBottom(npgridPW);
            printNPwithDEtoFile(fileidx, npCount, npPW, deIdxInSent, atp, type);
            npCount++;
          }
        }
        
        atreepairs.add(atp);
      }
      if (npgridPW != null) { AlignmentUtils.printAlignmentGridBottom(npgridPW); npgridPW.close(); }
      if (origtaPW != null) { AlignmentUtils.printAlignmentGridBottom(origtaPW); origtaPW.close(); }
      
      numNPwithDE  += AnnotatedTreePair.printAllwithDE(atreepairs);
      numtreepairs += atreepairs.size();
      
      if (npPW != null) {
        npPW.close();
      }
      validAlignments += alignment_list.size();
    }
    
    // count Countiguous NPs & Fragmented NPs
    
    System.err.println("# valid translation alignment = "+validAlignments);
    System.err.println("# Tree Pairs = "+numtreepairs);
    System.err.println("# NPs with DE = "+numNPwithDE);
    System.err.println("# NPs with DE (contiguous)= "+numNPwithDE_contiguous);
    System.err.println("# NPs with DE (fragmented)= "+numNPwithDE_fragmented);
    System.err.println(typeCounter);
  }

  private static Pair<Integer,Integer> getRangeA(String[] subtranslation, String[] subsource, int[][] submatrix, int deIdx) {
    int min = subtranslation.length;
    int max = -1;
    boolean setMin = false, setMax = false;
    for(int sidx = 0; sidx < deIdx; sidx++) {
      for(int tidx = 0; tidx < subtranslation.length; tidx++) {
        if (submatrix[tidx][sidx] > 0) {
          if (tidx < min) { min = tidx; setMin = true; }
          if (tidx > max) { max = tidx; setMax = true; }
        }
      }
    }
    if (!setMin || !setMax) { min = max = -1; }
    return new Pair<Integer,Integer>(min,max);
  }

  private static Pair<Integer,Integer> getRangeB(String[] subtranslation, String[] subsource, int[][] submatrix, int deIdx) {
    int min = subtranslation.length;
    int max = -1;
    boolean setMin = false, setMax = false;
    for(int sidx = deIdx+1; sidx < subsource.length; sidx++) {
      for(int tidx = 0; tidx < subtranslation.length; tidx++) {
        if (submatrix[tidx][sidx] > 0) {
          if (tidx < min) { min = tidx; setMin = true; }
          if (tidx > max) { max = tidx; setMax = true; }
        }
      }
    }
    if (!setMin || !setMax) { min = max = -1; }
    return new Pair<Integer,Integer>(min,max);
  }



  private static int getDEeidx(String[] subtranslation, String[] subsource, int[][] submatrix, int deIdx) {
    boolean set = false;
    int deEidx = -1;
    for (int eidx = 0; eidx < subtranslation.length; eidx++) {
      if (submatrix[eidx][deIdx] > 0) {
        if (set) return -1;
        if (!set) { deEidx = eidx; set = true; }
      }
    }
    if (set) return deEidx;
    return -1;
  }

  private static void printRangeAandB(Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, String[] subtranslation, String[] subsource) {
    if (rangeA.first >= 0 && rangeA.first < subtranslation.length &&
        rangeA.second >= 0 && rangeA.second < subtranslation.length) {
      System.err.print("RangeA = ");
      for (int i = rangeA.first; i <= rangeA.second; i++) {
        System.err.print(subtranslation[i]+" ");
      }
      System.err.println();
    } else {
      System.err.println("RangeA = NULL");
    }
    if (rangeB.first >= 0 && rangeB.first < subtranslation.length &&
        rangeB.second >= 0 && rangeB.second < subtranslation.length) {
      System.err.print("RangeB = ");
      for (int i = rangeB.first; i <= rangeB.second; i++) {
        System.err.print(subtranslation[i]+" ");
      }
      System.err.println();
    } else {
      System.err.println("RangeB = NULL");
    }
  }

  private static boolean onRangeEdge(int i, Pair<Integer,Integer> range) {
    if (i==-1) return false;
    if (range.second==-1) return false;
    if (range.first==i || range.second==i) return true;
    return false;
  }

  private static boolean inRange(int i, Pair<Integer,Integer> range) {
    if (i==-1) return false;
    if (range.second==-1) return false;
    if (range.first<=i && range.second>=i) return true;
    return false;
  }

  private static boolean checkOrderedAdverb(Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, Tree enTree) {
    if (enTree == null) return false;

    // check if the first part is an adjective
    Tree adj = ExperimentUtils.getTreeWithEdges(enTree, rangeA.first, rangeA.second+1);
    if (adj != null && (adj.value().startsWith("RB"))) {
      return true;
    }
    return false;
  }

  private static boolean checkOrderedAdjective(Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, Tree enTree) {
    if (enTree == null) return false;

    // check if the first part is an adjective
    Tree adj = ExperimentUtils.getTreeWithEdges(enTree, rangeA.first, rangeA.second+1);
    if (adj != null && (adj.value().startsWith("ADJP") || adj.value().startsWith("JJ"))) {
      return true;
    }

    // check if the first part is 2 subtrees, with forms like:
    // RB JJ, JJ JJ, NP JJ, RBR JJ, etc
    for (int sep = rangeA.first; sep <= rangeA.second; sep++) {
      Tree adj1 = ExperimentUtils.getTreeWithEdges(enTree, rangeA.first, sep+1);
      Tree adj2 = ExperimentUtils.getTreeWithEdges(enTree, sep+1, rangeA.second+1);
      if (adj1 != null && adj2 != null) {
        String adj1str = adj1.value();
        String adj2str = adj2.value();
        if (adj2str.startsWith("JJ") || adj2str.equals("ADJP")) {
          if (adj1str.startsWith("RB") || (adj1str.startsWith("JJ") || (adj1str.startsWith("N")))) {
            return true;
          }
        }
      }
    }
    return false;
  }


  private static String checkOrderedOtherModifier(Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, Tree enTree) {
    if (enTree == null) return null;
    Tree adj = ExperimentUtils.getTreeWithEdges(enTree, rangeA.first, rangeA.second+1);
    if (adj != null && (adj.value().startsWith("VBN") || adj.value().startsWith("VBG") || adj.value().startsWith("PRP$"))) {
      return adj.value();
    }
    return null;
  }

  private static boolean checkOrderedNoun(Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, Tree enTree) {
    if (enTree == null) return false;

    boolean firstNP = false;
    boolean secondNP = false;


    Tree np = ExperimentUtils.getTreeWithEdges(enTree, rangeA.first, rangeA.second+1);
    if (np != null && 
        (np.value().startsWith("N"))) {
      firstNP = true;
    }

    // check if the first part has a modifier and then a noun
    if (!firstNP) {
      for (int sep = rangeA.first; sep <= rangeA.second; sep++) {
        Tree n1 = ExperimentUtils.getTreeWithEdges(enTree, rangeA.first, sep+1);
        Tree n2 = ExperimentUtils.getTreeWithEdges(enTree, sep+1, rangeA.second+1);
        if (n1 != null && n2 != null) {
          String n1str = n1.value();
          String n2str = n2.value();
        if (n1str.equals("JJ") || n1str.startsWith("N")) {
          if (n2str.startsWith("N")) {
            firstNP = true;
            break;
          }
        }
        }
      }
    }

    Tree bT = ExperimentUtils.getTreeWithEdges(enTree, rangeB.first, rangeB.second+1);
    if (bT != null &&
        (bT.value().startsWith("N"))) {
      secondNP = true;
    }

    // check if the second part is a compound noun
    if (!secondNP)
      for (int sep = rangeB.first; sep <= rangeB.second; sep++) {

        Tree n1 = ExperimentUtils.getTreeWithEdges(enTree, rangeB.first, sep+1);
        Tree n2 = ExperimentUtils.getTreeWithEdges(enTree, sep+1, rangeB.second+1);
        if (n1 != null && n2 != null) {
          String n1str = n1.value();
          String n2str = n2.value();
          if (n1str.startsWith("N") && n2str.startsWith("N")) {
            secondNP = true;
            break;
          }
        }
      }
    
    
    if (firstNP && secondNP) return true;

    return false;
  }


  private static boolean checkSecondVP(Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, Tree enTree) {
    if (enTree == null) return false;

    Tree bT = ExperimentUtils.getTreeWithEdges(enTree, rangeB.first, rangeB.second+1);
    if (bT != null &&
        (bT.value().startsWith("VP"))) {
      return true;
    }

    return false;
  }


  private static boolean checkFlippedRelativeClause(Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, Tree enTree) {
    if (enTree == null) return false;
    Tree relc = ExperimentUtils.getTreeWithEdges(enTree, rangeA.first, rangeA.second+1);
    if (relc != null && 
        (relc.value().startsWith("VP") || 
         relc.value().startsWith("SBAR") || 
         // this second case here is for cases like:
         //(WHNP (WDT which))
         // (S
         // (VP (VBD won)
         // (NP (DT the) (JJ national)
         // (NX (JJ important) (NN invention))
         // (NN award))))))
         (relc.value().startsWith("S") && relc.firstChild().value().startsWith("VP")))) {
         
      return true;
    }
    return false;
  }


  private static void printNPwithDEtoFile(int fileidx, int npidx, 
                                          PrintWriter npPW, 
                                          int deIdx, 
                                          AnnotatedTreePair atp, String type) {
    //List<Pair<Integer,Integer>> englishNP = tp.NPwithDEs.get(np);
    Pair<Integer, Integer> np = atp.NPwithDEs_deIdx.get(deIdx);
    List<Pair<Integer,Integer>> englishNP = atp.getNPEnglishTranslation(deIdx);
    List<String> ch = new ArrayList<String>();
    for (int i = np.first; i<=np.second; i++) {
      ch.add(atp.alignment().source_[i]);
    }
    String chStr = StringUtils.join(ch, " ");

    List<String> en = new ArrayList<String>();
    for (Pair<Integer,Integer> enNP : englishNP) {
      StringBuilder ensb = new StringBuilder();
      for (int i = enNP.first; i<=enNP.second; i++) {
        ensb.append(atp.alignment().translation_[i]).append(" ");
      }
      en.add(ensb.toString());
    }
    String enStr = StringUtils.join(en, "|| ");

    npPW.printf("%d\t%d\t%s\t%d\t%s\t%s\n", fileidx, npidx, type, englishNP.size(), chStr, enStr);
  }

  private static List<List<Integer>> getIndexGroups(String[] leaves, String[] source) {
    List<List<Integer>> indexgroups = new ArrayList<List<Integer>>();

    int tidx = 0;
    for(int lidx = 0; lidx < leaves.length; lidx++) {
      List<Integer> indexgroup = new ArrayList<Integer>();
      String leaf = leaves[lidx];
      if (DEBUG) System.err.println("LEAF="+leaf);
      StringBuilder chunk = new StringBuilder();
      while(!leaf.equals(chunk.toString())) {
        //while(!ExperimentUtils.tokenEquals(leaf, chunk.toString())) {
        chunk.append(source[tidx]);
        indexgroup.add(tidx+1); // have to offset by 1, because 0 is NULL
        if (DEBUG) System.err.println("CHUNK="+chunk.toString());
        tidx++;
      }
      indexgroups.add(indexgroup);
    }
    return indexgroups;
  }

  private static void fixDeterminerOrOfOrWith(String[] translation, String[] source, int[][] matrix) {
    boolean err = false;
    if (translation.length != matrix.length || translation.length == 0) {
      err = true;
    } else if (matrix[0].length != source.length || source.length == 0) {
      err = true;
    }
    if (err) { throw new RuntimeException("printGridNoNull FAILED."); }
    
    for (int cidx = 0; cidx < source.length; cidx++) {
      // check if the word align to a determiner as well as something later.
      // but check backwards
      boolean isNonDT = false;
      for (int eidx = translation.length-1; eidx >= 0; eidx--) {
        if ( !isDeterminerOrOfOrWith(translation[eidx]) && matrix[eidx][cidx] > 0) { 
          isNonDT = true; 
        }
        else if ( matrix[eidx][cidx] > 0 && 
                  isDeterminerOrOfOrWith(translation[eidx]) && isNonDT) {
          // here it means that source[cidx] has linked to another word later than
          // the current eidx (which is a determiner).
          // We can clear the eidx entry in matrix
          matrix[eidx][cidx] = -1;
        }
      }
    }
  }

  
  private static boolean isDeterminerOrOfOrWith(String word) {
    word = word.toLowerCase();
    if (word.equals("the") || word.equals("a") || 
        word.equals("its") || word.equals("their") ||
        word.equals("an") || word.equals("with"))
      return true;
    return false;
  }

  private static void fixWhichWhere(String[] translation, String[] source, int[][] matrix, Pair<Integer,Integer> rangeA, Pair<Integer,Integer> rangeB, List<Integer> deIndices) {
    boolean err = false;
    if (translation.length != matrix.length || translation.length == 0) {
      err = true;
    } else if (matrix[0].length != source.length || source.length == 0) {
      err = true;
    }
    if (err) { throw new RuntimeException("printGridNoNull FAILED."); }

    if (deIndices.size() > 1) {
      // skip this case because it's multi-DEs
      return;
    }

    for (int cidx = deIndices.get(0)+1; cidx < source.length; cidx++) {
      boolean isNonWhichWhere = false;
      if (rangeB.second < translation.length && rangeB.second > 0) {
        for (int eidx = 0; eidx < rangeB.second; eidx++) {
          if ( (!translation[eidx].equals("which") && !translation[eidx].equals("where")) &&
               matrix[eidx][cidx] > 0) {
            isNonWhichWhere = true;
          }
        }
        if ((translation[rangeB.second].equals("which") || translation[rangeB.second].equals("where"))
            && matrix[rangeB.second][cidx] > 0 && isNonWhichWhere) {
          matrix[rangeB.second][cidx] = -1;
        }
      }
    }
  }

  private static boolean isPrep(String word) {
    if (word.equals("with") ||
        word.equals("within") ||
        word.equals("from") ||
        word.equals("in") ||
        word.equals("inside") ||
        word.equals("for") ||
        word.equals("on") ||
        word.equals("between") ||
        word.equals("by") ||
        word.equals("among") ||
        word.equals("at") ||
        word.equals("under")) 
      return true;
    
    return false;
  }

  private static void printGridNoNull(String[] translation, String[] source, int[][] matrix, PrintWriter pw) {
    boolean err = false;
    if (translation.length != matrix.length || translation.length == 0) {
      err = true;
    } else if (matrix[0].length != source.length || source.length == 0) {
      err = true;
    }
    if (err) { System.err.println("printGridNoNull FAILED."); return; }

    pw.println("<table>");
    pw.println("<tr><td></td>");
    for(int i = 0; i < source.length; i++) {
      pw.printf("<td>%s</td>\n", source[i]);
    }
    
    for(int tidx = 0; tidx < translation.length; tidx++) {
      pw.printf("<tr><td>%s</td>\n", translation[tidx]);
      for(int sidx = 0; sidx < source.length; sidx++) {
        if (matrix[tidx][sidx] == 0)
          pw.println("  <td>&nbsp;</td>");
        else if (matrix[tidx][sidx] > 0)
          pw.printf("    <td bgcolor=\"black\">%d,%d</td>\n", tidx, sidx);
        else if (matrix[tidx][sidx] == -1)
          pw.printf("    <td bgcolor=\"green\">%d</td>\n", matrix[tidx][sidx]);
        else
          pw.printf("    <td bgcolor=\"red\">%d</td>\n", matrix[tidx][sidx]);
      }
      pw.println("</tr>");
    }
    pw.println("</table>");
  }

  private static void printNPTree(Tree t, PrintWriter pw) {
    pw.println("<pre>");
    if (t != null) {
      t.pennPrint(pw);
    } else {
      pw.println("No Tree");
    }
    pw.println("</pre>");
  }
 
 

}