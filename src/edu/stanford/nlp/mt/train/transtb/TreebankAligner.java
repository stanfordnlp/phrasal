package edu.stanford.nlp.mt.train.transtb;

import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.pennchinese.ChineseUtils;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.StringUtils;

import java.util.*;
import java.util.zip.GZIPInputStream;
import java.io.*;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;

/**
 * Generate a sentence-aligned parallel treebank from CTB and its translation as distributed
 * by LDC.
 *
 * @author Michel Galley
 */
public class TreebankAligner {

  // Note: Simpler approach to alignment compared to Pichuan's implementation, since
  // we don't worry here about word alignment (just sentence alignment).

  private final String inDir, uDir, aDir;
  private int fileidx;

  private int goodAl=0, totalAl=0;

  static class TreeYield { // A tree and corrected yield:
    Tree tree;
    String[] yield;
    TreeYield(Tree t, String[] y) { tree = t; yield = y; }
  }

  public static void main(String[] args) throws IOException {
    if(args.length == 1)
      new TreebankAligner(args[0]).align();
    else if(args.length == 2)
      new TreebankAligner(args[0],args[1]).align();
    else
			System.err.println("Usage: java edu.stanford.nlp.mt.train.transtb.TreebankAligner (outBasename)");
  }

  public TreebankAligner(String outDir) {
    this.inDir = null;
    this.aDir = outDir+"/";
    this.uDir = outDir+".unaligned/";
    File af = new File(aDir);
    File uf = new File(uDir);
    if(af.exists()) {
      System.err.println("Target directory exists already : "+af.getAbsolutePath());
      System.exit(1);
    }
    if(uf.exists()) {
      System.err.println("Target directory exists already : "+uf.getAbsolutePath());
      System.exit(1);
    }
    ErasureUtils.noop(af.mkdir());
    ErasureUtils.noop(uf.mkdir());
  }

  public TreebankAligner(String inDir, String outDir) {
    this.inDir = inDir+"/";
    this.uDir = null;
    this.aDir = outDir+"/";
    File in = new File(inDir);
    File af = new File(aDir);
    if(!in.exists()) {
      System.err.println("Input directory does not exist: "+in.getAbsolutePath());
      System.exit(1);
    }
    if(af.exists()) {
      System.err.println("Target directory exists already : "+af.getAbsolutePath());
      System.exit(1);
    }
    ErasureUtils.noop(af.mkdir());
  }

  public void align() throws IOException {

    String wordalignmentDir = TransTBUtils.wordAlignmentDir();
    String ctbDir = TransTBUtils.ctbDir();
    String etbDir = TransTBUtils.etbDir();

    for(fileidx = 1; fileidx <= 325; fileidx++) {
      // Everytime, restart them so that when we get trees,
      // we won't match tree & sentences in different files.
      //alignment_list = new ArrayList<TranslationAlignment>();

      String aname = String.format("%schtb_%03d.txt", wordalignmentDir, fileidx);
      File file = new File(aname);
      boolean aligned = file.exists(), autoAligned=false;
      if(!aligned) {
        aname = String.format("%s%d_zh.snt.aligned", inDir, fileidx);
        file = new File(aname);
        aligned = file.exists();
        if(aligned) autoAligned=true;
      }
      System.err.printf("Processing: %d (%s)\n", fileidx, aligned?"aligned":"unaligned");

      // Read Chinese Trees:
      List<TreeYield> cTrees = new ArrayList<TreeYield>();
      String ctbname = String.format("%schtb_%04d.fid", ctbDir, fileidx);
      readTrees(ctbname, cTrees, true);
      System.err.println("Processing: "+ctbname);

      // Read English Trees:
      List<TreeYield> eTrees = new ArrayList<TreeYield>();
      String ename = String.format("%schtb_%03d.mrg.gz", etbDir, fileidx);
      readTrees(ename, eTrees, false);
      System.err.println("Processing: "+ename);

      // Read alignment files:
      if(aligned) {
        System.err.println("Processing: "+file);
        List<TranslationAlignment> align = autoAligned ?
          AlignmentUtils.readFromSentenceAlignedFiles(aname, aname.replace("_zh.snt","_en.snt")) :
          AlignmentUtils.readFromFile(file);
        printAlignedData(cTrees, eTrees, align);
      } else {
        assert(uDir != null);
        printUnalignedData(cTrees, "_zh", true);
        printUnalignedData(eTrees, "_en", false);
      }
    }
    System.err.printf("Good alignments: %d/%d = %f\n", goodAl, totalAl, goodAl*1.0/totalAl);
  }

