// Phrasal -- A Statistical Machine Translation Toolkit
// for Exploring New Model Features.
// Copyright (c) 2007-2010 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    java-nlp-user@lists.stanford.edu
//    http://nlp.stanford.edu/software/phrasal

package edu.stanford.nlp.mt.neural;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.text.SimpleDateFormat;

import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import edu.stanford.nlp.mt.train.AbstractPhraseExtractor;
import edu.stanford.nlp.mt.train.AlignmentSymmetrizer;
import edu.stanford.nlp.mt.train.AlignmentSymmetrizer.SymmetrizationType;
import edu.stanford.nlp.mt.train.AlignmentTemplate;
import edu.stanford.nlp.mt.train.GIZAWordAlignment;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.base.IOTools;

/**
 * Extract data for neural LM training based on PhraseExtract code by Michel Galley and Spence Green.
 * 
 * @author Thang Luong  
 */

public class PhraseExtractNeuralTraining {  
  static public final String CONFIG_OPT = "config";
  static public final String INPUT_DIR_OPT = "inputDir";
  static public final String F_CORPUS_OPT = "fCorpus";
  static public final String E_CORPUS_OPT = "eCorpus";
  static public final String A_CORPUS_OPT = "align";
  static public final String A_FE_CORPUS_OPT = "efAlign";
  static public final String A_EF_CORPUS_OPT = "feAlign";
  static public final String SYMMETRIZE_OPT = "symmetrization";
  static public final String VERBOSE_OPT = "verbose";
  static public final String HELP_OPT = "help";
  
  // Thang Feb14
  static public final String NGRAM_OPT = "ngram";
  static public final String SRC_WINDOW_OPT = "srcWindow";
  static public final String SRC_START_TOKEN_OPT = "srcStartToken";
  static public final String SRC_END_TOKEN_OPT = "srcEndToken";
  static public final String TGT_START_TOKEN_OPT = "tgtStartToken";
  static public final String OUTPUT_FILE = "outputFile";
  
  static public final String LOWERCASE_OPT = "lowercase";
  static public final String MEM_USAGE_FREQ_OPT = "memUsageFreq";
  static public final String THREADS_OPT = "threads";
  static public final String TRIPLE_FILE = "tripleFile";
  
  static final Set<String> REQUIRED_OPTS = Generics.newHashSet();
  static final Set<String> OPTIONAL_OPTS = Generics.newHashSet();
  static final Set<String> ALL_RECOGNIZED_OPTS = Generics.newHashSet();

  static {
    REQUIRED_OPTS.addAll(Arrays.asList(F_CORPUS_OPT, E_CORPUS_OPT, NGRAM_OPT, SRC_WINDOW_OPT, 
        SRC_START_TOKEN_OPT, SRC_END_TOKEN_OPT, TGT_START_TOKEN_OPT, OUTPUT_FILE));
    OPTIONAL_OPTS.addAll(Arrays.asList(A_CORPUS_OPT, A_EF_CORPUS_OPT,
        A_FE_CORPUS_OPT, SYMMETRIZE_OPT, INPUT_DIR_OPT, THREADS_OPT, HELP_OPT, VERBOSE_OPT
        , MEM_USAGE_FREQ_OPT, LOWERCASE_OPT, TRIPLE_FILE));
    ALL_RECOGNIZED_OPTS.addAll(REQUIRED_OPTS);
    ALL_RECOGNIZED_OPTS.addAll(OPTIONAL_OPTS);
  }

  public static final String DEBUG_PROPERTY = "DebugPhraseExtract";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  public static final String DETAILED_DEBUG_PROPERTY = "DetailedDebugPhraseExtract";
  public static final boolean DETAILED_DEBUG = Boolean.parseBoolean(System
      .getProperty(DETAILED_DEBUG_PROPERTY, "false"));

  boolean doneReadingData;
  boolean verbose;

  private Properties prop;
  private int startAtLine = -1, endAtLine = -1, memUsageFreq, nThreads = 0;
  private String fCorpus, eCorpus;
  private String alignCorpus, alignInvCorpus;
  private boolean lowercase;
  
