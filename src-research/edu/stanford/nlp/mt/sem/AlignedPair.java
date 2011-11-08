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
import edu.stanford.nlp.util.Pair;

/**
 * AlignedPair contains partially a word aligned
 * mt semantic graph.
 * 
 * @author daniel
 *
 */
public class AlignedPair {
   private GrammaticalStructure f;
   private GrammaticalStructure e;
   public TreeGraphNode e2f[];
   public TreeGraphNode f2e[];
   public TreeGraphNode[] fLeaves;
   public TreeGraphNode[] eLeaves;
   
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
   }
   
   public String toString() {
      StringBuilder sbuilder = new StringBuilder();
      sbuilder.append("F: ");
      sbuilder.append(f);
      sbuilder.append("\nE: ");
      sbuilder.append(e);
      sbuilder.append("\nA:");
      for (int i = 0; i < f2e.length; i++) {
         if (f2e[i] == null) {
            continue;
         }
         if (i != 0) sbuilder.append(", ");
         sbuilder.append(String.format("%s:%d->%s:%d",fLeaves[i],i,f2e[i], f2e[i].index()));
      }
      return sbuilder.toString();
   }
   
   public static void main(String[] args) throws IOException {
      LineNumberReader reader = new LineNumberReader(new FileReader(args[0]));
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
         String[] fields = line.split("\t");
         // fromStringReps(List<String> tokens,
         //      List<String> posTags, List<String> deps)
         List<String> ewords = Arrays.asList(fields[1].split("\\s"));
         List<String> fwords = Arrays.asList(fields[2].split("\\s"));
         Set<Pair<Integer,Integer>> alignmentsF2E = new HashSet<Pair<Integer,Integer>>();        
         for (String aF2Estr : fields[3].split("\\s")) {
            String[] aFields = aF2Estr.split("-");
            Pair<Integer,Integer> aF2E = new Pair<Integer,Integer>(new Integer(aFields[0]), new Integer(aFields[1]));
            alignmentsF2E.add(aF2E);
         }
         List<String> edeps = Arrays.asList(fields[4].split("\\|\\|\\|"));
         List<String> fdeps = Arrays.asList(fields[5].split("\\|\\|\\|"));
         // TODO Include POS Tags in format
         GrammaticalStructure gsE = GrammaticalStructure.fromStringReps(ewords, ewords, edeps);
         GrammaticalStructure gsF = GrammaticalStructure.fromStringReps(fwords, fwords, fdeps);
         AlignedPair alignedPair = new AlignedPair(gsF, gsE, alignmentsF2E);
         System.out.println(alignedPair);
         System.out.println();
      }
   }
}
