package mt.translationtreebank;
import edu.stanford.nlp.trees.*;
import java.util.*;

class TreePair {
  TranslationAlignment alignment;
  List<Tree> enTrees;
  List<Tree> chTrees;

  public TreePair(TranslationAlignment alignment, List<Tree> enTrees, List<Tree> chTrees) {
    this.alignment = alignment;
    this.enTrees = enTrees;
    this.chTrees = chTrees;
  }
}
