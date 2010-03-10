package edu.stanford.nlp.mt.train.zh;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.train.AlignmentGrid;
import edu.stanford.nlp.mt.train.AlignmentTemplate;
import edu.stanford.nlp.mt.train.AlignmentTemplateInstance;
import edu.stanford.nlp.mt.train.AlignmentTemplates;
import edu.stanford.nlp.mt.train.BshInterpreter;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.train.SourceFilter;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;

import java.util.*;
import java.io.*;
import java.lang.reflect.Constructor;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;

/**
 * modifined from CombinedFeatureExtractor.java
 *
 * @author Pi-Chuan Chang
 * @author Michel Galley
 */
public class ChineseSyntaxCombinedFeatureExtractor {

  public static boolean VERBOSE = false;

  // TODO: add support for config files
  
  static public final String F_CORPUS_OPT = "fCorpus";
  static public final String E_CORPUS_OPT = "eCorpus";
  static public final String A_CORPUS_OPT = "align";
  static public final String EXTRACTORS_OPT = "extractors";
  
  static public final String FILTER_CORPUS_OPT = "fFilterCorpus";
  static public final String FILTER_LIST_OPT = "fFilterList";
  static public final String REF_PTABLE_OPT = "refFile";
  static public final String SPLIT_SIZE_OPT = "split";
  static public final String OUTPUT_FILE_OPT = "outputFile";
  static public final String NO_ALIGN_OPT = "noAlign";
  static public final String MAX_PHRASE_LEN_OPT = "maxLen";
  static public final String MAX_PHRASE_LEN_E_OPT = "maxLenE";
  static public final String MAX_PHRASE_LEN_F_OPT = "maxLenF";
  static public final String NUM_LINES_OPT = "numLines";
  static public final String PRINT_FEATURE_NAMES_OPT = "printFeatureNames";
  static public final String MIN_COUNT_OPT = "minCount";
  static public final String START_AT_LINE_OPT = "startAtLine";
  static public final String END_AT_LINE_OPT = "endAtLine";
  static public final String MAX_FERTILITY_OPT = "maxFertility";

  // phrase translation probs:
  static public final String EXACT_PHI_OPT = "exactPhiCounts";
  static public final String IBM_LEX_MODEL_OPT = "ibmLexModel";
  static public final String PTABLE_PHI_FILTER_OPT = "phiFilter"; // p_phi(e|f) filtering
  static public final String PTABLE_LEX_FILTER_OPT = "lexFilter"; // p_lex(e|f) filtering
  // re-ordering probs:
  static public final String PHARAOH_LEX_MODEL_OPT = "pharaohLexicalizedModel";

  static final Set<String> REQUIRED_OPTS = new HashSet<String>();
  static final Set<String> OPTIONAL_OPTS = new HashSet<String>();
  static final Set<String> IGNORED_OPTS = new HashSet<String>();
  static final Set<String> ALL_RECOGNIZED_OPTS = new HashSet<String>();

  static {
    REQUIRED_OPTS.addAll(Arrays.asList(new String[] { 
                                         F_CORPUS_OPT, E_CORPUS_OPT, A_CORPUS_OPT, EXTRACTORS_OPT 
                                       }));
    OPTIONAL_OPTS.addAll(Arrays.asList(new String[] { 
                                         FILTER_CORPUS_OPT, FILTER_LIST_OPT, REF_PTABLE_OPT, 
                                         SPLIT_SIZE_OPT, OUTPUT_FILE_OPT, NO_ALIGN_OPT, 
                                         MAX_PHRASE_LEN_OPT, MAX_PHRASE_LEN_E_OPT, MAX_PHRASE_LEN_F_OPT, 
                                         NUM_LINES_OPT, PRINT_FEATURE_NAMES_OPT, MIN_COUNT_OPT,
                                         START_AT_LINE_OPT, END_AT_LINE_OPT, MAX_FERTILITY_OPT,
                                         EXACT_PHI_OPT, IBM_LEX_MODEL_OPT,
                                         PTABLE_PHI_FILTER_OPT, PTABLE_LEX_FILTER_OPT,
                                         PHARAOH_LEX_MODEL_OPT 
                                       }));
    ALL_RECOGNIZED_OPTS.addAll(REQUIRED_OPTS);
    ALL_RECOGNIZED_OPTS.addAll(OPTIONAL_OPTS);
    ALL_RECOGNIZED_OPTS.addAll(IGNORED_OPTS);
  }
  
