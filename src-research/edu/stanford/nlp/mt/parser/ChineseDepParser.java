package edu.stanford.nlp.mt.parser;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Filters;

public class ChineseDepParser {
  private final String defaultModel = "/u/nlp/apache-tomcat-7.0.12/webapps/parser/WEB-INF/data/xinhuaFactored.ser.gz";
  private final String defaultDataDir = "/u/nlp/apache-tomcat-7.0.12/webapps/parser/WEB-INF/data/chinesesegmenter";
  private final String defaultSegmenter = "/u/nlp/apache-tomcat-7.0.12/webapps/parser/WEB-INF/data/chinesesegmenter/05202008-ctb6.processed-chris6.lex.gz";
  private final String defaultNormalizationTable = "/u/nlp/apache-tomcat-7.0.12/webapps/parser/WEB-INF/data/chinesesegmenter/list_err.utf8";

  public static class ParserPack {
    public CRFClassifier segmenter;
    public LexicalizedParser parser;
    public TreebankLanguagePack tLP;
    public GrammaticalStructureFactory gsf;
  }

  public ParserPack pp;

  public ChineseDepParser() throws Exception {
    pp = new ParserPack();
    pp.parser = new LexicalizedParser(defaultModel);
    pp.tLP = pp.parser.getOp().tlpParams.treebankLanguagePack();

    Filter<String> puncWordFilter;
    puncWordFilter = Filters.acceptFilter();
    pp.gsf = pp.tLP.grammaticalStructureFactory(puncWordFilter);

    CRFClassifier classifier = new CRFClassifier(new Properties());
    BufferedInputStream bis = new BufferedInputStream(new GZIPInputStream(new FileInputStream(defaultSegmenter)));
    classifier.loadClassifier(bis,null);
    bis.close();

    // configure segmenter
    SeqClassifierFlags flags = classifier.flags;
    flags.sighanCorporaDict = defaultDataDir;
    flags.normalizationTable = defaultNormalizationTable;
    flags.normTableEncoding = "UTF-8";
    flags.inputEncoding = "UTF-8";
    flags.keepAllWhitespaces = true;
    flags.keepEnglishWhitespaces = true;
    flags.sighanPostProcessing = true;

    pp.segmenter = classifier;
  }

  public static void main(String[] args) throws Exception {
    Map<String, String> defaultQuery = new HashMap<String, String>();
    defaultQuery.put("Chinese", "猴子喜欢吃香蕉。");

    ChineseDepParser cParser = new ChineseDepParser();

    List<String> defaultQueryPieces;
    defaultQueryPieces = cParser.pp.segmenter.segmentString(defaultQuery.get("Chinese"));

    List<HasWord> defaultQueryWords = new ArrayList<HasWord>();
    for (String s : defaultQueryPieces) {
      defaultQueryWords.add(new Word(s));
    }
    cParser.pp.parser.parse(defaultQueryWords);
    Tree t = cParser.pp.parser.getBestParse();

    GrammaticalStructure gs = cParser.pp.gsf.newGrammaticalStructure(t);
    System.err.println(gs.typedDependencies());
    System.err.println(gs.typedDependenciesCCprocessed(true));

    System.err.println();

  }

}
