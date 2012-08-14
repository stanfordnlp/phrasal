import java.util.*;
import java.io.*;
import java.text.DecimalFormat;

public class CreateServerInfo
{
static Pair dummy;
  static String project_name = "MyProject";
  static int random_seed = 0;
  static String output_file_path = ".";
  static String sources_file_path = ".";
  static String references_file_path = ".";
  static String submissions_file_path = ".";

  static int num_task_types = 0;
//  static int num_domains = 0;
  static int num_langs = 0;

  static String[] task_names;
  static String[] task_short_names;
  static TreeMap<String,Integer> task_short_name_index = new TreeMap<String,Integer>();
  static Pair[][] subtasks; // indexed [task index][i]; both indices are 1-based
  static TreeSet<String> subtaskSet = new TreeSet<String>(); // stores things like 
//static String domain_name; // hack
//  static String[] domain_names;
//  static String[] domain_filename_prefixes;
  static String[] lang_names;
  static String[] lang_short_names;

  static TreeMap<String,String> otherName = new TreeMap<String,String>();

  // size of each of the following arrays will be 1+num_task_types
  static int[] sentence_range_min;
  static int[] sentence_range_max;
  static int[] task_set_size;
//  static int[] global_repeat_set_size;
//  static int[] pages_per_batch;
//  static int[] random_pages_per_batch;
//  static int[] global_repeat_pages_per_batch;
//  static int[] local_repeat_pages_per_batch;
  static int[] sentences_per_page;
  static int[] systems_per_sentence;
  static int[] constant_systems;

  static Random generator;
  static final DecimalFormat f2 = new DecimalFormat("###0.00");
  static final DecimalFormat f4 = new DecimalFormat("###0.0000");
  static final String separator = "|||";

