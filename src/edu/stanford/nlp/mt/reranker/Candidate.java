package mt.reranker;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.Tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class Candidate {
  List<TaggedWord> words; 
  Tree tree;
  Map<Integer, Map<Integer, String>> deps;
  double bleu;

  public Tree getTree() {
    return tree;
  }

  public Map<Integer, Map<Integer, String>> getDeps() {
    return deps;
  }

  public double getBleu() {
    return bleu;
  }

  public List<TaggedWord> getWords() {
    return tree.taggedYield(new ArrayList<TaggedWord>());
    //return words;
  }

  public Candidate(Tree tree, List<TaggedWord> tws) {
    this.tree = tree;
    this.words = tws;
  }

  @Override
	public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(bleu);
    sb.append("\t");
    for (TaggedWord w : getWords()) {
      sb.append(w.toString());
      sb.append(" ");
    }
    //sb.append("\n");
    //sb.append(tree.pennString());
    return sb.toString();
  }
}
