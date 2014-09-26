package edu.stanford.nlp.mt.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.mt.parser.Actions.Action;
import edu.stanford.nlp.mt.parser.Actions.ActionType;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.OpenAddressCounter;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.EnglishGrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.Pair;

/**
 * Action recoverer for building training data for shift-reduce dependency parser.
 * Given gold dependencies, it recovers the list of actions.
 *
 * @author heeyoung
 */
public class ActionRecoverer {

  private static final IncrementalTagger tagger = new IncrementalTagger();
  private static final Morphology lemmatizer = new Morphology();

  private ActionRecoverer() {} // static class

  /** recover the list of actions for constructing s.dependencies
   *  the actions are stored in s.actionTrace */
  private static void recoverActionTrace(Structure s) {
    s.stack = new LinkedStack<CoreLabel>();
    // extract arc & relation
    Counter<IndexedWord> leftsideEdgeCounter = new OpenAddressCounter<IndexedWord>();
    Set<Pair<IndexedWord, IndexedWord>> arcs = new HashSet<>();
    Map<Pair<IndexedWord,IndexedWord>, GrammaticalRelation> arcRelation = new HashMap<>();

    for(TypedDependency e : s.dependencies) {
      IndexedWord gov = e.gov();
      IndexedWord dependent = e.dep();
      if(gov.equals(dependent)) continue;
      Pair<IndexedWord, IndexedWord> pair = new Pair<IndexedWord, IndexedWord>(gov, dependent);
      arcs.add(pair);
      arcRelation.put(pair, e.reln());

      if(gov.get(IndexAnnotation.class) < dependent.get(IndexAnnotation.class)) {
        leftsideEdgeCounter.incrementCount(dependent);
      } else {
        leftsideEdgeCounter.incrementCount(gov);
      }
    }

    // recover actions
    Object[] inputs = s.input.peekN(s.input.size());
    for(int i = inputs.length-1 ; i > 0 ;){
      CoreLabel w = (CoreLabel)inputs[i];
      IndexedWord iw = new IndexedWord(w);
      if(leftsideEdgeCounter.getCount(w) > 0) {
        IndexedWord topStack = new IndexedWord(s.stack.peek());

        Pair<IndexedWord, IndexedWord> govPair = new Pair<IndexedWord, IndexedWord>(iw, topStack);
        Pair<IndexedWord, IndexedWord> depPair = new Pair<IndexedWord, IndexedWord>(topStack, iw);

        if(arcs.contains(govPair)) { // left-arc
          s.actionTrace.push(new Action(ActionType.LEFT_ARC, arcRelation.get(govPair)));
          leftsideEdgeCounter.decrementCount(iw);
          arcs.remove(govPair);
        } else if (arcs.contains(depPair)) { // right-arc
          s.actionTrace.push(new Action(ActionType.RIGHT_ARC, arcRelation.get(depPair)));
          leftsideEdgeCounter.decrementCount(iw);
          arcs.remove(depPair);
        } else {
          s.stack.pop();
          s.actionTrace.push(new Action(ActionType.REDUCE));
        }
      } else {
        i--;
        s.stack.push(w);
        s.actionTrace.push(new Action(ActionType.SHIFT));
      }
    }
    s.stack = new LinkedStack<CoreLabel>();
  }

  public static List<Structure> readTrainingData(String filename, MaxentTagger posTagger) throws IOException{
    List<GrammaticalStructure> gsList =
      EnglishGrammaticalStructure.readCoNLLXGrammaticalStructureCollection(filename);
    List<Structure> structures = new ArrayList<Structure>();
    for(GrammaticalStructure gs : gsList){
      Structure s = new Structure(gs, tagger, lemmatizer, posTagger);
      recoverActionTrace(s);
      structures.add(s);
    }
    return structures;
  }

  public static void main(String[] args) throws IOException{
    // String filename = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-dev-2011-01-13.conll";
    // String filename = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/temp.conll";
    // String filename = "/scr/heeyoung/mt/scr61/parser/debug/phrasal.8.trans.parse.basic.conllx";
    // List<Structure> structures = readTrainingData(filename, null);
  }

}