  static public void main(String[] args)
  {
    if (args.length != 1) {
      println("Usage: java CreateServerInfo specs.txt");
      System.exit(1);
    }

    String inFileName = args[0];

    // read specs
    processSpecFile(inFileName);

    // initialize random number generator
    generator = new Random(random_seed);

    try {

      FileOutputStream outStream = new FileOutputStream(fullPath(output_file_path,project_name+"_server_info.txt"), false);
      OutputStreamWriter outStreamWriter = new OutputStreamWriter(outStream, "utf8");
      BufferedWriter outFile = new BufferedWriter(outStreamWriter);


      writeLine("project_name" + " " + separator + " " + "value" + " " + separator + " " + project_name,outFile);
      writeLine("random_seed" + " " + separator + " " + "value" + " " + separator + " " + random_seed,outFile);
      writeLine("output_file_path" + " " + separator + " " + "value" + " " + separator + " " + (new File (output_file_path)).getCanonicalPath(),outFile);

      writeLine("langs" + " " + separator + " " + "count" + " " + separator + " " + num_langs,outFile);

      for (int g = 1; g <= num_langs; ++g) {
        String outLine = "";
        outLine += lang_names[g] + " ";
        outLine += separator + " ";
        outLine += "short_name" + " ";
        outLine += separator + " ";
        outLine += lang_short_names[g] + " ";

        writeLine(outLine.trim(),outFile);
      }


      writeLine("task_types" + " " + separator + " " + "count" + " " + separator + " " + num_task_types,outFile);

      for (int t = 1; t <= num_task_types; ++t) {
        String outLine = "";
        outLine += task_names[t] + " ";
        outLine += separator + " ";
        outLine += "short_name" + " ";
        outLine += separator + " ";
        outLine += task_short_names[t] + " ";

        writeLine(outLine.trim(),outFile);
      }


      for (int t = 1; t <= num_task_types; ++t) {
        String outLine = "";
        outLine += task_names[t] + " ";
        outLine += separator + " ";
        outLine += "settings" + " ";
        outLine += separator + " ";

        outLine += t + " ";
        outLine += task_short_names[t] + " ";
        outLine += sentences_per_page[t] + " ";
        outLine += systems_per_sentence[t] + " ";
        outLine += constant_systems[t] + " ";

        writeLine(outLine.trim(),outFile);

      }


      createLists(outFile);


File df = new File(submissions_file_path);
String[] fileList = df.list();
//println(arrayToString(fileList));

TreeSet<String> systems = new TreeSet<String>();

for (int i = 0; i < fileList.length; ++i) {

//println("fileList[i]: " + fileList[i]);

  if (fileList[i].startsWith(project_name)) {

    String[] A = fileList[i].split("\\.");
//    println(A[0] + "  " + A[1] + "  " + A[2]);

    String srcLang = (A[1].split("-"))[0];
    String tgtLang = (A[1].split("-"))[1];
    systems.add(A[2]);

    boolean includeSubmission = true;
/*
    boolean includeSubmission = false;

    for (int t = 1; t <= num_task_types; ++t) {
      if (subtaskSet.contains(task_short_names[t] + "|||" + srcLang + "-" + tgtLang)) includeSubmission = true;
    }
*/
    if (includeSubmission) {
//        println("    " + task_short_names[t]);
        String outLine = "";

          outLine = "";

          outLine += otherName.get(srcLang) + "-" + otherName.get(tgtLang) + " ";

          outLine += separator + " ";
          outLine += "submission" + " ";
          outLine += separator + " ";
          outLine += A[2] + " "; // system name
          outLine += separator + " ";
          outLine += fullPath(submissions_file_path,fileList[i]) + " ";

          writeLine(outLine.trim(),outFile);

    }

  } // if filename looks like a submission file

} // for (i)


/*
      FileOutputStream outStream_sysMap = new FileOutputStream(fullPath(output_file_path,project_name+"_system_map.txt"), false);
      OutputStreamWriter outStreamWriter_sysMap = new OutputStreamWriter(outStream_sysMap, "utf8");
      BufferedWriter outFile_sysMap = new BufferedWriter(outStreamWriter_sysMap);
*/

int sys_i = 0;
for (String sys : systems) {
  ++sys_i;
  String outLine = "";
  outLine += sys + " ";
  outLine += separator + " ";
  outLine += "system_number" + " ";
  outLine += separator + " ";
  outLine += sys_i + " ";

  writeLine(outLine.trim(),outFile);
//  writeLine(sys_i + "\t" + sys,outFile_sysMap);
}

//outFile_sysMap.close();


println("Creating context HTML folders...");
File dfm = null;
dfm = new File(fullPath(output_file_path,"context-html"));
dfm.mkdir();
dfm = new File(fullPath(output_file_path,"context-html/plus-minus-0"));
dfm.mkdir();
dfm = new File(fullPath(output_file_path,"context-html/plus-minus-2"));
dfm.mkdir();

df = new File(sources_file_path);
fileList = df.list();

for (int i = 0; i < fileList.length; ++i) {

  if (fileList[i].startsWith(project_name) && fileList[i].endsWith(".src")) {

    String[] A = fileList[i].split("\\.");
//    println(A[0] + "  " + A[1] + " " + A[2]);

    String srcLang = (A[1].split("-"))[0];
    String tgtLang = (A[1].split("-"))[1];

    boolean includeSource = true;

    if (includeSource) {

      String outLine = "";

          outLine = "";

          outLine += otherName.get(srcLang) + "-" + otherName.get(tgtLang) + " ";

          outLine += separator + " ";
          outLine += "source" + " ";
          outLine += separator + " ";
          outLine += fullPath(sources_file_path,fileList[i]) + " ";

          writeLine(outLine.trim(),outFile);

      dfm = new File(fullPath(output_file_path,"context-html/plus-minus-0/src."+srcLang+"-"+tgtLang));
      if (!dfm.exists()) {
        println("  " + fileList[i] + " (+-0).");
        dfm.mkdir();
        createContextHTML("src",sources_file_path,fileList[i],0,dfm.getAbsolutePath());
      } else {
        println("  Folder for " + fileList[i] + " (+-0) already exists.");
      }

      dfm = new File(fullPath(output_file_path,"context-html/plus-minus-2/src."+srcLang+"-"+tgtLang));
      if (!dfm.exists()) {
        println("  " + fileList[i] + " (+-2).");
        dfm.mkdir();
        createContextHTML("src",sources_file_path,fileList[i],2,dfm.getAbsolutePath());
      } else {
        println("  Folder for " + fileList[i] + " (+-2) already exists.");
      }

    }

  }
} // for (i)



df = new File(references_file_path);
fileList = df.list();

for (int i = 0; i < fileList.length; ++i) {

  if (fileList[i].startsWith(project_name) && fileList[i].endsWith(".ref")) {

    String[] A = fileList[i].split("\\.");
//    println(A[0] + "  " + A[1] + " " + A[2]);

    String srcLang = (A[1].split("-"))[0];
    String tgtLang = (A[1].split("-"))[1];

    boolean includeReference = true;

    if (includeReference) {

      String outLine = "";

          outLine = "";

          outLine += otherName.get(srcLang) + "-" + otherName.get(tgtLang) + " ";

          outLine += separator + " ";
          outLine += "reference" + " ";
          outLine += separator + " ";
          outLine += fullPath(references_file_path,fileList[i]) + " ";

          writeLine(outLine.trim(),outFile);

      dfm = new File(fullPath(output_file_path,"context-html/plus-minus-0/ref."+srcLang+"-"+tgtLang));
      if (!dfm.exists()) {
        println("  " + fileList[i] + " (+-0).");
        dfm.mkdir();
        createContextHTML("ref",references_file_path,fileList[i],0,dfm.getAbsolutePath());
      } else {
        println("  Folder for " + fileList[i] + " (+-0) already exists.");
      }

      dfm = new File(fullPath(output_file_path,"context-html/plus-minus-2/ref."+srcLang+"-"+tgtLang));
      if (!dfm.exists()) {
        println("  " + fileList[i] + " (+-2).");
        dfm.mkdir();
        createContextHTML("ref",references_file_path,fileList[i],2,dfm.getAbsolutePath());
      } else {
        println("  Folder for " + fileList[i] + " (+-2) already exists.");
      }

    }

  }
} // for (i)







      outFile.close();

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in main(String[]): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in main(String[]): " + e.getMessage());
      System.exit(99902);
    }

    System.exit(0);

  } // main(String[] args)

  static private void processSpecFile(String specFileName)
  {
    println("Processing spec file " + specFileName + "...");
    println("");

    int num_badLines = badLineCount(specFileName);
    if (num_badLines > 0) {
      System.err.println("There are at least " + num_badLines + " malformed lines in the spec file,");
      System.err.println("as detected by the badLineCount method.  Exiting...");
      System.exit(3);
    }

    // set general project settings
    processProjectSettings(specFileName);

    // set num_task_types, num_domains, and num_langs
    //    and set the associated arrays
    processArraySettings(specFileName);

    // create the arrays for task settings, and set their contents
    processTaskSettings(specFileName,num_task_types);

  } // void processSpecFile(String specFileName)


