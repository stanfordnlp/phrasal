import java.util.*;
import java.io.*;
import java.text.DecimalFormat;
import java.net.URLDecoder;

public class CreateBatches
{
  static private String sepKeyVal = "|||";
  static private String sepEntEnt = "|||||||||";
  static private String sepSetSet = "---------------------------";

  static TreeMap<String,String> otherName = new TreeMap<String,String>();

  static int num_task_types = 0;
  static int num_langs = 0;

  static String[] task_names;
  static String[] task_short_names;
  static TreeMap<String,Integer> task_short_name_index = new TreeMap<String,Integer>();
  static String[] lang_names;
  static String[] lang_short_names;

  static TreeMap<String,Vector<Integer>> task_random_list = new TreeMap<String,Vector<Integer>>();
    // TreeMap<task_short_name,Vector<indices>>

  static TreeMap<String,Vector<Integer>> subtask_repeat_list = new TreeMap<String,Vector<Integer>>();
    // TreeMap<task_short_name|||<From,To>,Vector<indices>> (lang names are full)

  static TreeMap<String,String[]> sources = new TreeMap<String,String[]>();
    // TreeMap<lang,sentences[]>

  static TreeMap<String,String[]> references = new TreeMap<String,String[]>();
    // TreeMap<lang,sentences[]>

  static TreeMap<Pair<String,String>,TreeMap<String,String[]>> submissions = new TreeMap<Pair<String,String>,TreeMap<String,String[]>>();
    // TreeMap<langPair,TreeMap<system,output[]>>

  static TreeMap<Pair<String,String>,TreeSet<String>> langPairSystems = new TreeMap<Pair<String,String>,TreeSet<String>>();
    // TreeMap<langPair,TreeSet<system>>

  static TreeSet<String> validShortLangPairs = new TreeSet<String>();
    // TreeSet<FL-TL>

  static TreeMap<String,String> systemNumber = new TreeMap<String,String>();
    // TreeMap<sysName,sysNumber>
  static TreeMap<String,String> systemAtNumber = new TreeMap<String,String>();
    // TreeMap<sysNumber,sysName>

  static TreeSet<String> subtasks = new TreeSet<String>();
    // TreeSet<task_short_name|||<From,To>>

  static TreeMap<String,String[]> expertSeenSegments = new TreeMap<String,String[]>();
    // maps segment name to an array of systems already chosen to an expert annotator
    // e.g. "cz-en_1371" -> {"bbn-combo","cambridge","upv-combo","onlineA","upc"}


  static boolean primaryOnly = false;
    // set to true to exclude systems with "contrative" or "secondary" in their names
    // set to false to include all systems

  static String project_name;
  static int random_seed = 0;
  static String output_file_path = ".";

  static int[] pages_per_batch;
  static int[] random_pages_per_batch;
//  static int[] global_repeat_pages_per_batch;
//  static int[] local_repeat_pages_per_batch;
  static int[] sentences_per_page;
  static int[] outputs_per_sentence;
  static int[] constant_systems;

  static Random generator;

  static String serverInfoFile;
  static String batchInfoFile;
  static String templateDir;

  static BufferedWriter outFile_info;

  // batch info
  static String collectionID = "";
  static int numBatches = -1;

  static String[] b_task = null, b_shortLangPair = null, b_location = null, b_quals = null;
  static int[] b_size__rand = null, b_srcRep__size = null, b_srcRep__repCount = null, b_fullRep__size = null;
  static int[] b_assgnsPerHIT = null, b_minApprovalRate = null, b_minApprovedHITs = null;
  static double[] b_reward = null;

  static public void main(String[] args) throws Exception
  {

    // java -Xmx300m CreateBatches serverInfo=server_info.txt batchInfo=batch_info.txt templateLoc=taskFilesDirName

    // default values for mandatory parameters
    serverInfoFile = ""; // serverInfo
    batchInfoFile = ""; // batchInfo
    templateDir = ""; // templateLoc


    // process arguments
    for (int i = 0; i < args.length; ++i) { processArg(args[i]); }

    if (serverInfoFile.equals("")) {
      println("You did not specify a server info file name.");
      println("Usage: java CreateBatches serverInfo=infoFileName batchInfo=batchInfoFileName templateLoc=taskFilesDirName");
      System.exit(99);
    }

    if (batchInfoFile.equals("")) {
      println("You did not specify a batch info file name.");
      println("Usage: java CreateBatches serverInfo=infoFileName batchInfo=batchInfoFileName templateLoc=taskFilesDirName");
      System.exit(99);
    }

    if (templateDir.equals("")) {
      println("You did not specify the location of the properties template files.");
      println("Usage: java CreateBatches serverInfo=infoFileName batchInfo=batchInfoFileName templateLoc=taskFilesDirName");
      System.exit(99);
    }

//    try {

    readServerInfo(serverInfoFile);
    // read server info file (does many things, such as read submission outputs)
    println("Finished reading server info file.");
    println("");

    generator = new Random(random_seed);

//println(subtasks);

    readBatchInfo(batchInfoFile);
    println("Finished reading batch info file.");
    println("");


    FileOutputStream outStream_info = new FileOutputStream(collectionID+".uploadinfo", false);
    OutputStreamWriter outStreamWriter_info = new OutputStreamWriter(outStream_info, "utf8");
    outFile_info = new BufferedWriter(outStreamWriter_info);


    for (int b = 1; b <= numBatches; ++b) {
      generateNewBatch(b);
    }

    outFile_info.close();

    println("");
    println("");

    println("Finished generating batches.  The upload info file is " + (collectionID+".uploadinfo"));
    println("");
    println("****************************************************************************");
    println(" IMPORTANT: you must modify the external URL in the newly created question");
    println("            file(s), instead of the existing (dummy) URL location(s).");
    println("            Question files are in the output folder and end in \".question\".");
    println("****************************************************************************");

/*
    String task = "RNK";
//    set_expertSeenSegments("C:\\Documents and Settings\\admin\\Desktop\\wmt10_software\\analysis\\data_RNK.csv",outputs_per_sentence[1]);

    String[] langs = {"Czech","German","English","French"};

    for (int fl = 0; fl < langs.length; ++fl) {
      for (int tl = 0; tl < langs.length; ++tl) {
        String fromLang = langs[fl];
        String toLang = langs[tl];
        int batchNumber = 1;
        if (fl != tl && (fromLang.equals("English") || toLang.equals("English"))) {
          Pair<String,String> langPair = new Pair<String,String>(fromLang,toLang);

          if (subtasks.contains(task+"|||"+langPair)) {
            generateNewBatch(task, langPair, batchNumber);
          }

        } // if valid langPair

      } // for (tl)

    } // for (fl)
*/





/*
    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in main(String[]): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in main(String[]): " + e.getMessage());
      System.exit(99902);
    }
*/
    System.exit(0);

  } // main(String[] args)

