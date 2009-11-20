package mt.train.discrimdistortion;

import java.util.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

import mt.base.IOTools;

import edu.stanford.nlp.stats.*;

public class ComparisonModels {

  private static final String algnExtension = ".algn";
  private static final String fExtension = ".f";
  private static final String eExtension = ".e";

  public static enum Mode {Train, Train2, Test};

  private static void trainTestCounts(final String trainPrefix, final String testPrefix) {
    Counter<DistortionModel.Class> counts = new ClassicCounter<DistortionModel.Class>();

    System.out.println(">> Counts model <<");

    double logLik = 0.0;
    int testAlignments = 0;
    for(Mode mode : Mode.values()) {
      if(mode == Mode.Train2) continue; //Not needed for this model
      String filePrefix = (mode == Mode.Train) ? trainPrefix : testPrefix;
      LineNumberReader algnReader = IOTools.getReaderFromFile(filePrefix + algnExtension);
      LineNumberReader enReader = IOTools.getReaderFromFile(filePrefix + eExtension);
      LineNumberReader arReader = IOTools.getReaderFromFile(filePrefix + fExtension);

      //Estimate the parameters
      try {
        while(algnReader.ready() && enReader.ready() && arReader.ready()) {
          final float arLen = arReader.readLine().split("\\s+").length;
          final float enLen = enReader.readLine().split("\\s+").length;

          StringTokenizer alignTokenizer = new StringTokenizer(algnReader.readLine());
          Map<Integer,Integer> alignmentMap = new HashMap<Integer,Integer>();
          while(alignTokenizer.hasMoreTokens()) {
            String alignment = alignTokenizer.nextToken();
            String[] indices = alignment.split("-");

            assert indices.length == 2;

            int sIdx = Integer.parseInt(indices[0]);
            int tIdx = Integer.parseInt(indices[1]);

            if(alignmentMap.containsKey(sIdx))
              System.err.printf("%WARNING many-to-one alignment at line %d. Are you using the intersect heuristic?\n", algnReader.getLineNumber());

            alignmentMap.put(sIdx, tIdx);
          }

          if(mode == Mode.Test)
            testAlignments += alignmentMap.keySet().size();

          for(Map.Entry<Integer, Integer> alignment : alignmentMap.entrySet()) {
            final int sIdx = alignment.getKey();
            final int tIdx = alignment.getValue();

            float sRel = ((float) sIdx / arLen) * 100.0f;  
            float tRel = ((float) tIdx / enLen) * 100.0f;

            float targetValue = tRel - sRel;

            DistortionModel.Class thisClass = DistortionModel.discretizeDistortion((int) targetValue);

            if(mode == Mode.Train)
              counts.incrementCount(thisClass);
            else
              logLik += Math.log(counts.getCount(thisClass));
          }     
        }

        algnReader.close();
        enReader.close();
        arReader.close();

        if(mode == Mode.Train) {
          Counters.normalize(counts);
          System.out.println("Parameters: ");
          for(DistortionModel.Class c : counts.keySet())
            System.out.printf("%s: %f\n", c.toString(), counts.getCount(c));
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
//              for(int i = lastSPos; !(alignedSToks.contains(i)) && i < sIdx; i++)
//                lastSPos++;
              distortion = lastSPos + 1 - sIdx;
            }
            distortion *= -1; //Turn it into a cost
            
            lastSPos = alignment.getValue();
            
            if(mode == Mode.Train) {
              m++;
              meanNum += distortion;
            } else if(mode == Mode.Train2)
              b_hat += Math.abs((double) distortion - u_hat);
            else
              logLik += (-1*Math.log(2*b_hat)) - (Math.abs((double) distortion - u_hat) / b_hat);
          }     
        }

        algnReader.close();

        if(mode == Mode.Train) {
          u_hat = (double) meanNum / m; //Sample mean
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
   * @param args
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
