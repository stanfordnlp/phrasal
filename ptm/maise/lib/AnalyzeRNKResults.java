import java.util.*;
import java.io.*;
import java.text.DecimalFormat;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class AnalyzeRNKResults
{
  public static String uploadFilesDir;
  public static String collectionID;
  public static String filterListFileName; // file name
  public static String serverInfoFile; // file name
  public static String taskToAnalyze;
  public static String answersFileName;
  public static boolean verbose;
  public static BufferedWriter outFile_log;
  public static int agreementAnalysisLevel;
  public static String langPairOfInterest;

  public static int h2h_format = 2; // 1: %'s and ties, no sig vals or winners; 2: %'s, and sig vals and winners; 3: same as 2, but for LaTeX

  static final DecimalFormat f2 = new DecimalFormat("###0.00");
  static final DecimalFormat f4 = new DecimalFormat("###0.0000");
  static final String NaN_str = f4.format(0.0/0.0);
  static private String sepKeyVal = "|||";
  static private String sepEntEnt = "|||||||||";
  static private String sepSetSet = "---------------------------";
  static private HashMap<String,String> otherName = new HashMap<String,String>();
  static int num_task_types = 0;
  static int num_langs = 0;

  static String[] task_names;
  static String[] task_short_names;
  static TreeMap<String,Integer> task_short_name_index = new TreeMap<String,Integer>();
  static String[] lang_names;
  static String[] lang_short_names;

  static Vector<String> seenLangPairs = new Vector<String>();

  static private HashMap<String,String> systemNumber = new HashMap<String,String>();  // maps system -> number
  static private HashMap<String,String> systemAtNumber = new HashMap<String,String>(); // maps number -> system

  static private TreeMap<String,String[]> sentenceMap = new TreeMap<String,String[]>(); // maps sen_i -> {docid,segid}

  static private Vector<TreeMap<String,String>> answers = new Vector<TreeMap<String,String>>();
//  static private Vector<TreeMap<String,String>> answers_contrastive = new Vector<TreeMap<String,String>>();
    // each element in the vector corresponds to answers from a single HIT assignment,
    // represented as a map from a name field to the answer for that field (e.g. "AID" -> "AXXXXXXXXX")

  static TreeMap<String,TreeMap<Integer,Integer>> batchProcessedInfo = new TreeMap<String,TreeMap<Integer,Integer>>();
    // TreeMap<batchName,TreeMap<HIT#,count>>
    // batchName format:
    //   worker:AXXXX#task:TSK#langs:yz-yz#batch:1

  static private TreeSet<String> contrastiveAIDs = new TreeSet<String>();

  static private TreeSet<String> recruitedAIDs = new TreeSet<String>();

  static private TreeSet<String> filterList = new TreeSet<String>();

  static private TreeMap<String,TreeMap<String,Double>> workerValue = new TreeMap<String,TreeMap<String,Double>>();
    // maps AID to a map of criterion -> value ("cont-noRef_Kappa" -> 0.43)
  static private TreeMap<String,TreeSet<Double>> observedWorkerValues = new TreeMap<String,TreeSet<Double>>();
    // maps criterion -> set of values that have been assigned to AIDs

  static private TreeMap<String,Stats> userStats = new TreeMap<String,Stats>();
    // maps AID to user statistics


  static int[] pages_per_batch;
  static int[] random_pages_per_batch;
  static int[] global_repeat_pages_per_batch;
  static int[] local_repeat_pages_per_batch;
  static int[] sentences_per_page;
  static int[] outputs_per_sentence;
  static int[] constant_systems;


  // RNK
  static private TreeMap<String,Vector<Integer>> submissionRanks = new TreeMap<String,Vector<Integer>>();
    // maps (langPair,sysName) to Vector of ranks it obtained
    // e.g. "de-en jhu-combo" -> <1,4,5,2,2,-1,1,2>

  static private TreeMap<String,Vector<Comparison>> submissionComparisons = new TreeMap<String,Vector<Comparison>>();
    // maps (langPair,sysName) to Vector of comparisons involving it

  static private TreeMap<String,Vector<Comparison>> userComparisons = new TreeMap<String,Vector<Comparison>>();
    // maps AID to Vector of comparisons by that worker

  static private TreeMap<String,Integer> submissionAppearanceCount = new TreeMap<String,Integer>();
    // maps (langPair,sysName) to a count of times that submission was chosen to be in a returned HIT
    // note: this is NOT the number of HITs in which a system appears, since a system might appear
    //       in a single HIT more than once

  // EDT
  static private TreeMap<String,Vector<Integer>> submissionActions = new TreeMap<String,Vector<Integer>>();
    // maps (langPair,sysName) to Vector of edit actions it obtained
    // e.g. "de-en jhu-combo" -> <1,1,2,1,1,3,0,1>


  static public void main(String[] args) throws Exception
  {

    // java -Xmx300m AnalyzeRNKResults answers=answers.log collection=myCollectionID serverInfo=server_info.txt
    //                                 [filterList=filterList.txt] [turkInputLoc=MTurkInputDirectory] [agreeAnalysis=1] [langPair=sr-tg]

    if (args.length == 0) {
      println("Usage: java AnalyzeRNKResults answers=answers.log collection=myCollectionID serverInfo=server_info.txt");
      println("                              [filterList=filterList.txt] [turkInputLoc=MTurkInputDirectory] [agreeAnalysis=1]");
      System.exit(9);
    }

    // default values for mandatory parameters
    answersFileName = ""; // answers
    taskToAnalyze = "RNK"; // task
    collectionID = ""; // collection
    serverInfoFile = ""; // serverInfo

    // default values for optional parameters
    uploadFilesDir = "";
    filterListFileName = "";
    agreementAnalysisLevel = 1;
    langPairOfInterest = "all";
    verbose = false;


    // process arguments
    for (int i = 0; i < args.length; ++i) { processArg(args[i]); }

    if (collectionID.equals("")) {
      println("Please specify the collection ID.");
      System.exit(99);
    }

    if (taskToAnalyze.equals("")) {
      println("Please specify the task to analyze.");
      System.exit(99);
    }

    if (!filterListFileName.equals("")) { // optional
      processFilterList(filterListFileName);
    }

    if (!serverInfoFile.equals("")) {
      readServerInfo(serverInfoFile);
    } else {
      println("Please specify the server info file name.");
      System.exit(99);
    }


    if (!answersFileName.equals("")) {
      println("Processing answers file " + answersFileName);
      println("");
      extractAnswers(answersFileName,answers,false);
//      extractAnswers("answers.log.curr",answers,true);
    } else {
      println("Please specify the answer log file name.");
      System.exit(99);
    }

/*
    for (TreeMap<String,String> ans : answers) {
      println(ans.size() + "   " + ans.get("HIT_info"));
    }
*/
/*
    println("Writing work time information to file " + (collectionID+".worktimes"));
    println("");

    FileOutputStream outStream_times = new FileOutputStream(collectionID+".worktimes", false);
    OutputStreamWriter outStreamWriter_times = new OutputStreamWriter(outStream_times, "utf8");
    BufferedWriter outFile_times = new BufferedWriter(outStreamWriter_times);

    String outLine = "";

    outLine += "\t" + "AID" + "\t" + "Country";
    outLine += "\t" + "# HITs" + "\t" + "time/HIT (sec)" + "\t" + "Time worked (sec)" + "\t" + "Time worked (hh:mm:ss)";
    outLine += "\t" + "Signup code" + "\t" + "Name" + "\t" + "E-mail";

    writeLine(outLine.substring(1),outFile_times);

    for (String AID : userStats.keySet()) {

      Stats S = userStats.get(AID);

      outLine = "";
      outLine += "\t" + AID + "\t" + S.country;
      outLine += "\t" + S.numHITs + "\t" + S.averageHITTime() + "\t" + S.totalWorkTime + "\t" + S.totalWorkTimeInTimeFormat();

      if (S.signupCode != null) {
        outLine += "\t" + S.signupCode + "\t" + S.name + "\t" + S.email;
      }

      writeLine(outLine.substring(1),outFile_times);

    }

    outFile_times.close();
*/


    println("Characterizing magnitude of collected labels");
    println("");

    FileOutputStream outStream_log = new FileOutputStream(collectionID+".analysis.html", false);
    OutputStreamWriter outStreamWriter_log = new OutputStreamWriter(outStream_log, "utf8");
    outFile_log = new BufferedWriter(outStreamWriter_log); // outFile is a global variable

    writeLine("<html>",outFile_log);
    writeLine("<head>",outFile_log);
    writeLine("<script src=\"sorttable.js\"></script>",outFile_log);
    writeLine("<script src=\"lib/sorttable.js\"></script>",outFile_log);
    writeLine("<script src=\"http://www.kryogenix.org/code/browser/sorttable/sorttable.js\"></script>",outFile_log);
    writeLine("</head>",outFile_log);
    writeLine("<body>",outFile_log);


    writeLine("After excluding data from filtered workers, we have:<br>",outFile_log);
    for (String batchName : batchProcessedInfo.keySet()) {
      TreeMap<Integer,Integer> batchCountInfo = batchProcessedInfo.get(batchName);
      writeLine("&nbsp;&nbsp;&nbsp;&nbsp;Batch " + batchName + " has " + batchCountInfo.size() + " (at least partially) completed HITs<br>",outFile_log);
    }
    writeLine("<br>",outFile_log);

    writeLine("Specifically, we have:<br>",outFile_log);
    for (String batchName : batchProcessedInfo.keySet()) {
      TreeMap<Integer,Integer> batchCountInfo = batchProcessedInfo.get(batchName);
      writeLine("&nbsp;&nbsp;&nbsp;&nbsp;In batch " + batchName + ":<br>",outFile_log);

      int maxAssgns = 1;
      for (Integer h : batchCountInfo.keySet()) {
        if (batchCountInfo.get(h) > maxAssgns) maxAssgns = batchCountInfo.get(h);
      }

      for (int assgnCount = 1; assgnCount <= maxAssgns; ++assgnCount) {
        int HITCount = 0;
        for (Integer h : batchCountInfo.keySet()) {
          if (batchCountInfo.get(h) == assgnCount) ++HITCount;
        }
        writeLine("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" + HITCount + " HIT" + (HITCount > 1 ? "s were " : " was ") + "completed " + assgnCount + " time" + (assgnCount > 1 ? "s" : "") + "<br>",outFile_log);
      }
    }
    writeLine("<br>",outFile_log);



    if (taskToAnalyze.equals("RNK")) {
      println("Analyzing data (task: " + taskToAnalyze + ")");
      String indicator = "task:"+taskToAnalyze;
      int questionsPerScreen = sentences_per_page[task_short_name_index.get(taskToAnalyze)];
      int systemsPerQuestion = outputs_per_sentence[task_short_name_index.get(taskToAnalyze)];
      analyzeResults_RNK(collectionID+".RNK_results"+".csv",",",indicator,questionsPerScreen,systemsPerQuestion);
      println("");
      println("");
    }


    writeLine("</body>",outFile_log);
    writeLine("</html>",outFile_log);

    outFile_log.close();

    println("Analysis complete.  See " + (collectionID+".analysis.html") + " for more details.");


    System.exit(0);

  } // main(String[] args)

  static private void processArg(String arg)
  {
    String param = arg.split("=")[0];
    String value = arg.split("=")[1];

    if (param.equals("answers")) {
      answersFileName = value;
    } else if (param.equals("task")) {
      taskToAnalyze = value;
    } else if (param.equals("collection")) {
      collectionID = value;
    } else if (param.equals("serverInfo")) {
      serverInfoFile = value;
    } else if (param.equals("filterList")) {
      filterListFileName = value;
    } else if (param.equals("turkInputLoc")) {
      uploadFilesDir = value;
    } else if (param.equals("agreeAnalysis")) {
      agreementAnalysisLevel = Integer.parseInt(value);
    } else if (param.equals("langPair")) {
      langPairOfInterest = value;
    } else if (param.equals("verbose")) {
      if (value.equals("true") || value.equals("1")) { verbose = true; }
      else { verbose = false; }
    } else {
      println("Unknown parameter " + param + "...");
      System.exit(99);
    }

  }

  static private void processFilterList(String fileName)
  {
    try {

      InputStream inStream = new FileInputStream(new File(fileName));
      BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));
      for (String line; (line = inFile.readLine()) != null;) {
        filterList.add(line.trim());
      }
      inFile.close();

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in processFilterList(String): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in processFilterList(String): " + e.getMessage());
      System.exit(99902);
    }

  }

  static private void extractAnswers(String inFileName, Vector<TreeMap<String,String>> answers, boolean isContrastive)
  {
    // each element in the vector corresponds to answers from a single HIT
    // each element is a map from a name field to the actual answer (e.g. "AID" -> "AXXXXXXXXX")

    // also sets username and totalWorkTime maps

//    Vector<TreeMap<String,String>> answers = new Vector<TreeMap<String,String>>();

    try {

      InputStream inStream = new FileInputStream(new File(inFileName));
      BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

      String line = inFile.readLine();

      while (line != null) {
        TreeMap<String,String> ansMap = new TreeMap<String,String>();

        if (isContrastive) ansMap.put("isContrastive","true");
        else ansMap.put("isContrastive","false");

        while (!line.equals(sepSetSet)) {

          // determine key
          String key = line;
          line = inFile.readLine(); // skip sepKeyVal

          // determine value
          String val = "";
          line = inFile.readLine(); // skip sepKeyVal
          while (!line.equals(sepEntEnt)) {
            val += " " + line.trim();
            line = inFile.readLine(); // read next line of val (or possibly sepEntEnt)
          }
          val = val.trim();

          ansMap.put(key,val);

          line = inFile.readLine(); // read next key (or possibly sepSetSet)

        } // while (line != sepSetSet)


//if (isContrastive) ansMap.put("AID","A000_contrastive");

        String AID = ansMap.get("AID");

if (isContrastive) contrastiveAIDs.add(AID);

        Stats S = null;
        if (userStats.containsKey(AID)) {
          S = userStats.get(AID);
        } else {
          S = new Stats();
          S.numHITs = 0;
          S.totalWorkTime = 0;
          S.country = ansMap.get("WorkerCountry");
        }

        if (ansMap.containsKey("HIT_info") && (ansMap.get("HIT_info")).equals("signup")) { // signup HIT

          String code = ansMap.get("code");
          String st = ansMap.get("EndTime_str");
          String nm = ansMap.get("name");
          String em = ansMap.get("email");

          S.signupCode = code;
          S.signupTime = st;
          S.name = nm;
          S.email = em;

          if (code != null && code.equals("FALKMURC")) {
            recruitedAIDs.add(AID);
          }

          answers.add(ansMap);
          userStats.put(AID,S);

        } else if ((ansMap.get("HIT_info")).contains("task:") && !filterList.contains(AID)) { // task HIT
          S.numHITs += 1;
          S.totalWorkTime += Integer.parseInt(ansMap.get("WorkTime"));

          processTaskAssignment(ansMap);

          answers.add(ansMap);
          userStats.put(AID,S);

        } else { // task HIT by a filtered AID
          // do nothing
	  // System.err.println("Filtering: " + AID);
        }

        line = inFile.readLine();

      } // while (line != null)

      inFile.close();

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in extractAnswers(String): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in extractAnswers(String): " + e.getMessage());
      System.exit(99902);
    }

//    return answers;

  }
