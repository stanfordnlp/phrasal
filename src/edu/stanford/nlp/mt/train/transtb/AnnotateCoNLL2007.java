package edu.stanford.nlp.mt.train.transtb;

import java.util.*;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.io.FileUtils;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.mt.syntax.mst.rmcd.io.DependencyWriter;
import edu.stanford.nlp.mt.syntax.mst.rmcd.io.DependencyReader;
import edu.stanford.nlp.mt.syntax.mst.rmcd.io.CONLLWriter;
import edu.stanford.nlp.mt.syntax.mst.rmcd.DependencyInstance;

import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;

/**
 * Reads in a CoNLL2007 file and annotate it with features
 * extracted from parallel treebank.
 * 
 * The input CoNLL2007 file contains target-language dependency structure, and
 * source-langage structure is read from penn trees passed as argument.
 * Word alignments must also be passed as argument.
 * 
 * Current assumpution: source-language is Chinese; all input files besides the CoNLL
 * file contain one sentence per line (no multi-line parses!).
 * 
 * @author Michel Galley
 */
public class AnnotateCoNLL2007 {

  private static final boolean DEBUG = false;

  // TODO: add crossing features
  // TODO: add valency
  public static final boolean FWORDS = Boolean.parseBoolean(System.getProperty("fWords","false"));

  static void usage() {
    System.err.println
     ("Usage: AnnotateCoNLL2007 (conll file) (target-sentences) (source-sentences) (source-trees) (source-target alignments) (headfinder)");
    System.exit(1);
  }

