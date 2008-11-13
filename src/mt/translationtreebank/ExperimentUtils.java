package mt.translationtreebank;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import java.io.*;
import java.util.*;

public class ExperimentUtils {


  static TreePattern dec = TreePattern.compile("DEC < 的");
  static TreePattern deg = TreePattern.compile("DEG < 的");
  static TreePattern de = TreePattern.compile("DEG|DEC < 的");
  static TreePattern va1 = TreePattern.compile("CP <, (IP <- (VP <: VA)) <- DEC");
  static TreePattern va2 = TreePattern.compile("CP <, (IP <- (VP <, (ADVP $+ (VP <: VA)))) <- DEC");
  static TreePattern adjpdeg = TreePattern.compile("DNP <, ADJP <- DEG");
  static TreePattern qpdeg = TreePattern.compile("DNP <, QP <- DEG");
  static TreePattern nppndeg = TreePattern.compile("DNP <, (NP < PN) <- DEG");

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

  static boolean hasDEC(Tree t) {
    TreeMatcher decM = dec.matcher(t);
    return decM.find();
  }

  static boolean hasDEG(Tree t) {
    TreeMatcher degM = deg.matcher(t);
    return degM.find();
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
    List<Pair<String, String>>[] result = new List[326];
    int[] maxNP = new int[326];

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
      int npid = Integer.parseInt(npids[i]);
      //result[fileid][npid].add(pair);
      result[fileid].add(pair);
    }
    return result;
  }


  static List<Pair<String, String>>[] readFinalCategories(String allFile) {
    return readFinalCategories(allFile, true);
  }

  static List<Pair<String, String>>[] readFinalCategories(String allFile, Boolean useReducedCategories) {
    String content = StringUtils.slurpFileNoExceptions(allFile);
    String[] lines = content.split("\\n");

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
      String npidStr = fields[1];
      String categoriesStr = fields[2];
      String npStr = fields[3];

      if (useReducedCategories)
        categoriesStr = normCategory(categoriesStr);
      else
        categoriesStr = categoriesStr;

      Pair<String, String> pair = new Pair<String, String>(categoriesStr, npStr);
      fileidStr = fileidStr.replaceAll("[^\\d]","");
      int fileid = Integer.parseInt(fileidStr);
      int npid = Integer.parseInt(npidStr);
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

  static String ctbDir() {
    String ctbdirname = "/afs/ir/data/linguistic-data/Chinese-Treebank/6/data/utf8/bracketed/";
    File ctbdir = new File(ctbdirname);
    if (!ctbdir.exists()) {
      ctbdirname = "C:\\cygwin\\home\\Pichuan Chang\\data\\CTB6\\data\\utf8\\bracketed\\";
      ctbdir = new File(ctbdirname);
      if (!ctbdir.exists())
        throw new RuntimeException("CTB6.0 doesn't exist in either of the hard-coded locations.");
    }
    return ctbdirname;
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

    
  static List<TreePair> readAnnotatedTreePairs() throws IOException {
    return readAnnotatedTreePairs(true);
  }

  static List<TreePair> readAnnotatedTreePairs(Boolean useReducedCategories) throws IOException {
    String wordalignmentDir = wordAlignmentDir();
    String ctbDir = ctbDir();
    String etbDir = etbDir();

    List<TranslationAlignment> alignment_list = new ArrayList<TranslationAlignment>();
    ChineseTreeReader ctr = new ChineseTreeReader();
    EnglishTreeReader etr = new EnglishTreeReader();

    List<TreePair> treepairs = new ArrayList<TreePair>();
    int numNPwithDE = 0;

    // Open the hand-annotate file
    String finalCategoriesFile = "C:\\cygwin\\home\\Pichuan Chang\\javanlp\\projects\\mt\\src\\mt\\translationtreebank\\data\\finalCategories_all.txt";
    List<Pair<String, String>>[] finalCategories = readFinalCategories(finalCategoriesFile, useReducedCategories);
    
    for(int fileidx = 1; fileidx <= 325; fileidx++) {
      // Everytime, restart them so that when we get trees,
      // we won't match tree & sentences in different files.
      alignment_list = new ArrayList<TranslationAlignment>();
      ctr = new ChineseTreeReader();
      etr = new EnglishTreeReader();


      // (1) Read alignment files
      String aname = String.format("%schtb_%03d.txt", wordalignmentDir, fileidx);
      File file = new File(aname);
      if (file.exists()) {
        //System.err.println("Processing  "+fileidx);
        alignment_list = TranslationAlignment.readFromFile(file);
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


      // (4) Going through entries in (1) and check if they exist in (2)
      // (5) Going through entries in (1) and check if they exist in (3)
      // (6) also, if the tests passed, this is going to the final examples
      int taidx = 1;
      List<TreePair> treepairs_inFile = new ArrayList<TreePair>();
      for (TranslationAlignment ta : alignment_list) {
        List<Tree> chTrees = ctr.getTreesWithWords(ta.source_);
        if (chTrees.size() == 0) {
          //System.err.printf("i=%d: Can't find tree in CTB.\n", fileidx);
          continue;
          // skip for now
        } else if (chTrees.size() > 1) {
          throw new RuntimeException("i="+fileidx+": Multiple trees.");
        }
        
        List<Tree> enTrees = etr.getTreesWithWords(ta.translation_);
        if (enTrees.size() == 0) {
          //System.err.printf("i=%d: Can't find tree in PTB.\n", fileidx);
          continue;
          // skip for now
        } else if (enTrees.size() > 1) {
          //System.err.printf("i=%d: Multiple trees.\n", fileidx);
        }
        ta = TranslationAlignment.fixAlignmentGridWithChineseTree(ta, chTrees);
        ta = TranslationAlignment.fixAlignmentGridMergingChinese(ta, chTrees);
        ta = TranslationAlignment.fixAlignmentGridWithEnglishTree(ta, enTrees);
        ta = TranslationAlignment.fixAlignmentGridMergingEnglish(ta, enTrees);
        TranslationAlignment.checkTranslationAlignmentAndEnTrees(ta, enTrees);
        TranslationAlignment.checkTranslationAlignmentAndChTrees(ta, chTrees);
        TreePair tp = new TreePair(ta, enTrees, chTrees);
        treepairs_inFile.add(tp);
        numNPwithDE += tp.NPwithDEs.size();
      }
      // Important: Read the categories of each NPwithDEs
      TreePair.annotateNPwithDEs(finalCategories[fileidx], treepairs_inFile);
      treepairs.addAll(treepairs_inFile);
    }
    System.err.println("Total Treepairs = "+treepairs.size());
    System.err.println("numNPwithDE = "+numNPwithDE);
    return treepairs;
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

  private static String coarseCategory(String cat) {
    String normcat;
    if (cat.startsWith("B") || cat.equals("relative clause")) {
      normcat = "swapped";
    } else if (cat.startsWith("A")) {
      normcat = "ordered";
    } else {
      normcat = "other";
      //throw new RuntimeException("Can't find coarse category for " + cat);
    }
    return normcat;
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
}