/*
  static private void setSystemMaps(String inFileName)
  {
    try {

      InputStream inStream = new FileInputStream(new File(inFileName));
      BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

      String line = inFile.readLine();

      while (line != null) {
        String sysNumber = line.split("\\s+")[0];
        String sysName = line.split("\\s+")[1];

        systemNumber.put(sysName,sysNumber);
        systemAtNumber.put(sysNumber,sysName);

        line = inFile.readLine();
      }

      inFile.close();

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in setSystemMaps(String): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in setSystemMaps(String): " + e.getMessage());
      System.exit(99902);
    }

  }
*/


  static private void analyzeResults_RNK(String outFileName, String sep, String indicator, int questionsPerScreen, int systemsPerQuestion) throws Exception
  {

    TreeMap<Pair< Pair<String,String> , Pair<String,String> >,Vector<RNKAnswers>> segRNKAnswers = new TreeMap<Pair< Pair<String,String> , Pair<String,String> >,Vector<RNKAnswers>>();
      // maps segName to a Vector of RNK answers
      // segName is a Pair of <langPair,segid>, where langPair is a <src,trg> Pair, and segid is a <docid,segid> Pair
      // e.g. < <"es","en"> , <"lidovky.cz/2009/12/10/75519","6"> >

//    TreeMap<Pair< Pair<String,String> , Pair<String,String> >,Vector<Comparison>> segComparisons = new TreeMap<Pair< Pair<String,String> , Pair<String,String> >,Vector<Comparison>>();
      // maps segName to a Vector of Comparisons
      // segName is a Pair of <langPair,segid>, where langPair is a <src,trg> Pair, and segid is a <docid,segid> Pair
      // e.g. < <"es","en"> , <"lidovky.cz/2009/12/10/75519","6"> >

    try {

      println("  Processing RNK answers (and writing to " + outFileName + ")");

      FileOutputStream outStream = new FileOutputStream(outFileName, false);
      OutputStreamWriter outStreamWriter = new OutputStreamWriter(outStream, "utf8");
      BufferedWriter outFile = new BufferedWriter(outStreamWriter);

      String outLine = "";

      outLine += sep + "srclang";
      outLine += sep + "trglang";

      outLine += sep + "srcIndex";
      outLine += sep + "documentId";
      outLine += sep + "segmentId";
      outLine += sep + "judgeId";

      for (int sys = 1; sys <= systemsPerQuestion; ++sys) {
        outLine += sep + "system" + sys + "Number";
        outLine += sep + "system" + sys + "Id";
      }

      //      for (int sys = 1; sys <= systemsPerQuestion; ++sys) {
        outLine += sep + "rank";
	//      }

      outLine = outLine.substring(1); // remove initial comma
      writeLine(outLine,outFile);

      for (TreeMap<String,String> ans : answers) {
        if ((ans.get("HIT_info")).contains(collectionID) && (ans.get("HIT_info")).contains(indicator)) {

          for (int q = 1; q <= questionsPerScreen; ++q) {

            String sen_i = ans.get("SrcIndex" + q);

            if (sen_i == null || sen_i.equals("")) {

              // ****** RECOVER SrcIndex* and System*_* USING AMT upload INPUT FILES ******

              String AID = ans.get("AID");
              String task = "RNK";

              String HIT_info = ans.get("HIT_info");
                // e.g. "emplus2010-c01#batch:1#task:RNK#langs:en-cz#HIT:1"
// *** OLD! WMT '10 format *** // e.g. "worker:A23Q0IA9ISEC3M#task:RNK#langs:es-en#batch:1#HIT:17"

              String langInfo = "", langPair = "", batchInfo = "", HITOrderInfo = "";
              int batchNumber = -1, HITOrder = -1;

              String[] A = HIT_info.split("#");
              for (int i = 0; i < A.length; ++i) {
                String str = A[i];
                if (str.startsWith("langs:")) { // e.g. "langs:en-cz"
                  langInfo = str; // e.g. "langs:en-cz"
                  langPair = langInfo.substring(6); // e.g. "en-cz"
                } else if (str.startsWith("batch:")) { // e.g. "batch:1"
                  batchInfo = str; // e.g. "batch:1"
                  batchNumber = Integer.parseInt(batchInfo.substring(6)); // e.g. "1"
                } else if (str.startsWith("HIT:")) { // e.g. "HIT:17"
                  HITOrderInfo = HIT_info.split("#")[4]; // e.g. "HIT:17"
                  HITOrder = Integer.parseInt(HITOrderInfo.substring(4)); // e.g. "17"
                }
              }

              String uploadFileName = collectionID + ".batch" + batchNumber + "." + task + "." + langPair + ".input";
                // e.g. "emplus2010-c01.batch1.RNK.cz-en.input"
              if (HIT_info.startsWith("worker:")) {
// *** OLD! WMT '10 format ***
                uploadFileName = AID + "." + task + "." + langPair + ".batch" + batchNumber + ".input";
                  // e.g. "A23Q0IA9ISEC3M.RNK.es-en.batch1.input"
              }

              uploadFileName = fullPath(uploadFilesDir,uploadFileName);

              if (fileExists(uploadFileName)) {
                InputStream inStream = new FileInputStream(new File(uploadFileName));
                BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

                TreeMap<String,Integer> colTitleMap = new TreeMap<String,Integer>();
                String line = inFile.readLine();
                String[] colTitles = line.split("\t");
                for (int i = 0; i < colTitles.length; ++i) {
                  colTitleMap.put(colTitles[i],i);
                }

                line = inFile.readLine();
                while (line != null) {
                  String[] entries = line.split("\t");
                  if (("\"" + HIT_info + "\"").equals(entries[colTitleMap.get("\"HIT_info\"")])) {

                    for (int qr = 1; qr <= questionsPerScreen; ++qr) {

                      String ir = entries[colTitleMap.get("\"srcIndex" + qr + "\"")];
                      ir = ir.substring(1,ir.length()-1); // remove quotes
                      ans.put("SrcIndex" + qr, ir);

                      for (int sysr = 1; sysr <= systemsPerQuestion; ++sysr) {

                        String sr = entries[colTitleMap.get("\"sys" + qr + "_" + sysr + "\"")];
                        sr = sr.substring(1,sr.length()-1); // remove quotes
                        ans.put("System" + qr + "_" + sysr, sr);

                      } // for (sysr)

                    } // for (qr)

                    break; // from while (line != null) loop

                  } // if HIT_info match

                  line = inFile.readLine();

                } // while (line != null)

                inFile.close();

              } // if upload file exists

            } // if (sen_i == null || sen_i.equals(""))


            sen_i = ans.get("SrcIndex" + q); // try again

            if (sen_i == null || sen_i.equals("")) { // couldn't recover

              println("    Couldn't recover " + ans.get("HIT_info"));

            } else {
              outLine = "";

              String HIT_info = ans.get("HIT_info");

              String langInfo = "", langPair = "";

              String[] A = HIT_info.split("#");
              for (int i = 0; i < A.length; ++i) {
                String str = A[i];
                if (str.startsWith("langs:")) { // e.g. "langs:en-cz"
                  langInfo = str; // e.g. "langs:en-cz"
                  langPair = langInfo.substring(6); // e.g. "en-cz"
                }
              }

              String AID = ans.get("AID");
              boolean isContrastive = false;
              if (ans.get("isContrastive").equals("true")) isContrastive = true;


              String srclang = otherName.get(langPair.split("-")[0]);
              String trglang = otherName.get(langPair.split("-")[1]);
/*
              String[] senInfo = sentenceMap.get(sen_i);
              String docid = senInfo[0];
              String segid = senInfo[1];
*/
              String docid = langPair + ".test"; // dummy docid
              String segid = "" + sen_i; // dummy segid

              outLine += sep + srclang;
              outLine += sep + trglang;

              outLine += sep + sen_i;
              outLine += sep + docid;
              outLine += sep + segid;

              outLine += sep + AID;

              String[] systemNames = new String[1+systemsPerQuestion];
              int[] systemNumbers = new int[1+systemsPerQuestion];
              int[] systemRanks = new int[1+systemsPerQuestion];

              for (int sys = 1; sys <= systemsPerQuestion; ++sys) {
                String sysNumber = ans.get("System" + q + "_" + sys);
                String sysName = systemAtNumber.get(sysNumber);
                systemNames[sys] = langPair + " " + sysName;
                systemNumbers[sys] = Integer.parseInt(sysNumber);

                if (!isContrastive) {
                  incrementMapCount(submissionAppearanceCount,langPair + " " + sysName);
                  outLine += sep + sysNumber;
                  outLine += sep + sysName;
                }
              }

	      //              for (int sys = 1; sys <= systemsPerQuestion; ++sys) {
                String sysRank_str = ans.get("RANK" + q);
                int sysRank = -1;
                if (sysRank_str != null) { sysRank = Integer.parseInt(sysRank_str); }
		//                systemRanks[sys] = sysRank;
                if (!isContrastive) {
                  outLine += sep + sysRank;
                }
		//              }



              for (int sys = 1; sys <= systemsPerQuestion; ++sys) {
                String sysNumber = ans.get("System" + q + "_" + sys);
                String subName = langPair + " " + systemAtNumber.get(sysNumber);
                sysRank_str = ans.get("RANK" + q);
                sysRank = -1;
                if (sysRank_str != null) { sysRank = Integer.parseInt(sysRank_str); }

                if (!isContrastive) {
                  Vector V = null;
                  if (submissionRanks.containsKey(subName)) { V = submissionRanks.get(subName); }
                  else { V = new Vector<Integer>(); }
                  V.add(sysRank);
                  submissionRanks.put(subName,V);
                }
              }


              Pair< Pair<String,String> , Pair<String,String> > segName = new Pair< Pair<String,String> , Pair<String,String> >();
                // e.g. < <es,en> , <"lidovky.cz/2009/12/10/75519","6"> >
              segName.first = new Pair<String,String>(); // e.g. <"es","en">
              segName.second = new Pair<String,String>(); // e.g. <"lidovky.cz/2009/12/10/75519","6">

              (segName.first).first = srclang;
              (segName.first).second = trglang;
              (segName.second).first = docid;
              (segName.second).second = segid;

              RNKAnswers segAnswers = new RNKAnswers(AID,isContrastive,Integer.parseInt(sen_i),docid,segid,systemsPerQuestion,systemNames,systemNumbers,systemRanks);
              Vector<RNKAnswers> segAnsVec = null;
              if (segRNKAnswers.containsKey(segName)) { segAnsVec = segRNKAnswers.get(segName); }
              else { segAnsVec = new Vector<RNKAnswers>(); }

              segAnsVec.add(segAnswers);
              segRNKAnswers.put(segName,segAnsVec);

              if (!isContrastive) {
                outLine = outLine.substring(1); // remove initial comma
                writeLine(outLine,outFile);
              }

            }

          } // for (q)

        } // if (HIT_info contains collectionID and indicator)
      } // for (ans)

      outFile.close();



    if (verbose) {

      writeLine("Rank summaries:",outFile_log);
      writeLine("submission" + "\t" + "rankCount" + "\t" + "ave_rank" + "\t" + "% unjudged" + "\t" + "% rank=1" + "\t" + "% rank=2" + "\t" + "% rank=3" + "\t" + "% rank=4" + "\t" + "% rank=5",outFile_log);
      for (String subName : submissionRanks.keySet()) {

        // in the following, 5 is the # possible ranks
        double[] summary = ranksSummary(submissionRanks.get(subName),5);

        outLine = "";

        outLine += subName + "\t" + (int)(summary[5+1]);
        outLine += "\t" + f2.format(summary[5+2]);
        for (int r = 0; r <= 5; ++r) { outLine += "\t" + f2.format(summary[r]); }
        writeLine(outLine,outFile_log);

      }

    }



    println("  Creating segment and user collections");

    TreeMap<String,Integer> langPairSegCount = new TreeMap<String,Integer>();
      // maps a langPair_str to the number of segments appearing with that language pair
      // e.g. "<English|||Czech>" -> 273
    TreeMap<String,Integer> langPairSegObservations = new TreeMap<String,Integer>();
      // maps a langPair_str to the number of segment *instances* appearing with that language pair
      // e.g. "<English|||Czech>" -> 591

    TreeSet<Pair<String,String>> observedSegments = new TreeSet<Pair<String,String>>();
      // a set of segments observed at least once (in any language pair)

    for (Pair< Pair<String,String> , Pair<String,String> > segName : segRNKAnswers.keySet()) {
      String segName_str = segName.toString();
      Vector<RNKAnswers> segAnsVec = segRNKAnswers.get(segName);

      if (verbose) {
        writeLine(segName_str + "\t" + segAnsVec.size(),outFile_log);
      }

      // update counts and observations for language pair
      String langPair_str = (segName.first).toString();
      int currCount = 0, currObservations = 0;
      if (langPairSegCount.containsKey(langPair_str)) currCount = langPairSegCount.get(langPair_str);
      if (langPairSegObservations.containsKey(langPair_str)) currObservations = langPairSegObservations.get(langPair_str);
      langPairSegCount.put(langPair_str,currCount+1);
      langPairSegObservations.put(langPair_str,currObservations+segAnsVec.size());

      observedSegments.add(segName.second);

if (langPair_str.equals(langPairOfInterest) || langPairOfInterest.equals("all")) {

      for (RNKAnswers segAnswers : segAnsVec) {

        Vector<Comparison> CV = segAnswers.getComparisons();
        // now CV has e.g. 10 pairwise Comparisons

        for (Comparison C : CV) {
if (isDesired(C)) {
          String AID = segAnswers.AID;
          Vector<Comparison> AID_V = null;
          if (userComparisons.containsKey(AID)) { AID_V = userComparisons.get(AID); }
          else { AID_V = new Vector<Comparison>(); }
          AID_V.add(C);
          userComparisons.put(AID,AID_V);

          if (!C.isContrastive) {
            String sys1 = C.firstSystem;
            Vector<Comparison> sys1_V = null;
            if (submissionComparisons.containsKey(sys1)) { sys1_V = submissionComparisons.get(sys1); }
            else { sys1_V = new Vector<Comparison>(); }

            String sys2 = C.secondSystem;
            Vector<Comparison> sys2_V = null;
            if (submissionComparisons.containsKey(sys2)) { sys2_V = submissionComparisons.get(sys2); }
            else { sys2_V = new Vector<Comparison>(); }

            sys1_V.add(C);
            sys2_V.add(C);

            submissionComparisons.put(sys1,sys1_V);
            submissionComparisons.put(sys2,sys2_V);
          }
}
        } // for (C)

      } // for (segAnswers)

}

    } // for (segName)

    if (verbose) {
      writeLine("",outFile_log);
    }

    writeLine("Observed " + observedSegments.size() + " source segments at least once:<br>",outFile_log);

    writeLine("<table class=\"sortable\" border=1>",outFile_log);
    outLine = "";
    outLine += "<thead>";
    outLine += "<tr>";
    outLine += "<th align=left>" + "langPair" + "</th>";
    outLine += "<th>" + "segCount" + "</th>";
    outLine += "<th>" + "segObservations" + "</th>";
    outLine += "</tr>";
    outLine += "</thead>";

    writeLine(outLine,outFile_log);

    int segObvs = 0;
    writeLine("<tbody align=center>",outFile_log);
    for (String langPair_str : langPairSegCount.keySet()) {
      outLine = "";
      outLine += "<tr>";
      outLine += "<td align=left>" + langPair_str.replaceAll("<","&lt;").replaceAll(">","&gt;") + "</td>";
      outLine += "<td>" + langPairSegCount.get(langPair_str) + "</td>";
      outLine += "<td>" + langPairSegObservations.get(langPair_str) + "</td>";
      outLine += "</tr>";
      writeLine(outLine,outFile_log);
      segObvs += langPairSegObservations.get(langPair_str);
    }

    writeLine("<tfoot align=center>",outFile_log);

    outLine = "";
    outLine += "<tr>";
    outLine += "<td align=left>" + "All lang pairs" + "</td>";
    outLine += "<td>" + observedSegments.size() + "</td>";
    outLine += "<td>" + segObvs + "</td>";
    outLine += "</tr></tfoot>";
    writeLine(outLine,outFile_log);
    writeLine("</tbody>",outFile_log);
    writeLine("</table>",outFile_log);

    writeLine("<br>",outFile_log);


///*

    println("  Computing basic annotator counts");

    int AID_cnt = (userComparisons.keySet()).size();
    String[] workerID = new String[1+AID_cnt]; // 1-indexed
    int a = 0;
    for (String AID : userComparisons.keySet()) {
      ++a;
      workerID[a] = AID;
    }

    int[] compCount = new int[1+AID_cnt];
    int[] compRefCount = new int[1+AID_cnt]; int[] compRefWinTieCount = new int[1+AID_cnt]; // how many times the _ref system won or tied
    int[] compNoRefCount = new int[1+AID_cnt];

    int[] compNoRef_sys1Count = new int[1+AID_cnt]; // how many times was the result a win for system1 (when there was no reference)
    int[] compNoRef_sys2Count = new int[1+AID_cnt]; // how many times was the result a win for system2 (when there was no reference)
    int[] compNoRef_tieCount = new int[1+AID_cnt]; // how many times was the result a tie (when there was no reference)

    int[][] common_cnt = new int[1+AID_cnt][1+AID_cnt];
    int[][] agree_cnt = new int[1+AID_cnt][1+AID_cnt];
    double[][] agree_rate = new double[1+AID_cnt][1+AID_cnt];
    int[][] common_ref_cnt = new int[1+AID_cnt][1+AID_cnt];
    int[][] agree_ref_cnt = new int[1+AID_cnt][1+AID_cnt];
    double[][] agree_ref_rate = new double[1+AID_cnt][1+AID_cnt];
    int[][] common_noRef_cnt = new int[1+AID_cnt][1+AID_cnt];
    int[][] agree_noRef_cnt = new int[1+AID_cnt][1+AID_cnt];
    double[][] agree_noRef_rate = new double[1+AID_cnt][1+AID_cnt];

    for (int a1 = 1; a1 <= AID_cnt; ++a1) {
      String AID1 = workerID[a1];
      Vector<Comparison> CV_1 = userComparisons.get(AID1);
      compCount[a1] = CV_1.size();
      int a1_noRefTieCount = 0, a1_noRefSys1WinCount = 0, a1_noRefSys2WinCount = 0;
      int a1_refCount = 0;
      int a1_refWinTieCount = 0;
      for (Comparison C1 : CV_1) {
        if (C1.hasRef) ++a1_refCount;
        else { // no reference
          if (C1.winner() == 1) ++a1_noRefSys1WinCount;
          if (C1.winner() == 2) ++a1_noRefSys2WinCount;
          if (C1.winner() == 0) ++a1_noRefTieCount;
        }
        if (C1.refTies() || C1.refWins()) ++a1_refWinTieCount;
      }
      compNoRef_sys1Count[a1] = a1_noRefSys1WinCount;
      compNoRef_sys2Count[a1] = a1_noRefSys2WinCount;
      compNoRef_tieCount[a1] = a1_noRefTieCount;

      compRefCount[a1] = a1_refCount;
      compRefWinTieCount[a1] = a1_refWinTieCount;
      compNoRefCount[a1] = compCount[a1] - compRefCount[a1];
    }


///*
boolean debug = false; // MAKE TO FALSE WHEN DOING REAL COUNTS
if (agreementAnalysisLevel > 0) {

    if (agreementAnalysisLevel == 1) {
      println("  Computing intra- (but not inter-) annotator agreement for " + AID_cnt + " annotators.");
    } else {
      println("  Computing intra- and inter-annotator agreement for " + AID_cnt + " annotators.");
    }

    if (debug) {
      AID_cnt = 1;
    }

    int comp_cnt = 0; // how many pairwise comparisons are there?
    int comp_noRef_cnt = 0;
    int comp_tie_cnt = 0; // how many pairwise comparisons had equal rank labels?
    int comp_noRef_tie_cnt = 0;

    for (int a1 = 1; a1 <= AID_cnt; ++a1) {

      String AID1 = workerID[a1];
      Vector<Comparison> CV_1 = userComparisons.get(AID1);

      for (Comparison C : CV_1) {
        boolean hasRef = C.hasRef;

        ++comp_cnt;
        if (!hasRef) ++comp_noRef_cnt;

        if (C.firstRank == C.secondRank) {
          ++comp_tie_cnt;
          if (!hasRef) ++comp_noRef_tie_cnt;
        }
      }

      compCount[a1] = CV_1.size();

      int a1_refCount = compRefCount[a1];

      for (int a2 = a1; a2 <= (agreementAnalysisLevel == 2? AID_cnt : a1); ++a2) {

        String AID2 = workerID[a2];
        Vector<Comparison> CV_2 = userComparisons.get(AID2);

//println("AID1: " + AID1 + "; AID2: " + AID2);

        for (Comparison C1 : CV_1) {
          for (Comparison C2 : CV_2) {
            if (C1.judgeCommonItem(C2)) {
              boolean involvesRef = false;
              involvesRef = C1.hasRef;

              common_cnt[a1][a2] += 1;
              if (involvesRef) common_ref_cnt[a1][a2] += 1;

              if (C1.isInAgreement(C2)) {
                agree_cnt[a1][a2] += 1;
                if (involvesRef) agree_ref_cnt[a1][a2] += 1;
              }
            }
          } // for (C2)
        } // for (C1)

        if (a1 == a2) {
          common_cnt[a1][a2] = (common_cnt[a1][a2] - CV_1.size()) / 2;
          agree_cnt[a1][a2] = (agree_cnt[a1][a2] - CV_1.size()) / 2;

          common_ref_cnt[a1][a2] = (common_ref_cnt[a1][a2] - a1_refCount) / 2;
          agree_ref_cnt[a1][a2] = (agree_ref_cnt[a1][a2] - a1_refCount) / 2;
        }

        common_noRef_cnt[a1][a2] = common_cnt[a1][a2] - common_ref_cnt[a1][a2];
        agree_noRef_cnt[a1][a2] = agree_cnt[a1][a2] - agree_ref_cnt[a1][a2];

        common_cnt[a2][a1] = common_cnt[a1][a2];
        agree_cnt[a2][a1] = agree_cnt[a1][a2];
        common_ref_cnt[a2][a1] = common_ref_cnt[a1][a2];
        agree_ref_cnt[a2][a1] = agree_ref_cnt[a1][a2];
        common_noRef_cnt[a2][a1] = common_noRef_cnt[a1][a2];
        agree_noRef_cnt[a2][a1] = agree_noRef_cnt[a1][a2];

      } // for (a2)

      if (a1%100 == 0) { println("#"+a1); }
      else if (a1%10 == 0) { print("."); }

    } // for (a1)
    println(""); // end progress output

//*/


    for (int a1 = 1; a1 <= AID_cnt; ++a1) {
      for (int a2 = a1; a2 <= AID_cnt; ++a2) {
        agree_rate[a1][a2] = (double)agree_cnt[a1][a2] / common_cnt[a1][a2];
        agree_ref_rate[a1][a2] = (double)agree_ref_cnt[a1][a2] / common_ref_cnt[a1][a2];
        agree_noRef_rate[a1][a2] = (double)agree_noRef_cnt[a1][a2] / common_noRef_cnt[a1][a2];
      } // for (a2)
    } // for (a1)

    int common_cnt__inter = 0; // across annotators
    int agree_cnt__inter = 0; // across annotators
    int common_noRef_cnt__inter = 0; // across annotators
    int agree_noRef_cnt__inter = 0; // across annotators
    int common_cnt__intra = 0; // self-consistency
    int agree_cnt__intra = 0; // self-consistency
    int common_noRef_cnt__intra = 0; // self-consistency
    int agree_noRef_cnt__intra = 0; // self-consistency
    int common_cnt__all = 0; // all annotator pairings
    int agree_cnt__all = 0; // all annotator pairings
    int common_noRef_cnt__all = 0; // all annotator pairings
    int agree_noRef_cnt__all = 0; // all annotator pairings

    for (int a1 = 1; a1 <= AID_cnt; ++a1) {
      String AID1 = workerID[a1];
      if (!contrastiveAIDs.contains(AID1)) {
        for (int a2 = a1; a2 <= AID_cnt; ++a2) {
          String AID2 = workerID[a2];
          if (!contrastiveAIDs.contains(AID2)) {

            if (a1 != a2) {
              common_cnt__inter += common_cnt[a1][a2];
              agree_cnt__inter += agree_cnt[a1][a2];
              common_noRef_cnt__inter += common_noRef_cnt[a1][a2];
              agree_noRef_cnt__inter += agree_noRef_cnt[a1][a2];
            } else {
              common_cnt__intra += common_cnt[a1][a2];
              agree_cnt__intra += agree_cnt[a1][a2];
              common_noRef_cnt__intra += common_noRef_cnt[a1][a2];
              agree_noRef_cnt__intra += agree_noRef_cnt[a1][a2];
            }
            common_cnt__all += common_cnt[a1][a2];
            agree_cnt__all += agree_cnt[a1][a2];
            common_noRef_cnt__all += common_noRef_cnt[a1][a2];
            agree_noRef_cnt__all += agree_noRef_cnt[a1][a2];
          }
        } // for (a2)
      }
    } // for (a1)

    double p_A = 0.0, p_E = 0.0, K = 0.0, p_tie = 0.0, p_notie = 0.0;
    double p_noRef_A = 0.0, p_noRef_E = 0.0, K_noRef = 0.0, p_noRef_tie = 0.0, p_noRef_notie = 0.0;

    p_tie = (double)(comp_tie_cnt)/comp_cnt;
    p_notie = 1.0 - p_tie;
    p_E = (p_tie*p_tie) + 2*((p_notie/2)*(p_notie/2));

    p_noRef_tie = (double)(comp_noRef_tie_cnt)/comp_noRef_cnt;
    p_noRef_notie = 1.0 - p_noRef_tie;
    p_noRef_E = (p_noRef_tie*p_noRef_tie) + 2*((p_noRef_notie/2)*(p_noRef_notie/2));

    println("comp_cnt: " + comp_cnt);
    println("comp_tie_cnt: " + comp_tie_cnt);
    println("comp_noRef_cnt: " + comp_noRef_cnt);
    println("comp_noRef_tie_cnt: " + comp_noRef_tie_cnt);
    println("  -> P(tie) = " + f4.format(p_tie));
    println("  -> P(E) = " + f4.format(p_E));
    println("  -> P_noRef(tie) = " + f4.format(p_noRef_tie));
    println("  -> P_noRef(E) = " + f4.format(p_noRef_E));

    println("common_cnt__inter: " + common_cnt__inter);
    println("agree_cnt__inter: " + agree_cnt__inter);
    println("common_noRef_cnt__inter: " + common_noRef_cnt__inter);
    println("agree_noRef_cnt__inter: " + agree_noRef_cnt__inter);
    p_A = (double)agree_cnt__inter/common_cnt__inter;
//    K = (p_A - p_E) / (1.0 - p_E);
    K = kappa(p_A,p_E);
    p_noRef_A = (double)agree_noRef_cnt__inter/common_noRef_cnt__inter;
//    K_noRef = (p_noRef_A - p_noRef_E) / (1.0 - p_noRef_E);
    K_noRef = kappa(p_noRef_A,p_noRef_E);
    println("  -> P(A) = " + f4.format(p_A));
    println("  -> K = " + f4.format(K));
    println("  -> P_noRef(A) = " + f4.format(p_noRef_A));
    println("  -> K_noRef = " + f4.format(K_noRef));

    println("common_cnt__intra: " + common_cnt__intra);
    println("agree_cnt__intra: " + agree_cnt__intra);
    println("common_noRef_cnt__intra: " + common_noRef_cnt__intra);
    println("agree_noRef_cnt__intra: " + agree_noRef_cnt__intra);
    p_A = (double)agree_cnt__intra/common_cnt__intra;
//    K = (p_A - p_E) / (1.0 - p_E);
    K = kappa(p_A,p_E);
    p_noRef_A = (double)agree_noRef_cnt__intra/common_noRef_cnt__intra;
//    K_noRef = (p_noRef_A - p_noRef_E) / (1.0 - p_noRef_E);
    K_noRef = kappa(p_noRef_A,p_noRef_E);
    println("  -> P(A) = " + f4.format(p_A));
    println("  -> K = " + f4.format(K));
    println("  -> P_noRef(A) = " + f4.format(p_noRef_A));
    println("  -> K_noRef = " + f4.format(K_noRef));

    println("common_cnt__all: " + common_cnt__all);
    println("agree_cnt__all: " + agree_cnt__all);
    println("common_noRef_cnt__all: " + common_noRef_cnt__all);
    println("agree_noRef_cnt__all: " + agree_noRef_cnt__all);
    p_A = (double)agree_cnt__all/common_cnt__all;
//    K = (p_A - p_E) / (1.0 - p_E);
    K = kappa(p_A,p_E);
    p_noRef_A = (double)agree_noRef_cnt__all/common_noRef_cnt__all;
//    K_noRef = (p_noRef_A - p_noRef_E) / (1.0 - p_noRef_E);
    K_noRef = kappa(p_noRef_A,p_noRef_E);
    println("  -> P(A) = " + f4.format(p_A));
    println("  -> K = " + f4.format(K));
    println("  -> P_noRef(A) = " + f4.format(p_noRef_A));
    println("  -> K_noRef = " + f4.format(K_noRef));
    println("");


if (verbose) {
    outLine = "";
    for (a = 1; a <= AID_cnt; ++a) outLine += "\t" + workerID[a];
    writeLine(outLine,outFile_log);
    for (int a1 = 1; a1 <= AID_cnt; ++a1) {
      outLine = "";
      outLine += workerID[a1];
      for (int a2 = 1; a2 <= AID_cnt; ++a2) outLine += "\t" + agree_cnt[a1][a2];
      writeLine(outLine,outFile_log);
    }
    writeLine("",outFile_log);

    outLine = "";
    for (a = 1; a <= AID_cnt; ++a) outLine += "\t" + workerID[a];
    writeLine(outLine,outFile_log);
    for (int a1 = 1; a1 <= AID_cnt; ++a1) {
      outLine = "";
      outLine += workerID[a1];
      for (int a2 = 1; a2 <= AID_cnt; ++a2) outLine += "\t" + common_cnt[a1][a2];
      writeLine(outLine,outFile_log);
    }
    writeLine("",outFile_log);

    outLine = "";
    for (a = 1; a <= AID_cnt; ++a) outLine += "\t" + workerID[a];
    writeLine(outLine,outFile_log);
    for (int a1 = 1; a1 <= AID_cnt; ++a1) {
      outLine = "";
      outLine += workerID[a1];
      for (int a2 = 1; a2 <= AID_cnt; ++a2) outLine += "\t" + agree_ref_cnt[a1][a2];
      writeLine(outLine,outFile_log);
    }
    writeLine("",outFile_log);

    outLine = "";
    for (a = 1; a <= AID_cnt; ++a) outLine += "\t" + workerID[a];
    writeLine(outLine,outFile_log);
    for (int a1 = 1; a1 <= AID_cnt; ++a1) {
      outLine = "";
      outLine += workerID[a1];
      for (int a2 = 1; a2 <= AID_cnt; ++a2) outLine += "\t" + common_ref_cnt[a1][a2];
      writeLine(outLine,outFile_log);
    }
    writeLine("",outFile_log);

    outLine = "";
    for (a = 1; a <= AID_cnt; ++a) outLine += "\t" + workerID[a];
    writeLine(outLine,outFile_log);
    for (int a1 = 1; a1 <= AID_cnt; ++a1) {
      outLine = "";
      outLine += workerID[a1];
      for (int a2 = 1; a2 <= AID_cnt; ++a2) outLine += "\t" + agree_noRef_cnt[a1][a2];
      writeLine(outLine,outFile_log);
    }
    writeLine("",outFile_log);

    outLine = "";
    for (a = 1; a <= AID_cnt; ++a) outLine += "\t" + workerID[a];
    writeLine(outLine,outFile_log);
    for (int a1 = 1; a1 <= AID_cnt; ++a1) {
      outLine = "";
      outLine += workerID[a1];
      for (int a2 = 1; a2 <= AID_cnt; ++a2) outLine += "\t" + common_noRef_cnt[a1][a2];
      writeLine(outLine,outFile_log);
    }
    writeLine("",outFile_log);

}

} // if (agreementAnalysisLevel > 0)



    println("  Printing annotator profiles");

    writeLine("<br>",outFile_log);
    writeLine("In the following worker profile table,<br>",outFile_log);
    writeLine("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b># comparisons</b>: number of pairwise comparisons implied by the rank labels.<br>",outFile_log); 
    writeLine("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b># refComp</b>: number of pairwise comparisons that involve an embedded reference sentence.<br>",outFile_log);
    writeLine("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>RPR</b>: reference preference rate; how often did an embedded reference sentences tie or win the comparison.<br>",outFile_log);
    writeLine("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>sys1_rate</b>: how often did the worker give a better rank for the system presented FIRST.<br>",outFile_log);
    writeLine("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>tie_rate</b> how often did the worker give the same rank label to both systems: <br>",outFile_log);
    writeLine("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>sys2_rate</b>: how often did the worker give a better rank for the system presented SECOND.<br>",outFile_log);
    writeLine("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>P(E)</b>: given the above three rates, the expected rate this annotator would give consistent comparisons by pure chance.<br>",outFile_log);
    if (agreementAnalysisLevel >= 1) {
      writeLine("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>self_common</b>: number of comparisons that were judged twice by the worker.<br>",outFile_log);
      writeLine("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>P(A)</b>: of the self_common repeated pairs, how often the worker gave the same comparison.<br>",outFile_log);
      writeLine("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b><a href=\"http://en.wikipedia.org/wiki/Cohen's_kappa\">K_intra</a></b>: given the annotator's P(E) and P(A), what is their Kappa coefficient?<br>",outFile_log);
    }
    if (agreementAnalysisLevel >= 2) {
      writeLine("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>other_common</b>: number of comparisons that were judged by the worker and other workers.<br>",outFile_log);
      writeLine("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>other_rate</b>: of the other_common repeated pairs, how often the worker gave the same comparison.<br>",outFile_log);
    }

    writeLine("<br>",outFile_log);

    writeLine("<u>Indications of atypical behavior</u>: a <b>low RPR</b> indicates random clicking (anything below 0.90 is almost certainly bad; typical good values are 0.97 and up); an overly <b>high tie_rate</b> indicates an overly conservative worker hesitant to distinguish between outputs; a <b>big difference between sys1_rate and sys2_rate</b> indicates the worker has systematic (perhaps suspicious) bias, because the outputs are ordered randomly and the two rates should be close; a <b>low P(A)</b> and especially a <b>low <a href=\"http://en.wikipedia.org/wiki/Cohen's_kappa\">K_intra</a></b> indicate random and/or careless performance.<br>",outFile_log);
    writeLine("<br>",outFile_log);
    writeLine("<u>Note</u>: the table is sortable by any of its columns; simply click on the column header to sort.<br>",outFile_log);
    writeLine("<br>",outFile_log);

    writeLine("<table class=\"sortable\" border=1>",outFile_log);

    outLine = "";
    outLine += "<thead>";
    outLine += "<tr>";

    outLine += "<th align=left>" + "AID" + "</th>";
    outLine += "<th align=left>" + "Country" + "</th>";
    outLine += "<th>" + "Work time" + "</th>";
    outLine += "<th>" + "# HITs" + "</th>";
    outLine += "<th>" + "Time/HIT (sec)" + "</th>";
    outLine += "<th>" + "# comparisons" + "</th>";

//    outLine += "<th>" + "# refWinTieComp" + "</th>";
    outLine += "<th>" + "# refComp" + "</th>";
    outLine += "<th>" + "RPR" + "</th>";

    outLine += "<th>" + "sys1_rate" + "</th>";
    outLine += "<th>" + "tie_rate" + "</th>";
    outLine += "<th>" + "sys2_rate" + "</th>";
    outLine += "<th>" + "P(E)" + "</th>";

    if (agreementAnalysisLevel >= 1) {
//      outLine += "<th>" + "self-withRef_agree" + "</th>";
//      outLine += "<th>" + "self-withRef_common" + "</th>";
//      outLine += "<th>" + "self-withRef_rate" + "</th>";
//////      outLine += "<th>" + "self-withRef_Kappa" + "</th>";
//      outLine += "<th>" + "self_agree" + "</th>";
      outLine += "<th>" + "self_common" + "</th>";
      outLine += "<th>" + "P(A)" + "</th>"; // aka self_rate
//////      outLine += "<th>" + "self_Kappa" + "</th>";
      outLine += "<th>" + "K_intra" + "</th>";
    }

    if (agreementAnalysisLevel >= 2) {
//      outLine += "<th>" + "other-withRef_agree" + "</th>";
//      outLine += "<th>" + "other-withRef_common" + "</th>";
//      outLine += "<th>" + "other-withRef_rate" + "</th>";
//////      outLine += "<th>" + "other-withRef_Kappa" + "</th>";
//      outLine += "<th>" + "other_agree" + "</th>";
      outLine += "<th>" + "other_common" + "</th>";
      outLine += "<th>" + "other_rate" + "</th>";
//////      outLine += "<th>" + "other_Kappa" + "</th>";
    }
/*
    outLine += "<th>" + "cont_agree" + "</th>";
    outLine += "<th>" + "cont_common" + "</th>";
    outLine += "<th>" + "cont_rate" + "</th>";
//////    outLine += "<th>" + "cont_Kappa" + "</th>";
    outLine += "<th>" + "cont-noRef_agree" + "</th>";
    outLine += "<th>" + "cont-noRef_common" + "</th>";
    outLine += "<th>" + "cont-noRef_rate" + "</th>";
//////    outLine += "<th>" + "cont-noRef_Kappa" + "</th>";
*/
    outLine += "</tr>";
    outLine += "</thead>";

    writeLine(outLine,outFile_log);


    writeLine("<tbody align=center>",outFile_log);

    for (int a1 = 1; a1 <= AID_cnt; ++a1) {
      String AID = workerID[a1];
      if (!contrastiveAIDs.contains(AID)) {
        Stats S = userStats.get(AID);

        outLine = "";
        outLine += "<tr>";

        outLine += "<td align=left>" + AID + "</td>";
        outLine += "<td align=left>" + (S.country.equals("")? "Unknown" : S.country) + "</td>";
        outLine += "<td sorttable_customkey=" + S.totalWorkTime + ">" + S.totalWorkTimeInTimeFormat() + "</td>";
        outLine += "<td>" + S.numHITs + "</td>";
        outLine += "<td>" + S.averageHITTime() + "</td>";
        outLine += "<td>" + compCount[a1] + "</td>";

//        outLine += "<td>" + compRefWinTieCount[a1] + "</td>";
        outLine += "<td>" + compRefCount[a1] + "</td>";
        outLine += "<td>" + f4.format((double)compRefWinTieCount[a1] / compRefCount[a1]) + "</td>"; // RPR

        double sys1_rate = (double)compNoRef_sys1Count[a1] / compNoRefCount[a1];
        double tie_rate  = (double)compNoRef_tieCount[a1]  / compNoRefCount[a1];
        double sys2_rate = (double)compNoRef_sys2Count[a1] / compNoRefCount[a1];
        double tr = tie_rate, ntr = 1.0 - tie_rate;
        double P_E = (tr*tr) + 2*(ntr/2.0)*(ntr/2.0);

        outLine += "<td>" + f4.format(sys1_rate) + "</td>";
        outLine += "<td>" + f4.format(tie_rate) + "</td>";
        outLine += "<td>" + f4.format(sys2_rate) + "</td>";
        outLine += "<td>" + f4.format(P_E) + "</td>"; // P(E)

        if (agreementAnalysisLevel >= 1) {
//          outLine += "<td>" + agree_cnt[a1][a1] + "</td>"; // self-withRef_agree
//          outLine += "<td>" + common_cnt[a1][a1] + "</td>"; // self-withRef_common
//          outLine += "<td>" + f4.format(agree_rate[a1][a1]) + "</td>"; // self-withRef_rate
//////          outLine += "<td>" + f4.format(kappa(agree_rate[a1][a1],1.0/3.0)) + "</td>"; // self-withRef_Kappa
//          outLine += "<td>" + agree_noRef_cnt[a1][a1] + "</td>"; // self_agree
          outLine += "<td>" + common_noRef_cnt[a1][a1] + "</td>"; // self_common
          outLine += "<td>" + f4.format(agree_noRef_rate[a1][a1]) + "</td>"; // P(A), aka self_rate
//////         outLine += "<td>" + f4.format(kappa(agree_noRef_rate[a1][a1],1.0/3.0)) + "</td>"; // self_Kappa
          outLine += "<td>" + f4.format((agree_noRef_rate[a1][a1] - P_E)/(1.0 - P_E)) + "</td>"; // K_intra
        }

        int other_agree = 0, other_common = 0, cont_agree = 0, cont_common = 0;
        int other_noRef_agree = 0, other_noRef_common = 0, cont_noRef_agree = 0, cont_noRef_common = 0;
        if (agreementAnalysisLevel >= 2) {
          for (int a2 = 1; a2 <= AID_cnt; ++a2) {
            if (a2 != a1) {
              if (!contrastiveAIDs.contains(workerID[a2])) {
                other_agree += agree_cnt[a1][a2];
                other_common += common_cnt[a1][a2];
                other_noRef_agree += agree_noRef_cnt[a1][a2];
                other_noRef_common += common_noRef_cnt[a1][a2];
              } else {
                cont_agree += agree_cnt[a1][a2];
                cont_common += common_cnt[a1][a2];
                cont_noRef_agree += agree_noRef_cnt[a1][a2];
                cont_noRef_common += common_noRef_cnt[a1][a2];
             }
            }
          } // for (a2)

//          outLine += "<td>" + other_agree + "</td>"; // other-withRef_agree
//          outLine += "<td>" + other_common + "</td>"; // other-withRef_common
//          outLine += "<td>" + f4.format((double)other_agree / other_common) + "</td>"; // other-withRef_rate
//////          outLine += "<td>" + f4.format(kappa((double)other_agree / other_common , 1.0/3.0)) + "</td>"; // other-withRef_Kappa
//          outLine += "<td>" + other_noRef_agree + "</td>"; // other_agree
          outLine += "<td>" + other_noRef_common + "</td>"; // other_common
          outLine += "<td>" + f4.format((double)other_noRef_agree / other_noRef_common) + "</td>"; // other_rate
//////          outLine += "<td>" + f4.format(kappa((double)other_noRef_agree / other_noRef_common , 1.0/3.0)) + "</td>"; // other_Kappa
        }
/*
        outLine += "<td>" + cont_agree + "</td>";
        outLine += "<td>" + cont_common + "</td>";
        outLine += "<td>" + f4.format((double)cont_agree / cont_common) + "</td>";
//////        outLine += "<td>" + f4.format(kappa((double)cont_agree / cont_common , 1.0/3.0)) + "</td>";
        outLine += "<td>" + cont_noRef_agree + "</td>";
        outLine += "<td>" + cont_noRef_common + "</td>";
        outLine += "<td>" + f4.format((double)cont_noRef_agree / cont_noRef_common) + "</td>";
//////        outLine += "<td>" + f4.format(kappa((double)cont_noRef_agree / cont_noRef_common , 1.0/3.0)) + "</td>";
*/

        if (compRefCount[a1] != 0) {
          double ref_rate = (double)compRefWinTieCount[a1] / compRefCount[a1];
          addWorkerValue(AID,"ref_rate",ref_rate);
//          double ref_rate_Kappa = kappa(ref_rate , 2.0/3.0);
//          addWorkerValue(AID,"ref_rate_Kappa",ref_rate_Kappa);
        }

        if (cont_noRef_common != 0) {
          double cont_noRef = (double)cont_noRef_agree / cont_noRef_common;
          addWorkerValue(AID,"cont-noRef",cont_noRef);

//          double cont_noRef_Kappa = kappa(cont_noRef , 1.0/3.0);
//          addWorkerValue(AID,"cont-noRef_Kappa",cont_noRef_Kappa);
        }

        outLine = outLine.replaceAll(NaN_str,"N/A");
        outLine = outLine.replaceAll("<td>N/A","<td sorttable_customkey=-1000>N/A");

        outLine += "</tr>";

        writeLine(outLine,outFile_log);

      } // if AID not in contrastive set
    } // for (a1)

    writeLine("</tbody>",outFile_log);

    writeLine("</table>",outFile_log);
    writeLine("<br>",outFile_log);
    writeLine("<br>",outFile_log);
    writeLine("<br>",outFile_log);

//*/

    // calculate rankings
    produceRankings(systemsPerQuestion, true, "cont-noRef_Kappa", -100000, true); // no cleaning; the choice of "cont-noRef_Kappa" as a criterion is arbitrary
                                     // outputOn


    double[] corrVals = null;
/*
    corrVals = produceRankings(systemsPerQuestion, false, "cont-noRef_Kappa", -100000, true); // no cleaning; the choice of "cont-noRef_Kappa" as a criterion is arbitrary
    print("noclean" + "\t" + corrVals[0] + "\t"); for (int i = 1; i < corrVals.length; ++i) { print(f4.format(corrVals[i]) + "\t"); } println("");

    corrVals = produceRankings(systemsPerQuestion, false, "ref_rate_Kappa", -100000, true); // no cleaning; making sure you get same results with another arbitrary criterion
    print("noclean2" + "\t" + corrVals[0] + "\t"); for (int i = 1; i < corrVals.length; ++i) { print(f4.format(corrVals[i]) + "\t"); } println("");

    corrVals = produceRankings(systemsPerQuestion, false, "unweighted-voting", 0.0, false); // unweighted voting
    print("voting" + "\t" + corrVals[0] + "\t"); for (int i = 1; i < corrVals.length; ++i) { print(f4.format(corrVals[i]) + "\t"); } println("");

    corrVals = produceRankings(systemsPerQuestion, false, "ref_rate", 0.0, false); // weighted scoring by ref_rate
    print("ref_rate" + "\t" + corrVals[0] + "\t"); for (int i = 1; i < corrVals.length; ++i) { print(f4.format(corrVals[i]) + "\t"); } println("");

    corrVals = produceRankings(systemsPerQuestion, false, "ref_rate_Kappa", 0.0, false); // weighted scoring by ref_rate_Kappa
    print("ref_rate_Kappa" + "\t" + corrVals[0] + "\t"); for (int i = 1; i < corrVals.length; ++i) { print(f4.format(corrVals[i]) + "\t"); } println("");

    corrVals = produceRankings(systemsPerQuestion, false, "cont-noRef", 0.0, false); // weighted scoring by cont-noRef
    print("cont-noRef" + "\t" + corrVals[0] + "\t"); for (int i = 1; i < corrVals.length; ++i) { print(f4.format(corrVals[i]) + "\t"); } println("");

    corrVals = produceRankings(systemsPerQuestion, false, "cont-noRef_Kappa", 0.0, false); // weighted scoring by cont-noRef_Kappa
    print("cont-noRef_Kappa" + "\t" + corrVals[0] + "\t"); for (int i = 1; i < corrVals.length; ++i) { print(f4.format(corrVals[i]) + "\t"); } println("");
*/
/*
    println("cont-noRef_Kappa filtering:");
    for (double cutoff : observedWorkerValues.get("cont-noRef_Kappa")) {
      corrVals = produceRankings(systemsPerQuestion, false, "cont-noRef_Kappa", cutoff, true); // filtering by low cont-noRef_Kappa
      print(cutoff + "\t" + corrVals[0] + "\t"); for (int i = 1; i < corrVals.length; ++i) { print(f4.format(corrVals[i]) + "\t"); } println("");
    }

    println("ref_rate filtering:");
    for (double cutoff : observedWorkerValues.get("ref_rate")) {
      corrVals = produceRankings(systemsPerQuestion, false, "ref_rate", cutoff, true); // filtering by low ref_rate_Kappa
      print(cutoff + "\t" + corrVals[0] + "\t"); for (int i = 1; i < corrVals.length; ++i) { print(f4.format(corrVals[i]) + "\t"); } println("");
    }
*/

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in analyzeResults_RNK(...): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in analyzeResults_RNK(...): " + e.getMessage());
      System.exit(99902);
    }


  }

