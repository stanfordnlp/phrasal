package mt.train.transtb;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.trees.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CyclicCoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Filters;
import edu.stanford.nlp.util.StringUtils;

import java.util.*;
import java.io.IOException;

/**
 * @author Michel Galley
 */
public class DependencyAnalyzer {

  private List<HasWord> yield;
  private Map<Integer,Set<String>>[] deps;

  public static void main(String[] args) throws IOException {

    String parseFile = args[0];

    HeadFinder ehf = new ModCollinsHeadFinder();
    EnglishTreeReader ectr = new EnglishTreeReader();
    Filter<String> puncWordFilter = Filters.acceptFilter();

    ectr.readMoreTrees(parseFile);
    List<Tree> trees = ectr.trees_;
    DependencyAnalyzer da = new DependencyAnalyzer();
    for(Tree t : trees) {
      da.addUntypedPathsDistance2(t, ehf);
      GrammaticalStructure gs = new EnglishGrammaticalStructure(t, puncWordFilter);
      da.addTypedPaths(gs);
      da.printDeps();
    }
  }

  public Map<Integer,Set<String>> getDeps(int i) {
    return deps[i];
  }

  public void printDeps() {
    if(deps != null) {
      System.err.println("sent: "+ StringUtils.join(yield," "));
      for(int i=0; i<deps.length; ++i) {
        System.out.printf("w[%02d] %s\n",i,yield.get(i));
        for(Map.Entry<Integer,Set<String>> e : deps[i].entrySet()) {
          int di = e.getKey();
          for(String ty : e.getValue()) {
            String yd = (di >= 0) ? yield.get(di).toString() : "ROOT";
            System.out.printf("  %s w[%02d] %s\n",ty, di, yd);
          }
        }
      }
    }
  }

  public void addUntypedPathsDistance2(Tree t, HeadFinder hf) {

    Filter<Dependency<Label, Label, Object>> dependencyFilter = Filters.acceptFilter();
    Tree indexedTree = t.deeperCopy(t.treeFactory(), CyclicCoreLabel.factory());
    indexedTree.indexLeaves();
    Set<Dependency<Label, Label, Object>> depsSet = indexedTree.mapDependencies(dependencyFilter, hf, "ROOT");
    List<Dependency<Label, Label, Object>> sortedDeps = new ArrayList<Dependency<Label, Label, Object>>(depsSet);
    Collections.sort(sortedDeps, Dependencies.dependencyIndexComparator());

    yield = t.yield();
    int len = yield.size();
    assert(depsSet.size() == len);
    deps = initDeps(len);

    // Create length 1 (parent/child) relations:
    for (int ei=0; ei<len; ++ei) {
      Dependency<Label, Label, Object> d = sortedDeps.get(ei);
      CoreMap cmd = (CoreMap) d.dependent();
      CoreMap cmg = (CoreMap) d.governor();
      int depi = cmd.get(CoreAnnotations.IndexAnnotation.class)-1;
      int govi = cmg.get(CoreAnnotations.IndexAnnotation.class)-1;
      String depw = yield.get(depi).toString();
      String govw = (govi >= 0) ? yield.get(govi).toString() : "ROOT";
      assert(depi == ei);
      System.err.printf("dep=%d(%s) head=%d(%s)\n", depi, depw, govi, govw);
      addDep(deps[depi],govi,"P");
      if(govi >= 0) {
        addDep(deps[govi],depi,"D");
      }
    }

    // Create length 2 (grand-parent/child+sister) relations:
    Map<Integer,Set<String>>[] deps_tmp = initDeps(len);
    for (int si=0; si<len; ++si) {
      for(Map.Entry<Integer,Set<String>> e : deps[si].entrySet()) {
        int di = e.getKey();
        for(String tp : e.getValue()) {
          if(tp.equals("P") && di >= 0) {
            for(Map.Entry<Integer,Set<String>> e2 : deps[di].entrySet()) {
              int di2 = e2.getKey();
              for(String tp2 : e2.getValue()) {
                if(di2 != si) {
                  if(tp2.equals("D")) {
                    if(si >= 0)  addDep(deps_tmp[si],di2,"S");
                    if(di2 >= 0) addDep(deps_tmp[di2],si,"S");
                  }
                  if(tp2.equals("P")) {
                    if(si >= 0)  addDep(deps_tmp[si],di2,"GP");
                    if(di2 >= 0) addDep(deps_tmp[di2],si,"GD");
                  }
                }
              }
            }
          }
        }
      }
    }
    addDeps(deps,deps_tmp);
  }

  public void addTypedPaths(GrammaticalStructure gs) {
    SemanticGraph g = SemanticGraphFactory.makeFromTree(gs, "doc1", 0);
    System.err.println("Semantic graph: "+g.toFormattedString());
    List<IndexedWord> list = g.vertexList();
    for (int i = 0; i < list.size(); i++) {
      for (int j = 0; j < list.size(); j++) {
        if (i!=j) {
          String path = getPath(i, j, list, g);
          if(path != null)
            addDep(deps[i], j, path);
        }
      }
    }
  }

  String getPath(int n1, int n2, List<IndexedWord> iw, SemanticGraph g) {
    List<SemanticGraphEdge> paths = g.getShortestPathEdges(iw.get(n1), iw.get(n2));
    int curI = n1;
    List<String> p = new ArrayList<String>();
    if (paths != null) {
      for (SemanticGraphEdge path : paths) {
        int govid = iw.indexOf(path.getGovernor());
        int depid = iw.indexOf(path.getDependent());
        String r = path.getRelation().toString();
        if (curI == govid) {
          p.add(r+"D");
          curI = depid;
        } else if (curI == depid) {
          p.add(r+"P");
          curI = govid;
        } else {
          throw new RuntimeException("Node neither governor nor dependent!");
        }
      }
    }
    System.err.println("type="+StringUtils.join(p,":"));
    return "type="+StringUtils.join(p,":");
  }
  
  @SuppressWarnings("unchecked")
  private Map<Integer,Set<String>>[] initDeps(int len) {
    Map<Integer,Set<String>>[] deps = new TreeMap[len];
    for(int i=0; i<len; ++i)
      deps[i] = new TreeMap<Integer,Set<String>>();
    return deps;
  }

  private void addDep(Map<Integer,Set<String>> deps, int i, String l) {
    Set<String> ls = deps.get(i);
    if(ls == null) {
      ls = new TreeSet<String>();
      deps.put(i,ls);
    }
    ls.add(l);
  }

  private void addDeps(Map<Integer,Set<String>>[] tgt, Map<Integer,Set<String>>[] src) {
    int len = src.length;
    for(int i=0; i<len; ++i) {
      for(Map.Entry<Integer,Set<String>> e : src[i].entrySet()) {
        for(String e2 : e.getValue())
          addDep(tgt[i],e.getKey(),e2);
      }
    }
  }

}