  static private void set_expertSeenSegments(String fileName, int OPS)
  {
    /* recall:
        static TreeMap<String,String[]> expertSeenSegments = new TreeMap<String,String[]>();
          // maps segment name to an array of systems already chosen to an expert annotator
          // e.g. "cz-en_1371" -> {"bbn-combo","cambridge","upv-combo","onlineA","upc"}
    */

    TreeMap<String,Integer> bestScore = new TreeMap<String,Integer>();
      // will be used to easily pick a random set of system numbers, without having to read the entire file beforehand

    try {

      InputStream inStream = new FileInputStream(new File(fileName));
      BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

      String line = inFile.readLine();
      line = inFile.readLine(); // need to skip header

      while (line != null) {
        String[] entries = line.split(",");

        String srcLang = otherName.get(entries[0]); // e.g. cz
        String trgLang = otherName.get(entries[1]); // e.g. en
        String srcIndex = entries[2]; // e.g. 1371
        String[] systemNumbers = new String[OPS]; // system names
        for (int s = 0; s < OPS; ++s) { systemNumbers[s] = entries[7+(2*s)]; }
          // e.g. {"bbn-combo","cambridge","upv-combo","onlineA","upc"}

        String segName = srcLang+"-"+trgLang+"_"+srcIndex; // e.g. cz-en_1371

        int score = randInt(1,10000);

        boolean add = false;
        if (!expertSeenSegments.containsKey(segName)) {
          add = true;
        } else {
          int currBestScore = bestScore.get(segName);
          if (score > currBestScore) { add = true; }
        }

        if (add) {
          expertSeenSegments.put(segName,systemNumbers);
          bestScore.put(segName,score);
        }

        line = inFile.readLine();

      }

      inFile.close();

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in readServerInfo(String): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in readServerInfo(String): " + e.getMessage());
      System.exit(99902);
    }
/*
    for (String segName : expertSeenSegments.keySet()) {
      String[] systemNumbers = expertSeenSegments.get(segName);
      print(segName + "\t" + bestScore.get(segName));
      for (int s = 0; s < OPS; ++s) { print("\t" + systemNumbers[s]); }
      println("");
    }
*/
  }

  static private void readBatchInfo(String infoFileName)
  {

    try {
      InputStream inStream = new FileInputStream(new File(infoFileName));
      BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));
      String line = inFile.readLine();

      int currBatch = 0;

      while (line != null) {
        if (line.indexOf("#") != -1) { line = line.substring(0,line.indexOf("#")); } // discard comment
        line = line.trim();

        if (line.length() > 0) {

          String[] A = line.split("\\s+");

          if (A[0].equals("collection-ID")) {

            collectionID = A[1];

          } else if (A[0].equals("number-of-batches-in-collection")) {

            numBatches = Integer.parseInt(A[1]);

            // in each of the following, [0] stores the default value
            b_task = new String[1+numBatches];          b_task[0] = null;
            b_shortLangPair = new String[1+numBatches]; b_shortLangPair[0] = null;
            b_location = new String[1+numBatches];      b_location[0] = null; // optional
            b_quals = new String[1+numBatches];         b_quals[0] = null; // optional
            b_size__rand = new int[1+numBatches];       b_size__rand[0] = -1;
            b_srcRep__size = new int[1+numBatches];     b_srcRep__size[0] = 0;
            b_srcRep__repCount = new int[1+numBatches]; b_srcRep__repCount[0] = 0;
            b_fullRep__size = new int[1+numBatches];    b_fullRep__size[0] = 0;
            b_assgnsPerHIT = new int[1+numBatches];     b_assgnsPerHIT[0] = -1;
            b_reward = new double[1+numBatches];        b_reward[0] = -1;
            b_minApprovalRate = new int[1+numBatches];  b_minApprovalRate[0] = -1;  initArray(b_minApprovalRate,-1);
            b_minApprovedHITs = new int[1+numBatches];  b_minApprovedHITs[0] = -1;  initArray(b_minApprovedHITs,-1);

          } else if (A[0].equals("batch")) {

            if (numBatches == -1) {
              println("Encountered batch properties for batch " + currBatch + " before determining number of batches.");
              System.exit(9);
            }

            currBatch = Integer.parseInt(A[1]);

            if (currBatch > numBatches || currBatch < 1) {
              println("Invalid batch number " + currBatch + "; must be between 1 and " + numBatches + ".");
              System.exit(9);
            }

          } else if (A[0].equals("task")) {
            b_task[currBatch] = A[1];
          } else if (A[0].equals("language-pair")) {
            b_shortLangPair[currBatch] = A[1];
          } else if (A[0].equals("location")) {

            String line_mod = "";
            for (int j = 1; j < A.length; ++j) {
              line_mod += A[j];
            }

            String locStr = "";
            String[] L = (line_mod).split(",");

            for (int j = 0; j < L.length; ++j) {
              L[j] = L[j].trim();
              if (!L[j].startsWith("!") && L.length > 1) {
                println("Problem processing the location qualification for batch #" + currBatch + ":");
                println("    When specifying location qualifications, you can either allow a single country,");
                println("    or ban several countries.  Allowing multiple countries is not supported by MTurk.");
                System.exit(5);
              }
              if (L[j].length() > 0) {
                locStr += "," + L[j];
              }
            }

            b_location[currBatch] = locStr.substring(1);

          } else if (A[0].equals("qualification")) {

            String line_mod = "";
            for (int j = 1; j < A.length; ++j) {
              line_mod += A[j];
            }

            String qualStr = "";
            String[] Q = (line_mod).split(",");

            for (int j = 0; j < Q.length; ++j) {
              Q[j] = Q[j].trim();
              if (Q[j].length() > 0) {
                qualStr += "," + Q[j];
              }
            }

            b_quals[currBatch] = qualStr.substring(1);

          } else if (A[0].endsWith("batch-size-in-HITs")) {
            b_size__rand[currBatch] = Integer.parseInt(A[1]);
          } else if (A[0].endsWith("srcRep-size-in-clusters")) {
            b_srcRep__size[currBatch] = Integer.parseInt(A[1]);
          } else if (A[0].endsWith("srcRep-count-per-cluster")) {
            b_srcRep__repCount[currBatch] = Integer.parseInt(A[1]);
          } else if (A[0].endsWith("fullRep-size-in-HITs")) {
            b_fullRep__size[currBatch] = Integer.parseInt(A[1]);
          } else if (A[0].endsWith("assignments-per-HIT")) {
            b_assgnsPerHIT[currBatch] = Integer.parseInt(A[1]);
          } else if (A[0].endsWith("reward-per-assignment")) {
            b_reward[currBatch] = Double.parseDouble(A[1]);
          } else if (A[0].endsWith("min-approval-rate")) {
            b_minApprovalRate[currBatch] = Integer.parseInt(A[1]);
          } else if (A[0].endsWith("min-approved-HITs")) {
            b_minApprovedHITs[currBatch] = Integer.parseInt(A[1]);
          }

        } // if (line length > 0)

        line = inFile.readLine();

      } // while (line != null)

//      println("collectionID: " + collectionID);