  static private int badLineCount(String fileName)
  {
    // detects most cases of malformed lines

    String[] validSettings_A =
            {"project_name","random_seed","output_file_path","task_names","task_short_names",
             "sources_file_path","references_file_path","submissions_file_path",
//             "domain_names",
//             "domain_filename_prefixes",
             "lang_names","lang_short_names",
//             "global_repeat_size","global_exclude_size"
            };
    String[] validTaskSettings_A =
            {
//             "subtasks",
             "sentence_range_min","sentence_range_max",
             "task_set_size",
//             "global_repeat_set_size",
//             "pages_per_batch","random_pages_per_batch",
//             "global_repeat_pages_per_batch","local_repeat_pages_per_batch",
             "sentences_per_page","systems_per_sentence","constant_systems"};

    HashSet<String> validSettings = new HashSet<String>(Arrays.asList(validSettings_A));
    HashSet<String> validTaskSettings = new HashSet<String>(Arrays.asList(validTaskSettings_A));

    int problemCount = 0;

    try {
      InputStream inStream_specs = new FileInputStream(new File(fileName));
      BufferedReader inFile_specs = new BufferedReader(new InputStreamReader(inStream_specs, "utf8"));

      String line = inFile_specs.readLine();
      while (line != null) {

        if (line.indexOf("#") != -1) { line = line.substring(0,line.indexOf("#")); } // discard comment
        line = line.trim();

        if (line.length() > 0) {

          String startWord = line.split("\\s+")[0];
          if (!validSettings.contains(startWord)) {
            if (startWord.indexOf('_') < 0) {
              System.err.println("Malformed line in spec file: \"" + line + " \"");
              ++problemCount;
            } else {
              startWord = startWord.substring(startWord.indexOf('_')+1); // strip task name and the '_'
              if (!validTaskSettings.contains(startWord)) {
              System.err.println("Malformed line in spec file: \"" + line + " \"");
                ++problemCount;
              }
            } // if no '_'
          } // if !in validSettings

        } // if (length > 0)

        line = inFile_specs.readLine();
      }

      inFile_specs.close();

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in containsBadLine(String): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in containsBadLine(String): " + e.getMessage());
      System.exit(99902);
    }

    return problemCount;

  } // int badLineCount(String fileName)


  static private void processProjectSettings(String fileName)
  {
    // ****** hard-coded for now ******
/*
    project_name = "wmt10";
    random_seed = 2010;
    output_file_path = ".";
    sources_file_path = "wmt10_data/src/plain";
    references_file_path = "wmt10_data/src/plain";
    submissions_file_path = "wmt10_data/submissions/all_plain";
*/
  } // void processProjectSettings(String fileName)


