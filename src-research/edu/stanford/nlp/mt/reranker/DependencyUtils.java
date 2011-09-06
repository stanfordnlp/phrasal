package edu.stanford.nlp.mt.reranker;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.pennchinese.ChineseTreebankLanguagePack;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Filters;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Given two {@link Tree} or {@link GrammaticalStructure} and alignments, this
 * class includes some utility methods for extracting some information from
 * them.
 * 
 * @author Pi-Chuan Chang
 */
public class DependencyUtils {

  public static ClassicCounter<String> extractEnZhPathFeatures(Tree enT,
      Tree chT, Alignment align, boolean EnZh) {
    // GrammaticalStructure gs = new ChineseGrammaticalStructure(chT);
    Filter<String> puncWordFilter = Filters.acceptFilter();
    TreebankLanguagePack tlp = new ChineseTreebankLanguagePack();
    GrammaticalStructureFactory gsf = tlp
        .grammaticalStructureFactory(puncWordFilter);
    GrammaticalStructure gs = gsf.newGrammaticalStructure(chT);

    return extractEnZhPathFeatures(enT, gs, align, EnZh);

  }

  public static ClassicCounter<String> extractEnZhPathFeatures(Tree enT,
      GrammaticalStructure chGs, Alignment align, boolean EnZh) {
    return extractEnZhPathFeatures(enT, chGs, align, EnZh, false);
  }

  /**
   * 
   * @param enT
   *          the {@link Tree} of the English sentence
   * @param chGs
   *          the {@link GrammaticalStructure} for the Chinese sentence
   * @param align
   *          the {@link Alignment} between the two
   * @param EnZh
   *          If set to true, the method will extract features mapping from
   *          English to Chinese. If set to false, it will be the other way
   *          around.
   * @param retricted
   *          if true, features will only be extracted when the alignment is
   *          one-to-one. (We restrict it this way to get higher precision)
   * @return a {@link ClassicCounter} that has the information of what features
   *         are extracted
   */
  public static ClassicCounter<String> extractEnZhPathFeatures(Tree enT,
      GrammaticalStructure chGs, Alignment align, boolean EnZh,
      boolean retricted) {
    // starting from here, we have:
    // srcTree: chT
    // tgtTree: enT
    // alignment: align
    System.err.println("DEBUG: tree: " + enT);
    // GrammaticalStructure enGs = new EnglishGrammaticalStructure(enT);
    Filter<String> puncWordFilter;
    puncWordFilter = Filters.acceptFilter();
    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    GrammaticalStructureFactory gsf = tlp
        .grammaticalStructureFactory(puncWordFilter);
    GrammaticalStructure enGs = gsf.newGrammaticalStructure(enT);

    Collection<TypedDependency> deps = enGs.typedDependencies();
    for (TypedDependency d : deps) {
      System.err.println("DEBUG: enT: " + d);
    }
    System.err.println("--------------------------------------");
    if (EnZh) {
      return extractEnZhPathFeatures(enGs, chGs, align, EnZh, retricted);
    } else {
      return extractEnZhPathFeatures(chGs, enGs, align, EnZh, retricted);
    }
  }

  // TODO: use "restricted" in this method
  private static ClassicCounter<String> extractEnZhPathFeatures(
      GrammaticalStructure enGs, GrammaticalStructure chGs, Alignment align,
      boolean EnZh, boolean retricted) {
    ClassicCounter<String> features = new ClassicCounter<String>();

    SemanticGraph graph = SemanticGraphFactory.makeFromTree(enGs, "doc1", 0);
    SemanticGraph chGraph = SemanticGraphFactory.makeFromTree(chGs, "doc1", 0);

    List<IndexedWord> list = chGraph.vertexListSorted();

    for (SemanticGraphEdge edge : graph.edgeListSorted()) {
      // System.err.println("EDGE:"+edge);
      int govIdx = edge.getGovernor().index();
      // System.err.println("govIdx:"+govIdx);
      int depIdx = edge.getDependent().index();
      // System.err.println("depIdx:"+depIdx);
      String enReln = edge.getRelation().toString();

      // if govIdx and depIdx is bigger than 100, this happens because GIZA++
      // has
      // 100-word limit.
      // TODO: use William's aligner
      if (govIdx > 100 || depIdx > 100) {
        System.err
            .println("warning: alignments for sentences longer than 100 words were cropped by GIZA++");
      }

      List<Integer> chGovIdx = align.get(govIdx - 1, EnZh);
      List<Integer> chDepIdx = align.get(depIdx - 1, EnZh);
      for (int chGovI : chGovIdx) {
        if (chGovI >= list.size()) {
          break;
        }
        for (int chDepI : chDepIdx) {
          if (chDepI >= list.size()) {
            break;
          }
          List<SemanticGraphEdge> paths = 
            chGraph.getShortestUndirectedPathEdges(
              list.get(chGovI), list.get(chDepI));
          int startI = chGovI;
          StringBuilder sb = new StringBuilder();
          if (EnZh)
            sb.append("EnZh");
          else
            sb.append("ZhEn");
          sb.append("|").append(enReln).append("=>");
          if (paths == null) {
            continue;
          }
          for (SemanticGraphEdge path : paths) {
            int govid = list.indexOf(path.getGovernor());
            int depid = list.indexOf(path.getDependent());
            sb.append(path.getRelation());
            if (startI == govid) {
              startI = depid;
            } else if (startI == depid) {
              sb.append("R");
              startI = govid;
            } else {
              throw new RuntimeException("blah");
            }
            sb.append("-");
          }
          System.err.println("DEBUG: add: " + sb.toString());
          features.incrementCount(sb.toString());
        }
      }
    }
    System.err.println("DEBUG: add: ======================================");
    return features;
  }

