package mt.translationtreebank;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import java.io.*;
import java.util.*;

class FullInformationExperiment {

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

  public static void main(String args[]) throws IOException {
    String wordalignmentDir = wordAlignmentDir();
    String ctbDir = ctbDir();
    String etbDir = etbDir();

    List<TranslationAlignment> alignment_list = new ArrayList<TranslationAlignment>();
    ChineseTreeReader ctr = new ChineseTreeReader();
    EnglishTreeReader etr = new EnglishTreeReader();

    List<TreePair> treepairs = new ArrayList<TreePair>();
    int numNPwithDE = 0;

    // Open the hand-annotate file
    String finalCategoriesFile = "C:\\cygwin\\home\\Pichuan Chang\\javanlp\\projects\\mt\\src\\mt\\translationtreebank\\data\\finalCategories.txt";
    String finalNPFile = "C:\\cygwin\\home\\Pichuan Chang\\javanlp\\projects\\mt\\src\\mt\\translationtreebank\\data\\finalCategories_NP.txt";
    String finalFileID = "C:\\cygwin\\home\\Pichuan Chang\\javanlp\\projects\\mt\\src\\mt\\translationtreebank\\data\\finalCategories_fileid.txt";
    String finalNPID = "C:\\cygwin\\home\\Pichuan Chang\\javanlp\\projects\\mt\\src\\mt\\translationtreebank\\data\\finalCategories_npid.txt";

    List<Pair<String, String>>[] finalCategories = readFinalCategories(finalCategoriesFile,finalNPFile,finalFileID,finalNPID);
    



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
        System.err.println("Processing  "+fileidx);
        alignment_list = TranslationAlignment.readFromFile(file);
      } else {
        System.err.println("Skip "+fileidx);
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
          System.err.printf("i=%d: Can't find tree in CTB.\n", fileidx);
          continue;
          // skip for now
        } else if (chTrees.size() > 1) {
          throw new RuntimeException("i="+fileidx+": Multiple trees.");
        }
        
        List<Tree> enTrees = etr.getTreesWithWords(ta.translation_);
        if (enTrees.size() == 0) {
          System.err.printf("i=%d: Can't find tree in PTB.\n", fileidx);
          continue;
          // skip for now
        } else if (enTrees.size() > 1) {
          System.err.printf("i=%d: Multiple trees.\n", fileidx);
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

    TwoDimensionalCounter<String,String> cc = new TwoDimensionalCounter<String,String>();
    ClassicCounter<String> deTypeCounter = new ClassicCounter<String>();

    PrintWriter bothPW = new PrintWriter(new BufferedWriter(new FileWriter("DEC_DEG.txt")));
    PrintWriter decPW  = new PrintWriter(new BufferedWriter(new FileWriter("DEC.txt")));
    PrintWriter degPW  = new PrintWriter(new BufferedWriter(new FileWriter("DEG.txt")));

    for(TreePair validSent : treepairs) {
      for(Map.Entry<IntPair, String> labeledNPs : validSent.NPwithDEs_categories.entrySet()) {
        String np = validSent.chNPwithDE(labeledNPs.getKey());
        np = np.trim();
        String type = labeledNPs.getValue();
        // TODO: Build examples from <features(labeledNPs), labeledNPs.getValue()>
        Tree chTree = validSent.chTrees.get(0);
        IntPair chNPrange = labeledNPs.getKey();
        Tree chNPTree = TranslationAlignment.getTreeWithEdges(chTree,chNPrange.getSource(), chNPrange.getTarget()+1);
        //chNPTree.pennPrint();
        //System.out.println("------------------------------------------------");

        TreePattern de = TreePattern.compile("DEC|DEG < 的");
        TreeMatcher deM = de.matcher(chNPTree);

        TreePattern dec = TreePattern.compile("DEC < 的");
        TreeMatcher decM = dec.matcher(chNPTree);

        TreePattern deg = TreePattern.compile("DEG < 的");
        TreeMatcher degM = deg.matcher(chNPTree);

        int deCount = 0;
        while(deM.find()) {
          deCount++;
        }
        
        boolean isDEC = decM.find();
        boolean isDEG = degM.find();

        if (deCount > 1) {
          //chNPTree.pennPrint(bothPW);
          if(!type.equals("multi-DEs")) {
            chNPTree.pennPrint(bothPW);
            bothPW.println("type="+type);
            bothPW.println("---------------------------------");
          }
          deTypeCounter.incrementCount(">1DE");
        } else if (deCount == 1) {
          if (isDEC && !isDEG) {
            chNPTree.pennPrint(decPW);
            deTypeCounter.incrementCount("DEC");
          } else if (isDEG && !isDEC) {
            chNPTree.pennPrint(degPW);
            deTypeCounter.incrementCount("DEG");
          } else {
            chNPTree.pennPrint(System.err);
            throw new RuntimeException("both?");
          }
        }
        else {
          chNPTree.pennPrint(System.err);
          throw new RuntimeException("");
        }
      }
    }
    bothPW.close();
    decPW.close();
    degPW.close();
    System.out.println(deTypeCounter);
  }
}
