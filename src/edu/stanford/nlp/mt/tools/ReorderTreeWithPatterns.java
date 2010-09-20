package edu.stanford.nlp.mt.tools;

import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.tsurgeon.*;
import edu.stanford.nlp.util.StringUtils;
import java.io.*;
import java.util.List;
import java.util.ArrayList;

public class ReorderTreeWithPatterns {

  @SuppressWarnings("unchecked")
  public static List<Tree> surgeriesOnTrees(String[] changes, List<Tree> lTrees) throws Exception {
    TregexPattern[] matchPatterns = new TregexPattern[changes.length/2];
    List<TsurgeonPattern>[] ps = new ArrayList[changes.length/2];
    
    for(int i = 0; i < changes.length/2; i++) {
      matchPatterns[i] = TregexPattern.compile(changes[i*2]);
      System.err.printf("Tregex Pattern #%d : %s\n", i, changes[i*2]);
      ps[i] = new ArrayList<TsurgeonPattern>();
      TsurgeonPattern p = Tsurgeon.parseOperation(changes[i*2+1]);
      System.err.printf("Tsurgeon Operation #%d : %s\n", i, changes[i*2+1]);
      ps[i].add(p);
    }

    for(int i = 0; i < changes.length/2; i++) {
      TregexPattern matchPattern = matchPatterns[i];
      List<TsurgeonPattern> p = ps[i];
      System.err.println("Applying rule #"+i);
      lTrees = Tsurgeon.processPatternOnTrees(matchPattern,Tsurgeon.collectOperations(p),lTrees);
    }
    return lTrees;
  }


  public static void main(String[] args) throws Exception {
    
    if (args.length %2 != 0) {
      System.err.println("Usage: java ReorderPatterns pattern1 cmd1 [pattern2 cmd2 ...] < treeFile");
    }


    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    String line; //= null;
    

    List<Tree> lTrees = new ArrayList<Tree>();
    List<Tree> origTrees = new ArrayList<Tree>();

    while((line = br.readLine()) != null) {
      Tree tree = null;
      Tree tree2 = null;
      try {
        tree = Tree.valueOf(line);
        tree2 = Tree.valueOf(line);
      } catch (Exception e) {
        e.printStackTrace();
      }

      lTrees.add(tree);
      origTrees.add(tree2);
    }

    lTrees = surgeriesOnTrees(args, lTrees);

    int numDiff = 0;
    
    for(int idx = 0 ; idx < lTrees.size(); idx++) {
      String tree1 = origTrees.get(idx).toString();
      String tree2 = lTrees.get(idx).toString();
      if ( ! tree1.equals(tree2)) {
        numDiff++;
        System.err.println("Tree # "+idx);
        System.err.println("Old:");
        //System.err.println(origTrees.get(idx));
        origTrees.get(idx).pennPrint(System.err);
        System.err.println("-----------------------------------");
        System.err.println("New:");
        //System.err.println(lTrees.get(idx));
        lTrees.get(idx).pennPrint(System.err);
        System.err.println();
      }

      // print the yield
      //List<Label> y = lTrees.get(idx).yield(new ArrayList<Label>());
      List<Object> y = lTrees.get(idx).yield(new ArrayList<Object>());
      List<String> words = new ArrayList<String>();
      //for (HasWord w : y) {
      for (Object w : y) {
        words.add(w.toString());
      }
      System.out.println(StringUtils.join(words," "));
    }
    System.err.println("Total number of different sentences: "+numDiff);
  }
}