  public void printAlignedData(List<TreeYield> c, List<TreeYield> e, List<TranslationAlignment> align) {

    PrintStream
      cStringOut = IOTools.getWriterFromFile(String.format("%s%03d_zh.snt",aDir,fileidx)),
      cTreeOut = IOTools.getWriterFromFile(String.format("%s%03d_zh.trees",aDir,fileidx)),
      eStringOut = IOTools.getWriterFromFile(String.format("%s%03d_en.snt",aDir,fileidx)),
      eTreeOut = IOTools.getWriterFromFile(String.format("%s%03d_en.trees",aDir,fileidx));

    Map<Sequence<String>,Tree> cMap = yield2treeMap(c), eMap = yield2treeMap(e);
    
    // Match trees using alignment:
    for (TranslationAlignment ta : align) {
      String cNorm = fix(StringUtils.join(ta.source_), true);
      String eNorm = fix(StringUtils.join(ta.translation_), false);
      Sequence<String> cStr = new SimpleSequence<String>(true, cNorm.split("\\s+"));
      Sequence<String> eStr = new SimpleSequence<String>(true, eNorm.split("\\s+"));
      // Max match:
      List<Tree> cTrees = maxMatch(cStr,cMap);
      List<Tree> eTrees = maxMatch(eStr,eMap);
      if(cTrees != null && eTrees != null) {
        //System.err.printf("ctrees: %d\n", cTrees.size());
        printTrees(cTrees, cTreeOut);
        printYields(cTrees, cStringOut, true);
        //System.err.printf("etrees: %d\n", eTrees.size());
        printTrees(eTrees, eTreeOut);
        printYields(eTrees, eStringOut, false);
        ++goodAl;
      }
      ++totalAl;
    }
    cStringOut.close();
    eStringOut.close();
    cTreeOut.close();
    eTreeOut.close();
  }

  List<Tree> maxMatch(Sequence<String> str, Map<Sequence<String>,Tree> trees) {
    int si = 0, matched = 0;
    boolean error = false;
    int errorCount = 0;
    List<Tree> matchedTrees = new ArrayList<Tree>();
    while(si < str.size()) {
      //System.err.printf("Matching %d-%d\n",si,str.size());
      for(int ei = str.size(); ei>si; --ei) {
        Sequence<String> seq = str.subsequence(si,ei);
        //System.err.printf("Trying %d-%d: {{{%s}}}\n", si, ei, seq.toString());
        if(trees.containsKey(seq)) {
          //System.err.printf("Found match at %d (len=%d)\n", si, seq.size());
          Tree t = trees.get(seq);
          matchedTrees.add(t);
          //so.print(prettyYield(yield(t)));
          //to.print(t.toString());
          si += seq.size();
          matched += seq.size();
          error = false;
          break;
        }
        if(si+1==ei) {
          if(!error) {
            System.err.printf("Failed to find tree matches for: {{{%s}}}\n", str.subsequence(si,str.size()).toString());
            for(Map.Entry<Sequence<String>,Tree> e : trees.entrySet()) {
              System.err.printf("    candidate: {{{%s}}}\n",e.getKey());
            }
          }
          System.err.printf("SKIPPING(%d): {{{%s}}}\n", si, str.get(si));
          ++si;
          ++errorCount;
          error=true;
        }
      }
    }
    return (errorCount < 5 && matched > 1) ? matchedTrees : null;
  }

  public void printUnalignedData(List<TreeYield> trees, String extension, boolean isChinese) {

    PrintStream stringOut = IOTools.getWriterFromFile(uDir+fileidx+extension+".snt");
    PrintStream treeOut = IOTools.getWriterFromFile(uDir+fileidx+extension+".trees");
    
    for(TreeYield t : trees) {
      treeOut.println(t.tree.toString());
      stringOut.println(prettyYield(yield(t.tree), isChinese));
    }
    stringOut.close();
    treeOut.close();
  }

  public void printTrees(List<Tree> trees, PrintStream os) {
    if(trees.size() > 1)
      os.print("( ");
    for(Tree t : trees)
      os.print(t.toString()+" ");
      //os.print(t.skipRoot().toString()+" ");
    if(trees.size() > 1)
      os.print(")");
    os.println();
  }

  public void printYields(List<Tree> trees, PrintStream os, boolean isChinese) {
    for(Tree t : trees)
      os.print(prettyYield(yield(t), isChinese)+" ");
    os.println();
  }

  public void readTrees(String filename, List<TreeYield> trees, boolean isChinese) throws IOException {
    Reader reader;
    if (filename.endsWith(".gz")) {
      reader = new InputStreamReader(new GZIPInputStream(new FileInputStream(filename)));
    } else {
      reader = new FileReader(filename);
    }

    Iterator<Tree> i = new TreeTokenizerFactory
       (new PennTreeReaderFactory()).getTokenizer(new BufferedReader(reader));

    while(i.hasNext()) {
      Tree t = i.next();
      if(t.toString().startsWith("(")) {
        String[] y = fix(yield(t), isChinese).split("\\s+");
        trees.add(new TreeYield(t,y));
      }
    }
  }