  static private void processArraySettings(String fileName)
  {
    String[] necessarySettings_A =
            {"project_name","random_seed","output_file_path",
             "sources_file_path","references_file_path","submissions_file_path",
             "task_names","task_short_names",
//             "domain_names",
//             "domain_filename_prefixes",
             "lang_names","lang_short_names"};
    HashSet<String> necessarySettings = new HashSet<String>(Arrays.asList(necessarySettings_A));

    int problemCount = 0;

    try {
      InputStream inStream_specs = new FileInputStream(new File(fileName));
      BufferedReader inFile_specs = new BufferedReader(new InputStreamReader(inStream_specs, "utf8"));

      String line = inFile_specs.readLine();
      while (line != null) {

        if (line.indexOf("#") != -1) { line = line.substring(0,line.indexOf("#")); } // discard comment
        line = line.trim();

        if (line.length() > 0) {

          String startWord = line.split("\\s+")[0];
          if (necessarySettings.contains(startWord)) {

            String array_str = (line.substring(startWord.length())).trim(); // strip startWord
            array_str = "\"," + array_str + ",\"";
            String[] arr = array_str.split("\"\\s*,\\s*\""); // split on "," possibly with spaces in between
            // note: arr[0] will be the empty string
            // for (int i = 1; i < arr.length; ++i) { println(arr[i]); } // debugging

            if (startWord.equals("project_name")) {
              project_name = arr[1];
            } else if (startWord.equals("random_seed")) {
              random_seed = Integer.parseInt(arr[1]);
            } else if (startWord.equals("output_file_path")) {
              output_file_path = arr[1];
            } else if (startWord.equals("sources_file_path")) {
              sources_file_path = arr[1];
            } else if (startWord.equals("references_file_path")) {
              references_file_path = arr[1];
            } else if (startWord.equals("submissions_file_path")) {
              submissions_file_path = arr[1];
            } else if (startWord.startsWith("task_")) {
              if (startWord.equals("task_names")) {
                task_names = arr;
              } else if (line.startsWith("task_short_names")) {
                task_short_names = arr;
              }
              if (num_task_types == 0) { num_task_types = arr.length-1; } // first time seen
              else if (num_task_types > 0) { if (num_task_types != arr.length-1) num_task_types = -1; } // mismatch
/*
            } else if (startWord.startsWith("domain_")) {

              if (startWord.equals("domain_names")) {
                domain_names = arr;
              } else if (line.startsWith("domain_filename_prefixes")) {
                domain_filename_prefixes = arr;
              }
              if (num_domains == 0) { num_domains = arr.length-1; } // first time seen
              else if (num_domains > 0) { if (num_domains != arr.length-1) num_domains = -1; } // mismatch
*/
            } else if (startWord.startsWith("lang_")) {

              if (startWord.equals("lang_names")) {
                lang_names = arr;
              } else if (line.startsWith("lang_short_names")) {
                lang_short_names = arr;
              }
              if (num_langs == 0) { num_langs = arr.length-1; } // first time seen
              else if (num_langs > 0) { if (num_langs != arr.length-1) num_langs = -1; } // mismatch

            }

            necessarySettings.remove(startWord);

          } // if startWord in necessarySettings

        }

        line = inFile_specs.readLine();
      }

      inFile_specs.close();


      // if num_X == 0, no X_... setting was seen in spec file
      // if num_X == -1, the X_... settings conflict with each other
      // otherwise, OK

      if (num_task_types <= 0) {
        System.err.println("Malformed spec file: # tasks not set properly.");
        ++problemCount;
      }
/*
      if (num_domains <= 0) {
        System.err.println("Malformed spec file: # domains not set properly.");
        ++problemCount;
      }
*/
      if (num_langs <= 0) {
        System.err.println("Malformed spec file: # languages not set properly.");
        ++problemCount;
      }
      if (!necessarySettings.isEmpty()) {
        // at least one necessary setting was not found
        for (String str : necessarySettings) {
          System.err.println("Spec file did not contain a line to set the contents of " + str);
          ++problemCount;
        }
      }

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in processArraySettings(String): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in processArraySettings(String): " + e.getMessage());
      System.exit(99902);
    }

    if (problemCount == 0) {
      println("First pass summary:");
      println("");

      println("Project name: " + project_name);
      println("Random seed: " + random_seed);
      println("Output file path: " + output_file_path);
      println("Sources file path: " + sources_file_path);
      println("References file path: " + references_file_path);
      println("Submissions file path: " + submissions_file_path);
      println("");

      println("  (*) " + num_task_types + " task types:");
      for (int t = 1; t <= num_task_types; ++t) {
        println("      Task #" + t + ": \"" + task_names[t] + "\" (short name \"" + task_short_names[t] + "\")");
        otherName.put(task_short_names[t],task_names[t]);
        otherName.put(task_names[t],task_short_names[t]);
        task_short_name_index.put(task_short_names[t],t);
      }
      task_names[0] = "Default";
      task_short_names[0] = "def";
      task_short_name_index.put(task_short_names[0],0);
      subtasks = new Pair[1+num_task_types][];
/*
      println("  (*) " + num_domains + " domains:");
      for (int d = 1; d <= num_domains; ++d) {
        println("      Domain #" + d + ": \"" + domain_names[d] + "\" (filename prefix \"" + domain_filename_prefixes[d] + "\")");
        otherName.put(domain_filename_prefixes[d],domain_names[d]);
        otherName.put(domain_names[d],domain_filename_prefixes[d]);
      }
domain_name = domain_names[0]; // hack
*/
      println("  (*) " + num_langs + " languages:");
      for (int g = 1; g <= num_langs; ++g) {
        println("      Language #" + g + ": \"" + lang_names[g] + "\" (short name \"" + lang_short_names[g] + "\")");
        otherName.put(lang_short_names[g],lang_names[g]);
        otherName.put(lang_names[g],lang_short_names[g]);
      }

      println("");

    } else {
      System.err.println("There are at least " + problemCount + " problems with the spec file,");
      System.err.println("as detected by the processArraySettings method.  Exiting...");
      System.exit(2);
    }

  } // void processArraySettings(String fileName)


  static private void processTaskSettings(String fileName, int taskTypeCount)
  {
    initializeTaskSettings(taskTypeCount);

    setTaskSettings(fileName);

  } // void processTaskSettings(specFileName,taskTypeCount)

  static private void initializeTaskSettings(int taskCount)
  {
    sentence_range_min = new int[1+taskCount];
    sentence_range_max = new int[1+taskCount];
    task_set_size = new int[1+taskCount];
//    global_repeat_set_size = new int[1+taskCount];
//    pages_per_batch = new int[1+taskCount];
//    random_pages_per_batch = new int[1+taskCount];
//    global_repeat_pages_per_batch = new int[1+taskCount];
//    local_repeat_pages_per_batch = new int[1+taskCount];
    sentences_per_page = new int[1+taskCount];
    systems_per_sentence = new int[1+taskCount];
    constant_systems = new int[1+taskCount];

    // initialize all elements to -1
    initializeArray(sentence_range_min,-1);
    initializeArray(sentence_range_max,-1);
    initializeArray(task_set_size,-1);
//    initializeArray(global_repeat_set_size,-1);
//    initializeArray(pages_per_batch,-1);
//    initializeArray(random_pages_per_batch,-1);
//    initializeArray(global_repeat_pages_per_batch,-1);
//    initializeArray(local_repeat_pages_per_batch,-1);
    initializeArray(sentences_per_page,-1);
    initializeArray(systems_per_sentence,-1);
    initializeArray(constant_systems,-1);

  } // void initializeTaskSettings(int taskCount)


