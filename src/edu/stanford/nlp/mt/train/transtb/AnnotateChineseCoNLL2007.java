package edu.stanford.nlp.mt.train.transtb;

import java.util.*;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.io.FileUtils;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.mt.syntax.mst.rmcd.io.DependencyReader;
import edu.stanford.nlp.mt.syntax.mst.rmcd.io.DependencyWriter;
import edu.stanford.nlp.mt.syntax.mst.rmcd.io.CONLLWriter;
import edu.stanford.nlp.mt.syntax.mst.rmcd.DependencyInstance;

/**
 * Annotate Chinese CoNLL2007 with dependency features extracted from Stanford parsed sentences.
 * extracted from parallel treebank.
 * 
 * @author Michel Galley
 */
public class AnnotateChineseCoNLL2007 {

  private static final boolean DEBUG = false;

  public static final boolean FWORDS = Boolean.parseBoolean(System.getProperty("fWords","false"));

  static void usage() {
    System.err.println
     ("Usage: AnnotateCoNLL2007 (txt) (dep-file) (pcfg-trees) (headfinder) (features) (out-file)");
    System.exit(1);
  }

  @SuppressWarnings("unchecked")
  public static void main(String args[]) throws Exception {

    if(args.length != 6)
      usage();
    String fTextFile = args[0]; // TODO: try removing
    String conllFile = args[1];
    String fParseFile = args[2];
    String hfName = args[3];
    String featsStr = args[4];
    String outFile = args[5];

    Set<String> fts = new HashSet<String>(Arrays.asList(featsStr.split("\\+")));
    System.err.println("features: "+fts.toString());

    // Determine head finder:
    HeadFinder chf;
    if("collins".equals(hfName))
      chf = new ChineseHeadFinder();
    else if("sunj".equals(hfName))
      chf = new SunJurafskyChineseHeadFinder();
    else if("sem".equals(hfName))
      chf = new ChineseSemanticHeadFinder();
    else if("bikel".equals(hfName))
      chf = new BikelChineseHeadFinder();
    else if("old".equals(hfName))
      chf = new OldChineseHeadFinder();
    else if("malt".equals(hfName))
      chf = new MaltTabHeadFinder(MaltTabHeadFinder.Lang.ZH);
    else
      throw new RuntimeException("unknown head finder: "+hfName);

    // Read all source-language sentences:
    List<String> fText = FileUtils.linesFromFile(fTextFile);
    List<String> fParses = FileUtils.linesFromFile(fParseFile);
    final int totSent = fText.size();
    assert(totSent == fParses.size());

    // Parse source-language trees:
    ChineseTreeReader ctr = new ChineseTreeReader();
    ctr.readMoreTrees(fParseFile);
    List<Tree> fTrees = ctr.trees_;
    if(totSent != fTrees.size())
      throw new RuntimeException(String.format("Wrong number of trees: %d != %d\n",totSent,fTrees.size())); 

    DependencyReader reader =
      DependencyReader.createDependencyReader(null, "CONLL", null);
    DependencyWriter writer =
         DependencyWriter.createDependencyWriter("CONLL");
    CONLLWriter.skipRoot(true);

    reader.startReading(conllFile, null, null);
    writer.startWriting(outFile);

    DependencyAnalyzer da = new DependencyAnalyzer();
    Filter<String> puncWordFilter = Filters.acceptFilter();

    int curSent=-1;
    DependencyInstance dep;
    while((dep = reader.getNext()) != null) {
      ++curSent;

      String[] fSent = fText.get(curSent).trim().split("\\s+");
      Tree fTree = fTrees.get(curSent);

      // Remove empty elements:
      fTree = fTree.prune(new BobChrisTreeNormalizer.EmptyFilter());

      // Check that tree and fSent match:
      List<HasWord> fYield = fTree.yield();
      List<Tree> fTreeYield = treeYield(fTree, new ArrayList<Tree>());
      assert(fYield.size() == fSent.length);
      for(int i=0; i<fSent.length; ++i) {
        String yw = fYield.get(i).word();
        String fw = fSent[i];
        if(!yw.equals(fw))
          System.err.printf("WARNING: mismatch: %s != %s\n",yw,fw);
      }

      if(DEBUG) {
        System.out.println("f-tree: ");
        fTree.pennPrint();
      }

      // Add source-tree features (dependency structure):
      da.addUntypedPathsDistance2(fTree, chf);
      if(fts.contains("TYPE"))
        da.addTypedPaths(new ChineseGrammaticalStructure(fTree, puncWordFilter));

      List<String>[] feats = new List[fSent.length+1];
      for(int fi=0; fi<fYield.size(); ++fi) {
        feats[fi+1] = new ArrayList<String>();
        Map<Integer, Set<String>> deps = da.getDeps(fi);
        for(Map.Entry<Integer, Set<String>> ry : deps.entrySet()) {
          int fid = ry.getKey();
          for(String tp : ry.getValue()) {

            if(DEBUG) {
              System.err.printf("f-link: %s %s -> %s\n",tp,fSent[fi],fSent[fid]);
            }

            String pos = "_"+Integer.toString(fid+1);
            
            // Add pairwise features (features that file for specific word pairs):
            if(tp.startsWith("type=")) {
              if(fts.contains("TYPE")) {
                String[] path = tp.replaceAll("^type=","").split(":");
                String dist = (path.length >= 0) ? getDist(path.length+1) : "na";
                feats[fi+1].add("len="+dist+pos);
                if(path.length <= 3) {
                  feats[fi+1].add(tp+pos);
                }
              }
            } else {
              if(fts.contains("PD0")) feats[fi+1].add(tp+pos);
              if(fts.contains("PD1")) feats[fi+1].add(tp+getAtt(fi,fid)+pos);
              if(fts.contains("PD2")) feats[fi+1].add(tp+getAttDist(fi,fid)+pos);
              if(tp.equals("P")) {
                // Add Chinese head as feature:
                if(fts.contains("SHW")) feats[fid+1].add("SHW="+fSent[fid]);
              }
            }
            if(DEBUG) {
              System.err.printf("e-link: %s %s -> %s\n",tp, fSent[fid], fSent[fid]);
            }
          }
        }
      }

      // Add source-language features:
      for(int fi=0; fi<fYield.size(); ++fi) {
        // Chinese POS as a feature:
        Tree t = fTreeYield.get(fi);
        if(t != null) {
          t = t.parent(fTree);
          if(t != null) {
            if(fts.contains("POS0"))
              feats[fi+1].add("P0="+t.label());
            Tree pt = t.parent(fTree);
            if(pt != null) {
              if(fts.contains("POS1"))
                feats[fi+1].add("P1="+t.label()+"^"+pt.label());
              Tree gpt = pt.parent(fTree);
              if(gpt != null) {
                if(fts.contains("POS2"))
                  feats[fi+1].add("P2="+t.label()+"^"+pt.label()+"^"+gpt.label());
              }
            }
          }
        }
      }
      // Convert features to arrays:
      for(int fi=0; fi<fYield.size(); ++fi) {
        int sz = feats[fi+1].size();
        dep.setFeats(fi+1,feats[fi+1].toArray(new String[sz]));
      }
      writer.write(dep);
    }
    writer.finishWriting();
  }

  static String getAttDist(int mi, int hi) {
    return "&"+getAtt(mi,hi)+"&"+getDist(mi,hi); 
  }

  static String getAtt(int mi, int hi) {
    assert(mi != hi);
    boolean attR = hi < mi;
    if(attR) return "R";
    return "L";
  }

  static String getDist(int mi, int hi) {
    int dist = Math.abs(mi-hi);
    return getDist(dist);
  }

  static String getDist(int dist) {
    String distBool;
    if (dist > 10)
      distBool = "10";
    else if (dist > 5)
      distBool = "5";
    else
      distBool = Integer.toString(dist - 1);
    return distBool;
  }

  static List<Tree> treeYield(Tree t, List<Tree> y) {
    if (t.isLeaf()) {
      y.add(t);
    } else {
      Tree[] kids = t.children();
      for(Tree kid : kids) {
        treeYield(kid,y);
      }
    }
    return y;
  }

}