  Map<Sequence<String>,Tree> yield2treeMap(List<TreeYield> trees) {
    Map<Sequence<String>,Tree> m = new HashMap<Sequence<String>,Tree>();
    for(TreeYield t : trees)
      m.put(new SimpleSequence<String>(t.yield),t.tree);
    return m;
  }

  static String normalizeZh(String s) {
    return ChineseUtils.normalize(s, ChineseUtils.ASCII, ChineseUtils.ASCII, ChineseUtils.LEAVE);
  }

  /// Ugly code below to fix inconsistencies between the three LDC releases
  /// (alignment, e-trees, c-trees)

  static String fix(String s, boolean isChinese) {
    //System.err.println("n-in: "+s);
    if(isChinese)
      s = normalizeZh(s);
    s = s.toLowerCase();
    s = revertTB(s);
    s = isChinese ? fixZh(s) : fixEn(s);
    s = s.replaceAll("^\\s+","");
    s = s.replaceAll("\\s+$","");
    //System.err.println("n-out: "+s);
    return s;
  }

  static String fixZh(String s) {
    s = s.replaceAll("[\\d]+", "D");
    s = s.replaceAll("[a-z]"," ");
    //s = s.replaceAll("[\\d０-９]+", "D");
    //s = s.replaceAll("[Ａ-Ｚａ-ｚa-z]"," ");
    s = s.replaceAll(""," ");
    s = s.replaceAll("─"," ");
    s = s.replaceAll("━"," ");
    s = s.replaceAll(">", " ");
    return s;
  }

  static String fixEn(String s) {
    s = s.replaceAll("[^\\p{ASCII}]"," ");
    // Various normalizations that simplify the matching:
    s = s.replaceAll("\\?{3,}","");
    s = s.replaceAll("(\\p{Punct})"," $1 ");
    s = s.replaceAll("\\d+", "D");
    s = s.replaceAll("\\s+"," ");
    s = s.replaceAll("^\\s+","");
    s = s.replaceAll("\\s+$","");
    // Fix errors in aligned sentences:
    s = s.replaceAll(" Dth "," D ");
    s = s.replaceAll(" -$","");
    s = s.replaceAll(" etc \\. \\."," etc .");
    s = s.replaceAll(" etc$"," etc .");
    s = s.replaceAll(" china s ", " china ' s ");
    s = s.replaceAll(" fang fang guizhou$", " fang fang");
    s = s.replaceAll("^guizhou mao - tai ", "mao - tai ");
    s = s.replace("new high level technology industries become shantou ' s economy ' s new growth spot",
                  "at present , new high level technology has become shantou ' s economy ' s new growth spot .");
    s = s.replace(" ( telephotograph )",""); 
    return s;
  }

  static String revertTB(String s) {
    s = s.replaceAll("\\\\/"," / ");
    s = s.replaceAll("(?i)isn ' t","is n't");
    s = s.replaceAll("(?i)aren ' t","are n't");
    s = s.replaceAll("(?i)didn ' t","did n't");
    s = s.replaceAll("(?i)cannot","can not");
    s = s.replaceAll("(?i)-lrb-","(");
    s = s.replaceAll("(?i)-rrb-",")");
    s = s.replaceAll("(?i)-lsb-","[");
    s = s.replaceAll("(?i)-rsb-","]");
    s = s.replaceAll("(?i)-lcb-","{");
    s = s.replaceAll("(?i)-rcb-","}");
    s = s.replaceAll("``","\"");
    s = s.replaceAll("''","\"");
    s = s.replaceAll("、",",");
    s = s.replaceAll("“","\"");
    s = s.replaceAll("”","\"");
    s = s.replaceAll("《","\"");
    s = s.replaceAll("》","\"");
    s = s.replaceAll("〈","'");
    s = s.replaceAll("〉","'");
    s = s.replaceAll("‘","'");
    s = s.replaceAll("’","'");
    s = s.replaceAll("—","-");
    //s = s.replaceAll("・"," ・"); // middot
    //s = s.replaceAll("·"," ·"); // middot
    //s = s.replaceAll("——","--");
    //s = s.replaceAll("———","---");
    return s;
  }

  static String prettyYield(String s, boolean isChinese) {
    if(isChinese)
      s = normalizeZh(s);
    return revertTB(s);
  }

  static String yield(Tree t) {
    Tree t2 = t.prune(new BobChrisTreeNormalizer.EmptyFilter());
    return Sentence.listToString(t2.yield(), false);
  }


}
