package mt.train.transtb;

import java.util.*;
import java.io.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;

public class TransTBUtils {

  /** the default is to use
   * useReducedCategories=true (means "B of A" is maped to "B prep A"), and
   * useNonOracleTrees=false (means features are extracted from parsed trees, not gold-standard trees)
   **/
  public static List<TreePair> readAnnotatedTreePairs() throws IOException {
    return readAnnotatedTreePairs(true, false);
  }

  public static List<TreePair> readAnnotatedTreePairs(
    Boolean useReducedCategories) throws IOException {
    return readAnnotatedTreePairs(useReducedCategories, false);
  }

  public static List<TreePair> readAnnotatedTreePairs(
    Boolean useReducedCategories, 
    Boolean useNonOracleTrees) throws IOException {

    String wordalignmentDir = wordAlignmentDir();
    String ctbDir = ctbDir();
    String etbDir = etbDir();
    
    String chParsedDir = null;
    if (useNonOracleTrees) {
      chParsedDir =
        System.getenv("JAVANLP_HOME")+
        "/projects/mt/src/mt/classifyde/data/ctb_parsed/bracketed/";
    }

    List<TranslationAlignment> alignment_list = new ArrayList<TranslationAlignment>();
    ChineseTreeReader ctr = new ChineseTreeReader();
    EnglishTreeReader etr = new EnglishTreeReader();
    ChineseTreeReader chparsedTR = new ChineseTreeReader();

    List<TreePair> treepairs = new ArrayList<TreePair>();

    for(int fileidx = 1; fileidx <= 325; fileidx++) {
      // Everytime, restart them so that when we get trees,
      // we won't match tree & sentences in different files.
      alignment_list = new ArrayList<TranslationAlignment>();
      ctr = new ChineseTreeReader();
      etr = new EnglishTreeReader();
      chparsedTR = new ChineseTreeReader();

      // (1) Read alignment files
      String aname = String.format("%schtb_%03d.txt", wordalignmentDir, fileidx);
      File file = new File(aname);
      if (file.exists()) {
        //System.err.println("Processing  "+fileidx);
        alignment_list = AlignmentUtils.readFromFile(file);
      } else {
        //System.err.println("Skip "+fileidx);
        continue;
      }

      // (2) Read Chinese Trees
      String ctbname =
        String.format("%schtb_%04d.fid", ctbDir, fileidx);
      ctr.readMoreTrees(ctbname);

      // (3) Read English Trees
      String ename =
        String.format("%schtb_%03d.mrg.gz", etbDir, fileidx);
      etr.readMoreTrees(ename);

      // (4) Read parsed Chinese Trees
      String chparsedname = null;
      //if (chParsedDir!=null) { 
      if (useNonOracleTrees) {
        chparsedname = String.format("%schtb_%04d.fid", chParsedDir, fileidx);
        //System.err.println("Reading "+chparsedname);
        chparsedTR.readMoreTrees(chparsedname);
        //System.err.println("chparsedTR.size="+chparsedTR.size());
      }

      // (4) Going through entries in (1) and check if they exist in (2)
      // (5) Going through entries in (1) and check if they exist in (3)
      // (6) also, if the tests passed, this is going to the final examples
      int taidx = 1;
      List<TreePair> treepairs_inFile = new ArrayList<TreePair>();
      for (TranslationAlignment ta : alignment_list) {
        List<Tree> chTrees = ctr.getTreesWithWords(ta.source_);
        List<Tree> chParsedTrees = null;
        //if (chParsedDir != null) {
        if (useNonOracleTrees) {
          chParsedTrees = chparsedTR.getTreesWithWords(ta.source_);
          //System.err.println("chParsedTrees.size="+chParsedTrees.size());
        } else {
          //System.err.println("chParsedTrees.null");
        }

        if (chTrees.size() == 0) {
          System.err.printf("i=%d: Can't find tree in CTB.\n", fileidx);
          System.err.println(StringUtils.join(ta.source_, " "));
          continue;
          // skip for now
        } else if (chTrees.size() > 1) {
          throw new RuntimeException("i="+fileidx+": Multiple trees.");
        }
        
        List<Tree> enTrees = etr.getTreesWithWords(ta.translation_);
        if (enTrees.size() == 0) {
          System.err.printf("i=%d: Can't find tree in PTB.\n", fileidx);
          System.err.println(StringUtils.join(ta.translation_, " "));
          continue;
          // skip for now
        } else if (enTrees.size() > 1) {
          //System.err.printf("i=%d: Multiple trees.\n", fileidx);
        }
        ta = AlignmentUtils.fixAlignmentGridWithChineseTree(ta, chTrees);
        ta = AlignmentUtils.fixAlignmentGridMergingChinese(ta, chTrees);
        ta = AlignmentUtils.fixAlignmentGridWithEnglishTree(ta, enTrees);
        ta = AlignmentUtils.fixAlignmentGridMergingEnglish(ta, enTrees);
        AlignmentUtils.checkTranslationAlignmentAndEnTrees(ta, enTrees);
        AlignmentUtils.checkTranslationAlignmentAndChTrees(ta, chTrees);
        TreePair tp;
        //if (chParsedDir!=null) {
        if (useNonOracleTrees) {
          tp = new TreePair(ta, enTrees, chTrees, chParsedTrees);
        }
        else 
          tp = new TreePair(ta, enTrees, chTrees, chTrees);

        tp.setFileID(fileidx);
        treepairs_inFile.add(tp);
      }
      treepairs.addAll(treepairs_inFile);
    }

    System.err.println("Total Treepairs = "+treepairs.size());
    return treepairs;
  }

  static String wordAlignmentDir() {
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
    return dirname;
  }

  static String ctbDir() {
    String ctbdirname = "/scr/nlp/data/ldc/ctb6.0/data/utf8/bracketed/";
    //String ctbdirname = "/afs/ir/data/linguistic-data/Chinese-Treebank/6/data/utf8/bracketed/";
    File ctbdir = new File(ctbdirname);
    if (!ctbdir.exists()) {
      ctbdirname = "C:\\cygwin\\home\\Pichuan Chang\\data\\CTB6\\data\\utf8\\bracketed\\";
      ctbdir = new File(ctbdirname);
      if (!ctbdir.exists())
        throw new RuntimeException("CTB6.0 doesn't exist in either of the hard-coded locations.");
    }
    return ctbdirname;
  }

  static String etbDir() {
    String ectbdirname = "/u/nlp/scr/data/ldc/LDC2007T02-EnglishChineseTranslationTreebankV1.0/data/pennTB-style-trees/";
    File ectbdir = new File(ectbdirname);
    if (!ectbdir.exists()) {
      ectbdirname = "C:\\cygwin\\home\\Pichuan Chang\\data\\LDC2007T02-EnglishChineseTranslationTreebankV1.0\\data\\pennTB-style-trees\\";
      ectbdir = new File(ectbdirname);
      if (!ectbdir.exists()) {
        throw new RuntimeException("EnglishChineseTranslationTreebankV1.0 doesn't exist in either of the hard-coded locations.");
      }
    }
    return ectbdirname;
  }

  static String chParsedDir() {
    return "chParsed/";
  }


}