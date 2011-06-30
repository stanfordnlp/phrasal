package edu.stanford.nlp.mt.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.mt.tools.MajorityTagger;
import edu.stanford.nlp.tagger.common.TaggerConstants;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.StringUtils;

/**
 * Interactive (/commandline) interface to the linear time parser
 * 
 * @author daniel cer (http://dmcer.net) and Heeyoung Lee
 */
public class InteractiveLinearParser {
  static public void main(String[] args) throws IOException, ClassNotFoundException {
    if (args.length != 1) {
      System.out.println("Usage:\n\tjava ...InteractiveLinearParser (parsing model)");
      System.exit(-1);
    }
    String modelFile = args[0];
    System.err.print("loading parser model...");
    DepDAGParser parser = IOUtils.readObjectFromFile(modelFile);
    System.err.println("done");
    Properties props = StringUtils.argsToProperties(args);
    String defaultDictionary = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tag_dict.txt";
    String dict = props.getProperty("dictionary", defaultDictionary);
    MajorityTagger tagger = new MajorityTagger(dict);

    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    System.out.print("> ");
    Structure struc = new Structure();
    LinkedStack<TypedDependency> deps;
    int idx = 1;
    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      if ("</s>".equals(line)) {
        // finalize parse for the current prefix/sentence
        List<CoreLabel> phrase = new ArrayList<CoreLabel>();
        CoreLabel w = new CoreLabel();
        w.set(TextAnnotation.class, TaggerConstants.EOS_WORD);
        w.set(PartOfSpeechAnnotation.class, TaggerConstants.EOS_TAG);
        w.set(IndexAnnotation.class, idx++);
        phrase.add(w);
        parser.parsePhrase(struc, phrase);

        deps = struc.getDependencies();
        System.out.println(deps);
        struc = new Structure();
        idx = 1;
      } else {
        // incrementally parse phrase give by line
        // in the context of the current prefix
        String[] toks = line.split("\\s+");
        List<CoreLabel> phrase = new ArrayList<CoreLabel>();
        for(String tok : toks){
          CoreLabel w = new CoreLabel();
          w.set(TextAnnotation.class, tok);
          tagger.tagWord(w, false);
          w.set(IndexAnnotation.class, idx++);
          phrase.add(w);
        }
        parser.parsePhrase(struc, phrase);
      }
      // System.out.printf("Current partial parse: %s\n", );
      // System.out.printf("Current stack contents: %s\n", );
      System.out.print("> ");
    }
  }
}
