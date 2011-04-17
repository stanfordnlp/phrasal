package edu.stanford.nlp.mt.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.ling.WordTag;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.objectbank.TokenizerFactory;
import edu.stanford.nlp.parser.maltparser.MaltParserInterface;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.Dependencies;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.Filter;

/**
 * Tool for interactively inspecting dependency chains 
 * 
 * @author danc
 *
 */
public class InteractiveDependencyChains {
  public static String wordOnly(String wordIndx) {
    return wordIndx.substring(0,wordIndx.lastIndexOf("-"));
  }
  public static void main(String[] args) throws Exception {
    MaxentTagger tagger = new MaxentTagger(MaxentTagger.DEFAULT_DISTRIBUTION_PATH);
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    MaltParserInterface mpi = new MaltParserInterface(args[0]);
    TokenizerFactory<CoreLabel> ptbtokf = PTBTokenizer.factory(false, false);
    Morphology morpha = new Morphology();
    Filter<String> puncFilter = new PennTreebankLanguagePack().punctuationWordRejectFilter();
    
    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      List<CoreLabel> words = ptbtokf.getTokenizer(new StringReader(line)).tokenize();      
      tagger.tagCoreLabels(words);
      for (CoreLabel word : words) {
        String text = word.get(CoreAnnotations.TextAnnotation.class);
        //System.err.println("Token #" + i + ": " + token);
        String posTag = word.get(PartOfSpeechAnnotation.class);
        WordTag wt = morpha.stem(text, posTag);
        word.setLemma(wt.word());
      }
      System.out.printf("Procesing: %s\n", words);
      List<TypedDependency> typeDeps = mpi.parseToGrammaticalStructure(words).typedDependenciesCCprocessed(true);
      List<TypedDependency> filteredDeps = new ArrayList<TypedDependency>(typeDeps.size());
      
      for (TypedDependency tdep : typeDeps) {
        if (puncFilter.accept(wordOnly(tdep.gov().label().toString())) && puncFilter.accept(wordOnly(tdep.dep().label().toString()))) {
          filteredDeps.add(tdep);
        }
      }
      System.out.println("Type Deps:\n");
      System.out.println(filteredDeps);
      
      System.out.printf("Chains:\n");
      Set<List<TypedDependency>> chains = Dependencies.getTypedDependencyChains(typeDeps, 4);
      for (List<TypedDependency> chain : chains) {
        System.out.println(chain);
      }
    }    
  }
}