  static private void setTaskSettings(String fileName)
  {
    String[] taskSettingNames_A =
            {"sentence_range_min","sentence_range_max","task_set_size",
//             "global_repeat_set_size","pages_per_batch","random_pages_per_batch",
//             "global_repeat_pages_per_batch","local_repeat_pages_per_batch",
             "sentences_per_page","systems_per_sentence","constant_systems"};
    HashSet<String> taskSettingNames = new HashSet<String>(Arrays.asList(taskSettingNames_A));

    try {
      InputStream inStream_specs = new FileInputStream(new File(fileName));
      BufferedReader inFile_specs = new BufferedReader(new InputStreamReader(inStream_specs, "utf8"));

      String line = inFile_specs.readLine();
      while (line != null) {

        if (line.indexOf("#") != -1) { line = line.substring(0,line.indexOf("#")); } // discard comment
        line = line.trim();

        if (line.length() > 0) {

          String startWord = line.split("\\s+")[0];
          if (startWord.indexOf('_') >= 0) {
            int us_i = startWord.indexOf('_'); // underscore index
            String taskShortName = startWord.substring(0,us_i);
            String settingName = startWord.substring(us_i+1);
            if (taskSettingNames.contains(settingName)) {

              int task_i = task_short_name_index.get(taskShortName);
              int value = Integer.parseInt(line.split("\\s+")[1]);

              if (settingName.equals("sentence_range_min")) { sentence_range_min[task_i] = value; }
              else if (settingName.equals("sentence_range_max")) { sentence_range_max[task_i] = value; }
              else if (settingName.equals("task_set_size")) { task_set_size[task_i] = value; }
//              else if (settingName.equals("global_repeat_set_size")) { global_repeat_set_size[task_i] = value; }
//              else if (settingName.equals("pages_per_batch")) { pages_per_batch[task_i] = value; }
//              else if (settingName.equals("random_pages_per_batch")) { random_pages_per_batch[task_i] = value; }
//              else if (settingName.equals("global_repeat_pages_per_batch")) { global_repeat_pages_per_batch[task_i] = value; }
//              else if (settingName.equals("local_repeat_pages_per_batch")) { local_repeat_pages_per_batch[task_i] = value; }
              else if (settingName.equals("sentences_per_page")) { sentences_per_page[task_i] = value; }
              else if (settingName.equals("systems_per_sentence")) { systems_per_sentence[task_i] = value; }
              else if (settingName.equals("constant_systems")) { constant_systems[task_i] = value; }

            } else if (settingName.equals("subtasks")) {
/*
              String array_str = (line.substring(startWord.length())).trim(); // strip startWord
              array_str = "," + array_str + ",";
              String[] arr = array_str.split("\\s*,\\s*"); // split on , possibly with spaces in between
              // note: arr[0] will be the empty string

              int task_i = task_short_name_index.get(taskShortName);
              subtasks[task_i] = new Pair[arr.length];
              for (int p = 1; p < arr.length; ++p) {
                subtasks[task_i][p] = strToPair(arr[p],"-");
                subtaskSet.add(task_short_names[task_i] + "|||" + arr[p]);
              }
*/
            }
          } // if startWord contains '_'

        } // if (line.length() > 0)

        line = inFile_specs.readLine();

      } // while (line != null)

      inFile_specs.close();

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in main(String[]): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in main(String[]): " + e.getMessage());
      System.exit(99902);
    }

    int problemCount = 0;

    for (int t = 1; t <= num_task_types; ++t) {

      if (sentence_range_min[t] == -1) {
        if (sentence_range_min[0] != -1) { sentence_range_min[t] = sentence_range_min[0]; } // backoff to default
        else {
          System.err.println("Cannot find " + task_short_names[t] + "_" + "sentence_range_min" + " in spec file,");
          System.err.println("and no default value is specified either.");
          ++problemCount;
        }
      }

      if (sentence_range_max[t] == -1) {
        if (sentence_range_max[0] != -1) { sentence_range_max[t] = sentence_range_max[0]; } // backoff to default
        else {
          System.err.println("Cannot find " + task_short_names[t] + "_" + "sentence_range_max" + " in spec file,");
          System.err.println("and no default value is specified either.");
          ++problemCount;
        }
      }

      if (task_set_size[t] == -1) {
        if (task_set_size[0] != -1) { task_set_size[t] = task_set_size[0]; } // backoff to default
        else {
          System.err.println("Cannot find " + task_short_names[t] + "_" + "task_set_size" + " in spec file,");
          System.err.println("and no default value is specified either.");
          ++problemCount;
        }
      }
/*
      if (global_repeat_set_size[t] == -1) {
        if (global_repeat_set_size[0] != -1) { global_repeat_set_size[t] = global_repeat_set_size[0]; } // backoff to default
        else {
          System.err.println("Cannot find " + task_short_names[t] + "_" + "global_repeat_set_size" + " in spec file,");
          System.err.println("and no default value is specified either.");
          ++problemCount;
        }
      }
*//*
      if (pages_per_batch[t] == -1) {
        if (pages_per_batch[0] != -1) { pages_per_batch[t] = pages_per_batch[0]; } // backoff to default
        else {
          System.err.println("Cannot find " + task_short_names[t] + "_" + "pages_per_batch" + " in spec file,");
          System.err.println("and no default value is specified either.");
          ++problemCount;
        }
      }
*//*
      if (random_pages_per_batch[t] == -1) {
        if (random_pages_per_batch[0] != -1) { random_pages_per_batch[t] = random_pages_per_batch[0]; } // backoff to default
        else {
          System.err.println("Cannot find " + task_short_names[t] + "_" + "random_pages_per_batch" + " in spec file,");
          System.err.println("and no default value is specified either.");
          ++problemCount;
        }
      }
*//*
      if (global_repeat_pages_per_batch[t] == -1) {
        if (global_repeat_pages_per_batch[0] != -1) { global_repeat_pages_per_batch[t] = global_repeat_pages_per_batch[0]; } // backoff to default
        else {
          System.err.println("Cannot find " + task_short_names[t] + "_" + "global_repeat_pages_per_batch" + " in spec file,");
          System.err.println("and no default value is specified either.");
          ++problemCount;
        }
      }
*//*
      if (local_repeat_pages_per_batch[t] == -1) {
        if (local_repeat_pages_per_batch[0] != -1) { local_repeat_pages_per_batch[t] = local_repeat_pages_per_batch[0]; } // backoff to default
        else {
          System.err.println("Cannot find " + task_short_names[t] + "_" + "local_repeat_pages_per_batch" + " in spec file,");
          System.err.println("and no default value is specified either.");
          ++problemCount;
        }
      }
*/
      if (sentences_per_page[t] == -1) {
        if (sentences_per_page[0] != -1) { sentences_per_page[t] = sentences_per_page[0]; } // backoff to default
        else {
          System.err.println("Cannot find " + task_short_names[t] + "_" + "sentences_per_page" + " in spec file,");
          System.err.println("and no default value is specified either.");
          ++problemCount;
        }
      }

      if (systems_per_sentence[t] == -1) {
        if (systems_per_sentence[0] != -1) { systems_per_sentence[t] = systems_per_sentence[0]; } // backoff to default
        else {
          System.err.println("Cannot find " + task_short_names[t] + "_" + "systems_per_sentence" + " in spec file,");
          System.err.println("and no default value is specified either.");
          ++problemCount;
        }
      }

      if (constant_systems[t] == -1) {
        if (constant_systems[0] != -1) { constant_systems[t] = constant_systems[0]; } // backoff to default
        else {
          System.err.println("Cannot find " + task_short_names[t] + "_" + "constant_systems" + " in spec file,");
          System.err.println("and no default value is specified either.");
          ++problemCount;
        }
      }
/*
      if (pages_per_batch[t] != random_pages_per_batch[t] + global_repeat_pages_per_batch[t] + local_repeat_pages_per_batch[t]) {
        System.err.println("The sum of random+global_repeat+local_repeat pages must be");
        System.err.println("  equal to pages_per_batch, yet for the " + task_names[t] + " task,");
        System.err.println("  " + random_pages_per_batch[t] + "+" + global_repeat_pages_per_batch[t] + "+" + local_repeat_pages_per_batch[t]
                         + " != " + pages_per_batch[t]);
        ++problemCount;
      }
*/
    } // for (t)

    if (problemCount == 0) {
      println("Task settings: (" + arrayToString(task_short_names) + ")");
      println("  sentence_range_min: " + arrayToString(sentence_range_min));
      println("  sentence_range_max: " + arrayToString(sentence_range_max));
      println("  task_set_size: " + arrayToString(task_set_size));
//      println("  global_repeat_set_size: " + arrayToString(global_repeat_set_size));
//      println("  pages_per_batch: " + arrayToString(pages_per_batch));
//      println("  random_pages_per_batch: " + arrayToString(random_pages_per_batch));
//      println("  global_repeat_pages_per_batch: " + arrayToString(global_repeat_pages_per_batch));
//      println("  local_repeat_pages_per_batch: " + arrayToString(local_repeat_pages_per_batch));
      println("  sentences_per_page: " + arrayToString(sentences_per_page));
      println("  systems_per_sentence: " + arrayToString(systems_per_sentence));
      println("  constant_systems: " + arrayToString(constant_systems));
      println("");
/*
      println("Subtasks:");
      for (int t = 1; t <= num_task_types; ++t) {
        println("  For task #" + t + " (" + task_short_names[t]+ "):");
        println("    " + arrayToString(subtasks[t],1));
      }
*/
    } else {
      System.err.println("There are at least " + problemCount + " problems with the spec file,");
      System.err.println("as detected by the setTaskSettings method.  Exiting...");
      System.exit(4);
    }

  } // void setTaskSettings(String fileName)


