package mt.translationtreebank;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.process.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.parser.lexparser.*;
import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.*;

class EnglishTreeReader extends AbstractTreeReader {
  private PTBEscapingProcessor ptbe_;

  public EnglishTreeReader() {
    trees_ = new ArrayList<Tree>();
    tlpp_ = new EnglishTreebankParserParams();
    treeprint_ = new TreePrint("words,penn,typedDependencies", "removeTopBracket", tlpp_.treebankLanguagePack());
    tt_ = new NMLandPOSTreeTransformer();
    ptbe_ = new PTBEscapingProcessor();
  }

  public EnglishTreeReader(String filename) throws IOException {
    this();
    readMoreTrees(filename);
  }
  
  /*
  public String normalizeSentence(String sent) {
    List<HasWord> words = new ArrayList<HasWord>();
    words.add(new Word(sent));
    words = ptbe_.apply(words);
    String output = words.get(0).word();
    return output;
  }

  public String normalizeSentence(String[] sents) {
    List<HasWord> words = new ArrayList<HasWord>();
    for(String w : sents) words.add(new Word(w));
    words = ptbe_.apply(words);
    return StringUtils.join(words, " ");
  }
  */

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
    //return createSignature(treeSent);
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
        while(nextTreeidx < trees_.size()) {
          Tree nextT = trees_.get(nextTreeidx);
          String nextTreeSig = createSignature(nextT);
          treeSig = treeSig+nextTreeSig;
          
          boolean outloop = false;

          if (alignEngSignature.equals(treeSig)) {
            trees.add(t);
            trees.add(nextT);
            outloop = true;
            break;
          } else if (alignEngSignature.startsWith(treeSig)) {
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

  public Tree transformTree(Tree tree) {
    tree = tree.deepCopy();
    for (Iterator<Tree> it = tree.iterator(); it.hasNext();) {
      Tree subtree = it.next();
      if (subtree.isPhrasal() && subtree.value().equals("NML")) {
        subtree.setValue("NX");
      }

      if (subtree.isPreTerminal() && subtree.value().equals("POS")) {
        Tree leaf = subtree.firstChild();
        if (leaf.value().equals("s")) {
          leaf.setValue("'s");
        }
      }
    }

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

    return tree;
  }
}
