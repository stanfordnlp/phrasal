///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2007 University of Texas at Austin and (C) 2005
// University of Pennsylvania and Copyright (C) 2002, 2003 University
// of Massachusetts Amherst, Department of Computer Science.
//
// This software is licensed under the terms of the Common Public
// License, Version 1.0 or (at your option) any subsequent version.
// 
// The license is approved by the Open Source Initiative, and is
// available from their website at http://www.opensource.org.
///////////////////////////////////////////////////////////////////////////////

package edu.stanford.nlp.mt.syntax.mst.rmcd;

import edu.stanford.nlp.mt.syntax.mst.rmcd.io.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.net.InetAddress;

/**
 * Hold all the options for the parser so they can be passed around easily.
 * <p/>
 * <p>
 * Created: Sat Nov 10 15:25:10 2001
 * </p>
 * 
 * @author Jason Baldridge
 * @version $Id: CONLLReader.java 103 2007-01-21 20:26:39Z jasonbaldridge $
 * @see edu.stanford.nlp.mt.syntax.mst.rmcd.io.DependencyReader
 */
public final class ParserOptions {

  private static final String DEFAULT_SERIALIZED_TAGGER = "/u/nlp/data/pos-tagger/wsj3t0-18-bidirectional/bidirectional-wsj-0-18.tagger";

  public String trainfile = null;
  public String testfile = null;
  public File trainforest = null;
  public File testforest = null;
  public boolean trainME = false;
  public boolean predictRight = false;
  public boolean train = false;
  public boolean eval = false;
  public boolean test = false;
  public String modelName = "dep.model";
  public String mixModelNames;
  public String lossType = "punc";
  public boolean createForest = true;
  public String decodeType = "proj";
  public boolean proj;
  public String format = "CONLL";
  public String goldformat = "CONLL";
  public String outputformat = "CONLL";
  public int numIters = 10;
  public int sents = -1;
  public String outfile = "out.txt";
  public String goldfile = null;
  public String tagger = DEFAULT_SERIALIZED_TAGGER;
  public boolean pretag = false;
  public int trainK = 1;
  public int testK = 1;
  public double l1reg = 1.0;
  public boolean useRelationalFeatures = false;
  public boolean discourseMode = false;
  public boolean labeled = true;
  public boolean prefixParser = false;
  public String txtModelName = null;
  public boolean genTextModel = false;
  public boolean ignoreLoops = false;
  public boolean debug = false, debugFeatures = false;
  public boolean binary = false;
  public boolean noreindex = false;
  public static String distBinStr = "1,2,3,4,5,6,9,12,20";
  // McDonald: "1,2,3,4,5,6,11";
  public boolean english = true;
  public boolean trim = false;

  // Bilingual stuff:
  public String ftrainfile = null, atrainfile = null;
  public String ftestfile = null, atestfile = null;

  // Feature extraction:
  public int posWindowSize = 0; // Integer.MAX_VALUE;
  public int offsetWindowSize = 0;
  public int bilingualDetail = 1;
  public boolean coreFeatures = true, lemmaFeatures = true,
      cposFeatures = true, posLinearFeatures = true, cposLinearFeatures = true,
      bilingualC = false, bilingualH = false, bilingualH2C = false;
  public String inBetweenPOS = "NN~NNS~VBD~VBZ~VBP~MD~,~.";
  public String inBetweenCPOS = "NN~VB~MD~IN~TO~CC~,~.";

  public void printFeatureOptions() {
    System.err.println("Features:" + "\n  english: " + english
        + "\n  typed dependencies: " + labeled + "\n  core features: "
        + coreFeatures + "\n  lemma features: " + lemmaFeatures
        + "\n  CPOS features: " + cposFeatures
        + "\n  POS in-between features: " + posLinearFeatures
        + "\n  CPOS in-between features: " + cposLinearFeatures
        + "\n  POS in-between features (fast): " + inBetweenPOS
        + "\n  CPOS in-between features (fast): " + inBetweenCPOS
        + "\n  bilingual head unigram features: " + bilingualH
        + "\n  bilingual child unigram features: " + bilingualC
        + "\n  bilingual head-to-child features: " + bilingualH2C
        + "\n  in-between POS window size:" + posWindowSize
        + "\n  in-between POS+offset window size:" + offsetWindowSize
        + "\n  bilingual features detail level: " + bilingualDetail);
  }

  public ParserOptions() {
    this(new String[0]);
  }

