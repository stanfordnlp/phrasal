package edu.stanford.nlp.mt.tools.deplm;

import java.io.IOException;
import java.io.LineNumberReader;
import java.text.DecimalFormat;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Properties;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.mt.lm.LanguageModel;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

public class DependencyLanguageModelScoreNBest {

  private static LanguageModel<IString> DEPLM;
  
  
  /**
   * Command-line option specification.
   */
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = Generics.newHashMap();
    optionArgDefs.put("sourceTokens", 1); 
    optionArgDefs.put("nBestList", 1); 
    optionArgDefs.put("dependencies", 1);
    optionArgDefs.put("lm", 1);
    return optionArgDefs;
  }
  
  
  
  public static void main(String[] args) throws IOException {

    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    String sourceTokens = PropertiesUtils.get(options, "sourceTokens", null, String.class);
    String nBestList = PropertiesUtils.get(options, "nBestList", null, String.class);
    String dependencies = PropertiesUtils.get(options, "dependencies", null, String.class);
    String lm = PropertiesUtils.get(options, "lm", null, String.class);

    
    if (sourceTokens == null || nBestList == null || dependencies == null || lm == null) {
      System.err.println("java " + DependencyLanguageModelScoreNBest.class.getCanonicalName() + " -sourceTokens file -nBestList file -dependencies file -lm file");
      return;
    }
    
    DEPLM = LanguageModelFactory.load(lm);

    
    LineNumberReader sourceReader = IOTools.getReaderFromFile(sourceTokens);
    LineNumberReader nBestListReader = IOTools.getReaderFromFile(nBestList);
    LineNumberReader dependenciesReader = IOTools.getReaderFromFile(dependencies);
    
    String separatorExpr = " \\|\\|\\| ";
    
    String separator = " ||| ";
    String sourceSentence;
    String nBestLine = nBestListReader.readLine();
    String currentId = nBestLine.split(separatorExpr)[0];
    
    DecimalFormat df = new DecimalFormat("0.####E0");

    while ((sourceSentence = sourceReader.readLine()) != null) {
      HashMap<Integer, Pair<IndexedWord, List<Integer>>> head2Dependents = DependencyUtils.getDependenciesFromCoNLLFileReader(dependenciesReader, true, true);
      Map<Integer, Integer> dependent2Head = DependencyUtils.getReverseDependencies(head2Dependents);

      while (nBestLine != null && nBestLine.split(separatorExpr)[0].equals(currentId)) {
        String nBestParts[] = nBestLine.split(separatorExpr);
        String translation = nBestParts[1];
        String alignmentString = nBestParts[4];
          
        SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(sourceSentence, translation, alignmentString);
          
          
        Map<Integer, NavigableSet<Integer>> projectedDependencies = DependencyProjectorCoNLL.projectDependencies(dependent2Head, alignment, true);

        double score = scoreTree(projectedDependencies, alignment.e());
          
        int deplmWordCount = 0;
        
        for (int i = 0; i < alignment.e().size(); i++) {
          if ( ! TokenUtils.isPunctuation(alignment.e().get(i).word())) {
            deplmWordCount++;
          }
        }
        
        System.out.print(nBestParts[0]);
        System.out.print(separator);
        System.out.print(nBestParts[1]);
        System.out.print(separator);
        System.out.print(nBestParts[2]);
        System.out.print(" DEPLM: ");
        System.out.print(df.format(score));
        System.out.print(" DEPLMWORDPENALTY: ");
        System.out.print(-deplmWordCount);
        System.out.print(" DEPLMPERP: ");
        System.out.print(deplmWordCount > 0 ? df.format(score / deplmWordCount) : 0);
        System.out.print(separator);
        System.out.print(nBestParts[3]);
        System.out.print(separator);
        System.out.print(nBestParts[4]);
        System.out.println("");

        nBestLine = nBestListReader.readLine();
      }
      currentId = nBestLine != null ? nBestLine.split(separatorExpr)[0] : "";
    }

  }



  private static double scoreTree(Map<Integer, NavigableSet<Integer>> projectedDependencies, Sequence<IString> e) throws IOException {
    
    HashMap<Integer, Pair<IndexedWord, List<Integer>>> forwardDependencies = new HashMap<Integer, Pair<IndexedWord, List<Integer>>>();

    for (int i = 1; i <= e.size(); i++) {
      String word = e.get(i-1).word();

      IndexedWord iw = new IndexedWord();
      iw.setIndex(i);
      iw.setWord(word);
      
      Pair<IndexedWord, List<Integer>> pair = new Pair<IndexedWord, List<Integer>>();
      pair.setFirst(iw);
      pair.setSecond(Generics.newLinkedList());
      forwardDependencies.put(i, pair);
    }

    {
      Pair<IndexedWord, List<Integer>> pair = new Pair<IndexedWord, List<Integer>>();
      pair.setSecond(Generics.newLinkedList());
      forwardDependencies.put(-1, pair);
    }
    
    BitSet dependentsWithHead = new BitSet();
    
    for (int gov : projectedDependencies.keySet()) {
      int oneIndexedGov = gov + 1;
      for (int dep : projectedDependencies.get(gov)) {
        dep = dep + 1;
        dependentsWithHead.set(dep);
        if (forwardDependencies.get(oneIndexedGov) == null) {
          Pair<IndexedWord, List<Integer>> pair = new Pair<IndexedWord, List<Integer>>();
          pair.setSecond(Generics.newLinkedList());
          forwardDependencies.put(oneIndexedGov, pair);
        }
        forwardDependencies.get(oneIndexedGov).second.add(dep);
      }
    }
 
    for (int i = 1; i <= e.size(); i++) {
      if ( ! dependentsWithHead.get(i)) {
        forwardDependencies.get(-1).second.add(i);
      }
    }
    
    return DependencyLanguageModelPerplexity2.scoreTree(forwardDependencies, DEPLM);
  }

}
