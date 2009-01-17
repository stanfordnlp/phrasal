package mt.translationtreebank;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.process.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.parser.lexparser.*;
import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.*;
import edu.stanford.nlp.trees.tregex.tsurgeon.*;
import edu.stanford.nlp.trees.tregex.*;

class EnglishTreeReader extends AbstractTreeReader {
  private PTBEscapingProcessor ptbe_;

  public EnglishTreeReader() {
    trees_ = new ArrayList<Tree>();
    tlpp_ = new EnglishTreebankParserParams();
    tlpp_.setOptionFlag(new String[] {"-leaveItAll", "1"}, 0);
    treeprint_ = new TreePrint("words,penn,typedDependencies", "removeTopBracket", tlpp_.treebankLanguagePack());
    tt_ = new NMLandPOSTreeTransformer();
    ptbe_ = new PTBEscapingProcessor();
  }

  public EnglishTreeReader(String filename) throws IOException {
    this();
    readMoreTrees(filename);
  }
  
  public String createSignature(String[] sents) {
    String output = StringUtils.join(sents, " ");
    output = output.replaceAll("\\s", "");
    return output;
  }

  public String createSignature(Tree t) {
    Sentence<HasWord> hws = getWords(t);
    String[] treeSent = new String[hws.size()];
    for(int i = 0; i < hws.size(); i++) {
      treeSent[i] = hws.get(i).word();
    }
    return StringUtils.join(treeSent, "");
  }

  // This one is different from the Chinese one, because
  // sometimes there are 2 trees in one alignment.
  public List<Tree> getTreesWithWords(String[] words) {
    String alignEngSignature = createSignature(words);
    List<Tree> trees = new ArrayList<Tree>();

    int treeidx = 0;
    while(treeidx < trees_.size()) {
      Tree t = trees_.get(treeidx);
      String treeSig = createSignature(t);
      if (alignEngSignature.equals(treeSig)) {
        trees.add(t);
        break;
      } else if (alignEngSignature.startsWith(treeSig)) {
        // try to combine with next signature
        int nextTreeidx = treeidx+1;
        List<Tree> newTrees = new ArrayList<Tree>();
        newTrees.add(t);

        while(nextTreeidx < trees_.size()) {
          Tree nextT = trees_.get(nextTreeidx);
          String nextTreeSig = createSignature(nextT);
          treeSig = treeSig+nextTreeSig;
          
          boolean outloop = false;

          if (alignEngSignature.equals(treeSig)) {
            newTrees.add(nextT);
            trees.addAll(newTrees);
            outloop = true;
            break;
          } else if (alignEngSignature.startsWith(treeSig)) {
            newTrees.add(nextT);
            nextTreeidx++;
          } else if (alignEngSignature.length() < treeSig.length()) {
            outloop = true;
            break;
          }
          if (outloop) break;
        }
      }
      treeidx++;
    }
  
    return trees;
  }


  public static void main(String args[]) throws IOException {
    EnglishTreeReader etr = new EnglishTreeReader();
    for(int i = 1; i <= 325; i++) {
      String name =
        String.format("/u/nlp/scr/data/ldc/LDC2007T02-EnglishChineseTranslationTreebankV1.0/data/pennTB-style-trees/chtb_%03d.mrg.gz", i);
      System.err.println(name);
      etr.readMoreTrees(name);
      System.err.println("number of trees="+etr.size());
    }
    etr.printAllTrees();
  }
}

class NMLandPOSTreeTransformer implements TreeTransformer {
  PTBEscapingProcessor ptbe;

  public NMLandPOSTreeTransformer() {
    ptbe = new PTBEscapingProcessor();
  }

