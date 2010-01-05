package edu.stanford.nlp.mt.classifyde;

import java.util.*;
import java.io.*;
import edu.stanford.nlp.trees.*;

class SentTreeFileReader {
  BufferedReader sentBR;
  BufferedReader treeBR;
  
  public SentTreeFileReader(String sentFile, String treeFile) throws IOException {
    sentBR = new BufferedReader(new FileReader(sentFile));
    treeBR = new BufferedReader(new FileReader(treeFile));
  }

  public Tree next() throws IOException {
    String sent = sentBR.readLine();
    String tree = treeBR.readLine();
    if (sent==null && tree==null) return null;
    if (sent==null && tree!=null) 
      throw new RuntimeException("sentFile is shorter than treeFile");
    if (sent!=null && tree==null) 
      throw new RuntimeException("sentFile is longer than treeFile");
    
    Tree t = Tree.valueOf(tree);
    List<Tree> leaves = t.getLeaves();
    sent = sent.replaceAll("\u00a0", " ");
    String[] words = sent.trim().split("\\s+");

    if (leaves.size() != words.length) {
      System.err.println("sent size & tree size doesn't match:");
      System.err.println("sent size = "+words.length);
      System.err.println("tree size = "+leaves.size());
      System.err.println("SENT="+sent);
      System.err.println("TREE=");
      t.pennPrint(System.err);
      throw new RuntimeException();
    }

    // sanity check -- at least one of the words should match..
    boolean matched = false;
    for(int i = 0; i < leaves.size(); i++) {
      if (leaves.get(i).value().equals(words[i])) { 
        matched = true;
      } else
        leaves.get(i).setValue(words[i]);
    }
    
    if (!matched) {
      System.err.println("sent & tree doesn't even have one match in word");
      System.err.println("SENT="+sent);
      System.err.println("TREE="+tree);
      //throw new RuntimeException();
    }

    return t;
  }
}