static private boolean isDesired(Comparison C)
{

return true;

/*
// indiv vs. indiv
if (C.firstSystem.contains("combo") || C.secondSystem.contains("combo")) return false;
else return true;
//*/

/*
// combo vs. combo
if ((C.firstSystem.contains("combo") || C.firstSystem.contains("_ref")) && (C.secondSystem.contains("combo") || C.secondSystem.contains("_ref"))) return true;
else return false;
//*/

/*
// indiv vs. combo
if (C.firstSystem.contains("_ref") || C.secondSystem.contains("_ref")) return false;
if (C.firstSystem.contains("combo")) {
  return (!C.secondSystem.contains("combo"));
} else {
  return (C.secondSystem.contains("combo"));
}
//*/

/*
// exclude rose
if (C.firstSystem.contains("rose") || C.secondSystem.contains("rose")) return false;
else return true;
//*/
}

static private double th_smoothed(double r, double t, double sigSq)
{
  double m = t - r;
  double fac = 1.0 / sigSq;
  return root(r,m,fac);
}

static private double root(double r, double m, double fac)
{
  double best_th = 0.0;
  double best_f = Math.abs(f_toRoot(0.0,r,m,fac));

  for (double th = 0.0; th <= 1.0; th += 0.01) {
    double f = f_toRoot(th,r,m,fac);
    if (Math.abs(f) < best_f) {
      best_th = th;
      best_f = f;
    }
  }

  return best_th;

}

