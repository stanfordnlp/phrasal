package edu.stanford.nlp.mt.base;

import java.lang.reflect.Constructor;
import java.util.*;

import edu.stanford.nlp.mt.metrics.*;

public class EvaluationMetricFactory {

  public static final boolean SMOOTH_BLEU_DEFAULT = false;

  public static final String METEOR_CLASS_NAME = "edu.stanford.nlp.mt.metrics.METEORMetric";
  public static final String TER_CLASS_NAME = "edu.stanford.nlp.mt.metrics.TERMetric";
  public static final String TERP_CLASS_NAME = "edu.stanford.nlp.mt.metrics.TERpMetric";
  public static final String OTER_CLASS_NAME = "edu.stanford.nlp.mt.metrics.OriginalTERMetric";

  private static AbstractMetric<IString, String> createMetric(
      String metricName, Class<AbstractMetric<IString, String>>[] argClasses,
      Object[] args) {
    AbstractMetric<IString, String> metric;
    try {
      @SuppressWarnings("unchecked")
      Class<AbstractMetric<IString, String>> cls = (Class<AbstractMetric<IString, String>>) Class
          .forName(metricName);
      Constructor<AbstractMetric<IString, String>> ct = cls
          .getConstructor(argClasses);
      metric = ct.newInstance(args);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return metric;
  }


  public static AbstractMetric<IString,String> newMetric(String evalMetric,  
    List<List<Sequence<IString>>> references) { 
    return newMetric(evalMetric, references, SMOOTH_BLEU_DEFAULT);
  }

  public static AbstractMetric<IString,String> newMetric(String evalMetric,  
    List<List<Sequence<IString>>> references, boolean smoothBLEU) { 

    AbstractMetric<IString,String> emetric = null;

    String[] fields = evalMetric.split(":");

    // METEORMetric created using reflection:
    AbstractMetric<IString, String> meteorMetric = null;
    if (evalMetric.contains("meteor")) {
      double alpha = 0.95, beta = 0.5, gamma = 0.5;
      if (fields.length > 1) {
        assert (fields.length == 4);
        alpha = Double.parseDouble(fields[1]);
        beta = Double.parseDouble(fields[2]);
        gamma = Double.parseDouble(fields[3]);
      }
      meteorMetric = createMetric(METEOR_CLASS_NAME, new Class[] { List.class,
          double.class, double.class, double.class }, new Object[] {
          references, alpha, beta, gamma });
    }

    if (evalMetric.equals("bleu:3-2terp")) {
      int BLEUOrder = 3;
      double terW = 2.0;
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, terW }, new BLEUMetric<IString, String>(references, BLEUOrder,
          smoothBLEU), createMetric(TERP_CLASS_NAME, new Class[] { List.class,
          int.class, int.class }, new Object[] { references, 5, 10 }));
      // new TERpMetric<IString, String>(references, 5, 10));
      System.err.printf("Maximizing %s: BLEU:3 minus 2*TERp (terW=%f)\n",
          evalMetric, terW);
    } else if (evalMetric.equals("bleu:3-terp")) {
      int BLEUOrder = 3;
      double terW = 1.0;
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, terW }, new BLEUMetric<IString, String>(references, BLEUOrder,
          smoothBLEU), createMetric(TERP_CLASS_NAME,
          new Class[] { List.class }, new Object[] { references }));
      // new TERpMetric<IString, String>(references));
      System.err.printf("Maximizing %s: BLEU:3 minus 1*TERp (terW=%f)\n",
          evalMetric, terW);
    } else if (evalMetric.equals("terp")) {
      emetric = createMetric(TERP_CLASS_NAME, new Class[] { List.class },
          new Object[] { references });
      // emetric = new TERpMetric<IString, String>(references);
    } else if (evalMetric.equals("terpa")) {
      emetric = createMetric(TERP_CLASS_NAME, new Class[] { List.class,
          boolean.class, boolean.class }, new Object[] { references, false,
          true });
      // emetric = new TERpMetric<IString, String>(references, false, true);
    } else if (evalMetric.equals("meteor") || evalMetric.startsWith("meteor:")) {
      emetric = meteorMetric;
    } else if (evalMetric.equals("ter") || evalMetric.startsWith("ter:")) {
      AbstractTERMetric<IString, String> termetric = (AbstractTERMetric<IString, String>) createMetric(TER_CLASS_NAME, new Class[] { List.class },
          new Object[] { references });
      // new TERMetric<IString, String>(references) :
      // new OriginalTERMetric<IString, String>(references);
      termetric.enableFastTER();
      if (fields.length > 1) {
        int beamWidth = Integer.parseInt(fields[1]);
        termetric.setBeamWidth(beamWidth);
        System.err
            .printf("TER beam width set to %d (default: 20)\n", beamWidth);
        if (fields.length > 2) {
          int maxShiftDist = Integer.parseInt(fields[2]);
          termetric.setShiftDist(maxShiftDist);
          System.err.printf(
              "TER maximum shift distance set to %d (default: 50)\n",
              maxShiftDist);
        }
      }
      emetric = termetric;
    } else if (evalMetric.equals("bleu") || evalMetric.startsWith("bleu:")) {
      if (evalMetric.contains(":")) {
        int BLEUOrder = Integer.parseInt(fields[1]);
        emetric = new BLEUMetric<IString, String>(references, BLEUOrder,
            smoothBLEU);
      } else {
        emetric = new BLEUMetric<IString, String>(references, smoothBLEU);
      }
    } else if (evalMetric.equals("nist")) {
      emetric = new NISTMetric<IString, String>(references);
    } else if (evalMetric.startsWith("bleu-2terp")) {
      double terW = 2.0;
      if (fields.length > 1) {
        assert (fields.length == 2);
        terW = Double.parseDouble(fields[1]);
      }
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, terW }, new BLEUMetric<IString, String>(references, smoothBLEU),
          createMetric(TERP_CLASS_NAME, new Class[] { List.class },
              new Object[] { references }));
      // new TERpMetric<IString, String>(references));
      System.err.printf("Maximizing %s: BLEU minus TERpA (terW=%f)\n",
          evalMetric, terW);
    } else if (evalMetric.startsWith("bleu+2meteor")) {
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, 2.0 }, new BLEUMetric<IString, String>(references, smoothBLEU),
          meteorMetric);
      System.err.printf("Maximizing %s: BLEU + 2*METEORTERpA (meteorW=%f)\n",
          evalMetric, 2.0);
    } else if (evalMetric.startsWith("bleu-2terpa")) {
      double terW = 2.0;
      if (fields.length > 1) {
        assert (fields.length == 2);
        terW = Double.parseDouble(fields[1]);
      }
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, terW }, new BLEUMetric<IString, String>(references, smoothBLEU),
          createMetric(TERP_CLASS_NAME, new Class[] { List.class,
              boolean.class, boolean.class }, new Object[] { references, false,
              true }));
      // new TERpMetric<IString, String>(references, false, true));
      System.err.printf("Maximizing %s: BLEU minus TERpA (terW=%f)\n",
          evalMetric, terW);
    } else if (evalMetric.startsWith("bleu-ter")) {
      double terW = 1.0;
      if (fields.length > 1) {
        assert (fields.length == 2);
        terW = Double.parseDouble(fields[1]);
      }
      AbstractTERMetric<IString, String> termetric = (AbstractTERMetric<IString, String>) createMetric(
          TER_CLASS_NAME, new Class[] { List.class },
          new Object[] { references });
      // new TERMetric<IString, String>(references) :
      // new OriginalTERMetric<IString, String>(references);
      termetric.enableFastTER();
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, terW }, new BLEUMetric<IString, String>(references, smoothBLEU),
          termetric);
      System.err.printf("Maximizing %s: BLEU minus TER (terW=%f)\n",
          evalMetric, terW);
    } else if (evalMetric.equals("wer")) {
      emetric = new WERMetric<IString, String>(references);
    } else if (evalMetric.equals("per")) {
      emetric = new PERMetric<IString, String>(references);
    } else {
      emetric = null;
      throw new UnsupportedOperationException(String.format(
          "Unrecognized metric: %s\n", evalMetric));
    }
    return emetric;
  }
}