  /**
   * Dumps aligned trees. If no argument is provided, dump to an HTML page.
   * If file base name is provided, dump source-language treebank to base_name.f,
   * target-language treebank to base_name.e, and alignment to base_name.a.
   * @param args Optional argument
   * @throws Exception ??
   */
  @SuppressWarnings("unchecked")
  public static void main(String args[]) throws Exception {

    if(args.length != 8)
      usage();
    String conllFile = args[0];
    String eTextFile = args[1];
    String fTextFile = args[2];
    String fParseFile = args[3];
    String alignFile = args[4];
    String hfName = args[5];
    String featsStr = args[6];
    String outFile = args[7];

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

    //DependencyAnalyzer.setActiveTypes(types.split(":+"));

    // Read all source-language sentences:
    List<String> eText = FileUtils.linesFromFile(eTextFile);
    List<String> fText = FileUtils.linesFromFile(fTextFile);
    List<String> fParses = FileUtils.linesFromFile(fParseFile);
    List<String> align = FileUtils.linesFromFile(alignFile);
    final int totSent = fText.size();
    assert(totSent == fParses.size());
    assert(totSent == align.size());

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

      String[] eForms = dep.getForms();
      String[] eSent = eText.get(curSent).trim().split("\\s+");
      String[] fSent = fText.get(curSent).trim().split("\\s+");
      Tree fTree = fTrees.get(curSent);

      // Remove empty elements:
      fTree = fTree.prune(new BobChrisTreeNormalizer.EmptyFilter());

      // Check that CoNLL and eSent match:
      boolean fail = false;
      if(eForms.length != eSent.length+1)
        fail=true;
      else {
        for(int i=0; i<eSent.length; ++i) {
          String ef = eForms[i+1].toLowerCase();
          String es = eSent[i].toLowerCase();
          if(!ef.equals("<num>") && !ef.equals(es)) {
            fail=true; break;
          }
        }
      }
      if(fail) {
        System.err.println("forms: "+ Arrays.toString(eForms));
        System.err.println("sent: "+ Arrays.toString(eSent));
        System.err.printf("WARNING: CoNLL file does not match english sentences (see above).");
      }

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

      // Create alignment:
      SymmetricalWordAlignment wa = new SymmetricalWordAlignment
         (StringUtils.join(fSent), StringUtils.join(eSent), align.get(curSent));
      if(DEBUG) {
        System.out.println("f-tree: ");
        fTree.pennPrint();
      }

      // Add source-tree features (dependency structure):
      da.addUntypedPathsDistance2(fTree, chf);
      if(fts.contains("TYPE"))
        da.addTypedPaths(new ChineseGrammaticalStructure(fTree, puncWordFilter));

      List<String>[] feats = new List[wa.eSize()+1];
      for(int ei=0; ei<feats.length; ++ei)
        feats[ei] = new ArrayList<String>();
      for(int fi=0; fi<wa.fSize(); ++fi) {
        if(wa.f2e(fi).size() == 0)
          continue;
        Map<Integer, Set<String>> deps = da.getDeps(fi);
        for(Map.Entry<Integer, Set<String>> ry : deps.entrySet()) {
          int fid = ry.getKey();
          for(String tp : ry.getValue()) {
            if(fid < 0) {
              for(int ei : wa.f2e(fi))
                feats[ei+1].add(tp+"_0");
              continue;
            }
            if(wa.f2e(fid).size() == 0)
              continue;
            if(DEBUG) {
              System.err.printf("f-link: %s %s -> %s\n",tp,fSent[fi],fSent[fid]);
            }
            for(int eid : wa.f2e(fid)) {
              for(int ei : wa.f2e(fi)) {
                if(eid == ei)
                  continue;
                String pos = "_"+Integer.toString(eid+1);
                // Add pairwise features (features that file for specific word pairs):
                if(tp.startsWith("type=")) {
                  if(fts.contains("TYPE")) {
                    String[] path = tp.replaceAll("^type=","").split(":");
                    String dist = (path.length >= 0) ? getDist(path.length+1) : "na";
                    feats[ei+1].add("len="+dist+pos);
                    /*for(int i=0; i<path.length; ++i) {
                      String loc = (i==0) ? "S" : ((i+1==path.length) ? "E" : "M");
                      feats[ei+1].add("type"+loc+"="+path[i]);
                    }*/
                    if(path.length <= 3) {
                      feats[ei+1].add(tp+pos);
                    }
                  }
                } else {
                  if(fts.contains("PD0")) feats[ei+1].add(tp+pos);
                  if(fts.contains("PD1")) feats[ei+1].add(tp+getAtt(fi,fid)+pos);
                  if(fts.contains("PD2")) feats[ei+1].add(tp+getAttDist(fi,fid)+pos);
                  if(tp.equals("P")) {
                    // Add Chinese head as feature:
                    if(fts.contains("SHW")) feats[ei+1].add("SHW="+fSent[fid]);
                  }
                }
                if(DEBUG) {
                  System.err.printf("e-link: %s %s -> %s\n",tp,eSent[ei],eSent[eid]);
                }
              }
            }
          }
        }
      }
      
      // Add source-language features:
      for(int ei=0; ei<wa.eSize(); ++ei) {
        for(int fi : wa.e2f(ei)) {
          // Chinese word as a feature:
          if(fts.contains("SW")) feats[ei+1].add("SW="+fSent[fi]);
          // Chinese POS as a feature:
          Tree t = fTreeYield.get(fi);
          if(t != null) {
            t = t.parent(fTree);
            if(t != null) {
              if(fts.contains("POS0"))
                feats[ei+1].add("P0="+t.label());
              Tree pt = t.parent(fTree);
              if(pt != null) {
                if(fts.contains("POS1"))
                  feats[ei+1].add("P1="+t.label()+"^"+pt.label());
                Tree gpt = pt.parent(fTree);
                if(gpt != null) {
                  if(fts.contains("POS2"))
                    feats[ei+1].add("P2="+t.label()+"^"+pt.label()+"^"+gpt.label());
                }
              }
            }
          }
        }
      }
      // Convert features to arrays:
      for(int ei=0; ei<wa.eSize(); ++ei) {
        int sz = feats[ei].size();
        dep.setFeats(ei,feats[ei].toArray(new String[sz]));
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