static private double f_toRoot(double th, double r, double m, double fac)
{
  return r - r*th - m*th - fac*(0.5*th - 1.5*th*th + th*th*th);
}

private static void addWorkerValue(String AID, String criterion, double value)
{
          TreeMap<String,Double> m = null;
          if (workerValue.containsKey(AID)) { m = workerValue.get(AID); }
          else m = new TreeMap<String,Double>();

          m.put(criterion,value);
          workerValue.put(AID,m);

          TreeSet<Double> v = null;
          if (observedWorkerValues.containsKey(criterion)) v = observedWorkerValues.get(criterion);
          else v = new TreeSet<Double>();

          v.add(value);
          observedWorkerValues.put(criterion,v);
}

private static double[] produceRankings(int systemsPerQuestion, boolean outputOn, String criterion, double cutoff, boolean filter) throws Exception
{
    // returns correlation values, three per language pair
/*
    String[] LP = {"en-cz","en-de","en-es","en-fr","cz-en","de-en","es-en","fr-en"};

    Vector<String> seenLangPairs = new Vector<String>();
    for (int p = 0; p < LP.length; ++p) {
      seenLangPairs.add(LP[p]);
    }

    int LPcount = LP.length;
*/

    int LPcount = seenLangPairs.size();

    double[] corrVals = new double[1+(3*LPcount)]; // [0] returns how many workers were removed for not making cutoff
    TreeSet<String> removedWorkers = new TreeSet<String>();

    int corrVals_i = 1;

//    for (int p = 0; p < langPairs.length; ++p) {
    for (String langPair : seenLangPairs) {

      if (true) {

        String srclang = langPair.split("-")[0];
        String trglang = langPair.split("-")[1];

        if (true) {
//          String langPair = langPairs[p];

          String outLine = "";
          if (outputOn) {
            writeLine(otherName.get(srclang) + "-to-" + otherName.get(trglang) + " ranking: (the table is sortable; simply click on the column header to sort)<br>",outFile_log);
            writeLine("<table class=\"sortable\" border=1>",outFile_log);

            outLine = "";
            outLine += "<thead>";
            outLine += "<tr>";
            outLine += "<th align=left>" + "srclang" + "</th>";
            outLine += "<th align=left>" + "trglang" + "</th>";
            outLine += "<th align=left>" + "sysName" + "</th>";
            outLine += "<th>" + "# appearances" + "</th>";
            outLine += "<th>" + "# comparisons" + "</th>";
            outLine += "<th>" + "expected # comparisons" + "</th>";
            outLine += "<th>" + "# blank comparisons" + "</th>";
            outLine += "<th>" + "completion rate (%)" + "</th>";
            outLine += "<th>" + "absolute score2" + "</th>";
            outLine += "<th>" + "absolute score" + "</th>";
            outLine += "<th>" + "normalized score2" + "</th>";
            outLine += "<th>" + "normalized score" + "</th>";
            outLine += "</tr>";
            outLine += "</thead>";
            writeLine(outLine,outFile_log);
          }

          double langPair_sysCount = 0;
          double langPair_appearanceCount = 0;
          double langPair_comparisonCount = 0;
          double langPair_expectedComparisonCount = 0;

TreeMap<String,Double> rankMap = new TreeMap<String,Double>();
  // maps system name to its rank, according to normalized score
TreeMap<String,Double> scoreMap = new TreeMap<String,Double>();
  // maps system name to its normalized score
TreeMap<Double,Vector<String>> normalizedScore = new TreeMap<Double,Vector<String>>();
  // maps normalized score to systems getting that score
  // (this is done to automatically obtain the ranking, without having to sort by score at the end)

TreeMap<String,String> sysName_from_sysInfo = new TreeMap<String,String>();
  // for convenience

          for (String subName : submissionComparisons.keySet()) {
            if (subName.startsWith(langPair)) {

              Vector<Comparison> subCV = submissionComparisons.get(subName);
              if (criterion.equals("unweighted-voting")) {
                subCV = unweightedVote(subCV);
              }
              double appearanceCount = submissionAppearanceCount.get(subName);
              double comparisonCount = 0;
              double expectedComparisonCount = (systemsPerQuestion-1)*submissionAppearanceCount.get(subName);

              double subScore = 0.0; // includes ties
              double subScore2 = 0.0; // does *not* include ties
              for (Comparison C : subCV) {
                if (criterion.equals("unweighted-voting") || !workerValue.containsKey(C.AID) || !workerValue.get(C.AID).containsKey(criterion)) {
                  subScore += C.sysScore(subName);
                  subScore2 += C.sysScore2(subName);
                  comparisonCount += 1;
                } else if (workerValue.get(C.AID).get(criterion) >= cutoff) {
                  double weight = 1.0;
                  if (!filter) weight = workerValue.get(C.AID).get(criterion);

                  subScore += weight*C.sysScore(subName);
                  subScore2 += weight*C.sysScore2(subName);
                  comparisonCount += weight;
                } else {
                  removedWorkers.add(C.AID);
                }
              }

              double compRate = 100 * ((double)comparisonCount/expectedComparisonCount);

/*
              outLine = "";
              outLine += "\t" + otherName.get(srclang);
              outLine += "\t" + otherName.get(trglang);
              outLine += "\t" + subName.substring(langPair.length() + 1); // system name; +1 to also skip " "
              outLine += "\t" + appearanceCount;
              outLine += "\t" + comparisonCount;
              outLine += "\t" + expectedComparisonCount;
              outLine += "\t" + (expectedComparisonCount - comparisonCount); // # blank comparisons
              outLine += "\t" + f2.format(compRate);
              outLine += "\t" + subScore2;
              outLine += "\t" + subScore;
              outLine += "\t" + f4.format(subScore2/comparisonCount);
              outLine += "\t" + f4.format(subScore/comparisonCount);

              if (outputOn) writeLine(outLine.substring(1),outFile_log);
*/

String sysName = subName.substring(langPair.length() + 1); // system name; +1 to also skip " "
double normScore = -(subScore/comparisonCount); // negative to sort it in descending order

scoreMap.put(sysName,-normScore);

              String sysInfo = "";
              sysInfo += "<td align=left sorttable_customkey=" + (sysName.equals("_ref")? "A000" : sysName) + "><b>" + sysName + "</b></td>";
              sysInfo += "<td>" + (int)appearanceCount + "</td>";
              sysInfo += "<td>" + (int)comparisonCount + "</td>";
              sysInfo += "<td>" + (int)expectedComparisonCount + "</td>";
              sysInfo += "<td>" + (int)(expectedComparisonCount - comparisonCount) + "</td>"; // # blank comparisons
              sysInfo += "<td>" + f2.format(compRate) + "</td>";
              sysInfo += "<td>" + (int)subScore2 + "</td>";
              sysInfo += "<td>" + (int)subScore + "</td>";
              sysInfo += "<td>" + f4.format(subScore2/comparisonCount) + "</td>";

sysName_from_sysInfo.put(sysInfo,sysName);

Vector<String> V = null;
if (normalizedScore.containsKey(normScore)) V = normalizedScore.get(normScore);
else V = new Vector<String>();

V.add(sysInfo);
normalizedScore.put(normScore,V);

              ++langPair_sysCount;
              langPair_appearanceCount += appearanceCount;
              langPair_comparisonCount += comparisonCount;
              langPair_expectedComparisonCount += expectedComparisonCount;

            }
          } // for (subName)

          if (outputOn) writeLine("<tbody align=center>",outFile_log);

          double seenSubs = 0;
          for (Double normScore : normalizedScore.keySet()) {
            Vector<String> V = normalizedScore.get(normScore);

            double rankInc = (V.size() + 1.0) / 2.0;
            double rank = seenSubs + rankInc;

            for (String sysInfo : V) {
              outLine = "";
              outLine += "<tr>";
              outLine += "<td align=left>" + otherName.get(srclang) + "</td>";
              outLine += "<td align=left>" + otherName.get(trglang) + "</td>";
              outLine += sysInfo; // already has <td> and </td>
              outLine += "<td><b>" + f4.format(-normScore) + "</b></td>";
              outLine += "</tr>";

              if (outputOn) writeLine(outLine,outFile_log);

              rankMap.put(sysName_from_sysInfo.get(sysInfo),rank);
            }

            seenSubs += V.size();
          }

          if (outputOn) {

            writeLine("<tfoot align=center>",outFile_log);

            outLine = "";
            outLine += "<tr>";
            outLine += "<td>" + otherName.get(srclang) + "</td>";
            outLine += "<td>" + otherName.get(trglang) + "</td>";
            outLine += "<td>" + "SUM (" + (int)langPair_sysCount + " systems)" + "</td>";
            outLine += "<td>" + (int)langPair_appearanceCount + "</td>";
            outLine += "<td>" + (int)langPair_comparisonCount + "</td>";
            outLine += "<td>" + (int)langPair_expectedComparisonCount + "</td>";
            outLine += "<td>" + (int)(langPair_expectedComparisonCount - langPair_comparisonCount) + "</td>"; // # blank comparisons
            double langPair_compRate = 100 * ((double)langPair_comparisonCount/langPair_expectedComparisonCount);
            outLine += "<td>" + f2.format(langPair_compRate) + "</td>";
            outLine += "<td>" + "" + "</td>";
            outLine += "<td>" + "" + "</td>";
            outLine += "</tr>";

            outLine = outLine.replaceAll(NaN_str,"N/A");

            writeLine(outLine,outFile_log);


            outLine = "";
            outLine += "<tr>";
            outLine += "<td>" + otherName.get(srclang) + "</td>";
            outLine += "<td>" + otherName.get(trglang) + "</td>";
            outLine += "<td>" + "AVE (" + (int)langPair_sysCount + " systems)" + "</td>";
            outLine += "<td>" + f2.format(langPair_appearanceCount/(double)langPair_sysCount) + "</td>";
            outLine += "<td>" + f2.format(langPair_comparisonCount/(double)langPair_sysCount) + "</td>";
            outLine += "<td>" + "" + "</td>";
            outLine += "<td>" + "" + "</td>";
            outLine += "<td>" + "" + "</td>";
            outLine += "<td>" + "" + "</td>";
            outLine += "<td>" + "" + "</td>";
            outLine += "</tr>";

            outLine = outLine.replaceAll(NaN_str,"N/A");

            writeLine(outLine,outFile_log);

            writeLine("</tfoot>",outFile_log);

          }

          writeLine("</tbody>",outFile_log);
          writeLine("</table>",outFile_log);
          writeLine("<br>",outFile_log);

          scoreMap.remove("_ref");
          rankMap.remove("_ref");




          double pearson = 0.0, spearman = 0.0, kendall = 0.0;
/*
          if (false) {}
          else if (langPair.equals("en-cz")) { pearson = correlation(scoreMap,score__en_cz__wmt); }
          else if (langPair.equals("cz-en")) { pearson = correlation(scoreMap,score__cz_en__wmt); }

          if (false) {}
          else if (langPair.equals("en-cz")) { spearman = correlation(rankMap,rank__en_cz__wmt); }
          else if (langPair.equals("cz-en")) { spearman = correlation(rankMap,rank__cz_en__wmt); }

          if (false) {}
          else if (langPair.equals("en-cz")) { kendall = tau(rankMap,rank__en_cz__wmt); }
          else if (langPair.equals("cz-en")) { kendall = tau(rankMap,rank__cz_en__wmt); }
*/
          corrVals[corrVals_i + 0] = pearson;
          corrVals[corrVals_i + 1] = spearman;
          corrVals[corrVals_i + 2] = kendall;
          corrVals_i += 3;
/*
          if (outputOn) {
            writeLine("Pearson correlation with wmt data (without the _ref score): " + pearson,outFile_log);
            writeLine("Spearman rank correlation with wmt data (without the _ref score): " + spearman,outFile_log);
            writeLine("Kendall's tau with wmt data (without the _ref score): " + kendall,outFile_log);
            writeLine("",outFile_log);
          }
*/

if (outputOn) {
          //////////////////
          // HEAD-TO-HEAD //
          //////////////////

          writeLine(otherName.get(srclang) + "-to-" + otherName.get(trglang) + " head-to-head: (the table is sortable; simply click on the column header to sort)<br>",outFile_log);
          writeLine("(value is how often system in column header beat system in row; # indicates significance at 0.01 level, + at 0.05 level, and * at 0.10 level)<br>",outFile_log);

          if (h2h_format != 3) {
            writeLine("<table border=1>",outFile_log);
            writeLine("<tbody align=center>",outFile_log);
          } else {
            writeLine("\\begin{table}[h]",outFile_log);
            writeLine("\\begin{center}",outFile_log);
            writeLine("\\scriptsize",outFile_log);

            // column headers
            int sysCount = 0;
            for (String subName1 : submissionComparisons.keySet()) { if (subName1.startsWith(langPair)) ++sysCount; }
            outLine = "";
            outLine += "\\begin{tabular}{r";
            for (int s = 1; s <= sysCount; ++s) { outLine += "p{2mm}"; }
            outLine += "}";
            writeLine(outLine,outFile_log);
          }

          // names in column
          outLine = "";
          for (String subName2 : submissionComparisons.keySet()) {
            if (subName2.startsWith(langPair) && !subName2.contains("combo") && subName2.contains("_ref")) { // reference
              outLine += "\t" + "\\sysNameTop{" + subName2.substring(langPair.length() + 1) + "}";
            }
          }
          for (String subName2 : submissionComparisons.keySet()) {
            if (subName2.startsWith(langPair) && !subName2.contains("combo") && !subName2.contains("_ref")) { // individual systems (minus reference)
              outLine += "\t" + "\\sysNameTop{" + subName2.substring(langPair.length() + 1) + "}";
            }
          }
          for (String subName2 : submissionComparisons.keySet()) {
            if (subName2.startsWith(langPair) && subName2.contains("combo")) { // combo systems
              outLine += "\t" + "\\sysNameTop{" + subName2.substring(langPair.length() + 1) + "}";
            }
          }

          if (h2h_format != 3) {
            outLine = outLine.replaceAll("\\\\sysNameTop\\{","<b>");
            outLine = outLine.replaceAll("}","</b>");
            outLine = outLine.replaceAll("\\t","</td><td align=left>");
            outLine = "<tr><td>" + outLine + "</td></tr>";
          } else {
            outLine = outLine.replaceAll("\\t"," & ");
            outLine = outLine.replaceAll("\\{_ref\\}","\\{ref\\}");
            outLine += " \\\\";
          }

          writeLine(outLine,outFile_log);

          // reference row
          for (String subName1 : submissionComparisons.keySet()) { // name in row
            if (subName1.startsWith(langPair) && subName1.contains("_ref")) {
              outLine = "";
              outLine += "\\sysName{" + subName1.substring(langPair.length() + 1) + "}";

              for (String subName2 : submissionComparisons.keySet()) { // name in column
                if (subName2.startsWith(langPair) && !subName2.contains("combo") && subName2.contains("_ref")) {
                  outLine += "\t" + winPctStr(subName1,subName2);
                }
              } // for (subName2) over reference

              for (String subName2 : submissionComparisons.keySet()) { // name in column
                if (subName2.startsWith(langPair) && !subName2.contains("combo") && !subName2.contains("_ref")) {
                  outLine += "\t" + winPctStr(subName1,subName2);
                }
              } // for (subName2) over individual systems (minus reference)

              for (String subName2 : submissionComparisons.keySet()) { // name in column
                if (subName2.startsWith(langPair) && subName2.contains("combo")) {
                  outLine += "\t" + winPctStr(subName1,subName2);
                }
              } // for (subName2) over combo systems

              if (h2h_format != 3) {
                outLine = outLine.replaceAll("\\\\sysName\\{","<b>");
                outLine = outLine.replaceAll("}","</b>");
                outLine = outLine.replaceAll("\\t","</td><td>");
                outLine = "<tr><td align=left>" + outLine + "</td></tr>";
              } else {
                outLine = outLine.replaceAll("\\t"," & ");
                outLine = outLine.replaceAll("\\{_ref\\}","\\{ref\\}");
                outLine += " \\\\";
              }
              writeLine(outLine,outFile_log);

            }
          } // for (subName1) over reference

          // individual system rows
          for (String subName1 : submissionComparisons.keySet()) { // name in row
            if (subName1.startsWith(langPair) && !subName1.contains("combo") && !subName1.contains("_ref")) {
              outLine = "";
              outLine += "\\sysName{" + subName1.substring(langPair.length() + 1) + "}";

              for (String subName2 : submissionComparisons.keySet()) { // name in column
                if (subName2.startsWith(langPair) && !subName2.contains("combo") && subName2.contains("_ref")) {
                  outLine += "\t" + winPctStr(subName1,subName2);
                }
              } // for (subName2) over reference

              for (String subName2 : submissionComparisons.keySet()) { // name in column
                if (subName2.startsWith(langPair) && !subName2.contains("combo") && !subName2.contains("_ref")) {
                  outLine += "\t" + winPctStr(subName1,subName2);
                }
              } // for (subName2) over individual systems (minus reference)

              for (String subName2 : submissionComparisons.keySet()) { // name in column
                if (subName2.startsWith(langPair) && subName2.contains("combo")) {
                  outLine += "\t" + winPctStr(subName1,subName2);
                }
              } // for (subName2) over combo systems

              if (h2h_format != 3) {
                outLine = outLine.replaceAll("\\\\sysName\\{","<b>");
                outLine = outLine.replaceAll("}","</b>");
                outLine = outLine.replaceAll("\\t","</td><td>");
                outLine = "<tr><td align=left>" + outLine + "</td></tr>";
              } else {
                outLine = outLine.replaceAll("\\t"," & ");
                outLine = outLine.replaceAll("\\{_ref\\}","\\{ref\\}");
                outLine += " \\\\";
              }
              writeLine(outLine,outFile_log);

            }
          } // for (subName1) over individual systems (minus reference)

          // combo system rows
          for (String subName1 : submissionComparisons.keySet()) { // name in row
            if (subName1.startsWith(langPair) && subName1.contains("combo")) {
              outLine = "";
              outLine += "\\sysName{" + subName1.substring(langPair.length() + 1) + "}";

              for (String subName2 : submissionComparisons.keySet()) { // name in column
                if (subName2.startsWith(langPair) && !subName2.contains("combo") && subName2.contains("_ref")) {
                  outLine += "\t" + winPctStr(subName1,subName2);
                }
              } // for (subName2) over reference

              for (String subName2 : submissionComparisons.keySet()) { // name in column
                if (subName2.startsWith(langPair) && !subName2.contains("combo") && !subName2.contains("_ref")) {
                  outLine += "\t" + winPctStr(subName1,subName2);
                }
              } // for (subName2) over individual systems (minus reference)

              for (String subName2 : submissionComparisons.keySet()) { // name in column
                if (subName2.startsWith(langPair) && subName2.contains("combo")) {
                  outLine += "\t" + winPctStr(subName1,subName2);
                }
              } // for (subName2) over combo systems

              if (h2h_format != 3) {
                outLine = outLine.replaceAll("\\\\sysName\\{","<b>");
                outLine = outLine.replaceAll("}","</b>");
                outLine = outLine.replaceAll("\\t","</td><td>");
                outLine = "<tr><td align=left>" + outLine + "</td></tr>";
              } else {
                outLine = outLine.replaceAll("\\t"," & ");
                outLine = outLine.replaceAll("\\{_ref\\}","\\{ref\\}");
                outLine += " \\\\";
              }
              writeLine(outLine,outFile_log);

            }
          } // for (subName1) over combo systems

          if (h2h_format != 3) {
            writeLine("</table>",outFile_log);
          } else {
            writeLine("\\hline",outFile_log);
            writeLine("$>$ others \\\\",outFile_log);
            writeLine("$>=$ others \\\\",outFile_log);
            writeLine("\\\\",outFile_log);
            writeLine("\\end{tabular}",outFile_log);
            writeLine("\\normalsize",outFile_log);
            writeLine("\\caption{Ranking scores for entries in the "+otherName.get(srclang)+"-"+otherName.get(trglang)+" task.}",outFile_log);
            writeLine("\\end{center}",outFile_log);
            writeLine("\\end{table}",outFile_log);
          }

          writeLine("<br>",outFile_log);
          writeLine("<br>",outFile_log);
} // if (outputOn)

        } // if langPair is valid

      } // for (trgL)

    } // for (srcL)

    if (outputOn) writeLine("",outFile_log);

    corrVals[0] = removedWorkers.size();

    return corrVals;

}



  static private Vector<Comparison> unweightedVote(Vector<Comparison> subCV)
  {

    TreeMap<String,Vector<Comparison>> subCV_groupedByKey = new TreeMap<String,Vector<Comparison>>();

    for (Comparison C : subCV) {
      String key = C.docid + " " + C.segid + " " + C.firstSystem + " " + C.secondSystem;
      Vector<Comparison> V = null;
      if (subCV_groupedByKey.containsKey(key)) V = subCV_groupedByKey.get(key);
      else V = new Vector<Comparison>();

      V.add(C);
      subCV_groupedByKey.put(key,V);
    }

    Vector<Comparison> new_subCV = new Vector<Comparison>();

    for (String key : subCV_groupedByKey.keySet()) {
      Vector<Comparison> group = subCV_groupedByKey.get(key);

      String docid = "", segid = "", sys1Name = "", sys2Name = "";
      double sys1TotScore = 0, sys2TotScore = 0;

      for (Comparison C : group) {
        if (sys1Name.equals("")) { docid = C.docid; segid = C.segid; sys1Name = C.firstSystem; sys2Name = C.secondSystem; }
        sys1TotScore += C.sysScore(sys1Name);
        sys2TotScore += C.sysScore(sys2Name);
      }

      Comparison C_new = new Comparison();
      C_new.AID = "";
      C_new.docid = docid;
      C_new.segid = segid;
      C_new.firstSystem = sys1Name;
      C_new.secondSystem = sys2Name;
      C_new.setRefLocation();
      C_new.isContrastive = false;

      if (sys1TotScore > sys2TotScore) {
        C_new.firstRank = 1;
        C_new.secondRank = 5;
      } else if (sys1TotScore < sys2TotScore) {
        C_new.firstRank = 5;
        C_new.secondRank = 1;
      } else {
        C_new.firstRank = 3;
        C_new.secondRank = 3;
      }

      C_new.orderSystems();

      new_subCV.add(C_new);

    }

    return new_subCV;

  }

  static private String winPctStr(String subName1, String subName2) {
                  // calculate: % subName2 beats subName1

if (subName1.equals(subName2)) { return "--"; }

                  Vector<Comparison> sub1CV = submissionComparisons.get(subName1);

                  int pairCompCount = 0;
                  int sub2WinCount = 0;
                  int tieCount = 0;
                  int sub2LossCount = 0;
                  for (Comparison C : sub1CV) {
                    if (   (C.firstSystem.equals(subName1) && C.secondSystem.equals(subName2))
                        || (C.firstSystem.equals(subName2) && C.secondSystem.equals(subName1))) { 
                      ++pairCompCount;
                      String sub2_res = C.sysResult(subName2);
                      if (sub2_res.equals("win")) ++sub2WinCount;
                      else if (sub2_res.equals("tie")) ++tieCount;
                      else if (sub2_res.equals("loss")) ++sub2LossCount;
                    }
                  } // for (C)

                  if (pairCompCount == 0) return "N/A";

//                  print("\t" + sub2WinCount+"_"+tieCount+"_"+sub2LossCount+"/"+pairCompCount);
//                  print("\t" + f2.format((double)sub2WinCount/pairCompCount));

                  String winPct = f2.format((double)sub2WinCount/pairCompCount);

                  if (h2h_format == 1) {
                    winPct = winPct + "(" + sub2WinCount + "/" + pairCompCount + "; ties=" + tieCount + ")";
                  }

                  if (winPct.charAt(0) == '0') winPct = winPct.substring(1);
                                               // substring(1) to remove the leading '0', e.g. ".44"

                  int pairCompCount_noTies = sub2WinCount + sub2LossCount;

                  if (h2h_format == 2) { // human-readable
                    if (significant(sub2WinCount,pairCompCount_noTies,0.01)) winPct += "#";
                    else if (significant(sub2WinCount,pairCompCount_noTies,0.05)) winPct += "+";
                    else if (significant(sub2WinCount,pairCompCount_noTies,0.10)) winPct += "*";

                    if (sub2WinCount > sub2LossCount) winPct = "w" + winPct;
                  } else if (h2h_format == 3) { // for LaTeX
                    if (significant(sub2WinCount,pairCompCount_noTies,0.01)) winPct += "$^\\ddagger$";
                    else if (significant(sub2WinCount,pairCompCount_noTies,0.05)) winPct += "$^\\dagger$";
                    else if (significant(sub2WinCount,pairCompCount_noTies,0.10)) winPct += "$^\\star$";

                    if (sub2WinCount > sub2LossCount) winPct = "\\wins{" + winPct + "}";
                  }

                  return winPct;
  }