  public Tree transformTree(Tree tree)  {
    try {
      // NML --> NX
      TregexPattern matchPattern = TregexPattern.compile("NML=nml");
      TsurgeonPattern p = Tsurgeon.parseOperation("relabel nml NX");
      List<Pair<TregexPattern,TsurgeonPattern>> ops = new ArrayList<Pair<TregexPattern,TsurgeonPattern>>();
      Pair<TregexPattern,TsurgeonPattern> op = new Pair<TregexPattern,TsurgeonPattern>(matchPattern, p);
      ops.add(op);
      tree = Tsurgeon.processPatternsOnTree(ops, tree);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("Error in Tsurgeon");
    }
    // merge (POS ') (* s) --> (POS 's)
    tree = mergeApostropheS(tree);

    // if after mergeApostropheS, there are still (POS s), 
    // change them into (POS 's)
    tree = sToApostropheS(tree);


    
    // normalize leaves
    List<Tree> leaves = tree.getLeaves();

    List<HasWord> words = new ArrayList<HasWord>();
    for (Tree leaf : leaves) {
      words.add(new Word(leaf.value()));
    }
    words = ptbe.apply(words);

    for (int i = 0; i < leaves.size(); i++) {
      leaves.get(i).setValue(words.get(i).word());
    }

    // fix cases like "aren't", "isn't"
    for (int i = 0; i < leaves.size()-1; i++) {
      String val = leaves.get(i).value();
      String nextval = leaves.get(i+1).value();
      if (nextval.equals("n't") && 
          (val.equals("are") || val.equals("is") || val.equals("did"))) {
        StringBuilder sb = new StringBuilder();
        sb.append(val).append("n");
        leaves.get(i).setValue(sb.toString());
        leaves.get(i+1).setValue("'t");
      }
    }

    // fix cases like "etc."
    for (int i = 0; i < leaves.size(); i++) {
      String val = leaves.get(i).value();
      if (val.equals("etc")) {
        leaves.get(i).setValue("etc.");
      }
      if (val.equals("Ltd")) {
        leaves.get(i).setValue("Ltd.");
      }
    }
    return tree;
  }


  static Tree sToApostropheS(Tree tree) {
    List<Tree> leaves = tree.getLeaves();
    for(Tree leaf : leaves) {
      if (leaf.value().equals("s") &&
          leaf.parent(tree).value().equals("POS")) {
        leaf.setValue("'s");
      }
    }
    return tree;
  }

  // TODO:
  // I think this method really should be replaced with 
  // a simple Tsurgeon operation.
  // I just haven't figure out how to relabel with 's
  // Thu Jan 15 23:12:16 2009 -pichuan
  static Tree mergeApostropheS(Tree tree) {
    Sentence<TaggedWord> tws = tree.taggedYield();
    boolean needProcess = (tws.size()>=2);
    while(needProcess) {
      int apostropheIdx = -1;
      tws = tree.taggedYield();
      for(int i = 0; i < tws.size()-1; i++) {
        String w1 = tws.get(i).word();
        String w2 = tws.get(i+1).word();
        if (w1.equals("'") && w2.equals("s")) {
          apostropheIdx = i;
          break;
        } else if (i==tws.size()-2) {
          needProcess = false;
        }
      }
      if (!needProcess) break;
      List<Tree> leaves = tree.getLeaves();
      if(apostropheIdx < 0) throw new RuntimeException("Tree="+tree.pennString());
      Tree tag1 = leaves.get(apostropheIdx).parent(tree);
      Tree tag2 = leaves.get(apostropheIdx+1).parent(tree);
      if (tag1.value().equals("POS") && 
          tag1.parent(tree) == tag2.parent(tree)) {
        Tree theParent = tag1.parent(tree);
        // get edge, so 'apostropheIdx' can be offset
        int parentIdx = Trees.leftEdge(theParent, tree);
        int apostropheIdx2 = apostropheIdx - parentIdx;
        Tree[] oldChildren = theParent.children();
        // reset children
        Tree[] newChildren = new Tree[oldChildren.length-1];
        for(int i = 0; i < oldChildren.length; i++) {
          if (i==apostropheIdx2) {
            if (!oldChildren[i].value().equals("POS")) throw new RuntimeException("");
            if (!oldChildren[i].firstChild().value().equals("'")) throw new RuntimeException("");
            oldChildren[i].firstChild().setValue("'s");
          }
          if (i<=apostropheIdx2) {
            newChildren[i] = oldChildren[i];
          } else if (i==apostropheIdx2+1) {
          } else {
            newChildren[i-1] = oldChildren[i];
          }
        }
        theParent.setChildren(newChildren);
      }
    }
    return tree;
  }

}