  static BshInterpreter interpreter = new BshInterpreter();

  public static final String DEBUG_PROPERTY = "DebugCombinedFeatureExtractor";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  public static final String MAX_SENT_LEN_PROPERTY = "maxLen";
  public static final int MAX_SENT_LEN = Integer.parseInt(System.getProperty(MAX_SENT_LEN_PROPERTY, "256"));

  public static final String NO_EMPTY_ALIGNMENT_PROPERTY = "NoEmptyAlignmentPhrases";
  public static final boolean NO_EMPTY_ALIGNMENT = Boolean.parseBoolean(System.getProperty(NO_EMPTY_ALIGNMENT_PROPERTY, "true"));

  public static final String PRINT_GRID_PROPERTY = "PrintGridWithMaxLen";
  public static final int PRINT_GRID_MAX_LEN = Integer.parseInt(System.getProperty(PRINT_GRID_PROPERTY, "-1"));

  public static final String SHOW_PHRASE_RESTRICTION_PROPERTY = "ShowPhraseRestriction";
  public static final boolean SHOW_PHRASE_RESTRICTION = 
    Boolean.parseBoolean(System.getProperty(SHOW_PHRASE_RESTRICTION_PROPERTY, "false"));

  private List<AbstractChineseSyntaxFeatureExtractor<String>> extractors;
  // each extract is allowed to have one file that contains extra information (one line per sentence)
  private List<String> infoFileForExtractors;
  private List<String> infoLinesForExtractors;

  private AlignmentTemplates alTemps;
  private AlignmentTemplateInstance alTemp;
  private AlignmentGrid alGrid = null;
  private AlignmentGrid fullAlGrid = null;

  private Index<String> featureIndex = new HashIndex<String>();

  private static int startAtLine = -1, endAtLine = -1;
  private static int maxPhraseLenF = 7;
  private static int maxPhraseLenE = 7;
  private static int minCount = 1;
  private static boolean filterFromDev = false;
  private static boolean printFeatureNames = true;

  // Some features need access to an alignment grid:
  private boolean needAlGrid = false;
  // Number of passes over training data needed:
  private int passNumber = 0;
  private int totalPassNumber = 1;

  private static SourceFilter sourceFilter = new SourceFilter();

  @SuppressWarnings("unchecked")
  public ChineseSyntaxCombinedFeatureExtractor(Properties prop) {
  	
    String exsString = prop.getProperty(EXTRACTORS_OPT);
    if(exsString.equals("moses"))
      exsString = "mt.PharaohFeatureExtractor:mt.LexicalReorderingFeatureExtractor";
    alTemps = new AlignmentTemplates(prop, sourceFilter);
    extractors = new ArrayList<AbstractChineseSyntaxFeatureExtractor<String>>();
    infoFileForExtractors = new ArrayList<String>();

    for(String exStr : exsString.split(":")) {
      try {
        AbstractChineseSyntaxFeatureExtractor fe = null;
        String[] extractorAndInfofile = exStr.split("=");
        String infoFilename = null;
        // if the extractor string contains "=", then assume 
        // that A in A=B is the extractor class name, and 
        // B is an "info" file with same number of lines as sentence pairs.
        if (extractorAndInfofile.length==2) {
          infoFilename = extractorAndInfofile[1];
          exStr = extractorAndInfofile[0];
        } else if (extractorAndInfofile.length!=1) {
          throw new RuntimeException("extractor argument format error");
        }
        // if exStr contains parentheses, assume it is a call to a constructor 
        // (without the "new"):
        int pos = exStr.indexOf('(');
        if(pos >= 0) {
          StringBuffer constructor = new StringBuffer("new ").append(exStr);
          System.err.println("Running constructor: "+constructor);
          fe = (AbstractChineseSyntaxFeatureExtractor) interpreter.eval(constructor.toString());
        } else {
          Class cls = Class.forName(exStr);
          Constructor ct = cls.getConstructor(new Class[] {});
          fe = (AbstractChineseSyntaxFeatureExtractor) ct.newInstance(new Object[] {});
        }
        fe.init(prop, featureIndex, alTemps);
        extractors.add(fe);
        infoFileForExtractors.add(infoFilename);
        System.err.println("New class instance: "+fe.getClass());
      } catch (Exception e) {
        e.printStackTrace();
        usage();
      }
    }
    
    
    this.alTemp = new AlignmentTemplateInstance();

    setTotalPassNumber();
    setAlGrid();
  }

