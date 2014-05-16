package edu.stanford.nlp.mt.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.tagger.common.TaggerConstants;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.BasicDependenciesAnnotation;
import edu.stanford.nlp.util.CoreMap;

/**
 * Interactive (/commandline) interface to the linear time parser
 * 
 * @author daniel cer (http://dmcer.net) and Heeyoung Lee
 */
public class InteractiveLinearParser {
  private static final boolean labelRelation = true;

  static public void main(String[] args) throws IOException, ClassNotFoundException {
    if (args.length != 1) {
      System.out.println("Usage:\n\tjava ...InteractiveLinearParser (parsing model)");
      System.exit(-1);
    }
    String modelFile = args[0];
    System.err.print("loading parser model...");
    DepDAGParser parser = IOUtils.readObjectFromFile(modelFile);
    parser.extractTree = true;
    System.err.println("done");
    IncrementalTagger tagger = new IncrementalTagger();

    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    Structure struc = new Structure();
    LinkedStack<TypedDependency> deps;
    int seqLen = tagger.ts.leftWindow() + 1;
    
    // for comparison, use Stanford parser
    Properties props = new Properties();
    props.put("annotators", "tokenize, ssplit, pos, parse");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    

    System.out.print("> ");
    int idx = 1;
    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      Set<String> depStrings = new HashSet<String>();
      Set<String> systemDepStrings = new HashSet<String>();
      // stanford dependencies
      Annotation document = new Annotation(line);
      pipeline.annotate(document);
      List<CoreMap> sentences = document.get(SentencesAnnotation.class);
      System.out.println("====================================================================");

      System.out.println("Sentence: "+line);
      Collection<TypedDependency> stanfordDeps = new HashSet<TypedDependency>();

      for(CoreMap sentence: sentences) {
        SemanticGraph dependencies = sentence.get(BasicDependenciesAnnotation.class);
        for(TypedDependency dep : dependencies.typedDependencies()) {
          depStrings.add(dep.toString());
        }
      }
      System.out.println("----------------------------------");
      
      
      if ("</s>".equals(line)) {
        // finalize parse for the current prefix/sentence
        List<CoreLabel> phrase = new ArrayList<CoreLabel>();
        CoreLabel w = new CoreLabel();
        w.set(TextAnnotation.class, TaggerConstants.EOS_WORD);
        w.set(PartOfSpeechAnnotation.class, TaggerConstants.EOS_TAG);
        w.set(IndexAnnotation.class, idx++);
        phrase.add(w);
        parser.parsePhrase(struc, phrase, labelRelation);

        deps = struc.getDependencies();
        System.out.println(deps);
        struc = new Structure();
        idx = 1;
      } else {
        // incrementally parse phrase give by line
        // in the context of the current prefix
        String[] toks = line.split("\\s+");
        for(String tok : toks){

          CoreLabel w = new CoreLabel();
          w.set(TextAnnotation.class, tok);
          w.set(IndexAnnotation.class, idx++);

          int len = Math.min(seqLen, struc.input.size()+1);
          IString[] sequence = new IString[len];
          int i = sequence.length-1;
          sequence[i--] = new IString(tok);
          if(len > 1) {
            for(Object c : struc.input.peekN(len-1)) {
              CoreLabel t = (CoreLabel) c;
              sequence[i--] = new IString(t.get(TextAnnotation.class));
            }
          }
          tagger.tagWord(w, sequence);
          parser.parseToken(struc, w, labelRelation);
        }
        struc.addRoot();
        deps = struc.getDependencies();
        
        StringBuilder sb = new StringBuilder();
        
        int countMatch = 0;
        int countRedundant = 0;
        int countMiss = 0;
        
        Object[] ds = deps.peekN(deps.size());

        sb = new StringBuilder("Dependencies: \n");
        for(int i = ds.length-1 ; i >= 0 ; i--) {
          String flag = "";
          systemDepStrings.add(ds[i].toString());
          if(depStrings.contains(ds[i].toString())) {
            countMatch++;
          } else {
            countRedundant++;
            flag = "\t-> redundant";
          }
          sb.append("\t").append(ds[i]).append(flag).append("\n");
        }
        System.out.println(sb.toString()+"\n");
                
        System.out.println("Stanford dependencies: ");
        for(String dep : depStrings) {
          String flag = "";
          if(!systemDepStrings.contains(dep)) {
            flag = "\t-> missed";
          }
          System.out.println("\t"+dep+flag);
        }
        
        countMiss = depStrings.size()-countMatch;

        System.out.println("count match: "+countMatch);
        System.out.println("count redundant: "+countRedundant);
        System.out.println("count miss: "+countMiss);
        
        sb = new StringBuilder();
        
        Object[] ts = struc.getInput().peekN(struc.getInput().size());
        for(int i = ts.length-1 ; i >= 0 ; i--) {
          CoreLabel cl = (CoreLabel) ts[i];
          sb.append("\t").append(cl.get(IndexAnnotation.class)).append("\t").append(cl.get(TextAnnotation.class)).append("\t").append(cl.get(PartOfSpeechAnnotation.class)).append("\n");
        }
        System.out.println(sb.toString()+"\n");
      }
      // System.out.printf("Current partial parse: %s\n", );
      // System.out.printf("Current stack contents: %s\n", );
      System.out.print("> ");
    }
  }
}
