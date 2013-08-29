package edu.stanford.nlp.mt.chinese;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.util.StringUtils;

import edu.stanford.nlp.parser.berkeley.*;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ScoredObject;

/**
 * Implements Chinese source-side preordering for MT, as described in:
 * Chao Wang, Michael Collins, and Philipp Koehn. 2007. 
 * Chinese Syntactic Reordering for Statistical Machine Translation. 
 * In proceedings of EMNLP-CoNLL 2007.
 * 
 * @author robvoigt
 */

public class ChineseSourcePreordering {

  /**
   * @param args
   * @throws IOException 
   */
  
  private static final Map<TregexPattern, TsurgeonPattern> rules;
  static {
    Map<TregexPattern, TsurgeonPattern> ruleMap = new HashMap<TregexPattern, TsurgeonPattern>();
    ruleMap.put(TregexPattern.compile("VP << (PP=pp $++ VP=vp)"), Tsurgeon.parseOperation("move pp $- vp")); // VP(PP:VP)
    ruleMap.put(TregexPattern.compile("VP << (LCP=lcp $++ VP=vp)"), Tsurgeon.parseOperation("move lcp $- vp")); // VP(LCP:VP)
    ruleMap.put(TregexPattern.compile("VP << ((NP=np << NT) $++ VP=vp)"), Tsurgeon.parseOperation("move np $- vp")); // VP(NT:VP)
    ruleMap.put(TregexPattern.compile("VP << (QP=qp $++ VP=vp)"), Tsurgeon.parseOperation("move qp $- vp")); // VP(QP:VP)
    ruleMap.put(TregexPattern.compile("NP [<< (CP=cp $++ NP)] & [<` NP=np]"), Tsurgeon.parseOperation("move cp $- np")); // NP(CP:NP)
    ruleMap.put(TregexPattern.compile("CP << (IP=ip $++ DEC=dec)"), Tsurgeon.parseOperation("move ip $- dec")); // fix DEC (described in NP(CP:NP) section of paper)
    ruleMap.put(TregexPattern.compile("NP [<< ((DNP=dnp << (NP !<< PN)) $++ NP)] & [<` NP=np]"), Tsurgeon.parseOperation("move dnp $- np")); // DNP(NP):NP
    ruleMap.put(TregexPattern.compile("NP [<< ((DNP=dnp << PP) $++ NP)] & [<` NP=np]"), Tsurgeon.parseOperation("move dnp $- np")); // DNP(PP):NP
    ruleMap.put(TregexPattern.compile("NP [<< ((DNP=dnp << LCP) $++ NP)] & [<` NP=np]"), Tsurgeon.parseOperation("move dnp $- np")); // DNP(LCP):NP
    ruleMap.put(TregexPattern.compile("LCP << (__=left $+ LC=lc)"), Tsurgeon.parseOperation("move lc $+ left")); // LCP(XP:LC)
    rules = Collections.unmodifiableMap(ruleMap);
  }
  
  public static Tree preorderTree(Tree t) {
	for (TregexPattern pat : rules.keySet()) {
	  TsurgeonPattern surgery = rules.get(pat);
	  Tsurgeon.processPattern(pat, surgery, t);
	}
	return t;
  }
  
  public static String preorder(String parsedSentence) {
    Tree t = Tree.valueOf(parsedSentence);
    for (TregexPattern pat : rules.keySet()) {
      TsurgeonPattern surgery = rules.get(pat);
      Tsurgeon.processPattern(pat, surgery, t);
    }
    return t.toString(); 
  }

  public static void main(String[] args) throws IOException {
	// for testing purposes: args[0] is the location of a pre-trained berkeley parser .gr file, and args[1] is the location of a word-segmented file to reorder
	  
    //String parserModel = args[0];
    String inputFile = args[0];    
    //BerkeleyParserWrapper parser = new BerkeleyParserWrapper(parserModel, false, false, false, false, false, true, 1, false); // new berkeley parser for chinese (the one true is the chinese boolean)
   
    InputStream fis = new FileInputStream(inputFile);
    BufferedReader br = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));
    String line;
    while ((line = br.readLine()) != null) {
      
      //List<String> words = Arrays.asList(line.split(" "));
      //ScoredObject<Tree> parse = parser.getParses(words, null).get(0);
      //Tree tree = parse.object();
      //System.out.println("\n--------------\n");
      //System.out.println("Original Tree:");
      //tree.pennPrint();
      //System.out.println(tree.toString());
      //System.out.println("\nModified Tree:");
      //tree.pennPrint();
      //System.out.println(tree.toString());
      
      Tree tree = Tree.valueOf(line.trim());
      tree = preorderTree(tree);
      String flatTree = "";
      for (Tree word : tree.getLeaves()) {
    	  flatTree += word.toString() + " ";
      }
      System.out.println(flatTree.trim());

    }
  }
}
