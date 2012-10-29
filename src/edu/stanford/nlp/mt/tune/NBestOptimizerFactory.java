package edu.stanford.nlp.mt.tune;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import edu.stanford.nlp.mt.tune.optimizers.BasicPowellOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.CerStyleOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.DownhillSimplexOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.KoehnStyleOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.LineSearchOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.PairwiseRankingOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.PowellOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.SequenceOptimizer;

/**
 * @author Daniel Cer
 * @author Michel Galley
 */
public class NBestOptimizerFactory {

  private NBestOptimizerFactory() {
  }

  public static NBestOptimizer factory(String name, int point, MERT mert) {

    if (name.contains("+") || name.endsWith("~")) {
      boolean loop = name.endsWith("~");
      name = name.replaceAll("~", "");
      System.err.println("seq: loop: " + loop);
      List<NBestOptimizer> opts = new ArrayList<NBestOptimizer>();
      for (String el : name.split("\\+")) {
        opts.add(factory(el, point, mert));
        System.err.println("seq: adding " + el);
      }
      return new SequenceOptimizer(mert, opts, loop);
    } else if (name.equalsIgnoreCase("cer")) {
      return new CerStyleOptimizer(mert, point);
    } else if (name.equalsIgnoreCase("koehn")) {
      return new KoehnStyleOptimizer(mert);
    } else if (name.equalsIgnoreCase("basicPowell")) {
      return new BasicPowellOptimizer(mert);
    } else if (name.equalsIgnoreCase("powell")) {
      return new PowellOptimizer(mert);
    } else if (name.startsWith("simplex")) {
      return new DownhillSimplexOptimizer(mert);
    } else if (name.startsWith("pro")) {
      return new PairwiseRankingOptimizer(mert);
    } else if (name.equalsIgnoreCase("length")) {
      return new LineSearchOptimizer(mert);
    } else {
      System.err.println("Non-standard optimizer: " + name + " loading by reflection");
      String[] fields = name.split(":");      
      String[] args = Arrays.copyOfRange(fields, 1, fields.length);
      String[] pathsToCheck = new String[]{"edu.stanford.nlp.mt.tune.optimizers." + fields[0], "edu.stanford.nlp.mt.tune.optimizers."+fields[0]+"Optimizer", fields[0]};
      String loadPath = null;
      
      for (String path : pathsToCheck) {
        try {
          Class.forName(path);
          loadPath = path;
        } catch(ClassNotFoundException e) { }
      }
      
      if (loadPath == null) {
        throw new UnsupportedOperationException("Unknown optimizer: " + name);
      }
      
      System.err.printf("Loading %s", loadPath);
      try {
          System.err.printf("Trying: %s\n", loadPath);
          NBestOptimizer nbo = (NBestOptimizer) Class.forName(loadPath).getConstructor(MERT.class, args.getClass()).newInstance(new Object[]{mert, args});
          System.err.println("Loaded optimizer "+nbo.getClass().toString());
          return nbo;
      } catch (Exception e) { 
         e.printStackTrace(System.err);
         throw new RuntimeException(e);            
      }                     

    }
  }
}