/*
  static private void analyzeResults_EDT(String outFileName, String sep, String indicator, int questionsPerScreen, int systemsPerQuestion)
  {

    TreeMap<Pair< Pair<String,String> , Pair<String,String> >,Vector<EDTAnswers>> segEDTAnswers = new TreeMap<Pair< Pair<String,String> , Pair<String,String> >,Vector<EDTAnswers>>();
      // maps segName to a Vector of EDT answers
      // segName is a Pair of <langPair,segid>, where langPair is a <src,trg> Pair, and segid is a <docid,segid> Pair
      // e.g. < <"es","en"> , <"lidovky.cz/2009/12/10/75519","6"> >

    try {

      FileOutputStream outStream = new FileOutputStream(outFileName, false);
      OutputStreamWriter outStreamWriter = new OutputStreamWriter(outStream, "utf8");
      BufferedWriter outFile = new BufferedWriter(outStreamWriter);

      String outLine = "";

      outLine += sep + "editId";

      outLine += sep + "srclang";
      outLine += sep + "trglang";

      outLine += sep + "srcIndex";
      outLine += sep + "documentId";
      outLine += sep + "segmentId";
      outLine += sep + "editorId";

      for (int sys = 1; sys <= systemsPerQuestion; ++sys) {
        outLine += sep + "system" + sys + "Number";
        outLine += sep + "system" + sys + "Id";
      }

      for (int sys = 1; sys <= systemsPerQuestion; ++sys) {
        outLine += sep + "system" + sys + "actionVal";
      }

      for (int sys = 1; sys <= systemsPerQuestion; ++sys) {
        outLine += sep + "system" + sys + "actionStr";
      }

      for (int sys = 1; sys <= systemsPerQuestion; ++sys) {
        outLine += sep + "system" + sys + "edit";
      }

      outLine = outLine.substring(1); // remove initial comma
      writeLine(outLine,outFile);

      int currId = 0; // incremented just before processing an edit

      for (TreeMap<String,String> ans : answers) {
        if ((ans.get("HIT_info")).contains(collectionID) && (ans.get("HIT_info")).contains(indicator)) {

          for (int q = 1; q <= questionsPerScreen; ++q) {

            String sen_i = ans.get("SrcIndex" + q);

            if (sentenceMap.get(sen_i) == null) {

              // ****** RECOVER SrcIndex* and System*_* USING AMT upload INPUT FILES ******

              String AID = ans.get("AID");
              String task = "EDT";

              String HIT_info = ans.get("HIT_info");
                // e.g. "worker:A23Q0IA9ISEC3M#task:RNK#langs:es-en#batch:1#HIT:17"

              String langInfo = HIT_info.split("#")[2]; // e.g. "langs:es-en"
              String langPair = langInfo.substring(6); // e.g. "es-en"
              String batchInfo = HIT_info.split("#")[3]; // e.g. "batch:1"
              int batchNumber = Integer.parseInt(batchInfo.substring(6)); // e.g. "1"
              String HITOrderInfo = HIT_info.split("#")[4]; // e.g. "HIT:17"
              int HITOrder = Integer.parseInt(HITOrderInfo.substring(4)); // e.g. "17"

              String uploadFileName = AID + "." + task + "." + langPair + ".batch" + batchNumber + ".input";
                // e.g. "A23Q0IA9ISEC3M.RNK.es-en.batch1.input"
              uploadFileName = fullPath(uploadFilesDir,uploadFileName);

              if (fileExists(uploadFileName)) {
                InputStream inStream = new FileInputStream(new File(uploadFileName));
                BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

                TreeMap<String,Integer> colTitleMap = new TreeMap<String,Integer>();
                String line = inFile.readLine();
                String[] colTitles = line.split("\t");
                for (int i = 0; i < colTitles.length; ++i) {
                  colTitleMap.put(colTitles[i],i);
                }

                line = inFile.readLine();
                while (line != null) {
                  String[] entries = line.split("\t");
                  if (("\"" + HIT_info + "\"").equals(entries[colTitleMap.get("\"HIT_info\"")])) {

                    for (int qr = 1; qr <= questionsPerScreen; ++qr) {

                      String ir = entries[colTitleMap.get("\"srcIndex" + qr + "\"")];
                      ir = ir.substring(1,ir.length()-1); // remove quotes
                      ans.put("SrcIndex" + qr, ir);

                      for (int sysr = 1; sysr <= systemsPerQuestion; ++sysr) {

                        String sr = entries[colTitleMap.get("\"sys" + qr + "_" + sysr + "\"")];
                        sr = sr.substring(1,sr.length()-1); // remove quotes
                        ans.put("System" + qr + "_" + sysr, sr);

                      } // for (sysr)

                    } // for (qr)

                    break; // from while (line != null) loop

                  } // if HIT_info match

                  line = inFile.readLine();

                } // while (line != null)

                inFile.close();

              } // if upload file exists

            } // if (sentenceMap.get(sen_i) == null)


            sen_i = ans.get("SrcIndex" + q); // try again

            if (sentenceMap.get(sen_i) == null) { // couldn't recover

              println("    Couldn't recover " + ans.get("HIT_info"));

            } else {
              outLine = "";

              ++currId;

              String langInfo = (ans.get("HIT_info")).split("#")[2];
                // e.g. "langs:es-en"
              String langPair = langInfo.substring(6);
                // e.g. "es-en"
              String AID = ans.get("AID");
              boolean isContrastive = false;
              if (ans.get("isContrastive").equals("true")) isContrastive = true;

              String srclang = otherName.get(langPair.split("-")[0]);
              String trglang = otherName.get(langPair.split("-")[1]);
              String[] senInfo = sentenceMap.get(sen_i);
              String docid = senInfo[0];
              String segid = senInfo[1];

              outLine += sep + currId;

              outLine += sep + srclang;
              outLine += sep + trglang;

              outLine += sep + sen_i;
              outLine += sep + docid;
              outLine += sep + segid;

              outLine += sep + AID;

              String[] systemNames = new String[1+systemsPerQuestion];
              int[] systemNumbers = new int[1+systemsPerQuestion];
              int[] systemActions = new int[1+systemsPerQuestion];
              String[] systemEdits = new String[1+systemsPerQuestion];

              for (int sys = 1; sys <= systemsPerQuestion; ++sys) {
                String sysNumber = ans.get("System" + q + "_" + sys);
                String sysName = systemAtNumber.get(sysNumber);
                systemNames[sys] = langPair + " " + sysName;
                systemNumbers[sys] = Integer.parseInt(sysNumber);
                incrementMapCount(submissionAppearanceCount,langPair + " " + sysName);
                outLine += sep + sysNumber;
                outLine += sep + sysName;
              }

              for (int sys = 1; sys <= systemsPerQuestion; ++sys) {
                String sysAction_str = ans.get("EditAction" + q + "_" + sys);
                int sysAction = 0;
                if (sysAction_str != null) { sysAction = Integer.parseInt(sysAction_str); }
                systemActions[sys] = sysAction;
                outLine += sep + sysAction;
                switch (sysAction) {
                  case 0: outLine += sep + "N/A"; break;
                  case 1: outLine += sep + "EDIT"; break;
                  case 2: outLine += sep + "OK"; break;
                  case 3: outLine += sep + "BAD"; break;
                }
              }

              for (int sys = 1; sys <= systemsPerQuestion; ++sys) {
                String sysEdit = ans.get("edit" + q + "_" + sys);
                systemEdits[sys] = sysEdit;
                outLine += sep + sysEdit;
              }



              for (int sys = 1; sys <= systemsPerQuestion; ++sys) {
                String sysNumber = ans.get("System" + q + "_" + sys);
                String subName = langPair + " " + systemAtNumber.get(sysNumber);
                String sysAction_str = ans.get("EditAction" + q + "_" + sys);
                int sysAction = 0;
                if (sysAction_str != null) { sysAction = Integer.parseInt(sysAction_str); }

                Vector V = null;
                if (submissionActions.containsKey(subName)) { V = submissionActions.get(subName); }
                else { V = new Vector<Integer>(); }
                V.add(sysAction);
                submissionActions.put(subName,V);
              }


              Pair< Pair<String,String> , Pair<String,String> > segName = new Pair< Pair<String,String> , Pair<String,String> >();
                // e.g. < <es,en> , <"lidovky.cz/2009/12/10/75519","6"> >
              segName.first = new Pair<String,String>(); // e.g. <"es","en">
              segName.second = new Pair<String,String>(); // e.g. <"lidovky.cz/2009/12/10/75519","6">

              (segName.first).first = srclang;
              (segName.first).second = trglang;
              (segName.second).first = docid;
              (segName.second).second = segid;

              EDTAnswers segAnswers = new EDTAnswers(AID,isContrastive,currId,Integer.parseInt(sen_i),docid,segid,systemsPerQuestion,systemNames,systemNumbers,systemActions,systemEdits);
              Vector<EDTAnswers> segAnsVec = null;
              if (segEDTAnswers.containsKey(segName)) { segAnsVec = segEDTAnswers.get(segName); }
              else { segAnsVec = new Vector<EDTAnswers>(); }

              segAnsVec.add(segAnswers);
              segEDTAnswers.put(segName,segAnsVec);

              outLine = outLine.substring(1); // remove initial comma
              writeLine(outLine,outFile);

            }

          } // for (q)

        } // if (HIT_info contains collectionID and indicator)
      } // for (ans)

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in analyzeResults_EDT(...): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in analyzeResults_EDT(...): " + e.getMessage());
      System.exit(99902);
    }

    println("Edit summaries:");
    println("submission" + "\t" + "editCount" + "\t" + "ave_actionVal" + "\t" + "% unjudged" + "\t" + "% EDIT" + "\t" + "% OK" + "\t" + "% BAD");
    for (String subName : submissionActions.keySet()) {

      // in the following, 3 is the # possible actions
      double[] summary = editActionsSummary(submissionActions.get(subName),3);

      print(subName + "\t" + (int)(summary[3+1]));
      print("\t" + f2.format(summary[3+2]));
      for (int a = 0; a <= 3; ++a) { print("\t" + f2.format(summary[a])); }
      println("");

    }

    // info
    for (Pair< Pair<String,String> , Pair<String,String> > segName : segEDTAnswers.keySet()) {
      String segName_str = segName.toString();
      Vector<EDTAnswers> segAnsVec = segEDTAnswers.get(segName);
      println(segName_str + "\t" + segAnsVec.size());
    } // for (segName)


    ////////////////////////////
    // create AMT upload file //
    ////////////////////////////

    int SPS = 2; // sentences per screen
    int OPS = 5; // (maximum) outputs per sentence
    int PPO = 4; // parts per output

    // print column titles

    String outLine = "";
    outLine += "\t" + "\"" + "HIT_info" + "\"";
    outLine += "\t" + "\"" + "fromLang" + "\"";
    outLine += "\t" + "\"" + "toLang" + "\"";

    for (int si = 1; si <= SPS; ++si) {
      outLine += "\t" + "\"" + "srcIndex" + si + "\"";
      outLine += "\t" + "\"" + "numSystems" + si + "\"";
      for (int oi = 1; oi <= OPS; ++oi) {
        outLine += "\t" + "\"" + "sys" + si + "_" + oi + "\"";
        outLine += "\t" + "\"" + "edId" + si + "_" + oi + "\"";
        for (int p = 1; p <= PPO; ++p) {
          outLine += "\t" + "\"" + "sen" + si + "_" + oi + "_" + "part" + p + "\"";
        }
      }
    }

    String srcLang = "es", trgLang = "en", langPairId = "<Spanish|||English>";
    int batchNumber = 3;

    TreeMap<Pair< Pair<String,String> , Pair<String,String> >,Vector<EDTAnswers>> segEDTAnswers_langPair = new TreeMap<Pair< Pair<String,String> , Pair<String,String> >,Vector<EDTAnswers>>();

    for (Pair< Pair<String,String> , Pair<String,String> > segName : segEDTAnswers.keySet()) {
      if (segName.first.toString().equals(langPairId)) {
        segEDTAnswers_langPair.put(segName,segEDTAnswers.get(segName));
      }
    }

    try {
      FileOutputStream uploadStream = new FileOutputStream("ACC_"+srcLang+"-"+trgLang+".input", false);
      OutputStreamWriter uploadStreamWriter = new OutputStreamWriter(uploadStream, "utf8");
      BufferedWriter uploadFile = new BufferedWriter(uploadStreamWriter);

      int writtenHITs = 0;
      int nextOrder = 0;

      while (!segEDTAnswers_langPair.isEmpty()) {

        TreeSet<Pair< Pair<String,String> , Pair<String,String> >> keysToProcess = new TreeSet(segEDTAnswers_langPair.keySet());

        for (Pair< Pair<String,String> , Pair<String,String> > segName : keysToProcess) {

          if (nextOrder % SPS == 0) {
            writeLine(outLine.substring(1),uploadFile);
            ++writtenHITs;

            String hitInfo = "worker:" + "A_" + trgLang + "#task:ACC" + "#langs:" + srcLang + "-" + trgLang + "#batch:" + batchNumber + "#HIT:" + writtenHITs;

            outLine = "";
            outLine += "\t" + "\"" + hitInfo + "\"";
            outLine += "\t" + "\"" + srcLang + "\"";
            outLine += "\t" + "\"" + trgLang + "\"";

            nextOrder = 1;

          } else {
            ++nextOrder;
          }

          Vector<EDTAnswers> segAnsVec = segEDTAnswers_langPair.get(segName);
          outLine += "\t" + "\"" + (segAnsVec.elementAt(0)).srcIndex + "\"";
          outLine += "\t" + "\"" + Math.min(OPS,segAnsVec.size()) + "\"";

          int oi = 1;
          for (; oi <= OPS; ++oi) {
            if (segAnsVec.size() > 0) {
              EDTAnswers segAnswers = segAnsVec.elementAt(0);

              outLine += "\t" + "\"" + segAnswers.sysNumbers[1] + "\"";
              outLine += "\t" + "\"" + segAnswers.editID + "\"";

              String output = segAnswers.sysEdits[1];
              int partSize = output.length() / 4;

              String output_part1 = output.substring(0*partSize,1*partSize);
              String output_part2 = output.substring(1*partSize,2*partSize);
              String output_part3 = output.substring(2*partSize,3*partSize);
              String output_part4 = output.substring(3*partSize);

              output_part1 = output_part1.replaceAll("\"","\"\""); // replace quotes with double quotes
              output_part2 = output_part2.replaceAll("\"","\"\""); // replace quotes with double quotes
              output_part3 = output_part3.replaceAll("\"","\"\""); // replace quotes with double quotes
              output_part4 = output_part4.replaceAll("\"","\"\""); // replace quotes with double quotes

              outLine += "\t" + "\"" + output_part1 + "\"";
              outLine += "\t" + "\"" + output_part2 + "\"";
              outLine += "\t" + "\"" + output_part3 + "\"";
              outLine += "\t" + "\"" + output_part4 + "\"";

              segAnsVec.removeElementAt(0);
            } else {
              break; // from for (oi) loop
            }
          } // for (oi)

          for (; oi <= OPS; ++oi) {
            outLine += "\t" + "\"" + 0 + "\"";
            outLine += "\t" + "\"" + 0 + "\"";
            outLine += "\t" + "\"" + " " + "\"";
            outLine += "\t" + "\"" + " " + "\"";
            outLine += "\t" + "\"" + " " + "\"";
            outLine += "\t" + "\"" + " " + "\"";
          }

          if (segAnsVec.size() == 0) {
            segEDTAnswers_langPair.remove(segName);
          }

        } // for (segName)
  
      } // while more keys not (fully) processed yet

      if (nextOrder % SPS != 0) {
        while (nextOrder % SPS != 0) {
          outLine += "\t" + "\"" + 0 + "\""; // dummy srcIndex
          outLine += "\t" + "\"" + 0 + "\"";
          for (int oi = 1; oi <= OPS; ++oi) {
            outLine += "\t" + "\"" + 0 + "\"";
            outLine += "\t" + "\"" + 0 + "\"";
            outLine += "\t" + "\"" + " " + "\"";
            outLine += "\t" + "\"" + " " + "\"";
            outLine += "\t" + "\"" + " " + "\"";
            outLine += "\t" + "\"" + " " + "\"";
          }
          ++nextOrder;
        }
        writeLine(outLine.substring(1),uploadFile);
        ++writtenHITs;
      }

      uploadFile.close();

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in analyzeResults_EDT(...): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in analyzeResults_EDT(...): " + e.getMessage());
      System.exit(99902);
    }

  }
*/

