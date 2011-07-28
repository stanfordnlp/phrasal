package edu.stanford.nlp.mt.decoder.annotators;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.parser.ChineseDepParser;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.Tree;

/** Source = Chinese for now  */
public class SourceDependencyAnnotator<TK> implements Annotator<TK> {
  public final ChineseDepParser parser;
  public GrammaticalStructure gs;

  public SourceDependencyAnnotator() {
    try {
      this.parser = new ChineseDepParser();
      this.gs = null;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  private SourceDependencyAnnotator(ChineseDepParser parser, GrammaticalStructure gs) {
    this.parser = parser;
    this.gs = gs;
  }

  @Override
  public Annotator<TK> initalize(Sequence<TK> source) {

    List<HasWord> sentence = new ArrayList<HasWord>();
    for (TK s : source) {
      sentence.add(new Word(s.toString()));
    }
    parser.pp.parser.parse(sentence);
    Tree t = parser.pp.parser.getBestParse();
    GrammaticalStructure grammaticalStruc = parser.pp.gsf.newGrammaticalStructure(t);
    SourceDependencyAnnotator<TK> annotator = new SourceDependencyAnnotator<TK>(parser, grammaticalStruc);

    return annotator;
  }

  @Override
  public Annotator<TK> extend(ConcreteTranslationOption<TK> option) {
    SourceDependencyAnnotator<TK> pa = new SourceDependencyAnnotator<TK>(parser, this.gs);
    return pa;
  }
}