  static public TreeSet<Integer>[] divideIntoClusters()
  {
    TreeSet<Integer>[] taskSubset_clusters = new TreeSet[1+num_task_types];
    for (int tsk_i = 0; tsk_i <= num_task_types; ++tsk_i) {
      taskSubset_clusters[tsk_i] = new TreeSet<Integer>();
    }

    int min_min = minOf(sentence_range_min);
    int max_max = maxOf(sentence_range_max);
    int totalSenCount = max_max - min_min + 1;

    // we will pretend there's an extra "task" indexed at tsk_i = 0, with clusters of size 1
    // this task will get assigned to it any sentences not assigned to the actual tasks
    int[] clusterSize = new int[1+num_task_types];
    int[] clusterCount = new int[1+num_task_types];
    int[] actual_task_set_size = new int[1+num_task_types];
    // and remember: task_set_size[]
    //               sentences_per_page[]

    int dummy = 0;
    for (int tsk_i = 1; tsk_i <= num_task_types; ++tsk_i) {
      clusterSize[tsk_i] = sentences_per_page[tsk_i];
      clusterCount[tsk_i] = task_set_size[tsk_i] / clusterSize[tsk_i];
      actual_task_set_size[tsk_i] = clusterCount[tsk_i] * clusterSize[tsk_i];
      dummy += actual_task_set_size[tsk_i];
    }

    actual_task_set_size[0] = (totalSenCount - dummy) + 2; // +2 for dummy sentences before and after
    clusterSize[0] = 1;
    clusterCount[0] = actual_task_set_size[0];
/*
    for (int tsk_i = 0; tsk_i <= num_task_types; ++tsk_i) {
      println("tsk_i " + tsk_i + ": actualSize / clusterSize / clusterCount = " + actual_task_set_size[tsk_i] + " / " + clusterSize[tsk_i] + " / " + clusterCount[tsk_i]);
    }
*/
    double[] completed_frac = new double[1+num_task_types]; // what fraction of clusters have been assigned

    // first, assign sentences to task 0:
    // (*) the two dummy sentences
    taskSubset_clusters[0].add(0);
    taskSubset_clusters[0].add(totalSenCount+1);

    // (*) sentences not assignable to other tasks
    for (int i = min_min; i <= max_max; ++i) {
      boolean assignable = false;
      for (int tsk_i = 1; tsk_i <= num_task_types; ++tsk_i) {
        if (sentence_range_min[tsk_i] <= i && sentence_range_max[tsk_i] >= i) {
          assignable = true;
          break; // from for (tsk_i)
        }
      } // for (tsk_i)
      if (!assignable) {
        taskSubset_clusters[0].add(i);
      }
    }

    completed_frac[0] = taskSubset_clusters[0].size() / (double)clusterCount[0];



    for (int i = min_min; i <= max_max; ) {
      // i is the start of the next cluster

      // determine minFrac_tsk, the task index with the lowest completion fraction...
      double minFrac = completed_frac[0];
      int minFrac_tsk = 0;
      for (int tsk_i = 1; tsk_i <= num_task_types; ++tsk_i) {
        if (completed_frac[tsk_i] < minFrac && sentence_range_min[tsk_i] <= i && sentence_range_max[tsk_i] >= i) {
          minFrac = completed_frac[tsk_i];
          minFrac_tsk = tsk_i;
        }
      }

      // ...assign next cluster for minFrac_tsk...
      taskSubset_clusters[minFrac_tsk].add(i);

      // ...update its completion fraction
      completed_frac[minFrac_tsk] = taskSubset_clusters[minFrac_tsk].size() / (double)clusterCount[minFrac_tsk];

      // ...and advance i appropriately
      i += clusterSize[minFrac_tsk];

    }

    return taskSubset_clusters;

  }