  public ParserOptions(String[] args) {

    try {
      java.net.InetAddress localMachine = java.net.InetAddress.getLocalHost();
      System.err.println("Hostname: " + localMachine.getHostName());
    } catch (java.net.UnknownHostException e) {
      e.printStackTrace();
    }

    for (String arg : args) {
      String[] pair = arg.split(":");

      if (pair[0].equalsIgnoreCase("train")) {
        train = true;
      } else if (pair[0].equalsIgnoreCase("noreindex")) {
        noreindex = true;
      } else if (pair[0].equalsIgnoreCase("predict-right")) {
        predictRight = true;
      } else if (pair[0].equalsIgnoreCase("binary")) {
        binary = true;
      } else if (pair[0].equalsIgnoreCase("no-english")) {
        english = false;
      } else if (pair[0].equalsIgnoreCase("trim")) {
        trim = true;
      } else if (pair[0].equalsIgnoreCase("dist-bin")) {
        distBinStr = pair[1];
      } else if (pair[0].equalsIgnoreCase("maxent")) {
        train = true;
        trainME = true;
      } else if (pair[0].equalsIgnoreCase("debug")) {
        debug = true;
      } else if (pair[0].equalsIgnoreCase("debug-features")) {
        debug = true;
        debugFeatures = true;
      } else if (pair[0].equalsIgnoreCase("eval")) {
        eval = true;
      } else if (pair[0].equalsIgnoreCase("test")) {
        test = true;
      } else if (pair[0].equalsIgnoreCase("tagger")) {
        tagger = pair[1];
      } else if (pair[0].equalsIgnoreCase("pretag")) {
        pretag = true;
      } else if (pair[0].equalsIgnoreCase("prefix")) {
        prefixParser = true;
      } else if (pair[0].equalsIgnoreCase("iters")) {
        numIters = Integer.parseInt(pair[1]);
      } else if (pair[0].equalsIgnoreCase("sents")) {
        sents = Integer.parseInt(pair[1]);
      } else if (pair[0].equalsIgnoreCase("l1reg")) {
        trainME = true;
        l1reg = Double.parseDouble(pair[1]);
      } else if (pair[0].equalsIgnoreCase("output-file")) {
        outfile = pair[1];
      } else if (pair[0].equalsIgnoreCase("gold-file")) {
        goldfile = pair[1];
      } else if (pair[0].equalsIgnoreCase("train-file")) {
        trainfile = pair[1];
      } else if (pair[0].equalsIgnoreCase("test-file")) {
        testfile = pair[1];
      } else if (pair[0].equalsIgnoreCase("f-train-file")) {
        ftrainfile = pair[1];
      } else if (pair[0].equalsIgnoreCase("f-test-file")) {
        ftestfile = pair[1];
      } else if (pair[0].equalsIgnoreCase("a-train-file")) {
        atrainfile = pair[1];
      } else if (pair[0].equalsIgnoreCase("a-test-file")) {
        atestfile = pair[1];
      } else if (pair[0].equalsIgnoreCase("model-name")) {
        modelName = pair[1];
        txtModelName = pair[1].replaceAll("gz", "txt.gz");
        if (txtModelName.equals(modelName))
          txtModelName = null;
      } else if (pair[0].equalsIgnoreCase("model-txt")) {
        genTextModel = true;
      } else if (pair[0].equalsIgnoreCase("training-k")) {
        trainK = Integer.parseInt(pair[1]);
      } else if (pair[0].equalsIgnoreCase("pos-win-sz")) {
        posWindowSize = Integer.parseInt(pair[1]);
        english = false;
      } else if (pair[0].equalsIgnoreCase("offset-win-sz")) {
        offsetWindowSize = Integer.parseInt(pair[1]);
        english = false;
      } else if (pair[0].equalsIgnoreCase("bilingual-feature-detail")) {
        bilingualDetail = Integer.parseInt(pair[1]);
      } else if (pair[0].equalsIgnoreCase("disable-core")) {
        coreFeatures = false;
      } else if (pair[0].equalsIgnoreCase("disable-lemma")) {
        lemmaFeatures = false;
      } else if (pair[0].equalsIgnoreCase("disable-cpos")) {
        cposFeatures = false;
      } else if (pair[0].equalsIgnoreCase("disable-linear-pos")) {
        posLinearFeatures = false;
      } else if (pair[0].equalsIgnoreCase("disable-linear-cpos")) {
        cposLinearFeatures = false;
      } else if (pair[0].equalsIgnoreCase("enable-bilingual-h")) {
        bilingualH = true;
      } else if (pair[0].equalsIgnoreCase("enable-bilingual-c")) {
        bilingualC = true;
      } else if (pair[0].equalsIgnoreCase("enable-bilingual-h2c")) {
        bilingualH2C = true;
      } else if (pair[0].equalsIgnoreCase("in-between-pos")) {
        inBetweenPOS = pair[1];
      } else if (pair[0].equalsIgnoreCase("in-between-cpos")) {
        inBetweenCPOS = pair[1];
      } else if (pair[0].equalsIgnoreCase("ignore-loops")) {
        ignoreLoops = true;
      } else if (pair[0].equalsIgnoreCase("loss-type")) {
        lossType = pair[1];
      } else if (pair[0].equalsIgnoreCase("mix-models")) {
        mixModelNames = pair[1];
      } else if (pair[0].equalsIgnoreCase("create-forest")) {
        createForest = pair[1].equalsIgnoreCase("true");
      } else if (pair[0].equalsIgnoreCase("decode-type")) {
        decodeType = pair[1];
      } else if (pair[0].equalsIgnoreCase("format")) {
        format = pair[1];
      } else if (pair[0].equalsIgnoreCase("gold-format")) {
        goldformat = pair[1];
      } else if (pair[0].equalsIgnoreCase("relational-features")) {
        useRelationalFeatures = pair[1].equalsIgnoreCase("true");
      } else if (pair[0].equalsIgnoreCase("discourse-mode")) {
        discourseMode = pair[1].equalsIgnoreCase("true");
      } else if (pair[0].equalsIgnoreCase("labeled")) {
        labeled = Boolean.parseBoolean(pair[1]);
      } else if (pair[0].equalsIgnoreCase("txt-model")) {
        txtModelName = pair[1];
      } else {
        throw new RuntimeException("unknown options: " + Arrays.toString(pair));
      }
    }

    if (decodeType.equalsIgnoreCase("proj")) {
      proj = true;
    } else if (decodeType.equalsIgnoreCase("non-proj")) {
      proj = false;
    } else {
      throw new RuntimeException("Unknown decoding type: " + decodeType);
    }

    File localDir;
    try {
      String machine = InetAddress.getLocalHost().getHostName().split("\\.")[0]
          .toLowerCase();
      System.err.println("Machine name: " + machine);
      File tmpDir = new File("/" + machine + "/scr1");
      if (!tmpDir.isDirectory())
        tmpDir = new File("/tmp");
      assert (tmpDir.isDirectory());
      localDir = new File(tmpDir.toString() + "/mst");
      if (!localDir.isDirectory()) {
        if (!localDir.mkdir()) {
          if (!localDir.isDirectory()) {
            throw new IOException("Can't create directory: "
                + localDir.toString());
          }
        }
      }
      System.err.println("Local directory: " + localDir);
      if (null != trainfile) {
        trainforest = File.createTempFile("train", ".forest", localDir);
        trainforest.deleteOnExit();
      } else {
        train = false;
      }

      if (null != testfile) {
        testforest = File.createTempFile("test", ".forest", localDir);
        testforest.deleteOnExit();
      } else {
        test = false;
      }

    } catch (java.io.IOException e) {
      System.out.println("Unable to create tmp files for feature forests!");
      e.printStackTrace();
      System.exit(0);
    }
    System.err.printf("Debug mode: %s. Features: %s\n", debug, debugFeatures);
    DependencyReader.setSerializedTaggerModel(tagger);
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("FLAGS [");
    sb.append("train-file: ").append(trainfile);
    sb.append(" | ");
    sb.append("test-file: ").append(testfile);
    sb.append(" | ");
    sb.append("gold-file: ").append(goldfile);
    sb.append(" | ");
    sb.append("output-file: ").append(outfile);
    sb.append(" | ");
    sb.append("model-name: ").append(modelName);
    sb.append(" | ");
    sb.append("train: ").append(train);
    sb.append(" | ");
    sb.append("test: ").append(test);
    sb.append(" | ");
    sb.append("eval: ").append(eval);
    sb.append(" | ");
    sb.append("loss-type: ").append(lossType);
    sb.append(" | ");
    sb.append("training-iterations: ").append(numIters);
    sb.append(" | ");
    sb.append("training-k: ").append(trainK);
    sb.append(" | ");
    sb.append("decode-type: ").append(decodeType);
    sb.append(" | ");
    sb.append("create-forest: ").append(createForest);
    sb.append(" | ");
    sb.append("format: ").append(format);
    sb.append(" | ");
    sb.append("relational-features: ").append(useRelationalFeatures);
    sb.append(" | ");
    sb.append("discourse-mode: ").append(discourseMode);
    sb.append("]\n");
    return sb.toString();
  }
}