  // Thang Feb14
  int ngram, srcWindow;
  String srcStartToken, srcEndToken, tgtStartToken;
  PrintStream writer;
  String outputFile;
  
  // Triple file format:
  // Single source ||| target ||| alignment triple file
  private final static String tripleDelim = Pattern.quote(AlignmentTemplate.DELIM);
  boolean tripleFile = false;
  
  private SymmetrizationType symmetrizationType = null;

  private int totalPassNumber = 1;
  
  public PhraseExtractNeuralTraining(Properties prop) throws IOException {
    processProperties(prop);
  }

  public void processProperties(Properties prop) throws IOException {

    this.prop = prop;

    // Possibly load properties from config file:
    String configFile = prop.getProperty(CONFIG_OPT);
    if (configFile != null) {
      try {
        IOTools.addConfigFileProperties(prop, configFile);
      } catch (IOException e) {
        usage();
        throw new RuntimeException(String.format(
            "I/O error while reading configuration file: %s\n", configFile));
      }
    }

    // UCB aligner input dir:
    if (prop.containsKey(INPUT_DIR_OPT)) {
      String inputDir = prop.getProperty(INPUT_DIR_OPT);
      String fId = null, eId = null, cFile = inputDir + "/options.map";
      for (String line : ObjectBank.getLineIterator(cFile)) {
        String[] els = line.split("\\t");
        if (els[0].equals("Data.foreignSuffix")) {
          fId = els[1];
        } else if (els[0].equals("Data.englishSuffix")) {
          eId = els[1];
        }
      }
      if (fId == null || eId == null)
        throw new RuntimeException("Didn't find language identifiers in: "
            + cFile);
      prop.setProperty(F_CORPUS_OPT, inputDir + "/training." + fId);
      prop.setProperty(E_CORPUS_OPT, inputDir + "/training." + eId);
      prop.setProperty(A_CORPUS_OPT, inputDir + "/training.align");
    }
    
    // Single source ||| target ||| alignment triple file
    if (prop.containsKey(TRIPLE_FILE)) {
      String tripleFileFn = prop.getProperty(TRIPLE_FILE);
      tripleFile = true;
      prop.setProperty(F_CORPUS_OPT, tripleFileFn);
      prop.setProperty(E_CORPUS_OPT, tripleFileFn);
      prop.setProperty(A_CORPUS_OPT, tripleFileFn);
    }

    // Check required, optional properties:
    if (!prop.keySet().containsAll(REQUIRED_OPTS)) {
      Set<String> missingFields = Generics.newHashSet(REQUIRED_OPTS);
      missingFields.removeAll(prop.keySet());
      usage();
      throw new RuntimeException(String.format(
          "The following required fields are missing: %s\n", missingFields));
    }

    if (!ALL_RECOGNIZED_OPTS.containsAll(prop.keySet())) {
      Set<Object> extraFields = Generics.newHashSet(prop.keySet());
      extraFields.removeAll(ALL_RECOGNIZED_OPTS);
      usage();
      throw new RuntimeException(String.format(
          "The following fields are unrecognized: %s\n", extraFields));
    }

    // Analyze props:
    // Mandatory arguments:
    fCorpus = prop.getProperty(F_CORPUS_OPT);
    eCorpus = prop.getProperty(E_CORPUS_OPT);

    // Thang Feb14
    ngram = Integer.parseInt(prop.getProperty(NGRAM_OPT));
    srcWindow = Integer.parseInt(prop.getProperty(SRC_WINDOW_OPT));
    srcStartToken = prop.getProperty(SRC_START_TOKEN_OPT);
    srcEndToken = prop.getProperty(SRC_END_TOKEN_OPT);
    tgtStartToken = prop.getProperty(TGT_START_TOKEN_OPT);
    outputFile = prop.getProperty(OUTPUT_FILE);
    
    // Alignment arguments:
    symmetrizationType = SymmetrizationType.valueOf(prop.getProperty(
        SYMMETRIZE_OPT, "grow-diag").replace('-', '_'));
    alignCorpus = prop.getProperty(A_CORPUS_OPT);
    if (alignCorpus == null) {
      alignCorpus = prop.getProperty(A_FE_CORPUS_OPT);
      alignInvCorpus = prop.getProperty(A_EF_CORPUS_OPT);
      if (symmetrizationType == SymmetrizationType.none)
        throw new RuntimeException(
            "You need to specify a symmetrization heuristic with GIZA input.");
    }

    // Other optional arguments:
    nThreads = Integer.parseInt(prop.getProperty(THREADS_OPT, "0"));
    memUsageFreq = Integer.parseInt(prop
        .getProperty(MEM_USAGE_FREQ_OPT, "1000"));
    
    lowercase = Boolean.parseBoolean(prop.getProperty(LOWERCASE_OPT, "false"));
    verbose = Boolean.parseBoolean(prop.getProperty(VERBOSE_OPT, "false"));
  }

  
  public void init() {
    // Thang Feb14
    File f = (new File(outputFile)).getParentFile();
    if(f!= null && !f.exists()){
      f.mkdirs();
    }
    writer = IOTools.getWriterFromFile(outputFile);
    
    Extractor.srcStartToken = srcStartToken;
    Extractor.srcEndToken = srcEndToken;
    Extractor.tgtStartToken = tgtStartToken;
  }

