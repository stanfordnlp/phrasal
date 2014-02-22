package edu.stanford.nlp.mt.service.tools;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;

import edu.stanford.nlp.international.french.process.FrenchTokenizer;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.InputProperties;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.process.fr.FrenchPreprocessor;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;

/**
 * Takes a raw French document and converts it to a JSON file for the Phrasal 
 * service.
 * 
 * TODO: Support languages other than French for which we do not have CoreNLP.
 * 
 * @author Spence Green
 *
 */
public class RawFrenchToJSON {
  
  /**
   * Extract chunks. 
   * 
   * @param tree
   * @return
   */
  static int[] getChunkVector(Tree tree) {
    String[] iobVector = new String[tree.yield().size()];
    Arrays.fill(iobVector, "O");
    
    // NOTE: The order in which these patterns are applied is important.
    
    // Base XPs
    TregexPattern baseXPPattern = TregexPattern.compile("__ < (__ < (__ !< __)) !< (__ < (__ < __))");
    
    // Non-recursive NPs
    TregexPattern NPPattern = TregexPattern.compile("@NP < (__ $ __) !<< (@NP < (__ $ __)) !<< @PP");

    // Non-recursive PPs
    TregexPattern PPattern = TregexPattern.compile("@PP !<< @PP");
    
    TregexMatcher tregexMatcher = baseXPPattern.matcher(tree);
    CoreNLPToJSON.fillVectorWithYield(iobVector, tregexMatcher);
    
    tregexMatcher = NPPattern.matcher(tree);
    CoreNLPToJSON.fillVectorWithYield(iobVector, tregexMatcher);
    
    tregexMatcher = PPattern.matcher(tree);
    CoreNLPToJSON.fillVectorWithYield(iobVector, tregexMatcher);
    
    int[] indexVector = CoreNLPToJSON.iobToIndices(iobVector);
    return indexVector;
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.printf("Usage: java %s grammar file [inputproperties_str] > json%n", RawFrenchToJSON.class.getName());
      System.exit(-1);
    }
    String parserFile = args[0];
    String textFile = args[1];
    InputProperties inputProperties = args.length > 2 ? InputProperties.fromString(args[2]) : new InputProperties();

    // Load parser
    LexicalizedParser parser = LexicalizedParser.loadModel(parserFile);
    
    // Configure tokenizer
    FrenchPreprocessor preprocessor = new FrenchPreprocessor(true);
    preprocessor.setOptions(FrenchTokenizer.FTB_OPTIONS);
    
    LineNumberReader reader = IOTools.getReaderFromFile(textFile);
    Map<Integer,SourceSegment> annotations = new TreeMap<Integer,SourceSegment>();
    try {
      for (String line; (line = reader.readLine()) != null;) {
        SymmetricalWordAlignment alignment = preprocessor.processAndAlign(line.trim());
        Sequence<IString> tokenizedSequence = alignment.e();
        String preprocessedLine = tokenizedSequence.toString();
        Tree tree = parser.parse(preprocessedLine);
        List<Label> posSequence = tree.preTerminalYield();
        tree.indexLeaves();
        int[] chunkVector = getChunkVector(tree);
        SourceSegment segment = new SourceSegment(tokenizedSequence.size());
        segment.layoutSpec.addAll(CoreNLPToJSON.makeLayoutSpec(alignment));
        segment.inputProperties = inputProperties.toString();
        for (int j = 0, size = tokenizedSequence.size(); j < size; ++j) {
          String token = tokenizedSequence.get(j).toString();
          segment.tokens.add(CoreNLPToJSON.unescape(token));
          String pos = posSequence.get(j).toString();
          segment.pos.add(pos);
          segment.ner.add("O");
          segment.chunkVector[j] = chunkVector[j];
        }
        annotations.put(annotations.size(), segment);
      }
      reader.close();
      
    } catch (IOException e) {
      e.printStackTrace();
    }
    
    System.err.printf("Read %d lines from %s%n", reader.getLineNumber(), textFile);
    System.err.printf("Wrote %d lines to JSON output%n", annotations.size());
    
    final SourceDocument jsonDocument = new SourceDocument(textFile, annotations);
    
    // Convert to json
    Gson gson = new Gson();
    String json = gson.toJson(jsonDocument);
    System.out.println(json);
  }
}