  /**
   * Restrict feature extraction to source-language phrases that appear in 
   * a given test/dev corpus.
   *
   */
  @SuppressWarnings("unchecked")
  public static Sequence<IString>[] getPhrasesFromDevCorpus(String fFilterCorpus) {
    sourceFilter.addPhrasesFromCorpus(fFilterCorpus, maxPhraseLenF, Integer.MAX_VALUE, false);
    /*
    AlignmentTemplates tmpSet = new AlignmentTemplates();
    System.err.println("Filtering against corpus: "+fFilterCorpus);
    filterFromDev = true;  
    try {
      LineNumberReader fReader = IOTools.getReaderFromFile(fFilterCorpus);
      for (String fLine; (fLine = fReader.readLine()) != null; ) {
        Sequence<IString> f = new SimpleSequence<IString>(true, IStrings.toIStringArray(fLine.split("\\s+")));
        for(int i=0; i<f.size(); ++i) {
          for(int j=i; j<f.size() && j-i<maxPhraseLenF; ++j) {
            Sequence<IString> fPhrase = f.subsequence(i,j+1);
            if(SHOW_PHRASE_RESTRICTION)
              System.err.printf("restrict to phrase (i=%d,j=%d,M=%d): %s\n",i,j,maxPhraseLenF,fPhrase.toString());
            tmpSet.addForeignPhraseToIndex(fPhrase);
          }
        }
      }
      fReader.close();
    } catch(IOException e) {
      e.printStackTrace();
    }
    Sequence<IString>[] phrases = new Sequence[tmpSet.sizeF()];
    for(int i=0; i<phrases.length; ++i) {
      int[] fArray = tmpSet.getF(i);
      phrases[i] = new SimpleSequence<IString>(true, IStrings.toIStringArray(fArray));
    }
    Collections.shuffle(Arrays.asList(phrases));
    return phrases;
    */
    return null;
  }

  /**
   * Restrict feature extraction to a pre-defined list of source-language phrases.
   */
  @SuppressWarnings("unchecked")
  public static Sequence<IString>[] getPhrasesFromList(String fileName) {
    ArrayList<Sequence<IString>> list = new ArrayList<Sequence<IString>>();
    System.err.println("Filtering against list: "+fileName);
    filterFromDev = true;  
    try {
      LineNumberReader fReader = IOTools.getReaderFromFile(fileName);
      for (String fLine; (fLine = fReader.readLine()) != null; ) {
        Sequence<IString> f = new SimpleSequence<IString>(true, IStrings.toIStringArray(fLine.split("\\s+")));
        if(SHOW_PHRASE_RESTRICTION)
          System.err.printf("restrict to phrase: %s\n",f.toString());
        list.add(f);
      }
      fReader.close();
    } catch(IOException e) {
      e.printStackTrace();
    }
    return (Sequence<IString>[]) list.toArray(new Sequence[list.size()]);
  }

  public void restrictExtractionTo(Sequence<IString>[] list) {
    restrictExtractionTo(list,0,Integer.MAX_VALUE);
  }

  /**
   * Restrict feature extraction to a pre-defined list of source-language phrases.
   */
  public void restrictExtractionTo(Sequence<IString>[] list, int start, int end) {
    sourceFilter.setRange(start, end);
    if(end < Integer.MAX_VALUE)
      System.err.printf("Filtering against phrases: %d-%d\n", start, end-1);
    /*
    filterFromDev = true;
    for(int i=start; i<end && i<list.length; ++i) {
      Sequence<IString> f = list[i];
      if(SHOW_PHRASE_RESTRICTION)
        System.err.printf("restrict to: %s\n",f.toString());
      alTemps.addForeignPhraseToIndex(f);
    }
    */
  }