  @Deprecated
  private static ClassicCounter<String> extractPathFeatures(Tree enT,
      GrammaticalStructure chGs, Alignment align, int lengLimit) {
    return extractPathFeatures(enT, chGs, align, lengLimit, false);
  }

  @Deprecated
  private static ClassicCounter<String> extractPathFeatures(Tree enT,
      GrammaticalStructure chGs, Alignment align, int lengLimit,
      boolean retrictedAlign) {
    Filter<String> puncWordFilter;
    puncWordFilter = Filters.acceptFilter();
    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    GrammaticalStructureFactory gsf = tlp
        .grammaticalStructureFactory(puncWordFilter);
    GrammaticalStructure enGs = gsf.newGrammaticalStructure(enT);

    return extractPathFeatures(enGs, chGs, align, lengLimit, retrictedAlign);
  }

  public static ClassicCounter<String> extractPathFeatures(
      GrammaticalStructure enGs, GrammaticalStructure chGs, Alignment align,
      int lengLimit) {
    return extractPathFeatures(enGs, chGs, align, lengLimit, false);
  }

  private static ClassicCounter<String> extractPathFeatures(
      GrammaticalStructure enGs, GrammaticalStructure chGs, Alignment align,
      int lengLimit, boolean restrictedAlign) {
    ClassicCounter<String> features = new ClassicCounter<String>();
    SemanticGraph enGraph = SemanticGraphFactory.makeFromTree(enGs, "doc1", 0);
    SemanticGraph chGraph = SemanticGraphFactory.makeFromTree(chGs, "doc1", 0);

    List<IndexedWord> enlist = enGraph.vertexListSorted();
    List<IndexedWord> chlist = chGraph.vertexListSorted();

    for (int i = 0; i < chlist.size(); i++) {
      for (int j = i; j < chlist.size(); j++) { 
        // Note: we actually consider j==i as well -- in the case that
        // English words are reduced (aligned) to one Chinese word, we
        // can see what kind of dependencies are reduced most
        List<SemanticGraphEdge> chPaths = chGraph.getShortestUndirectedPathEdges(
            chlist.get(i), chlist.get(j));
        // skip if chPaths doesn't exist or is too long
        if (chPaths == null)
          continue;
        if (chPaths.size() > lengLimit)
          continue;
        // if path do exist:
        List<Integer> enIs = align.get(i, false);
        List<Integer> enJs = align.get(j, false);
        if (restrictedAlign) {
          if (enIs.size() > 1 || enJs.size() > 1) {
            continue;
          }
        }
        StringBuilder sbC = new StringBuilder("PATH|");
        int start = i;
        for (SemanticGraphEdge p : chPaths) {
          sbC.append(p.getRelation().toString());
          int govIdx = p.getGovernor().index() - 1;
          int depIdx = p.getDependent().index() - 1;
          if (govIdx == start) {
            sbC.append("-");
            start = depIdx;
          } else if (depIdx == start) {
            sbC.append("R-");
            start = govIdx;
          } else {
            throw new RuntimeException(
                "either governoor or dependent should be the same as 'start'!!");
          }
        }

        for (int enIdxI = 0; enIdxI < enIs.size(); enIdxI++) {
          for (int enIdxJ = 0; enIdxJ < enJs.size(); enIdxJ++) {
            int enI = enIs.get(enIdxI);
            int enJ = enJs.get(enIdxJ);
            if (i == j) { // if chinese indices i==j, we're looking at the same
                          // list
              if (enI >= enJ)
                continue;
            }
            List<SemanticGraphEdge> enPaths = enGraph.getShortestUndirectedPathEdges(
                enlist.get(enI), enlist.get(enJ));
            if (enPaths == null)
              continue;
            if (enPaths.size() > lengLimit)
              continue;

            // if English path exist as well...
            StringBuilder sb = new StringBuilder(sbC.toString());
            sb.append("=>");
            int startE = enI;
            for (SemanticGraphEdge p : enPaths) {
              sb.append(p.getRelation().toString());
              int govIdx = p.getGovernor().index() - 1;
              int depIdx = p.getDependent().index() - 1;
              if (govIdx == startE) {
                sb.append("-");
                startE = depIdx;
              } else if (depIdx == startE) {
                sb.append("R-");
                startE = govIdx;
              } else {
                throw new RuntimeException(
                    "either governoor or dependent should be the same as 'startE'!!");
              }
            }
            System.err.println("Add: " + sb.toString());
            features.incrementCount(sb.toString());
          }
        }
      }
    }
    return features;
  }

  public static void main(String[] args) throws IOException {
    Tree chT = Tree.valueOf(args[0]);
    Tree enT = Tree.valueOf(args[1]);

    Filter<String> puncWordFilter = Filters.acceptFilter();
    TreebankLanguagePack tlp = new ChineseTreebankLanguagePack();
    GrammaticalStructureFactory gsf = tlp
        .grammaticalStructureFactory(puncWordFilter);
    GrammaticalStructure chG = gsf.newGrammaticalStructure(chT);

    System.err.println("DEBUG: tree: " + chT);
    Collection<TypedDependency> deps = chG.typedDependencies();
    for (TypedDependency d : deps) {
      System.err.println("DEBUG: chT: " + d);
    }
    Alignment al = Alignment.readOneAlignmentFromLine(args[2]);
    // extractEnZhPathFeatures(enT, chG, al, EnZh);
    extractPathFeatures(enT, chG, al, Integer.MAX_VALUE);
  }

}