  /**
   * Input to the extractor.
   * 
   * @author Spence Green
   *
   */
  private static class ExtractorInput {
    public final int lineNb;
    public final String fLine;
    public final String eLine;
    public final String aLine;
    public ExtractorInput(int lineNb, String fLine, String eLine, String aLine) {
      this.lineNb = lineNb;
      this.fLine = fLine;
      this.eLine = eLine;
      this.aLine = aLine;
    }
  }
  
  /**
   * Output from the extractor
   * 
   * @author Thang Luong
   *
   */
  private static class ExtractorOutput {
    public List<String> outputStrs;
    public ExtractorOutput(List<String> outputStrs) {
      this.outputStrs = outputStrs;
    }
  }
  
  /**
   * Extract and featurize a sentence pair.
   * 
   * @author Spence Green
   *
   */
  private static class Extractor implements ThreadsafeProcessor<ExtractorInput,ExtractorOutput> {
    public static String srcStartToken;
    public static String srcEndToken;
    public static String tgtStartToken;
    
    private final SymmetricalWordAlignment sent;
    private final Properties properties;
    private int ngram;
    private int srcWindow;
    
    public Extractor(Properties properties, int ngram, int srcWindow) {
      sent = new SymmetricalWordAlignment(properties);
      this.properties = properties;
      this.ngram = ngram;
      this.srcWindow = srcWindow;
    }