  /**
   * Make as many passes over training data as needed to extract features. 
   *
   */
  public void extractFromMergedAlignment(String fCorpus, String eCorpus, String aCorpus) {
    if(!filterFromDev)
      System.err.println("WARNING: extracting phrase table not targeted to a specific dev/test corpus!");
    System.gc();
    long startTimeMillis = System.currentTimeMillis();
    long startStepTimeMillis = startTimeMillis;

    SymmetricalWordAlignment sent = new SymmetricalWordAlignment();

    try {
      for(passNumber=0; passNumber<totalPassNumber; ++passNumber) {
        // Set current pass:
        for(AbstractChineseSyntaxFeatureExtractor<String> e : extractors)
          e.setCurrentPass(passNumber);
        // Read data and process data:
        System.err.printf("Pass %d on training data (max phrase len: %d,%d)...\n",passNumber+1,maxPhraseLenF, maxPhraseLenE);
        LineNumberReader
          fReader = IOTools.getReaderFromFile(fCorpus),
          eReader = IOTools.getReaderFromFile(eCorpus),
          aReader = IOTools.getReaderFromFile(aCorpus);

        // make Readers from the info files for each extractors
        List<LineNumberReader>
          infoReaders = new ArrayList<LineNumberReader>();
        for(String infoFile : infoFileForExtractors) {
          LineNumberReader r = null;
          if (infoFile != null) {
            r = IOTools.getReaderFromFile(infoFile);
          }
          infoReaders.add(r);
        }
        
        int line=0;
        for (String fLine;; ++line) {
          fLine = fReader.readLine();
          boolean done = (fLine == null || line == endAtLine);
          if(line % 1000 == 0 || done) {
            long totalMemory = Runtime.getRuntime().totalMemory()/(1<<20);
            long freeMemory = Runtime.getRuntime().freeMemory()/(1<<20);
            double totalStepSecs = (System.currentTimeMillis() - startStepTimeMillis)/1000.0;
            startStepTimeMillis = System.currentTimeMillis();
            System.err.printf("line %d (secs = %.3f, totalmem = %dm, freemem = %dm, %s)...\n",
                              line, totalStepSecs, totalMemory, freeMemory, alTemps.getSizeInfo());
          }
          if(done) {
            if(startAtLine >= 0 || endAtLine >= 0)
              System.err.printf("Range done: [%d-%d], current line is %d.\n",
                                startAtLine, endAtLine-1, line);
            break;
          }
          String eLine = eReader.readLine();
          if(eLine == null)
            throw new IOException("Target-language corpus is too short!");
          String aLine = aReader.readLine();
          if(aLine == null)
            throw new IOException("Alignment file is too short!");
          if(aLine.equals(""))
            continue;

          infoLinesForExtractors = new ArrayList<String>();
          for (LineNumberReader infoReader : infoReaders) {
            String infoLine = null;
            if (infoReader != null) {
              infoLine = infoReader.readLine();
              if(infoLine == null)
                throw new IOException("Info file for one extractor is too short!");
            }
            infoLinesForExtractors.add(infoLine);
          }
          
          if(line < startAtLine)
            continue;
          if (VERBOSE) {
            System.err.print("processing eLine: ");
            String[] eLines = eLine.split(" +");
            for(int i = 0; i < eLines.length; i++) {
              System.err.print(eLines[i]+"("+i+") ");
            }
            System.err.println();
            
            System.err.println("processing fLine: ");
            String[] fLines = fLine.split(" +");
            for(int i = 0; i < fLines.length; i++) {
              System.err.print(fLines[i]+"("+i+") ");
            }
            System.err.println();
            
            System.err.println("processing aLine: "+aLine);
          }
          sent.init(fLine,eLine,aLine);
          extractPhraseFeatures(sent);
          extractSentenceFeatures(sent);
        }
        if(eReader.readLine() != null && startAtLine < 0 && endAtLine < 0)
          throw new IOException("Target-language corpus contains extra lines!");
        if(aReader.readLine() != null && startAtLine < 0 && endAtLine < 0)
          throw new IOException("Alignment file contains extra lines!");
        fReader.close();
        eReader.close();
        aReader.close();
        double totalTimeSecs = (System.currentTimeMillis() - startTimeMillis)/1000.0;
        System.err.printf("Done with pass %d. Seconds: %.3f.\n", passNumber+1, totalTimeSecs);
      }

      // just let each extractor output some stuff to the STDERR
      for(AbstractChineseSyntaxFeatureExtractor<String> e : extractors)
        e.report(alTemps);

    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  private void extractPhraseFeatures(SymmetricalWordAlignment sent) {

    int fsize = sent.f().size();
    int esize = sent.e().size();
    if(fsize > MAX_SENT_LEN || esize > MAX_SENT_LEN) {
      System.err.println("Warning: skipping too long sentence. Length: f="+fsize+" e="+esize);
      return;
    }

    if(needAlGrid) {
      alGrid.init(sent);
      //alGrid.init(esize,fsize);
      fullAlGrid.init(sent);
      //fullAlGrid.init(esize,fsize);
    }

    // For each English phrase:
    for(int e1=0; e1<esize; ++e1) {
      for(int e2=e1; e2<esize; ++e2) {
        // Find range of f aligning to e1...e2:
        int f1=Integer.MAX_VALUE;
        int f2=Integer.MIN_VALUE;
        for(int ei=e1; ei<=e2; ++ei) {
          for(int fi : sent.e2f(ei)) {
            if(fi<f1) f1 = fi;
            if(fi>f2) f2 = fi;
          }
        }

        // No word alignment within that range, or phrase too long?
        if(NO_EMPTY_ALIGNMENT && f1>f2)
          continue;
        // Check if range [e1-e2] [f1-f2] is admissible:
        boolean admissible = true;
        for(int fi=f1; fi<=f2 && admissible; ++fi) {
          for(int ei : sent.f2e(fi)) {
            if(ei<e1 || ei>e2) {
              admissible = false;
              break;
            }
          }
        }
        if(!admissible)
          continue;
        // See how much we can expand the phrase to cover unaligned words:
        int F1 = f1, F2 = f2;
        //while(F1-1>=0    && f2-F1<maxPhraseLenF-1 && sent.f2e(F1-1).size()==0) { --F1; }
        //while(F2+1<fsize && F2-f1<maxPhraseLenF-1 && sent.f2e(F2+1).size()==0) { ++F2; }
        while(F1-1>=0    && sent.f2e(F1-1).size()==0) { --F1; }
        while(F2+1<fsize && sent.f2e(F2+1).size()==0) { ++F2; }
        
        for(int i=F1; i<=f1; ++i)
          for(int j=f2; j<=F2; ++j) {
            if(j-i < maxPhraseLenF && e2-e1 < maxPhraseLenE) {
              if (VERBOSE) System.err.printf("Add to Al Grid: f(%d-%d) e(%d-%d)\n", i, j, e1, e2);
              addAlTemp(sent,i,j,e1,e2);
            }
            alTemp = new AlignmentTemplateInstance(sent,i,j,e1,e2,1.0f);
            if (VERBOSE) System.err.printf("Add to Full Grid: f(%d-%d) e(%d-%d)\n", i, j, e1, e2);
            fullAlGrid.addAlTemp(alTemp,true);
          }
      }
    }
    
    if(needAlGrid) {
      // Features are extracted only once all phrases for a given
      // sentece pair are in memory
      for(int i = 0; i < extractors.size(); i++) {
        AbstractChineseSyntaxFeatureExtractor<String> e = extractors.get(i);
        String infoLine = infoLinesForExtractors.get(i);
        
        for(AlignmentTemplateInstance alTemp : alGrid.getAlTemps()) {
          e.extract(alTemp, alGrid, fullAlGrid, infoLine);
          if(fsize < PRINT_GRID_MAX_LEN && esize < PRINT_GRID_MAX_LEN)
            alGrid.printAlTempInGrid(null,alTemp,System.out);
        }
      }
    }
  }

  private void extractSentenceFeatures(SymmetricalWordAlignment sent) {
    for(int i = 0; i < extractors.size(); i++) {
      AbstractChineseSyntaxFeatureExtractor<String> e = extractors.get(i);
      String infoLine = infoLinesForExtractors.get(i);
      e.extract(sent,infoLine,alGrid);
    }
  }

  private void addAlTemp(SymmetricalWordAlignment sent, int f1, int f2, int e1, int e2) {
    assert(checkAlignmentConsistency(sent,f1,f2,e1,e2));
    // Create alTemp:
    AlignmentTemplateInstance alTemp = null;

    if(needAlGrid) {
      alTemp = new AlignmentTemplateInstance(sent,f1,f2,e1,e2,1.0f);
      alGrid.addAlTemp(alTemp,true);
    } else {
      alTemp = this.alTemp;
      alTemp.init(sent,f1,f2,e1,e2,1.0f);
    }

    // Add it to index:
    if(filterFromDev)
      alTemps.addToIndexIfInDev(alTemp);
    else
      alTemps.addToIndex(alTemp);
    // Increase count for alTemp's alignment:
    if(passNumber==0)
      alTemps.incrementAlignmentCount(alTemp);
    
    // Run each feature extractor for each altemp:
    if(!needAlGrid)
      for(AbstractChineseSyntaxFeatureExtractor<String> e : extractors) {
        e.extract(alTemp, null);
      }
  }

  private boolean checkAlignmentConsistency(SymmetricalWordAlignment sent, int f1, int f2, int e1, int e2) {
    boolean aligned = false;
    if(f2-f1 > maxPhraseLenF) return false;
    if(e2-e1 > maxPhraseLenE) return false;
    for(int fi=f1; fi<=f2; ++fi)
      for(int ei : sent.f2e(fi)) {
        if(!(e1 <= ei && ei <= e2))
          return false;
        aligned = true;
      }
    if(!aligned) return false;
    for(int ei=e1; ei<=e2; ++ei)
      for(int fi : sent.e2f(ei))
        if(!(f1 <= fi && fi <= f2))
          return false;
    return true;
  }

  /**
   * Write combined features to a stream.
   */
  public boolean write(Object output, boolean noAlign) {
    boolean needToClose = false;
    PrintStream oStream = System.out;
    if(output != null) {
      if(output instanceof String) {
        String fileName = (String) output;
        System.err.println("saving features to: "+fileName);
        oStream = IOTools.getWriterFromFile(fileName);
        needToClose = true;
      } else if(oStream instanceof PrintStream)
        oStream = (PrintStream) output;
    }

    long startTimeMillis = System.currentTimeMillis();
    long startStepTimeMillis = startTimeMillis;

    for(int idx=0; idx<alTemps.size(); ++idx) {
      StringBuilder str = new StringBuilder();
      if(idx % 10000 == 0 || idx+1==alTemps.size()) {
        long totalMemory = Runtime.getRuntime().totalMemory()/(1<<20);
        long freeMemory = Runtime.getRuntime().freeMemory()/(1<<20);
        double totalStepSecs = (System.currentTimeMillis() - startStepTimeMillis)/1000.0;
        startStepTimeMillis = System.currentTimeMillis();
        System.err.printf("writing phrase %d (secs = %.3f, totalmem = %dm, freemem = %dm)...\n",
                          idx, totalStepSecs, totalMemory, freeMemory);
      }
      alTemps.reconstructAlignmentTemplate(alTemp, idx);
      str.append(alTemp.toString(noAlign));
      str.append(AlignmentTemplate.DELIM);
      for(AbstractChineseSyntaxFeatureExtractor<String> e : extractors) {
        Object scores = e.score(alTemp);
        if(scores == null)
          continue;
        if(scores instanceof double[]) { // as dense vector
          double[] scoreArray = (double[]) scores;
          for(int i=0; i<scoreArray.length; ++i) {
            str.append(scoreArray[i]).append("\t");
          }
        } else if(scores instanceof Int2IntLinkedOpenHashMap) { // as sparse vector
          Int2IntLinkedOpenHashMap counter = (Int2IntLinkedOpenHashMap) scores;
          for(int fIdx : counter.keySet()) {
            int cnt = counter.get(fIdx);
            if(cnt >= minCount) {
              str.append(printFeatureNames ? featureIndex.get(fIdx) : fIdx);
              str.append("=").append(cnt).append(" ");
            }
          }
        } else {
          throw new UnsupportedOperationException
            ("AbstractChineseSyntaxFeatureExtractor should return double[] or Counter, not "+scores.getClass());
        }
      }
      oStream.println(str.toString());
    }
    if(needToClose)
      oStream.close();

    double totalTimeSecs = (System.currentTimeMillis() - startTimeMillis)/1000.0;
    System.err.printf("Done with writing phrase table. Seconds: %.3f.\n", totalTimeSecs);
    return true;
  }

  public int getMaxPhraseLenE() { return maxPhraseLenE; }
  public int getMaxPhraseLenF() { return maxPhraseLenF; }
  public void setMaxPhraseLenE(int newMaxPhraseLenE) { maxPhraseLenE = newMaxPhraseLenE; }
  public void setMaxPhraseLenF(int newMaxPhraseLenF) { maxPhraseLenF = newMaxPhraseLenF; }

  private void setTotalPassNumber() {
    totalPassNumber = 0;
    for(AbstractChineseSyntaxFeatureExtractor<String> ex : extractors) {
      int p = ex.getRequiredPassNumber();
      if(p > totalPassNumber)
        totalPassNumber = p;
    }
  }

  private void setAlGrid() {
    needAlGrid = false;
    if(PRINT_GRID_MAX_LEN > 0)
      needAlGrid = true;
    else {
      for(AbstractChineseSyntaxFeatureExtractor<String> ex : extractors)
        if(ex.needAlGrid()) {
          needAlGrid = true;
          break;
        }
    }
    if(needAlGrid) {
      alGrid = new AlignmentGrid(0,0);
      fullAlGrid = new AlignmentGrid(0,0);
    }
    System.err.println("Using AlignmentGrid: "+needAlGrid);
  }

  @SuppressWarnings("unchecked")
  public static boolean multiPassFeatureExtract(Properties prop) {
    // Check mandatory arguments:
    String fCorpus = prop.getProperty(F_CORPUS_OPT);
    String eCorpus = prop.getProperty(E_CORPUS_OPT);
    String align = prop.getProperty(A_CORPUS_OPT);

    // Phrase filtering arguments:
    String fFilterCorpus = prop.getProperty(FILTER_CORPUS_OPT);
    String fFilterList = prop.getProperty(FILTER_LIST_OPT);
    int numSplits = Integer.parseInt(prop.getProperty(SPLIT_SIZE_OPT,"0"));
    Sequence<IString>[] fPhrases = null;
    if(fFilterList != null)
      fPhrases = getPhrasesFromList(fFilterList);
    else if(fFilterCorpus != null)
      fPhrases = getPhrasesFromDevCorpus(fFilterCorpus);
    // Other optional arguments:
    startAtLine = Integer.parseInt(prop.getProperty(START_AT_LINE_OPT,"-1"));
    endAtLine = Integer.parseInt(prop.getProperty(END_AT_LINE_OPT,"-2"))+1;
    int numLines = Integer.parseInt(prop.getProperty(NUM_LINES_OPT,"-1"));
    if(numLines > 0) {
      startAtLine = 0;
      endAtLine = numLines;
    }
    boolean noAlign = Boolean.parseBoolean(prop.getProperty(NO_ALIGN_OPT,"false"));
    String outputFile = prop.getProperty(OUTPUT_FILE_OPT);
    // Split filter list into N chunks:
    if(numSplits > 1) {
      if(!filterFromDev) {
        System.err.println("-"+SPLIT_SIZE_OPT+" argument only possible with -"+FILTER_CORPUS_OPT+", -"+FILTER_LIST_OPT+".");
        return false;
      }
      PrintStream oStream = IOTools.getWriterFromFile(outputFile);
      int size = fPhrases.length/numSplits+1;
      int startLine = 0;
      while(startLine < fPhrases.length) {
        ChineseSyntaxCombinedFeatureExtractor combined = new ChineseSyntaxCombinedFeatureExtractor(prop);
        combined.restrictExtractionTo(fPhrases, startLine, startLine+size);
        combined.extractFromMergedAlignment(fCorpus, eCorpus, align);
        combined.write(oStream, noAlign);
        startLine += size;
      }
      if(oStream != null)
        oStream.close();
    } 
    // Only one chunk at a time (more advanced features available here):
    else {
      ChineseSyntaxCombinedFeatureExtractor combined = new ChineseSyntaxCombinedFeatureExtractor(prop);
      // Various filtering options:
      if(fPhrases != null)
        combined.restrictExtractionTo(fPhrases);
      combined.extractFromMergedAlignment(fCorpus, eCorpus, align);
      // Check phrase table against existing one:
      combined.write(outputFile, noAlign);
    }
    return true;
  }

  static void setStaticProperties(Properties prop) {
    printFeatureNames = Boolean.parseBoolean(prop.getProperty(PRINT_FEATURE_NAMES_OPT,"true"));
    int max = Integer.parseInt(prop.getProperty(MAX_PHRASE_LEN_OPT,"-1"));
    int maxF = Integer.parseInt(prop.getProperty(MAX_PHRASE_LEN_F_OPT,"-1"));
    int maxE = Integer.parseInt(prop.getProperty(MAX_PHRASE_LEN_E_OPT,"-1"));
    
    if(max > 0) {
      System.err.printf("changing default max phrase len: %d -> %d\n", maxPhraseLenF, max);
      maxPhraseLenF = max;
      maxPhraseLenE = max;
    }
    if(maxF > 0) {
      System.err.printf("changing default max phrase len (F): %d -> %d\n", maxPhraseLenF, maxF);
      maxPhraseLenF = maxF;
    }
    if(maxE > 0) {
      System.err.printf("changing default max phrase len (E): %d -> %d\n", maxPhraseLenE, maxE);
      maxPhraseLenE = maxE;
    }
  }

  public static void usage() {
    System.err.print
      ("Usage: java ChineseSyntaxCombinedFeatureExtractor [ARGS]\n"+
       "Mandatory arguments:\n"+
       " -fCorpus <file> : source-language corpus\n"+ 
       " -eCorpus <file> : target-language corpus\n"+
       " -align <file> : alignment file\n"+
       " -extractors <class1> [<class2> ... <classN>]\n"+
       "Optional arguments:\n"+
       " -outputFile <file>\n"+
       " -fFilterCorpus <file> : same as -fDevCorpus\n"+
       " -fFilterList <file> : phrase extraction restricted to this list\n"+
       " -split <N> : split filter list into N chunks\n"+
       "  (divides memory usage by N, but multiplies running time by N)\n"+
       " -refFile <file> : check features against a Pharaoh phrase table\n"+
       " -maxLen <n> : max phrase length\n"+
       " -maxLenF <n> : max phrase length (source-language)\n"+
       " -maxLenE <n> : max phrase length (target-language)\n"+
       " -numLines <n> : number of lines to process (<0 : all)\n"+
       " -startAtLine <n> : start at line <n> (<0 : all)\n"+
       " -endAtLine <n> : end at line <n> (<0 : all)\n"+
       " -noAlign : do not write alignment to stdout\n");
    //System.exit(1);
  }

  @SuppressWarnings("unchecked")
  public static void main(String[] args) {
    Properties prop = StringUtils.argsToProperties(args);
    System.err.println("props: "+prop.toString());

    if (!prop.keySet().containsAll(REQUIRED_OPTS)) {
      Set<String> missingFields = new HashSet<String>(REQUIRED_OPTS);
      missingFields.removeAll(prop.keySet());
      usage();
      throw new RuntimeException(String.format(
                                   "The following required fields are missing: %s\n", missingFields));
    }

    if (!ALL_RECOGNIZED_OPTS.containsAll(prop.keySet())) {
      Set extraFields = new HashSet(prop.keySet());
      extraFields.removeAll(ALL_RECOGNIZED_OPTS);
      usage();
      throw new RuntimeException(String.format(
                                   "The following fields are unrecognized: %s\n", extraFields));
    }

    setStaticProperties(prop);
    if(!multiPassFeatureExtract(prop))
      usage();
  }
}