  static public void createLists(BufferedWriter outFile)
  {
    try {
      TreeSet<Integer> availableIndices = new TreeSet<Integer>();
      int min_min = minOf(sentence_range_min);
      int max_max = maxOf(sentence_range_max);
      for (int i = min_min; i <= max_max; ++i) availableIndices.add(i);

//      TreeSet[] taskSubset = new TreeSet[1+num_task_types];
      TreeSet<Integer>[] taskSubset = divideIntoClusters();

      String outLine = "";

      for (int t = 1; t <= num_task_types; ++t) {
//        taskSubset[t] = randomIndices(availableIndices,task_set_size[t],sentence_range_min[t],sentence_range_max[t],true);

        outLine = "";
        outLine += task_names[t] + " ";
        outLine += separator + " ";
        outLine += "random_list" + " ";
        outLine += separator + " ";

        for (Integer ind : taskSubset[t]) { outLine += ind + "-" + (ind+sentences_per_page[t]-1) + " "; }

        writeLine(outLine.trim(),outFile);
/*
        for (int st = 1; st < subtasks[t].length; ++st) {
          outLine = "";

          String srcLang = (String)subtasks[t][st].first;
          String tgtLang = (String)subtasks[t][st].second;
          outLine += otherName.get(srcLang) + "-" + otherName.get(tgtLang) + " ";

          outLine += task_names[t] + " ";

          outLine += separator + " ";

          outLine += "subtask" + " ";

          outLine += separator + " ";

          outLine += "#" + " ";

          writeLine(outLine.trim(),outFile);

        } // for (st)
*/
/*
        for (int st = 1; st < subtasks[t].length; ++st) {
          outLine = "";

          String srcLang = (String)subtasks[t][st].first;
          String tgtLang = (String)subtasks[t][st].second;
          outLine += otherName.get(srcLang) + "-" + otherName.get(tgtLang) + " ";

          outLine += task_names[t] + " ";

          outLine += separator + " ";

          outLine += "repeat_list" + " ";

          outLine += separator + " ";

          TreeSet<Integer> subtask_repeatList =
            randomIndices(taskSubset[t],global_repeat_set_size[t]/sentences_per_page[t],
                          (Integer)taskSubset[t].first(),(Integer)taskSubset[t].last(),true);

          for (Integer ind : subtask_repeatList) { outLine += ind + "-" + (ind+sentences_per_page[t]-1) + " "; }

          writeLine(outLine.trim(),outFile);

        } // for (st)
*/
      } // for (t)

    } catch (IOException e) {
      System.err.println("IOException in createLists(String[]): " + e.getMessage());
      System.exit(99902);
    }

  } // void createLists(BufferedWriter outFile)