    @Override
    public ExtractorOutput process(ExtractorInput input) {
      List<String> outputStrs = new ArrayList<String>();
      try {
        sent.init(input.lineNb, input.fLine, input.eLine, input.aLine, false, false);        
        int fsize = sent.f().size();
        int esize = sent.e().size();
        
        for (int i = 0; i < esize; ++i) { // English n-gram e_(i-ngram+1) : e_i
          // find alignments of e_i
          SortedSet<Integer> fIndices = new TreeSet<Integer>();
          int curE = i+1;
          do {
            curE--;
            fIndices = sent.e2f(curE);
          } while(fIndices.isEmpty() && curE>0 && (curE>(i-ngram))); // backoff to the alignments of a previous word if no alignment
          
          if(!fIndices.isEmpty()){
            // find avg fIndex
            int avgF = 0;
            for (Integer integer : fIndices) {
              avgF += integer;
            }
            avgF = avgF/fIndices.size();
            
            StringBuilder sb = new StringBuilder();
            // build target ngram e_(i-ngram+1) : e_i
            for (int j = (i-ngram+1); j <= i; j++) { 
              if(j<0) sb.append(tgtStartToken + " ");
              else {
                sb.append(sent.e().get(j) + " ");
              }
            }
            
            // build source ngram f_(avgF-srcWindow) : f_(avgF+srcWindow)
            for (int j = (avgF-srcWindow); j <= (avgF+srcWindow); j++) { 
              if(j<0) sb.append(srcStartToken + " ");
              else if (j>=fsize) sb.append(srcEndToken + " ");
              else {
                sb.append(sent.f().get(j) + " ");
              }
            }
            
            outputStrs.add(sb.toString());
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      
      return new ExtractorOutput(outputStrs);
    }

    @Override
    public ThreadsafeProcessor<ExtractorInput, ExtractorOutput> newInstance() {
      return new Extractor(this.properties, this.ngram, this.srcWindow);
    }
  }

  // Make as many passes over training data as needed to extract features.
  void extractFromAlignedData() {
    long startTimeMillis = System.currentTimeMillis();

    try {
      for (int passNumber = 0; passNumber < totalPassNumber; ++passNumber) {
        doneReadingData = false;

        MulticoreWrapper<ExtractorInput,ExtractorOutput> wrapper = 
            new MulticoreWrapper<ExtractorInput,ExtractorOutput>(nThreads, 
                new Extractor(prop, ngram, srcWindow), false);

        boolean useGIZA = alignInvCorpus != null;

        // Read data and process data:
        if (passNumber > 0)
          System.err
              .println("Some feature extractor needs an additional pass over the data.");
        System.err.printf(
            "Pass %d on training data (max phrase len: %d,%d)...\nLine",
            passNumber + 1, AbstractPhraseExtractor.maxPhraseLenF,
            AbstractPhraseExtractor.maxPhraseLenE);
        LineNumberReader aInvReader = null, fReader = IOTools
            .getReaderFromFile(fCorpus), eReader = IOTools
            .getReaderFromFile(eCorpus), aReader = IOTools
            .getReaderFromFile(alignCorpus);
        if (useGIZA)
          aInvReader = IOTools.getReaderFromFile(alignInvCorpus);

        int lineNb = 0;
        for (String fLine;; ++lineNb) {
          fLine = fReader.readLine();

          boolean done = (fLine == null || lineNb == endAtLine);

          if (tripleFile && !done) {
            fLine = fLine.split(" \\|\\|\\| ")[0];
          }

          if (lineNb % memUsageFreq == 0 || done) {
            // long totalMemory = Runtime.getRuntime().totalMemory()/(1<<20);
            long freeMemory = Runtime.getRuntime().freeMemory() / (1 << 20);
            // double totalStepSecs = (System.currentTimeMillis() -
            // startStepTimeMillis)/1000.0;
            // startStepTimeMillis = System.currentTimeMillis();
            System.err.printf(" %d (mem=%dm)...", lineNb, freeMemory);
            // if (verbose)
            // System.err.printf("line %d (secs = %.3f, totalmem = %dm, freemem = %dm, %s)...\n",
            // lineNb, totalStepSecs, totalMemory, freeMemory,
            // alTemps.getSizeInfo());
          }

          if (done) {
            if (startAtLine >= 0 || endAtLine >= 0)
              System.err.printf("\nRange done: [%d-%d], current line is %d.\n",
                  startAtLine, endAtLine - 1, lineNb);
            break;
          }

          String eLine = eReader.readLine();
          if (tripleFile) {
            eLine = eLine.split(tripleDelim)[1].trim();
          }
          if (eLine == null)
            throw new IOException("Target-language corpus is too short!");

          boolean skipLine = (fLine.isEmpty() || eLine.isEmpty());

          // Read alignment:
          String aLine = null;
          if (useGIZA) {
            String ef1 = aReader.readLine();
            String ef2 = aReader.readLine();
            String ef3 = aReader.readLine();
            String fe1 = aInvReader.readLine();
            String fe2 = aInvReader.readLine();
            String fe3 = aInvReader.readLine();
            if (!skipLine) {
              GIZAWordAlignment gizaAlign = new GIZAWordAlignment(fe1, fe2,
                  fe3, ef1, ef2, ef3);
              SymmetricalWordAlignment symAlign = AlignmentSymmetrizer
                  .symmetrize(gizaAlign, symmetrizationType);
              symAlign.reverse();
              aLine = symAlign.toString().trim();
            }
          } else {
            aLine = aReader.readLine();
            if (tripleFile) {
              String[] toks = aLine.split(tripleDelim);
              if (toks.length >= 3) {
                aLine = aLine.split(tripleDelim)[2].trim();
              } else {
                aLine = "";
              }
            }
            if (aLine == null)
              throw new IOException("Alignment file is too short!");
          }
          if (skipLine || aLine.isEmpty())
            continue;

          if (lineNb < startAtLine)
            continue;
          if (DETAILED_DEBUG) {
            System.err.printf("e(%d): %s\n", lineNb, eLine);
            System.err.printf("f(%d): %s\n", lineNb, fLine);
            System.err.printf("a(%d): %s\n", lineNb, aLine);
          }
          if (lowercase) {
            fLine = fLine.toLowerCase();
            eLine = eLine.toLowerCase();
          }
          
          wrapper.put(new ExtractorInput(lineNb,fLine, eLine, aLine));
          while(wrapper.peek()) {
            // Thang Feb14
            ExtractorOutput output = wrapper.poll();
            for (String outputStr : output.outputStrs) {
              writer.append(outputStr + "\n");
            }
          }
        }

        if (eReader.readLine() != null && startAtLine < 0 && endAtLine < 0)
          throw new IOException("Target-language corpus contains extra lines!");
        if (aReader.readLine() != null && startAtLine < 0 && endAtLine < 0)
          throw new IOException("Alignment file contains extra lines!");

        fReader.close();
        eReader.close();
        aReader.close();

        doneReadingData = true;
        wrapper.join();
        while(wrapper.peek()) {
          // Thang Feb14
          ExtractorOutput output = wrapper.poll();
          for (String outputStr : output.outputStrs) {
            writer.append(outputStr + "\n");
          }
        }

        double totalTimeSecs = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
        System.err.printf("\nDone with pass %d. Seconds: %.3f.\n",
            passNumber + 1, totalTimeSecs);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } 
  }

  public void extractAll() {
    init();
    extractFromAlignedData();
    writer.close();
  }

  static void usage() {
    System.err
        .print("Usage: java edu.stanford.nlp.mt.train.PhraseExtract [ARGS]\n"
            + "Sets of mandatory arguments (user must select either set 1, 2, or 3):\n"
            + "Set 1:\n"
            + " -fCorpus <file> : source-language corpus\n"
            + " -eCorpus <file> : target-language corpus\n"
            + " -align <file> : alignment file (Moses format)\n"
            + "Set 2:\n"
            + " -fCorpus <file> : source-language corpus\n"
            + " -eCorpus <file> : target-language corpus\n"
            + " -feAlign <file> : f-e alignment file (GIZA format)\n"
            + " -efAlign <file> : e-f alignment file (GIZA format)\n"
            + "Set 3:\n"
            + " -inputDir <directory> : alignment directory created by Berkeley aligner v2.1\n"
            + "Set 4:\n"
            + " -tripleFile <file> : source ||| target ||| alignment triple format\n"
            + "Optional arguments:\n"
            + " -verbose : enable verbose mode\n"
            
            // Thang Feb14
            + " -ngram <n> : n-gram size\n"
            + " -srcWindow <n> : extract (2*srcWindow+1) source words that correspond to the current n-gram\n"
            + " -srcStartToken token : e.g. <src_s>\n"
            + " -srcEndToken token : e.g. </src_s>\n"
            + " -tgtStartToken token : e.g. <s>\n"
            + " -outputFile path : Output file to <path>\n"
            );
  }

  /**
   * Extract phrases from an aligned bitext.
   * 
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 1 && args[0].equals("-help")) {
      usage();
      return;
    }

    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MMM-dd hh:mm aaa");
    System.err.printf("Extraction started at %s on %s.%n",
        formatter.format(new Date()), InetAddress.getLocalHost().getHostName());

    Properties prop = StringUtils.argsToProperties(args);
    System.err.println("Properties: " + prop.toString());

    try {
      PhraseExtractNeuralTraining e = new PhraseExtractNeuralTraining(prop);
      e.extractAll();
    } catch (Exception e) {
      e.printStackTrace();
      usage();
    }

    System.err.println("Extraction ended at " + formatter.format(new Date()));
  }
}