      int problemCount = 0;

      for (int b = 1; b <= numBatches; ++b) {
        if (b_size__rand[b] == 0) b_size__rand[b] = b_size__rand[0];
        if (b_srcRep__size[b] == 0) b_srcRep__size[b] = b_srcRep__size[0];
        if (b_srcRep__repCount[b] == 0) b_srcRep__repCount[b] = b_srcRep__repCount[0];
        if (b_fullRep__size[b] == 0) b_fullRep__size[b] = b_fullRep__size[0];
        if (b_assgnsPerHIT[b] == 0) b_assgnsPerHIT[b] = b_assgnsPerHIT[0];
        if (b_reward[b] == 0.0) b_reward[b] = b_reward[0];
        if (b_minApprovalRate[b] == -1) b_minApprovalRate[b] = b_minApprovalRate[0];
        if (b_minApprovedHITs[b] == -1) b_minApprovedHITs[b] = b_minApprovedHITs[0];
/*
        println("b=" + b + ":");
        println("  task: " + b_task[b]);
        println("  shortLangPair: " + b_shortLangPair[b]);
        println("  location: " + b_location[b]);
        println("  size: " + b_size__rand[b]);
        println("  srcRep-size: " + b_srcRep__size[b]);
        println("  srcRep-repCount: " + b_srcRep__repCount[b]);
        println("  fullRep-size: " + b_fullRep__size[b]);
        println("  assgnsPerHIT: " + b_assgnsPerHIT[b]);
        println("  reward: " + b_reward[b]);
        println("  minApprovalRate: " + b_minApprovalRate[b]);
        println("  minApprovedHITs: " + b_minApprovedHITs[b]);
//*/
        if (b_size__rand[b] < 0) { println("Invalid batch size for batch " + b + "; must be positive."); ++problemCount; }
        if (b_srcRep__size[b] < 0) { println("Invalid srcRep-size for batch " + b + "; must be non-negative."); ++problemCount; }
        if (b_srcRep__repCount[b] < 0) { println("Invalid srcRep-repCount for batch " + b + "; must be non-negative."); ++problemCount; }
        if (b_fullRep__size[b] < 0) { println("Invalid fullRep-size for batch " + b + "; must be non-negative."); ++problemCount; }
        if (b_assgnsPerHIT[b] <= 0) { println("Invalid assignment count for batch " + b + "; must be positive."); ++problemCount; }
        if (b_reward[b] < 0.00) { println("Invalid reward for batch " + b + "; must be non-negative."); ++problemCount; }
        if (b_minApprovalRate[b] < 0 || b_minApprovalRate[b] > 100) { println("Invalid approval rate for batch " + b + "; must be between 0 and 100."); ++problemCount; }
        if (b_minApprovedHITs[b] < 0) { println("Invalid minimum approved HITs for batch " + b + "; must be non-negative."); ++problemCount; }

        if (b_task[b] == null) { println("Unspecified task for batch " + b + "."); ++problemCount; }
        else if (!task_short_name_index.containsKey(b_task[b])) { println("Unrecognized task for batch " + b + "; must be one of " + task_short_name_index.keySet() + "."); ++problemCount; }

        if (b_shortLangPair[b] == null) { println("Unspecified language pair for batch " + b + "."); ++problemCount; }
        else if (!validShortLangPairs.contains(b_shortLangPair[b])) { println(b_shortLangPair[b] + "Invalid language pair for batch " + b + "; must be one of " + validShortLangPairs); ++problemCount; }

      } // for (b)

