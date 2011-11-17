package edu.stanford.nlp.mt.sem;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.Pair;

/**
 * AlignedPair contains partially a word aligned
 * mt semantic graph.
 * 
 * @author daniel cer
 *
 */
public class AlignedPair {
   private GrammaticalStructure f;
   private GrammaticalStructure e;
   public TreeGraphNode e2f[];
   public TreeGraphNode f2e[];
   public TreeGraphNode[] fLeaves;
   public TreeGraphNode[] eLeaves;
   public TypedDependency[][] eParents;
   public TypedDependency[][] fParents;   
   public TypedDependency[][] eChildren;
   public TypedDependency[][] fChildren;
   
   
   public AlignedPair(GrammaticalStructure f, GrammaticalStructure e, Set<Pair<Integer,Integer>> alignmentsF2E) {
      fLeaves = f.root().getLeaves().toArray(new TreeGraphNode[0]);
      eLeaves = e.root().getLeaves().toArray(new TreeGraphNode[0]);
      
	   e2f = new TreeGraphNode[eLeaves.length];
	   f2e = new TreeGraphNode[fLeaves.length];
	   for (Pair<Integer,Integer> aF2E : alignmentsF2E) {
	      int fI = aF2E.first;
	      int eI = aF2E.second;
	      e2f[eI] = fLeaves[fI];
	      f2e[fI] = eLeaves[eI];
	   }
	   this.f = f;
	   this.e = e;
	   eParents = buildParentsArray(e, eLeaves);
	   fParents = buildParentsArray(f, fLeaves);
	   eChildren = buildChildrenArray(e, eLeaves);
	   fChildren = buildChildrenArray(f, fLeaves);
   }
   
   static private TypedDependency[][] buildChildrenArray(GrammaticalStructure gs, TreeGraphNode[] leaves) {
      TypedDependency[][] children = new TypedDependency[leaves.length][];      
      for (TypedDependency td : gs.allTypedDependencies()) {
        int parentIdx = td.gov().index()-1;
        if (parentIdx == -1) continue;
        if (children[parentIdx] == null) {
           children[parentIdx] = new TypedDependency[1];
        } else {
           children[parentIdx] = Arrays.copyOf(children[parentIdx], 
                children[parentIdx].length+1);
        }
        children[parentIdx][children[parentIdx].length-1] = td;
      }
      return children;
    }
   
   static private TypedDependency[][] buildParentsArray(GrammaticalStructure gs, TreeGraphNode[] leaves) {
     TypedDependency[][] parents = new TypedDependency[leaves.length][];
     for (TypedDependency td : gs.allTypedDependencies()) {
       int childIdx = td.dep().index()-1;
       if (parents[childIdx] == null) {
          parents[childIdx] = new TypedDependency[1];
       } else {
          parents[childIdx] = Arrays.copyOf(parents[childIdx], 
               parents[childIdx].length+1);
       }
       parents[childIdx][parents[childIdx].length-1] = td;
     }
     return parents;
   }
   
   public String toString() {
      StringBuilder sbuilder = new StringBuilder();
      for (TreeGraphNode n : eLeaves) {
         sbuilder.append(n.label().word());
         sbuilder.append(" ");
      }
      sbuilder.setLength(sbuilder.length() - 1);
      sbuilder.append("\t");
      for (TreeGraphNode n : fLeaves) {
         sbuilder.append(n.label().word());
         sbuilder.append(" ");
      }
      sbuilder.setLength(sbuilder.length() - 1);
      sbuilder.append("\t");
      for (int i = 0; i < e2f.length; i++) {
         if (e2f[i] == null) {
            continue;
         }
         sbuilder.append(e2f[i].index()-1);
         sbuilder.append("-");
         sbuilder.append(i);
         sbuilder.append(" ");
      }
      sbuilder.setLength(sbuilder.length() - 1);
      
      sbuilder.append("\t");
      for (TypedDependency dep : e.allTypedDependencies()) {
         sbuilder.append(dep.toString());
         sbuilder.append("|||");
      }
      sbuilder.setLength(sbuilder.length()-3);
      
      sbuilder.append("\t");
      for (TypedDependency dep : f.allTypedDependencies()) {
         sbuilder.append(dep.toString());
         sbuilder.append("|||");
      }
      sbuilder.setLength(sbuilder.length()-3);
      
      return sbuilder.toString();
   }
   
   public static AlignedPair fromString(String s) {
      try {
         String[] fields = s.split("\t");
         // fromStringReps(List<String> tokens,
         //      List<String> posTags, List<String> deps)
         List<String> ewords = Arrays.asList(fields[1].split("\\s"));
         List<String> fwords = Arrays.asList(fields[2].split("\\s"));
         Set<Pair<Integer,Integer>> alignmentsF2E = new HashSet<Pair<Integer,Integer>>();
         if (!"".equals(fields[3])) {
            for (String aF2Estr : fields[3].split("\\s")) {
               String[] aFields = aF2Estr.split("-");
               Pair<Integer,Integer> aF2E = new Pair<Integer,Integer>(new Integer(aFields[0]), new Integer(aFields[1]));
               alignmentsF2E.add(aF2E);
            }
         }
         List<String> edeps = Arrays.asList(fields[4].split("\\|\\|\\|"));
         List<String> fdeps = Arrays.asList(fields[5].split("\\|\\|\\|"));
         // TODO Include POS Tags in format
         GrammaticalStructure gsE = GrammaticalStructure.fromStringReps(ewords, ewords, edeps);
         GrammaticalStructure gsF = GrammaticalStructure.fromStringReps(fwords, fwords, fdeps);
         AlignedPair alignedPair = new AlignedPair(gsF, gsE, alignmentsF2E);
         return alignedPair;
      } catch (RuntimeException e) {
         throw new RuntimeException(String.format("Error parsing aligned pair: '%s'\n",s),e);
      }
   }
   
   public static void main(String[] args) throws IOException {
      LineNumberReader reader = new LineNumberReader(new FileReader(args[0]));
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
         AlignedPair aPair = AlignedPair.fromString(line);
         System.out.println(aPair);
         System.out.println();
      }
   }
}