/*
  static private void analyzeResults_ACC(String outFileName, String sep, String indicator, int questionsPerScreen, int systemsPerQuestion)
  {
    try {
      TreeMap<String,Integer> subYesCount = new TreeMap<String,Integer>();
      TreeMap<String,Integer> subNoCount = new TreeMap<String,Integer>();
      TreeMap<String,Integer> subNoneCount = new TreeMap<String,Integer>();

      FileOutputStream outStream = new FileOutputStream(outFileName, false);
      OutputStreamWriter outStreamWriter = new OutputStreamWriter(outStream, "utf8");
      BufferedWriter outFile = new BufferedWriter(outStreamWriter);


      String outLine = "";

      outLine += sep + "srclang";
      outLine += sep + "trglang";

      outLine += sep + "srcIndex";
      outLine += sep + "documentId";
      outLine += sep + "segmentId";
      outLine += sep + "judgeId";

      outLine += sep + "systemNumber";
      outLine += sep + "systemId";
      outLine += sep + "editId";

      outLine += sep + "judgment";

      outLine = outLine.substring(1); // remove initial comma
      writeLine(outLine,outFile);

      for (TreeMap<String,String> ans : answers) {
        if ((ans.get("HIT_info")).contains(collectionID) && (ans.get("HIT_info")).contains(indicator)) {
          for (int q = 1; q <= questionsPerScreen; ++q) {

            String sen_i = ans.get("SrcIndex" + q);

            if (sentenceMap.get(sen_i) == null) {

              // ****** RECOVER SrcIndex* and System*_* USING AMT upload INPUT FILES ******

              String task = "ACC";

              String HIT_info = ans.get("HIT_info");
                // e.g. "worker:A_de#task:ACC#langs:en-de#batch:1#HIT:71"

              String langInfo = HIT_info.split("#")[2]; // e.g. "langs:en-de"
              String langPair = langInfo.substring(6); // e.g. "en-de"
              String batchInfo = HIT_info.split("#")[3]; // e.g. "batch:1"
              int batchNumber = Integer.parseInt(batchInfo.substring(6)); // e.g. "1"
              String HITOrderInfo = HIT_info.split("#")[4]; // e.g. "HIT:71"
              int HITOrder = Integer.parseInt(HITOrderInfo.substring(4)); // e.g. "71"

              String uploadFileName = "ACC-batch" + batchNumber + "\\" + task + "_" + langPair + ".input";
                // e.g. "ACC-batch1\ACC_en-de.input"
              uploadFileName = fullPath(uploadFilesDir,uploadFileName);

              if (fileExists(uploadFileName)) {
                InputStream inStream = new FileInputStream(new File(uploadFileName));
                BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

                TreeMap<String,Integer> colTitleMap = new TreeMap<String,Integer>();
                String line = inFile.readLine();
                String[] colTitles = line.split("\t");
                for (int i = 0; i < colTitles.length; ++i) {
                  colTitleMap.put(colTitles[i],i);
                }

                line = inFile.readLine();
                while (line != null) {
                  String[] entries = line.split("\t");
                  if (("\"" + HIT_info + "\"").equals(entries[colTitleMap.get("\"HIT_info\"")])) {

                    for (int qr = 1; qr <= questionsPerScreen; ++qr) {

                      String ir = entries[colTitleMap.get("\"srcIndex" + qr + "\"")];
                      ir = ir.substring(1,ir.length()-1); // remove quotes
                      ans.put("SrcIndex" + qr, ir);

                      String nr = entries[colTitleMap.get("\"numSystems" + qr + "\"")];
                      nr = nr.substring(1,nr.length()-1); // remove quotes
                      ans.put("SystemCount" + qr, nr);

                      for (int sysr = 1; sysr <= systemsPerQuestion; ++sysr) {

                        String sr = entries[colTitleMap.get("\"sys" + qr + "_" + sysr + "\"")];
                        sr = sr.substring(1,sr.length()-1); // remove quotes
                        ans.put("System" + qr + "_" + sysr, sr);

                        String er = entries[colTitleMap.get("\"edId" + qr + "_" + sysr + "\"")];
                        er = er.substring(1,er.length()-1); // remove quotes
                        ans.put("editID" + qr + "_" + sysr, er);

                      } // for (sysr)

                    } // for (qr)

                    break; // from while (line != null) loop

                  } // if HIT_info match

                  line = inFile.readLine();

                } // while (line != null)

                inFile.close();

              } // if upload file exists

            } // if (sentenceMap.get(sen_i) == null)


            sen_i = ans.get("SrcIndex" + q); // try again


            if (sentenceMap.get(sen_i) == null) { // couldn't recover
              if (!sen_i.equals("0")) {
                println("    Couldn't recover " + ans.get("HIT_info"));
              } // otherwise it just means the HIT screen only had one source segement
            } else {

              String langInfo = (ans.get("HIT_info")).split("#")[2];
                // e.g. "langs:es-en"
              String langPair = langInfo.substring(6);
                // e.g. "es-en"
              String AID = ans.get("AID");
              boolean isContrastive = false;
              if (ans.get("isContrastive").equals("true")) isContrastive = true;

              String srclang = otherName.get(langPair.split("-")[0]);
              String trglang = otherName.get(langPair.split("-")[1]);
              String[] senInfo = sentenceMap.get(sen_i);
              String docid = senInfo[0];
              String segid = senInfo[1];

              for (int sys = 1; sys <= systemsPerQuestion; ++sys) {
                String sysNumber = ans.get("System" + q + "_" + sys);
//                if (!sysNumber.equals("0") && !isContrastive) {
                if (!sysNumber.equals("0")) {
                  String srcIndex = ans.get("SrcIndex" + q);
                  String sysName = systemAtNumber.get(sysNumber);
                  String editId = ans.get("editID" + q + "_" + sys);
                  String judgment = ans.get("JUDGE" + q + "_0" + sys);

                  if (judgment == null) incrementMapCount(subNoneCount,langPair + " " + sysName);
                  else if (judgment.equals("1")) incrementMapCount(subYesCount,langPair + " " + sysName);
                  else if (judgment.equals("2")) incrementMapCount(subNoCount,langPair + " " + sysName);
                  else incrementMapCount(subNoneCount,langPair + " " + sysName);

                  outLine = "";

                  outLine += sep + srclang;
                  outLine += sep + trglang;

                  outLine += sep + srcIndex;
                  outLine += sep + docid;
                  outLine += sep + segid;
                  outLine += sep + AID;

                  outLine += sep + sysNumber;
                  outLine += sep + sysName;
                  outLine += sep + editId;

                  if (judgment == null) outLine += sep + "UNK";
                  else if (judgment.equals("1")) outLine += sep + "YES";
                  else if (judgment.equals("2")) outLine += sep + "NO";
                  else outLine += sep + "UNK";

                  outLine = outLine.substring(1); // remove initial comma
                  writeLine(outLine,outFile);

                }
              } // for (sys)


            }


          } // for (q)

        } // if (HIT_info contains collectionID and indicator)
      } // for (ans)


      outFile.close();


      TreeSet<String> sysNameSet = new TreeSet<String>();
      for (String name : subYesCount.keySet()) sysNameSet.add(name);
      for (String name : subNoCount.keySet()) sysNameSet.add(name);
      for (String name : subNoneCount.keySet()) sysNameSet.add(name);

      String[] langs = {"en","cz","de","es","fr"};
      for (int srcL = 0; srcL < langs.length; ++srcL) {
        for (int trgL = 0; trgL < langs.length; ++trgL) {
          String srclang = langs[srcL];
          String trglang = langs[trgL];
          if (!srclang.equals(trglang) && (srclang.equals("en") || trglang.equals("en"))) {

            TreeMap<Double,Vector<String>> scoreMap = new TreeMap<Double,Vector<String>>();

            String langPair = srclang + "-" + trglang;
            println("*** " + langPair + " ***");
            println("System" + "\t" + "Yes count" + "\t" + "No count" + "\t" + "N/A count" + "\t" + "Total count" + "\t" + "% Yes");
            for (String subName : sysNameSet) {

              if (subName.startsWith(langPair)) {

                int yesCount = 0, noCount = 0, noneCount = 0, totalCount = 0;
                if (subYesCount.containsKey(subName)) { yesCount = subYesCount.get(subName); }
                if (subNoCount.containsKey(subName)) { noCount = subNoCount.get(subName); }
                if (subNoneCount.containsKey(subName)) { noneCount = subNoneCount.get(subName); }
                totalCount = yesCount + noCount + noneCount;

                double score = -((double)yesCount/totalCount); // negative to sort it in descending order
                String sysInfo = "";
                sysInfo += "\t" + subName;
                sysInfo += "\t" + yesCount;
                sysInfo += "\t" + noCount;
                sysInfo += "\t" + noneCount;
                sysInfo += "\t" + totalCount;
                sysInfo += "\t" + f2.format((double)yesCount/totalCount);

                Vector<String> V = null;
                if (scoreMap.containsKey(score)) V = scoreMap.get(score);
                else V = new Vector<String>();
                V.add(sysInfo.substring(1));
                scoreMap.put(score,V);
              }

            } // for (subName)

            for (Double sc : scoreMap.keySet()) {
              Vector<String> V = scoreMap.get(sc);
              for (String sysInfo : V) {
                println(sysInfo);
              }
            }

            println("");

          } // if langPair is valid

        } // for (trgL)
      } // for (srcL)

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in analyzeResults_RNK(...): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in analyzeResults_RNK(...): " + e.getMessage());
      System.exit(99902);
    }

  }
*/


  static private void processTaskAssignment(TreeMap<String,String> ansMap)
  {
    // adds a new entry to batchProcessedInfo

    String HIT_info = ansMap.get("HIT_info");
      // e.g. "emplus2010-c01#batch:1#task:RNK#langs:en-cz#HIT:17"

    String workerID = ansMap.get("AID");
      // e.g. "AXXXXXXXXX"

    String batchName = HIT_info.substring(0,HIT_info.indexOf("#HIT:"));

    int HITOrder = Integer.parseInt(HIT_info.substring(HIT_info.indexOf("#HIT:")+5)); // e.g. 17

    TreeMap<Integer,Integer> batchCountInfo = batchProcessedInfo.get(batchName);
    if (batchCountInfo == null) batchCountInfo = new TreeMap<Integer,Integer>();

    int count = 0;
    if (batchCountInfo.containsKey(HITOrder)) count = batchCountInfo.get(HITOrder);
    ++count;
    batchCountInfo.put(HITOrder,count);

    batchProcessedInfo.put(batchName,batchCountInfo);

  }



  static private double[] ranksSummary(Vector<Integer> ranks, int maxRank)
  {
    double[] A = new double[1+maxRank+1+1]; // 1 for % of -1, +1 for count, +1 for average rank

    int rankCount = 0;
    double rankSum = 0;

    for (Integer r : ranks) {
      if (r == -1) A[0] += 1;
      else { A[r] += 1; rankSum += r; }
      ++rankCount;
    }

    for (int r = 0; r <= maxRank; ++r) { A[r] = (A[r] / rankCount) * 100; }

    A[maxRank+1] = rankCount;
    A[maxRank+2] = rankSum / rankCount;

    return A;
  }

  static private double[] editActionsSummary(Vector<Integer> actions, int maxAction)
  {
    double[] A = new double[1+maxAction+1+1]; // 1 for % of 0 (N/A), +1 for count, +1 for average action value

    int actionCount = 0;
    double actionValueSum = 0;

    for (Integer a : actions) {
      int val = 0;
      switch (a) {
        case 0: val = 0; break; // N/A
        case 1: val = 0; break; // edited
        case 2: val = +1; break; // fine as is
        case 3: val = -1; break; // can't correct
      }
      A[a] += 1;
      actionValueSum += val;
      ++actionCount;
    }

    for (int a = 0; a <= maxAction; ++a) { A[a] = (A[a] / actionCount) * 100; }

    A[maxAction+1] = actionCount;
    A[maxAction+2] = actionValueSum / actionCount;

    return A;
  }

  static private <K> void incrementMapCount(TreeMap<K,Integer> M, K key)
  {
    incrementMapCount(M,key,1);
  }

  static private <K> void incrementMapCount(TreeMap<K,Integer> M, K key, int inc)
  {
    int cnt = 0;
    if (M.containsKey(key)) cnt = M.get(key);
    cnt += inc;
    M.put(key,cnt);
  }

  static private void writeLine(String line, BufferedWriter writer) throws IOException
  {
    writer.write(line, 0, line.length());
    writer.newLine();
    writer.flush();
  }

  static private int countLines(String fileName)
  {
    int count = 0;

    try {
      BufferedReader inFile = new BufferedReader(new FileReader(fileName));

      String line;
      do {
        line = inFile.readLine();
        if (line != null) ++count;
      }  while (line != null);

      inFile.close();
    } catch (IOException e) {
      System.err.println("IOException in countLines(String): " + e.getMessage());
      System.exit(99902);
    }

    return count;
  }

  static private String fullPath(String dir, String fileName)
  {
    File dummyFile = new File(dir,fileName);
    return dummyFile.getAbsolutePath();
  }

  static private boolean fileExists(String fileName)
  {
    if (fileName == null) return false;
    File checker = new File(fileName);
    return checker.exists();
  }

  static private double kappa(double p_A, double p_E)
  {
    return (p_A - p_E) / (1.0 - p_E);
  }

  static private double signTest_th(double wins, double trials)
  {
    if (wins < trials/2.0) {
      return signTest_th(trials-wins,trials);
    } else {
      return ((wins-0.5)-(trials/2.0))/(Math.sqrt(trials)/2);
    }
  }

  static private boolean significant(double wins, double trials, double pSig)
  {
    double thSig = 0.0;
    if (pSig == 0.10) thSig = 1.645;
    if (pSig == 0.05) thSig = 1.960;
    if (pSig == 0.01) thSig = 2.575;

    double th = signTest_th(wins,trials);

    if (th >= thSig) return true;
    else return false;
  }

  static private <K> double average(TreeMap<K,Double> X)
  {
    return sum(X) / X.size();
  }

  static private <K> double sum(TreeMap<K,Double> X)
  {
    double s = 0.0;
    for (K key : X.keySet()) { s += X.get(key); }
    return s;
  }

  static private <K> double sumSqErr(TreeMap<K,Double> X)
  {
    double ave = average(X);
    double SSE = 0.0;
    for (K key : X.keySet()) {
      double x = X.get(key);
      SSE += Math.pow(x-ave,2);
    }
    return SSE;
  }

  static private <K> double correlation(TreeMap<K,Double> X, TreeMap<K,Double> Y)
  {
    double numerator = 0.0;

    double x_bar = average(X);
    double y_bar = average(Y);

    for (K key : X.keySet()) { // also Y.keySet()
      double x = X.get(key);
      double y = Y.get(key);
      numerator += (x - x_bar)*(y - y_bar);
    }

    double SSE_X = sumSqErr(X);
    double SSE_Y = sumSqErr(Y);
    double denomenator = Math.sqrt(SSE_X) * Math.sqrt(SSE_Y);

    return numerator / denomenator;

  }

  static private <K> double tau(TreeMap<K,Double> X, TreeMap<K,Double> Y)
  {

    int num_con = 0;
    int num_dis = 0;
    int num_tot = 0;

    for (K key1 : X.keySet()) { // also Y.keySet()
      for (K key2 : X.keySet()) { // also Y.keySet()
        if (!key1.equals(key2)) {
          double val1_X = X.get(key1);
          double val1_Y = Y.get(key1);
          double val2_X = X.get(key2);
          double val2_Y = Y.get(key2);

          if (false) {}
          else if (val1_X > val2_X && val1_Y > val2_Y) ++num_con;
          else if (val1_X < val2_X && val1_Y < val2_Y) ++num_con;
          else ++num_dis;

          ++num_tot;
        }
      }
    }

    double numerator = num_con - num_dis;
    double denomenator = num_tot;

    return numerator / denomenator;

  }


  static private void readServerInfo(String infoFileName)
  {
    systemNumber.put("blank","0");
    systemAtNumber.put("0","blank");

    try {
      InputStream inStream = null;
      BufferedReader inFile = null;
      String line = null;


      inStream = new FileInputStream(new File(infoFileName));
      inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

      line = inFile.readLine();
      while (line != null) {

        String[] A = line.split(" \\|\\|\\| ");

        if (line.equals("") || A == null || A.length == 0) {
          // do nothing if line is empty
        } else if (A[1].equals("value")) {

//          if (A[0].equals("project_name")) project_name = A[2];
//          if (A[0].equals("random_seed")) random_seed = Integer.parseInt(A[2]);

        } else if (A[1].equals("count")) {
          if (A[0].equals("langs")) {

            num_langs = Integer.parseInt(A[2]);

            lang_names = new String[1+num_langs];
            lang_short_names = new String[1+num_langs];
            for (int g = 1; g <= num_langs; ++g) {
              line = inFile.readLine();
              String[] B = line.split(" \\|\\|\\| ");
              lang_names[g] = B[0];
              lang_short_names[g] = B[2];
              otherName.put(lang_names[g],lang_short_names[g]);
              otherName.put(lang_short_names[g],lang_names[g]);
            }

          } else if (A[0].equals("task_types")) {

            num_task_types = Integer.parseInt(A[2]);

            task_names = new String[1+num_task_types];
            task_short_names = new String[1+num_task_types];
            for (int t = 1; t <= num_task_types; ++t) {
              line = inFile.readLine();
              String[] B = line.split(" \\|\\|\\| ");
              task_names[t] = B[0];
              task_short_names[t] = B[2];
              task_short_name_index.put(task_short_names[t],t);
              otherName.put(task_names[t],task_short_names[t]);
              otherName.put(task_short_names[t],task_names[t]);
            }

          }
/*
        } else if (A[1].equals("subtask")) {
          // A[0] is language pair FROM-TO *and* task, A[2] is dummy "#"

          int s_i = A[0].indexOf(" "); // A[0] is e.g. "English-Spanish Ranking"
          String langs = A[0].substring(0,s_i);
          String task = A[0].substring(s_i+1);

          subtasks.add(otherName.get(task)+"|||"+"<"+langs.replaceAll("-",",")+">");
*/
        } else if (A[1].equals("system_number")) {
          // A[0] is system name, A[2] is system number
          systemNumber.put(A[0],A[2]);
          systemAtNumber.put(A[2],A[0]);
        }


        line = inFile.readLine();

      } // while (line != null)

      inFile.close();

    pages_per_batch = new int[1+num_task_types];
    random_pages_per_batch = new int[1+num_task_types];
    global_repeat_pages_per_batch = new int[1+num_task_types];
    local_repeat_pages_per_batch = new int[1+num_task_types];
    sentences_per_page = new int[1+num_task_types];
    outputs_per_sentence = new int[1+num_task_types];
    constant_systems = new int[1+num_task_types];

      inStream = new FileInputStream(new File(infoFileName));
      inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

      line = inFile.readLine();
      while (line != null) {

        String[] A = line.split(" \\|\\|\\| ");

        if (line.equals("") || A == null || A.length == 0) {
          // do nothing if line is empty
        } else if (A[1].equals("settings")) {

          String[] S = A[2].split(" "); // e.g. "1 RNK 3 5 0"
          int t = Integer.parseInt(S[0]);
          sentences_per_page[t] = Integer.parseInt(S[2]);
          outputs_per_sentence[t] = Integer.parseInt(S[3]);
          constant_systems[t] = Integer.parseInt(S[4]);

        } else if (A[1].equals("submission")) {
          // A[0] is language pair FROM-TO, A[2] is system, A[3] is file location
          String srclang = otherName.get((A[0].split("-"))[0]);
          String trglang = otherName.get((A[0].split("-"))[1]);

          if (!seenLangPairs.contains(srclang+"-"+trglang)) {
            seenLangPairs.add(srclang+"-"+trglang);
          }
        }

        line = inFile.readLine();

      } // while (line != null)

      inFile.close();

      if (!langPairOfInterest.equals("all")) {
        String srclangOfInterest = langPairOfInterest.split("-")[0];
        String trglangOfInterest = langPairOfInterest.split("-")[1];
        langPairOfInterest = "<" + otherName.get(srclangOfInterest) + "," + otherName.get(trglangOfInterest) + ">";
      }

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in readServerInfo(String): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in readServerInfo(String): " + e.getMessage());
      System.exit(99902);
    }

  }


  static private void println(Object obj) { System.out.println(obj); }
  static private void print(Object obj) { System.out.print(obj); }

}

