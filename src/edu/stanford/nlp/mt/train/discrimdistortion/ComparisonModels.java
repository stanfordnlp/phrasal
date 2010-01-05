package edu.stanford.nlp.mt.train.discrimdistortion;

import java.util.*;
import java.io.*;
import java.util.Map;

import edu.stanford.nlp.mt.base.IOTools;

import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

public class ComparisonModels {

  private static final String algnExtension = ".algn";
  private static final String fExtension = ".f";
  private static final String eExtension = ".e";

  public static enum Mode {Train, Train2, Test};

  private static void trainTestCounts(final String trainPrefix, final String testPrefix) {
    Counter<DistortionModel.Class> counts = new ClassicCounter<DistortionModel.Class>();
    Index<DistortionModel.Class> classIndex = new HashIndex<DistortionModel.Class>();
    for(DistortionModel.Class c : DistortionModel.Class.values())
      classIndex.add(c);

    System.out.println(">> Counts model <<");

    double logLik = 0.0;
    int testAlignments = 0;
    for(Mode mode : Mode.values()) {
      if(mode == Mode.Train2) continue; //Not needed for this model
      String filePrefix = (mode == Mode.Train) ? trainPrefix : testPrefix;
      File algnFile = new File(filePrefix + algnExtension);
      File enFile = new File(filePrefix + eExtension);
      File arFile = new File(filePrefix + fExtension);

      FeatureExtractor fe = new FeatureExtractor(1,arFile,enFile,algnFile);
      fe.setMinWordCount(1);
      fe.setVerbose(true);
      fe.setExtractOnly();

      TrainingSet ts = fe.extract(new HashIndex<DistortionModel.Feature>(), classIndex, 14000);
      if(mode == Mode.Test)
        testAlignments = ts.getNumExamples();
      
      for(Datum d : ts) {
        DistortionModel.Class goldClass = DistortionModel.discretizeDistortion((int) d.getTarget());
        if(mode == Mode.Train)
          counts.incrementCount(goldClass);
        else
          logLik += Math.log(counts.getCount(goldClass));
      }

      if(mode == Mode.Train) {
        Counters.printCounterSortedByKeys(counts);
        Counters.normalize(counts);
        System.out.println("Parameters: ");
        PrintStream ps = IOTools.getWriterFromFile("multinomial.params");
        for(DistortionModel.Class c : counts.keySet()) {
          String params = String.format("%s %f", c.toString(), counts.getCount(c));
          System.out.println(params);
          ps.println(params);
        }
        ps.close();
      }
    }

    System.out.println("===================================");
    System.out.printf("Test alignments: %d\n", testAlignments);
    System.out.printf("LogLik: %f\n", logLik);  
  }


  private static void trainTestMoses(final String trainPrefix, final String testPrefix) {

    System.out.println(">> Moses model (Laplacian) <<");

    double m = 0.0;
    double u_hat = 0.0;
    double b_hat = 0.0;

    double logLik = 0.0;
    int testAlignments = 0;
    int meanNum = 0;
    for(Mode mode : Mode.values()) {
      String filePrefix = (mode == Mode.Train || mode == Mode.Train2) ? trainPrefix : testPrefix;
      LineNumberReader algnReader = IOTools.getReaderFromFile(filePrefix + algnExtension);

      //Estimate the parameters
      try {
        while(algnReader.ready()) {
          StringTokenizer alignTokenizer = new StringTokenizer(algnReader.readLine());
          Map<Integer,Integer> alignmentMap = new TreeMap<Integer,Integer>();
          Set<Integer> alignedSToks = new HashSet<Integer>();
          while(alignTokenizer.hasMoreTokens()) {
            String alignment = alignTokenizer.nextToken();
            String[] indices = alignment.split("-");

            assert indices.length == 2;

            int sIdx = Integer.parseInt(indices[0]);
            int tIdx = Integer.parseInt(indices[1]);

            alignedSToks.add(sIdx);

            if(alignmentMap.containsKey(tIdx))
              System.err.printf("%WARNING many-to-one alignment at line %d. Are you using the intersect heuristic?\n", algnReader.getLineNumber());

            alignmentMap.put(tIdx, sIdx);
          }

          if(mode == Mode.Test)
            testAlignments += alignmentMap.keySet().size();

          int lastSPos = Integer.MIN_VALUE;
          for(Map.Entry<Integer, Integer> alignment : alignmentMap.entrySet()) {
            //Account for null alignments
            int distortion = 0;
            if(lastSPos == Integer.MIN_VALUE)
              distortion = alignment.getValue();
            else {
              int sIdx = alignment.getValue();
              distortion = lastSPos + 1 - sIdx;
              if(distortion > 0)
                distortion--; //Adjust for gap
              distortion *= -1; //Turn it into a cost            
            }
            lastSPos = alignment.getValue();

            if(mode == Mode.Train) {
              m++;
              meanNum += distortion;
            } else if(mode == Mode.Train2)
              b_hat += Math.abs(((double) distortion) - u_hat);
            else
              logLik += (-1.0 * Math.log(2.0 * b_hat)) - (Math.abs(((double) distortion) - u_hat) / b_hat);
          }     
        }

        algnReader.close();

        if(mode == Mode.Train) {
          u_hat = ((double) meanNum) / m; //Sample mean
          System.out.println("Parameters: ");
          System.out.printf("m: %f\n",m);
          System.out.printf("u_hat: %f\n", u_hat);

        } else if(mode == Mode.Train2) {
          b_hat /= m;
          System.out.printf("b_hat: %f\n", b_hat);
        }

      } catch (NumberFormatException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    System.out.println("===================================");
    System.out.printf("Test alignments: %d\n", testAlignments);
    System.out.printf("LogLik: %f\n", logLik);   
  }


  /**
   */
  public static void main(String[] args) {
    if(args.length != 3) {
      System.err.println("usage: ComparisonModels [-m|-c] trainPrefix testPrefix");
      System.exit(-1);
    }

    if(args[0].equals("-m"))
      trainTestMoses(args[1],args[2]);
    else if(args[0].equals("-c"))
      trainTestCounts(args[1],args[2]);
    else
      System.err.printf("Unknown option %f\n", args[0]);
  }
}