  static private TreeSet<Integer> randomIndices(TreeSet<Integer> availableIndices, int desiredSize, int minIndex, int maxIndex, boolean withRemoval)
  {
    TreeSet<Integer> retSet = new TreeSet<Integer>();

    if (desiredSize > availableIndices.size()) {
      System.err.println("The availableIndices set (of size " + availableIndices.size() + ") passed to randomIndices");
      System.err.println("is not large enough (need at least " + desiredSize + " elements).");
      System.exit(5);
    } else {
      ArrayList<Integer> list = new ArrayList<Integer>(availableIndices);
      Collections.shuffle(list,generator);
      int added = 0;
      int i = 0;
      while (added < desiredSize) {
        int x = -1;
        while (x == -1) {
          x = list.get(i);
          if (x >= minIndex && x <= maxIndex) {
            retSet.add(x);
            if (withRemoval) { availableIndices.remove(x); }
          }
          ++i;
        }
        ++added;
      }

    } // if (desiredSize > availableIndices.size())

    return retSet;

  }

  static private void createRankTemplate()
  {


  }

  static private void createEditTemplate()
  {

  }

  static private void createContextHTML(String type, String inDirName, String inFileName, int windowHalfSize, String outDir)
  {
    inFileName = fullPath(inDirName, inFileName);

    int n = countLines(inFileName);
    String[] sentences = new String[1+n];

    int s = windowHalfSize; // half-size of window (excluding center)

    boolean isSource = true;
    if (type.equals("src")) isSource = true;
    else isSource = false;

    try {

      InputStream inStream = new FileInputStream(new File(inFileName));
      BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

      for (int i = 1; i <= n; ++i) {
        String line = inFile.readLine();
        sentences[i] = line;
      }

      inFile.close();

      for (int i = 1; i <= n; ++i) {

        FileOutputStream outStream = new FileOutputStream(fullPath(outDir,""+i+".html"), false);
        OutputStreamWriter outStreamWriter = new OutputStreamWriter(outStream, "utf8");
        BufferedWriter outFile = new BufferedWriter(outStreamWriter);

        writeLine("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 Transitional//EN\">",outFile);
        writeLine("<html>",outFile);
        writeLine("  <head>",outFile);
        writeLine("    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>",outFile);
        writeLine("  </head>",outFile);
        writeLine("  <p>",outFile);

        if (isSource) {
          writeLine("    <font color=crimson>",outFile); // for source sentences
          writeLine("      <b><u>Source</u>:</b><br>",outFile);
        } else {
          writeLine("    <font color=#008000>",outFile); // for reference sentences
          writeLine("      <b><u>Reference</u>:</b><br>",outFile);
        }

        for (int i2 = i - s; i2 <= i + s; ++i2) {
          if (i2 == i) {
            writeLine("      <b>" + sentences[i] + "</b><br>",outFile);
          } else if (i2 >= 1 && i2 <= n) {
            writeLine("      " + sentences[i2] + "<br>",outFile);
          }
        }
        writeLine("    </font>",outFile);
        writeLine("  </p>",outFile);
        writeLine("</html>",outFile);

        outFile.close();

      } // for (a)

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in main(String[]): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in main(String[]): " + e.getMessage());
      System.exit(99902);
    }

  }

  static private String arrayToString(int[] A)
  {
    return arrayToString(A,0,A.length-1);
  }

  static private String arrayToString(int[] A, int firstIndex)
  {
    return arrayToString(A,firstIndex,A.length-1);
  }

  static private String arrayToString(int[] A, int firstIndex, int lastIndex)
  {
    String str = "";
    str += "{";
    for (int i = firstIndex; i < lastIndex; ++i) str += A[i] + ",";
    str += A[lastIndex];
    str += "}";

    return str;
  }

  static private String arrayToString(Object[] A)
  {
    return arrayToString(A,0,A.length-1);
  }

  static private String arrayToString(Object[] A, int firstIndex)
  {
    return arrayToString(A,firstIndex,A.length-1);
  }

  static private String arrayToString(Object[] A, int firstIndex, int lastIndex)
  {
    String str = "";
    str += "{";
    for (int i = firstIndex; i < lastIndex; ++i) str += A[i] + ",";
    str += A[lastIndex];
    str += "}";

    return str;
  }

  static private void initializeArray(int[] A, int x)
  {
    for (int i = 0; i < A.length; ++i) A[i] = x;
  }

  static private void initializeArray(int[] A)
  {
    initializeArray(A,0);
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

  static private Pair<String,String> strToPair(String str, String splitStr)
  {
    String first = str.substring(0,str.indexOf(splitStr));
    String second = str.substring(str.indexOf(splitStr)+1);
    Pair<String,String> retPair = new Pair<String,String>(first,second);
    return retPair;
  }

  static private String fullPath(String dir, String fileName)
  {
    File dummyFile = new File(dir,fileName);
    return dummyFile.getAbsolutePath();
  }

  static private void println(Object obj) { System.out.println(obj); }
  static private void print(Object obj) { System.out.print(obj); }

}
/*
class Pair<F,S> {
  public F first;
  public S second;
  public Pair(F x, S y) { first = x; second = y; }
  public String toString() { return "<" + first + "," + second + ">"; }
}
*/