class Stats {
  public int numHITs;
  public int totalWorkTime;
  public String country;
  public String signupCode;
  public String signupTime;
  public String name;
  public String email;

  public String totalWorkTimeInTimeFormat()
  {
    int temp = totalWorkTime;
    int time_hr = temp/3600;
    temp -= (3600*time_hr);
    int time_min = temp/60;
    temp -= (60*time_min);
    int time_sec = temp;

    return "" + time_hr + ":" + (time_min < 10? "0" : "") + time_min + ":" + (time_sec < 10? "0" : "") + time_sec;
  }

  public int averageHITTime()
  {
    if (numHITs > 0) {
      return totalWorkTime / numHITs;
    } else {
      return -1;
    }
  }

}

class Comparison {
  public String AID;
  public String docid;
  public String segid;
  public String firstSystem; // e.g. "de-en jhu-combo"
  public String secondSystem;
  public boolean hasRef;
  public int refLocation; // 0 if hasRef is false
  public int firstRank;
  public int secondRank;
  public boolean isContrastive;
  static public double lossScore = 0.0;
  static public double tieScore = 1.0;
  static public double winScore = 1.0;


  public void orderSystems()
  {
    // ensure that firstSystem is lexicographically less than secondSystem
    if (firstSystem.compareTo(secondSystem) > 0) {
      String temp_s = firstSystem;
      firstSystem = secondSystem;
      secondSystem = temp_s;

      int temp_i = firstRank;
      firstRank = secondRank;
      secondRank = temp_i;

      if (hasRef) {
        if (refLocation == 1) refLocation = 2;
        else refLocation = 1;
      }
    }
  }

