package mt.train.transtb;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.pennchinese.ChineseHeadFinder;
import edu.stanford.nlp.ling.CyclicCoreLabel;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Filters;

import java.util.*;
import java.io.*;

public class DumpCoNLL2007 {

  static void printTreesAndAlignment(TreePair treepair, TranslationAlignment alignment) {
    treepair.printTreePair();
    AlignmentUtils.printAlignmentGrid(alignment);
  }

  /**
   * Dumps aligned trees. If no argument is provided, dump to an HTML page.
   * If file base name is provided, dump source-language treebank to base_name.f,
   * target-language treebank to base_name.e, and alignment to base_name.a.
   * @param args Optional argument
   * @throws Exception ??
   */
  public static void main(String args[]) throws Exception {

    List<TreePair> treepairs;
    Boolean reducedCategory = true;
    Boolean nonOracleTree = false;
    Filter<Dependency> dependencyFilter = Filters.acceptFilter();
    HeadFinder chf = new ChineseHeadFinder();
    HeadFinder ehf = new ModCollinsHeadFinder();
    
    PrintStream ps = System.out;

    // This call reads in the data, including :
    // CTB, E-C translation treebank, word alignment,
    // and also the "category" of the DEs under NPs.
    // Each TreePair is kinda like a sentence pair in the parallel text.
    // In the data you read in, every treepair must have one Chinese tree,
    // but could have more than one English trees.
    // Other than trees, the "alignment" data member is also useful 
    // for general purpose.
    treepairs = TransTBUtils.readAnnotatedTreePairs(reducedCategory, nonOracleTree);

    for(TreePair validSent : treepairs) {
      // In our dataset, every TreePair actually only just have one 
      // Chinese sentence(tree). There were no cases when there are 
      // multiple Chinese trees aligned to English trees
      Tree chTree = validSent.chTrees().get(0);
      // English trees should be one or more
      List<Tree> enTrees = validSent.enTrees();
      // This is the alignment
      TranslationAlignment alignment = validSent.alignment;

      // Analyze Chinese dependency structure:
      List<String> zhH = new ArrayList<String>();
      List<String> zhD = new ArrayList<String>();
      System.err.println("ZH TREE: "+chTree.toString());
      Sentence<TaggedWord> ctagged;
      {
        Tree indexedTree = chTree.deeperCopy(chTree.treeFactory(),
             CyclicCoreLabel.factory());
        ctagged = indexedTree.taggedYield();
        indexedTree.indexLeaves();
        Set<Dependency> depsSet = indexedTree.mapDependencies(dependencyFilter, chf);
        List<Dependency> sortedDeps = new ArrayList<Dependency>(depsSet);
        Collections.sort(sortedDeps, Dependencies.dependencyIndexComparator());
        assert(ctagged.size() == sortedDeps.size()+1);

        for (int wi=0, di=0; wi<ctagged.size(); ++wi, ++di) {
          TaggedWord w = ctagged.get(wi);
          int depi=-1, govi=-1;
          if(di < sortedDeps.size()) {
            Dependency d = sortedDeps.get(di);
            CoreMap dep = (CoreMap) d.dependent();
            CoreMap gov = (CoreMap) d.governor();
            depi = dep.get(CoreAnnotations.IndexAnnotation.class);
            govi = gov.get(CoreAnnotations.IndexAnnotation.class);
          }
          String dStr = w.word(), hStr;
          if(wi+1 != depi)  {
            --di;
            hStr = "ROOT";
          } else {
            hStr = ctagged.get(govi-1).word();
          }
          zhD.add(dStr);
          zhH.add(hStr);
          System.err.printf("dep=%s head=%s\n",dStr,hStr);
        }
      }

      // 0  dep=small    head=car   d=zh(small) h=zh(car)
      // 1  dep=car      head=sold  d=zh(car)   h=zh(sold)

      // Print English dependency structure:
      for(Tree outputTree : enTrees) { // TODO: merge if more than 1
        System.err.println("EN TREE: "+outputTree.toString());
        Tree indexedTree = outputTree.deeperCopy(outputTree.treeFactory(),
                                                 CyclicCoreLabel.factory());
        //GrammaticalStructure gs = gsf.newGrammaticalStructure(outputTree);
        //List<TypedDependency> tdep = gs.typedDependenciesCCprocessed(true);
        Sentence<TaggedWord> etagged = indexedTree.taggedYield();
        indexedTree.indexLeaves();
        Set<Dependency> depsSet = indexedTree.mapDependencies(dependencyFilter, ehf);
        List<Dependency> sortedDeps = new ArrayList<Dependency>(depsSet);
        Collections.sort(sortedDeps, Dependencies.dependencyIndexComparator());
        assert(etagged.size() == sortedDeps.size()+1);

        //System.err.printf("tagged=%d tdep=%d sorteddeps=%d\n",tagged.size(),tdep.size(),sortedDeps.size());
        for (int wi=0, di=0; wi<etagged.size(); ++wi, ++di) {
          TaggedWord w = etagged.get(wi);
          int depi=-1, govi=-1;
          if(di < sortedDeps.size()) {
            Dependency d = sortedDeps.get(di);
            CoreMap dep = (CoreMap) d.dependent();
            CoreMap gov = (CoreMap) d.governor();
            depi = dep.get(CoreAnnotations.IndexAnnotation.class);
            govi = gov.get(CoreAnnotations.IndexAnnotation.class);
          }
          ps.print(wi+1); ps.print("\t");
          ps.print(w.word()); ps.print("\t_\t");
          ps.print(w.tag()); ps.print("\t_\t");

          // Add features:
          int[][] m = alignment.matrix_;
          int z=wi+1;
          boolean first=true;
          for(int i=1; i<m[z].length; ++i) {
            if(m[z][i] != 0) {
              if(!first)
                ps.print("|");
              first = false;
              ps.printf("zd=%s|zh=%s",substring(zhD.get(i-1),0,3),substring(zhH.get(i-1),0,3));
              System.err.printf("al: %d-%d %s-%s\n",z,i,etagged.get(z-1),ctagged.get(i-1));
            }
          }
          if(first)
            ps.print("_");
          ps.print("\t");
          if(wi+1 != depi)  {
            ps.print("0\tROOT\t"); --di;
          } else {
            ps.print(govi); ps.print("\t");
            ps.print("_\t");
          }
          // Done:
          ps.print("_\t_\n");
        }
        ps.println();
      }

      /*fWriter.println(skipRoot(chTree.getChild(0)).toString());
      StringBuffer buf = new StringBuffer();
      if(enTrees.size() > 1)
        buf.append("( ");
      for(int i=0; i<enTrees.size(); ++i) {
        if(i>0)
          buf.append(" ");
        buf.append(skipRoot(enTrees.get(i)).toString());
      }
      if(enTrees.size() > 1)
        buf.append(" )");
      eWriter.println(buf.toString());
      AbstractWordAlignment al = new SymmetricalWordAlignment();
      al.init(alignment.matrix_);
      aWriter.println(al.toString());*/
    }
  }

	/** Returns child if it is unique and the current label is "ROOT".
   *
   * @return
   */
  static Tree skipRoot(Tree t) {
    return (t.isUnaryRewrite() && "ROOT".equals(t.label().value())) ? t.firstChild() : t;
  }

  static String substring(String s, int i, int j) {
    return (s.length() <= j) ? s : s.substring(i,j);
  }
}