      if (problemCount > 0) {
        println("There were " + problemCount + " problems in the batch specs file; exiting.");
        System.exit(9);
      }

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in readBatchInfo(String): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in readBatchInfo(String): " + e.getMessage());
      System.exit(99902);
    }

  } // void readBatchInfo(String)

  static private void initArray(int[] A, int x) { for (int i = 0; i < A.length; ++i) A[i] = x; }

  static private void readServerInfo(String infoFileName)
  {

    otherName = new TreeMap<String,String>();

    // http://www.iso.org/iso/country_codes/iso_3166_code_lists/english_country_names_and_code_elements.htm
    /*** HACKS ***/
/*
    langLocation = new TreeMap<String,String>();

    langLocation.put("cz","CZ");
    langLocation.put("de","DE");
    langLocation.put("en","US");
    langLocation.put("es","ES");
    langLocation.put("fr","FR");
*/
    /*************/

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

        if (A[1].equals("value")) {

          if (A[0].equals("project_name")) project_name = A[2];
          if (A[0].equals("random_seed")) random_seed = Integer.parseInt(A[2]);
          if (A[0].equals("output_file_path")) output_file_path = A[2];

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

//    num_task_types = 3;
//    task_names = new String[1+num_task_types];
//    task_short_names = new String[1+num_task_types];
//    otherName.put("RNK","Ranking"); otherName.put("Ranking","RNK");
//    task_short_name_index.put("RNK",1); task_names[1] = "Ranking"; task_short_names[1] = "RNK";
//    otherName.put("AFR","Adequacy/Fluency Rating"); otherName.put("Adequacy/Fluency Rating","AFR");
//    task_short_name_index.put("AFR",2); task_names[2] = "Adequacy/Fluency Rating"; task_short_names[2] = "AFR";
//    otherName.put("EDT","MT Output Editing"); otherName.put("MT Output Editing","EDT");
//    task_short_name_index.put("EDT",3); task_names[3] = "MT Output Editing"; task_short_names[3] = "EDT";

    pages_per_batch = new int[1+num_task_types];
    random_pages_per_batch = new int[1+num_task_types];
//    global_repeat_pages_per_batch = new int[1+num_task_types];
//    local_repeat_pages_per_batch = new int[1+num_task_types];
    sentences_per_page = new int[1+num_task_types];
    outputs_per_sentence = new int[1+num_task_types];
    constant_systems = new int[1+num_task_types];

/*
    pages_per_batch[1] = 10; pages_per_batch[2] = 10;
    random_pages_per_batch[1] = 10; random_pages_per_batch[2] = 10;
    global_repeat_pages_per_batch[1] = 0; global_repeat_pages_per_batch[2] = 0;
    local_repeat_pages_per_batch[1] = 0; local_repeat_pages_per_batch[2] = 0;
    sentences_per_page[1] = 3; sentences_per_page[2] = 5;
    outputs_per_sentence[1] = 5; outputs_per_sentence[2] = 1;
    constant_systems[1] = 0; constant_systems[2] = 1;
*/

      inStream = new FileInputStream(new File(infoFileName));
      inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

      line = inFile.readLine();
      while (line != null) {

        String[] A = line.split(" \\|\\|\\| ");

        if (A[1].equals("settings")) {

          String[] S = A[2].split(" "); // e.g. "1 RNK 3 5 0"
          int t = Integer.parseInt(S[0]);
          sentences_per_page[t] = Integer.parseInt(S[2]);
          outputs_per_sentence[t] = Integer.parseInt(S[3]);
          constant_systems[t] = Integer.parseInt(S[4]);

        } else if (A[1].equals("submission")) {
          // A[0] is language pair FROM-TO, A[2] is system, A[3] is file location

          if (primaryOnly && (A[2].contains("contrastive") || A[2].contains("secondary"))) {

            println("SKIPPING non-primary sub: " + A[0] + " " + A[2]);

          } else {

            println("processing sub: " + A[0] + " " + A[2]);

            int subSize = countLines(A[3]);
            println("subSize: " + subSize);

            InputStream inStream_sub = new FileInputStream(new File(A[3]));
            BufferedReader inFile_sub = new BufferedReader(new InputStreamReader(inStream_sub, "utf8"));

            String[] out = new String[1+subSize];

            for (int sen = 1; sen <= subSize; ++sen) {
              out[sen] = inFile_sub.readLine();
            }

            String fromLang = (A[0].split("-"))[0];
            String toLang = (A[0].split("-"))[1];


            Pair<String,String> langPair = new Pair<String,String>(fromLang,toLang);

            TreeMap<String,String[]> taskSub = null;
            TreeSet<String> taskSys = null;
            if (submissions.containsKey(langPair)) {
              taskSub = submissions.get(langPair);
              taskSys = langPairSystems.get(langPair);
            } else {
              taskSub = new TreeMap<String,String[]>();
              taskSys = new TreeSet<String>();
              validShortLangPairs.add(otherName.get(fromLang)+"-"+otherName.get(toLang));
            }

            taskSub.put(A[2],out);
            taskSys.add(A[2]);

            submissions.put(langPair,taskSub);
            langPairSystems.put(langPair,taskSys);

            inFile_sub.close();

          } // if (primary or secondary)

        } else if (A[1].equals("source")) {
          // A[0] is language FROM, A[2] is file location
          println("src: " + A[0]);

          int srcSize = countLines(A[2]);
          println("srcSize: " + srcSize);

          InputStream inStream_src = new FileInputStream(new File(A[2]));
          BufferedReader inFile_src = new BufferedReader(new InputStreamReader(inStream_src, "utf8"));

          String[] src = new String[1+srcSize];

          for (int sen = 1; sen <= srcSize; ++sen) {
            src[sen] = inFile_src.readLine();
          }

          String lang = A[0];


          sources.put(lang,src);

          inFile_src.close();

        } else if (A[1].equals("reference")) {
          // A[0] is language TO, A[2] is file location
          println("ref: " + A[0]);

          int refSize = countLines(A[2]);
          println("refSize: " + refSize);

          InputStream inStream_ref = new FileInputStream(new File(A[2]));
          BufferedReader inFile_ref = new BufferedReader(new InputStreamReader(inStream_ref, "utf8"));

          String[] ref = new String[1+refSize];

          for (int sen = 1; sen <= refSize; ++sen) {
            ref[sen] = inFile_ref.readLine();
          }

          String lang = A[0];


          references.put(lang,ref);

          inFile_ref.close();

        } else if (A[1].equals("random_list")) {
          // A[0] is task (no language pair), A[2] is list of indices
          Vector<Integer> L = ssvToVector(A[2]);
          task_random_list.put(otherName.get(A[0]),L);
/*
        } else if (A[1].equals("repeat_list")) {
          // A[0] is language pair FROM-TO *and* task, A[2] is list of indices
          Vector<Integer> L = ssvToVector(A[2]);

          int s_i = A[0].indexOf(" "); // A[0] is e.g. "English-Spanish Ranking"
          String langs = A[0].substring(0,s_i);
          String task = A[0].substring(s_i+1);
          subtask_repeat_list.put(otherName.get(task)+"|||"+"<"+langs.replaceAll("-",",")+">",L);

          subtasks.add(otherName.get(task)+"|||"+"<"+langs.replaceAll("-",",")+">");
*/
        }


        line = inFile.readLine();

      } // while (line != null)

      inFile.close();



    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in readServerInfo(String): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in readServerInfo(String): " + e.getMessage());
      System.exit(99902);
    }

    println("");


    println("Summary by language pair:");

    for (Pair langPair : langPairSystems.keySet()) {
      print("  " + langPair + ":");
      TreeSet<String> T = langPairSystems.get(langPair);
      println(" " + T.size() + " systems...");
      print("    ");
      for (String sys : T) {
        print(sys);
        if (!sys.equals(T.last())) { print(", "); }
      }
      println("");

    } // for (Pair)

    println("");



    println("random list sizes:");
    for (String str : task_random_list.keySet()) {
      println("  " + str + ": size=" + (task_random_list.get(str)).size() + " clusters, each of size " + sentences_per_page[task_short_name_index.get(str)]);
    }
/*
    println("repeat list sizes:");
    for (String str : subtask_repeat_list.keySet()) {
      println("  " + str + ": size=" + (subtask_repeat_list.get(str)).size() + " clusters, each of size " + sentences_per_page[task_short_name_index.get(str)]);
    }
*/
  }


  static private Vector<Integer> ssvToVector(String str)
  {
    // the ssv list is a list of ranges; here we extract the initial index from each range
    // e.g. str could be   "1-3 9-11 17-19 20-22 28-30 32-34"
    //     and we return   <1,9,17,20,28,32>
    Vector<Integer> retV = new Vector<Integer>();

    String[] A = str.split(" ");
    for (int i = 0; i < A.length; ++i) {
      retV.add(Integer.parseInt( (A[i].split("-"))[0] ));
    }

    return retV;
  }


  static private void generateNewBatch(int batchNumber)
  {
    String task = b_task[batchNumber]; // e.g. "RNK"
    int tsk_i = task_short_name_index.get(task);
    String langs = b_shortLangPair[batchNumber]; // e.g. "cz-en"
    String fromLang = otherName.get(langs.split("-")[0]); // e.g. "Czech"
    String toLang = otherName.get(langs.split("-")[1]); // e.g. "English"
    Pair<String,String> langPair = new Pair<String,String>(fromLang,toLang);
    double reward = b_reward[batchNumber];
    int assgns = b_assgnsPerHIT[batchNumber];
    int minAppRate = b_minApprovalRate[batchNumber];
    int minAppHITs = b_minApprovedHITs[batchNumber];
    String locStr = b_location[batchNumber];
    String qualStr = b_quals[batchNumber];

    String rootDir = output_file_path;
    String outLine = "";
    TreeMap<String,String[]> langPairSubs = submissions.get(langPair);
    String[] srcSens = sources.get(fromLang);
    String[] refSens = references.get(toLang);

    String CID = collectionID;

    String batchName = "";
    batchName += CID;
    batchName += "#" + "batch" + ":" + batchNumber;
    batchName += "#" + "task" + ":" + task;
    batchName += "#" + "langs" + ":" + langs;

    println("Generating batch " + batchName);

    try {

      Vector<Integer> random_list = task_random_list.get(task);
      Vector<Integer> repeat_list = subtask_repeat_list.get(task + "|||" + langPair);

      String inputFile = fullPath(rootDir, CID + ".batch" + batchNumber + "." + task + "." + langs + ".input");
      FileOutputStream outStream_input = new FileOutputStream(inputFile, false);
      OutputStreamWriter outStreamWriter_input = new OutputStreamWriter(outStream_input, "utf8");
      BufferedWriter outFile_input = new BufferedWriter(outStreamWriter_input);

      int OPS = outputs_per_sentence[tsk_i];
      int SPP = sentences_per_page[tsk_i];
      int PPB__rand = b_size__rand[batchNumber];
      int srcRep__size = b_srcRep__size[batchNumber];
      int srcRep__repCount = b_srcRep__repCount[batchNumber];
      int PPB_fullRep = b_fullRep__size[batchNumber];
      int PPB = PPB__rand + (srcRep__size*srcRep__repCount) + PPB_fullRep;

      // create header
      outLine = "";
      outLine += "\t" + "\"HIT_info\"";
      outLine += "\t" + "\"fromLang\"";
      outLine += "\t" + "\"fromLang_full\"";
      outLine += "\t" + "\"toLang\"";
      outLine += "\t" + "\"toLang_full\"";
      for (int sen = 1; sen <= SPP; ++sen) {
        outLine += "\t" + "\"srcIndex" + sen + "\"";

        for (int out = 1; out <= OPS; ++out) {
          outLine += "\t" + "\"sys" + sen + "_" + out + "\""; // system number

          outLine += "\t" + "\"sen" + sen + "_" + out + "_part" + 1 + "\""; // the actual output, part 1
          outLine += "\t" + "\"sen" + sen + "_" + out + "_part" + 2 + "\""; // the actual output, part 2
          outLine += "\t" + "\"sen" + sen + "_" + out + "_part" + 3 + "\""; // the actual output, part 3
          outLine += "\t" + "\"sen" + sen + "_" + out + "_part" + 4 + "\""; // the actual output, part 4

        }

      }

      writeLine(outLine.substring(1),outFile_input); // write header

      String[][] sen_info_items = new String[PPB][SPP*(4+OPS)]; // 4 for AID, task, langs, and srcIndex (OPS is for system *names*)

      int page = 0;
      TreeSet<Integer> sen_0__set = new TreeSet<Integer>();
        // to avoid duplicate HIT's within the same batch (other than for local repeats)

      for (; page < PPB-PPB_fullRep; ++page) {

        int sen_0 = 0;
        int expertOrder = randInt(0,SPP-1); // the expertOrder'th sentence on this page will not have random systems.
                                            // Instead, we will use the same OPS systems previously chosen for an expert worker.

        for (int sen = 0; sen < SPP; ++sen) {
          int offset = sen*(4+OPS);
          sen_info_items[page][offset+0] = CID;
          sen_info_items[page][offset+1] = task;
          sen_info_items[page][offset+2] = langs;

///* // use this for clustered sentences
          if (sen == 0) {
            if (page < PPB__rand+srcRep__size) {
              sen_0 = random_list.elementAt(randInt(1,random_list.size())-1);
              int d = 0;
              while (sen_0__set.contains(sen_0) && d < 10) { // avoid duplicates
                sen_0 = random_list.elementAt(randInt(1,random_list.size())-1);
                ++d;
              }
            } else { // if (page >= PPB__rand+srcRep__size && page < PPB) {
              sen_0 = Integer.parseInt(sen_info_items[PPB__rand + ((page-PPB__rand)%srcRep__size)][offset+3]);
            }

            sen_0__set.add(sen_0);

            sen_info_items[page][offset+3] = "" + sen_0;

          } else {
            sen_info_items[page][offset+3] = "" + (sen_0+sen);
          }

          // the source index is sen_0+sen
//*/

/* // use this for non-clustered sentences
          if (page < random_pages_per_batch[tsk_i]) {
            sen_info_items[page][offset+3] = "" + random_list.elementAt(randInt(1,random_list.size())-1);
          } else {
            sen_info_items[page][offset+3] = "" + repeat_list.elementAt(randInt(1,repeat_list.size())-1);
          }
*/

          if (sen == 0 || constant_systems[tsk_i] == 0) {
            String[] systems = null;
            if (sen != expertOrder || !expertSeenSegments.containsKey(langs+"_"+(sen_0+sen))) {
              // choose OPS systems randomly
              systems = randSystems(langPair, OPS);
            } else {
              // choose same OPS systems already shown to an expert annotator
              systems = expertSeenSegments.get(langs+"_"+(sen_0+sen));
            }

            for (int s = 0; s < OPS; ++s) {
              sen_info_items[page][offset+4+s] = systems[s];
            }
          } else {
            for (int s = 0; s < OPS; ++s) {
              sen_info_items[page][offset+4+s] = sen_info_items[page][0+4+s]; // use systems from first sentence,
                                                                              // whose offset was 0*(4+OPS)=0
            }
          }

        } // for (sen)
/*
        print("SII[pg" + page + "]:");
        for (int i = 0; i < SPP*(4+OPS); ++i) {
          print(" " + sen_info_items[page][i]);
        }
        println("");
*/
      } // for (page)
///*
      TreeSet<Integer> fullRepeats = new TreeSet<Integer>(); // for local repeats
      while (fullRepeats.size() < PPB_fullRep) {
        fullRepeats.add(randInt(1,PPB-PPB_fullRep)-1);
      }

      for (Integer repeatPage : fullRepeats) {
        for (int i = 0; i < SPP*(4+OPS); ++i) {
          sen_info_items[page][i] = sen_info_items[repeatPage][i];
        }

//        print("SII[pg" + page + "]:");
//        for (int i = 0; i < SPP*(4+OPS); ++i) {
//          print(" " + sen_info_items[page][i]);
//        }
//        println("");

        ++page;
      }
//*/

/*   *** OLD ***
"HIT_info"	"sen1_info"	"sen1_src"	"sen1_ref"	"sen1_out1"	"sen1_out2"	"sen1_out3"	"sen1_out4"	"sen1_out5"
"worker:AGA961CZDY79B#task:RNK#langs:cz-en#batch:1#HIT:1"	"srcIndex:1#systems:google:uedin:umd:jhu:jhu-LOL-Tromble"	"Some source sentence #1 in foreign language."	"Reference #1."	"translation_for_s1"	"he said ""good day!"""	"he said ""good day!"" to me."	"""good day!"" he said."	"he exclaimed: ""GOOD `~!@#$%^&*()-_=+[]{}\|';:,.<>/? DAY!!!"""
*/

/*
"HIT_info"	"fromLang"	"toLang"	"srcIndex_1"	"sys1_1"	"sys1_2"	"sys1_3"	"sys1_4"	"sys1_5"
"worker:AGA961CZDY79B#task:RNK#langs:cz-en#batch:1#HIT:1"	"de"	"en"	"1"	"1"	"2"	"3"	"4"	"5"
*/

      Vector<Integer> reorderedPages = new Vector<Integer>();
      for (page = 0; page < PPB; ++page) { reorderedPages.add(page); }
      Collections.shuffle(reorderedPages);

      int HITOrder = 0;
//      for (page = 0; page < PPB; ++page) {
      for (Integer pg : reorderedPages) {
        ++HITOrder;
        outLine = "";

        // HIT_info
        outLine += "\t" + "\"";
        outLine += batchName;
        outLine += "#" + "HIT" + ":" + (HITOrder);
        outLine += "\"";

        // fromLang
        outLine += "\t" + "\"";
        outLine += otherName.get(fromLang); // convert to short name
        outLine += "\"";

        outLine += "\t" + "\"";
        outLine += fromLang;
        outLine += "\"";

        // toLang
        outLine += "\t" + "\"";
        outLine += otherName.get(toLang); // convert to short name
        outLine += "\"";

        outLine += "\t" + "\"";
        outLine += toLang;
        outLine += "\"";

        for (int sen = 0; sen < SPP; ++sen) {

          // srcIndex_sen
          int srcIndex = Integer.parseInt(sen_info_items[pg][(sen*(4+OPS))+3]);
          outLine += "\t" + "\"";
          outLine += srcIndex;
          outLine += "\"";



          String[] systems = new String[OPS];

          for (int s = 0; s < OPS; ++s) {
            systems[s] = sen_info_items[pg][(sen*(4+OPS))+4+s];
            outLine += "\t" + "\"";
            outLine += "" + systemNumber.get(systems[s]);
            outLine += "\"";

            String output = " ";
            String output_part1 = " ";
            String output_part2 = " ";
            String output_part3 = " ";
            String output_part4 = " ";

            if (!systems[s].equals("blank")) {
              output = (langPairSubs.get(systems[s]))[srcIndex];
              int partSize = output.length() / 4;

              output_part1 = output.substring(0*partSize,1*partSize);
              output_part2 = output.substring(1*partSize,2*partSize);
              output_part3 = output.substring(2*partSize,3*partSize);
              output_part4 = output.substring(3*partSize);

              output_part1 = output_part1.replaceAll("\"","\"\""); // replace quotes with double quotes
              output_part2 = output_part2.replaceAll("\"","\"\""); // replace quotes with double quotes
              output_part3 = output_part3.replaceAll("\"","\"\""); // replace quotes with double quotes
              output_part4 = output_part4.replaceAll("\"","\"\""); // replace quotes with double quotes
            }

            outLine += "\t" + "\"" + output_part1 + "\"";
            outLine += "\t" + "\"" + output_part2 + "\"";
            outLine += "\t" + "\"" + output_part3 + "\"";
            outLine += "\t" + "\"" + output_part4 + "\"";

          } // for (s)

        } // for (sen)

        writeLine(outLine.substring(1),outFile_input);

      } // for (pg)

      outFile_input.close();

///*
      String propertiesFile = fullPath(rootDir, CID + ".batch" + batchNumber + "." + task + "." + langs + ".properties");
      FileOutputStream outStream_props = new FileOutputStream(propertiesFile, false);
      OutputStreamWriter outStreamWriter_props = new OutputStreamWriter(outStream_props, "utf8");
      BufferedWriter outFile_props = new BufferedWriter(outStreamWriter_props);

      InputStream inStream = new FileInputStream(new File(fullPath(templateDir,task + ".properties.template")));
      BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

      String nextLine = inFile.readLine();

      while (nextLine != null) {
        nextLine = nextLine.replaceAll("%BATCHNUMBER%",""+batchNumber);
        nextLine = nextLine.replaceAll("%FROMLANG%",langPair.first);
        nextLine = nextLine.replaceAll("%TOLANG%",langPair.second);
        nextLine = nextLine.replaceAll("%FL%",otherName.get(langPair.first));
        nextLine = nextLine.replaceAll("%TL%",otherName.get(langPair.second));
        nextLine = nextLine.replaceAll("%PROJECT%",project_name);
        nextLine = nextLine.replaceAll("%COLLECTION%",collectionID);

        writeLine(nextLine,outFile_props);

        nextLine = inFile.readLine();
      }

      writeLine("reward: " + reward,outFile_props);
      writeLine("assignments: " + assgns,outFile_props);
      writeLine("",outFile_props);
/*
      writeLine("# this Assignment Duration value is 60 * 60 = 1 hour",outFile_props);
      writeLine("assignmentduration:3600",outFile_props);
      writeLine("# this HIT Lifetime value is 60*60*24*30 = 30 days",outFile_props);
      writeLine("hitlifetime:2592000",outFile_props);
      writeLine("# this Auto Approval period is 60*60*24*7 = 7 days",outFile_props);
      writeLine("autoapprovaldelay:604800",outFile_props);
      writeLine("",outFile_props);
*/
      writeLine(annStr("HIT_info"),outFile_props);
      writeLine(annStr("fromLang"),outFile_props);
      writeLine(annStr("fromLang_full"),outFile_props);
      writeLine(annStr("toLang"),outFile_props);
      writeLine(annStr("toLang_full"),outFile_props);
      writeLine("",outFile_props);

//      writeLine(annStr(),outFile_props);

      for (int sen = 1; sen <= SPP; ++sen) {

        writeLine(annStr("srcIndex"+sen),outFile_props);

        for (int s = 1; s <= OPS; ++s) {

          writeLine(annStr("sys"+sen+"_"+s),outFile_props);

          for (int p = 1; p <= 4; ++p) {
            writeLine(annStr("sen"+sen+"_"+s+"_"+"part"+p),outFile_props);
          } // for (p)

        } // for (s)

        writeLine("",outFile_props);

      } // for (sen)

      int qualOrder = 0;

      writeLine("######################################",outFile_props);
      writeLine("## Qualification Properties",outFile_props);
      writeLine("######################################",outFile_props);
      writeLine("",outFile_props);

      ++qualOrder;
      writeLine("# Approval rate",outFile_props);
      writeLine("qualification."+qualOrder+":000000000000000000L0",outFile_props);
      writeLine("qualification.comparator."+qualOrder+":GreaterThanOrEqualTo",outFile_props);
      writeLine("qualification.value."+qualOrder+":" + minAppRate,outFile_props);
      writeLine("qualification.private."+qualOrder+":false",outFile_props);
      writeLine("",outFile_props);
 
     ++qualOrder;
      writeLine("# Total approved HITs",outFile_props);
      writeLine("qualification."+qualOrder+":00000000000000000040",outFile_props);
      writeLine("qualification.comparator."+qualOrder+":GreaterThanOrEqualTo",outFile_props);
      writeLine("qualification.value."+qualOrder+":" + minAppHITs,outFile_props);
      writeLine("qualification.private."+qualOrder+":false",outFile_props);
      writeLine("",outFile_props);

      if (locStr != null) {
        String[] locs = locStr.split(",");
        if (locs.length == 1) {
          writeLine("# Location restriction",outFile_props);
        } else {
          writeLine("# Location restrictions",outFile_props);
        }

        for (int j = 0; j < locs.length; ++j) {
          ++qualOrder;
          String location = locs[j];
          writeLine("qualification."+qualOrder+":00000000000000000071",outFile_props);

          if (!location.startsWith("!")) {
            writeLine("qualification.comparator."+qualOrder+":EqualTo",outFile_props);
            writeLine("qualification.locale."+qualOrder+":" + location,outFile_props); // notice, "locale" and not "value"
          } else {
            writeLine("qualification.comparator."+qualOrder+":NotEqualTo",outFile_props);
            writeLine("qualification.locale."+qualOrder+":" + location.substring(1),outFile_props); // notice, substring to remove "!"
          }
          writeLine("qualification.private."+qualOrder+":false",outFile_props);
          writeLine("",outFile_props);
        } // for (j)
      }

      if (qualStr != null) {
        String[] quals = qualStr.split(",");
        if (quals.length == 1) {
          writeLine("# Additional qualification",outFile_props);
        } else {
          writeLine("# Additional qualifications",outFile_props);
        }

        for (int j = 0; j < quals.length; ++j) {
          ++qualOrder;
          String qualification = quals[j];
          writeLine("qualification."+qualOrder+":"+qualification,outFile_props);
          writeLine("qualification.comparator."+qualOrder+":GreaterThanOrEqualTo",outFile_props);
          writeLine("qualification.value."+qualOrder+":" + "1",outFile_props);
          writeLine("qualification.private."+qualOrder+":false",outFile_props);
          writeLine("",outFile_props);
        } // for (j)
      }

      outFile_props.close();
      inFile.close();
//*/


///*
      String questionFile = fullPath(rootDir, CID + "." + task + ".question");
      FileOutputStream outStream_quest = new FileOutputStream(questionFile, false);
      OutputStreamWriter outStreamWriter_quest = new OutputStreamWriter(outStream_quest, "utf8");
      BufferedWriter outFile_quest = new BufferedWriter(outStreamWriter_quest);

      writeLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>",outFile_quest);
      writeLine("<!-- Each $paramname is defined as a field in the input file. -->",outFile_quest);
      writeLine("<!-- Please note that the text starting with <ExternalURL> and -->",outFile_quest);
      writeLine("<!-- ending with </ExternalURL> must remain in a SINGLE LINE.  -->",outFile_quest);
      writeLine("<ExternalQuestion xmlns=\"http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2006-07-14/ExternalQuestion.xsd\">",outFile_quest);

      outLine = "\t";
      outLine += "<ExternalURL>";
      String taskURL = "http://www.YOUR-HOST.com/PATH/TO/YOUR/HOSTING/LOCATION/html/";
      outLine += taskURL;
      outLine += task + ".shtml";
      outLine += "?";

      outLine += "HIT_info=${helper.urlencode($HIT_info)}";
      outLine += "&amp;FL=${helper.urlencode($fromLang)}";
      outLine += "&amp;FL_f=${helper.urlencode($fromLang_full)}";
      outLine += "&amp;TL=${helper.urlencode($toLang)}";
      outLine += "&amp;TL_f=${helper.urlencode($toLang_full)}";

      for (int sen_i = 1; sen_i <= SPP; sen_i++) {

        outLine += "&amp;SI"+sen_i+"=${helper.urlencode($srcIndex"+sen_i+")}";

        for (int cand_i = 1; cand_i <= OPS; cand_i++) {

          outLine += "&amp;s"+sen_i+"_"+cand_i+"=${helper.urlencode($sys"+sen_i+"_"+cand_i+")}";

          for (int part = 1; part <= 4; ++part) {
            outLine += "&amp;sen"+sen_i+"_"+cand_i+"_p"+part+"=${helper.urlencode($sen"+sen_i+"_"+cand_i+"_part"+part+")}";
          } // for (part)

        } // for (cand_i)

      } // for (sen_i)

      outLine += "</ExternalURL>";

      writeLine(outLine,outFile_quest);

      writeLine("\t" + "<FrameHeight>800</FrameHeight>",outFile_quest);

      writeLine("</ExternalQuestion>",outFile_quest);

      outFile_quest.close();
//*/


      // write to upload info file

      writeLine("Batch #" + batchNumber,outFile_info);
      writeLine((new File (inputFile)).getCanonicalPath(),outFile_info);
      writeLine((new File (propertiesFile)).getCanonicalPath(),outFile_info);
      writeLine((new File (questionFile)).getCanonicalPath(),outFile_info);
      writeLine("",outFile_info);

/*
    store batch info
    write batch info
*/

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in generateNewBatch(...): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in generateNewBatch(...): " + e.getMessage());
      System.exit(99902);
    }

  } // generateNewBatch(int)

  static private String annStr(String S)
  {
    return "annotation:${" + S + "}";
  }

  static private String[] randSystems(Pair<String,String> langPair, int count)
  {
    String[] retA = new String[count];

    TreeSet<String> candSystems = new TreeSet<String>((submissions.get(langPair)).keySet());
    TreeSet<String> toRemove = new TreeSet<String>();

    for (String sys : candSystems) {
      if (sys.contains("secondary") || sys.contains("contrastive")) toRemove.add(sys);
    }

    for (String sys : toRemove) candSystems.remove(sys);

    int added = 0;

    while (added < count && candSystems.size() > 0) {

      String randSys = "";
      int randLoc = randInt(1,candSystems.size());
      int i = 1;
      for (String sys : candSystems) {
        randSys = sys; // i'th system (1-based)
        if (i == randLoc) break;
        else ++i;
      }

      retA[added] = randSys;
      candSystems.remove(randSys);
      ++added;
    }

    while (added < count) {
      retA[added] = "blank";
      ++added;
    }

    return retA;
  }

  static private void processArg(String arg)
  {
    String param = arg.split("=")[0];
    String value = arg.split("=")[1];

    if (param.equals("serverInfo")) {
      serverInfoFile = value;
    } else if (param.equals("batchInfo")) {
      batchInfoFile = value;
    } else if (param.equals("templateLoc")) {
      templateDir = value;
    } else {
      println("Unknown parameter " + param + "...");
      System.exit(99);
    }

  }

  static private int randInt(int strt, int fnsh)
  {
    int retVal = generator.nextInt((fnsh-strt) + 1); // gives value in [0,fnsh-strt]
    return retVal + strt; // returns value in [strt,fnsh]
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

  static private int minOf(int[] A)
  {
    int minVal = A[0];
    for (int i = 1; i < A.length; ++i) {
      if (A[i] < minVal) { minVal = A[i]; }
    }
    return minVal;
  }

  static private int maxOf(int[] A)
  {
    int maxVal = A[0];
    for (int i = 1; i < A.length; ++i) {
      if (A[i] > maxVal) { maxVal = A[i]; }
    }
    return maxVal;
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

  static private void println(Object obj) { System.out.println(obj); }
  static private void print(Object obj) { System.out.print(obj); }

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

    if (pairStr.contains(",")) {
      first = (F)(pairStr.split(",")[0]);
      second = (S)(pairStr.split(",")[1]);
    } else if (pairStr.contains("-")) {
      first = (F)(pairStr.split("-")[0]);
      second = (S)(pairStr.split("-")[1]);
    }
  }
  public String toString() { return "<" + first + "," + second + ">"; }
  public int compareTo(Object obj) { return (this.toString()).compareTo(obj.toString()); }
}
*/