  public int winner()
  {
    // 0: tie
    // 1: first system wins
    // 2: second system wins
    if (firstRank == secondRank) return 0;
    else if (firstRank < secondRank) return 1;
    else return 2;
  }

  public double sysScore(String sys)
  {
    String sysRes = sysResult(sys);
    if (sysRes.equals("win")) return winScore;
    else if (sysRes.equals("loss")) return lossScore;
    else if (sysRes.equals("tie")) return tieScore;
    else return 0.0;
  }

  public double sysScore2(String sys)
  {
    String sysRes = sysResult(sys);
    if (sysRes.equals("win")) return winScore;
    else if (sysRes.equals("loss")) return lossScore;
    else return 0.0;
  }

  public String sysResult(String sys)
  {
    int sysLoc = 0;
    if (sys.equals(firstSystem)) sysLoc = 1;
    else if (sys.equals(secondSystem)) sysLoc = 2;
    else return "N/A";

    if (firstRank == secondRank) return "tie";

    if (sysLoc == winner()) return "win";
    else return "loss";
  }

  public boolean judgeCommonItem(Comparison other)
  {
    // do the two Comparisons judge the same item? (same segment and same system pair)
    if ((firstSystem.equals(other.firstSystem)) && (secondSystem.equals(other.secondSystem))) {
      // note that the systems are already ordered lexicographically
      return (docid.equals(other.docid) && segid.equals(other.segid));
    } else { // not the same systems
      return false;
    }
  }

  public boolean isInAgreement(Comparison other)
  {
    // do the two Comparisons judge the same item and give the same comparison?
    // this only returns true if both Comparisons give the same result; otherwise it's false

    if (!judgeCommonItem(other)) return false;

    return (winner() == other.winner());
      // note that the systems are already ordered lexicographically
  }

  public boolean isInLooseAgreement(Comparison other)
  {
    // do the two Comparisons judge the same item and give *loosely* the same comparison?
    // this only returns false if neither Comparison gave a tie AND the results are opposite; otherwise it's true

    if (!judgeCommonItem(other)) return false;

    if ((sysResult(firstSystem)).equals("tie")) return true;
    if (other.sysResult(firstSystem).equals("tie")) return true;

    return ((sysResult(firstSystem)).equals(other.sysResult(firstSystem)));
  }

  public void setRefLocation()
  {
    if (firstSystem.contains("_ref")) refLocation = 1;
    else if (secondSystem.contains("_ref")) refLocation = 2;
    else refLocation = 0;

    if (refLocation > 0) hasRef = true;
  }

  public boolean refWins() { return refHasResult("win"); }
  public boolean refTies() { return refHasResult("tie"); }
  public boolean refLosess() { return refHasResult("loss"); }

  public boolean refHasResult(String res)
  {
    int loc = refLocation;
    String refSysName = "";

    if (loc == 0) {
      return false;
    } else if (loc == 1) {
      refSysName = firstSystem;
    } else { // if (loc == 2)
      refSysName = secondSystem;
    }

    return (sysResult(refSysName).equals(res));

  }

}
/*
class Pair<F,S> implements Comparable {
  public F first;
  public S second;
  public Pair() { first = null; second = null; }
  public Pair(F x, S y) { first = x; second = y; }
  public Pair(String pairStr) {
    if (pairStr.startsWith("<")) {
      pairStr = pairStr.substring(1,pairStr.length()-1); // remove < and >
    }

    if (pairStr.contains("|||")) {
      first = (F)(pairStr.split("\\|\\|\\|")[0]);
      second = (S)(pairStr.split("\\|\\|\\|")[1]);
    } else if (pairStr.contains(",")) {
      first = (F)(pairStr.split(",")[0]);
      second = (S)(pairStr.split(",")[1]);
    } else if (pairStr.contains("-")) {
      first = (F)(pairStr.split("-")[0]);
      second = (S)(pairStr.split("-")[1]);
    }
  }
  public String toString() { return "<" + first + "|||" + second + ">"; }
  public int compareTo(Object obj) { return (this.toString()).compareTo(obj.toString()); }
}
*/
class RNKAnswers {
  int numSystems;
  public String[] sysNames;
  public int[] sysNumbers;
  public int[] sysRanks;
  String AID;
  int srcIndex;
  String docid;
  String segid;
  boolean isContrastive;

  public RNKAnswers(String aid, boolean cont, int si, String did, String sid, int cnt, String[] names, int[] numbers, int[] ranks) {
    AID = aid;
    isContrastive = cont;
    srcIndex = si;
    docid = did;
    segid = sid;
    numSystems = cnt;
    sysNames = new String[1+numSystems];
    sysNumbers = new int[1+numSystems];
    sysRanks = new int[1+numSystems];
    for (int sys = 1; sys <= numSystems; ++sys) {
      sysNames[sys] = names[sys];
      sysNumbers[sys] = numbers[sys];
      sysRanks[sys] = ranks[sys];
    }
  }

  public Vector<Comparison> getComparisons()
  {
    Vector<Comparison> retV = new Vector<Comparison>();

    for (int sys1 = 1; sys1 < numSystems; ++sys1) {
      for (int sys2 = sys1+1; sys2 <= numSystems; ++sys2) {
        Comparison C = new Comparison();
        C.AID = AID;
        C.docid = docid;
        C.segid = segid;
        C.firstSystem = sysNames[sys1];
        C.secondSystem = sysNames[sys2];
        C.setRefLocation();
        C.firstRank = sysRanks[sys1];
        C.secondRank = sysRanks[sys2];
        C.orderSystems();
        C.isContrastive = isContrastive;
        if (C.firstRank != -1 && C.secondRank != -1) {
          retV.add(C);
        }
      }
    }

    return retV;
  }

}

class EDTAnswers {
  int numSystems;
  public String[] sysNames;
  public int[] sysNumbers;
  public int[] sysActions;
  public String[] sysEdits;
  String AID;
  int editID;
  int srcIndex;
  String docid;
  String segid;
  boolean isContrastive;

  public EDTAnswers(String aid, boolean cont, int ei, int si, String did, String sid, int cnt, String[] names, int[] numbers, int[] actions, String[] edits) {
    AID = aid;
    isContrastive = cont;
    editID = ei;
    srcIndex = si;
    docid = did;
    segid = sid;
    numSystems = cnt;
    sysNames = new String[1+numSystems];
    sysNumbers = new int[1+numSystems];
    sysActions = new int[1+numSystems];
    sysEdits = new String[1+numSystems];
    for (int sys = 1; sys <= numSystems; ++sys) {
      sysNames[sys] = names[sys];
      sysNumbers[sys] = numbers[sys];
      sysActions[sys] = actions[sys];
      sysEdits[sys] = edits[sys];
    }
  }

}

