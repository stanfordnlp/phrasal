package edu.stanford.nlp.mt.tune;



import edu.stanford.nlp.mt.tune.optimizers.BasicPowellOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.CerStyleOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.BadLicenseDownhillSimplexOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.DownhillSimplexOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.KoehnStyleOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.LineSearchOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.PowellOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.SequenceOptimizer;
import edu.stanford.nlp.optimization.DownhillSimplexMinimizer;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;



/**
 * @author Daniel Cer
 * @author Michel Galley
 */
public class NBestOptimizerFactory {

  private NBestOptimizerFactory() {
  }

  public static NBestOptimizer factory(String name, MERT mert) {

    if (name.contains("+") || name.endsWith("~")) {
      boolean loop = name.endsWith("~");
      name = name.replaceAll("~", "");
      System.err.println("seq: loop: " + loop);
      List<NBestOptimizer> opts = new ArrayList<NBestOptimizer>();
      for (String el : name.split("\\+")) {
        opts.add(factory(el, mert));
        System.err.println("seq: adding " + el);
      }
      return new SequenceOptimizer(mert, opts, loop);
    } else if (name.equalsIgnoreCase("cer")) {
      return new CerStyleOptimizer(mert);
    } else if (name.equalsIgnoreCase("koehn")) {
      return new KoehnStyleOptimizer(mert);
    } else if (name.equalsIgnoreCase("basicPowell")) {
      return new BasicPowellOptimizer(mert);
    } else if (name.equalsIgnoreCase("powell")) {
      return new PowellOptimizer(mert);
    } else if (name.startsWith("simplex")) {
      return new DownhillSimplexOptimizer(mert);
    } else if (name.startsWith("deprecatedSimplex")) {
      String[] els = name.split(":");
      int iter = els.length == 2 ? Integer.parseInt(els[1]) : 1;
      return new BadLicenseDownhillSimplexOptimizer(mert, iter, false);
    } else if (name.startsWith("deprecatedRandomSimplex")) {
      String[] els = name.split(":");
      int iter = els.length == 2 ? Integer.parseInt(els[1]) : 1;
      return new BadLicenseDownhillSimplexOptimizer(mert, iter, true);
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
            throw new RuntimeException(e);            
      }                     

    }
  }
}
