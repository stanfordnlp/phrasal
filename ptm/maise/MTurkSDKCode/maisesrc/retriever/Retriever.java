package retriever;

import java.util.*;
import java.io.*;
import java.text.DecimalFormat;
import java.net.URLDecoder;

import com.amazonaws.mturk.requester.*;
import com.amazonaws.mturk.addon.*;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.util.PropertiesClientConfig;

public class Retriever
{
static boolean showDetails = false;
  static private String sepKeyVal = "|||";
  static private String sepEntEnt = "|||||||||";
  static private String sepSetSet = "---------------------------";

  static private RequesterService service;
  static int searchField_int; // 1: title, 2: keywords, 3: description
  static String keyword; // aka searchQuery
  static boolean isDecisionPass;
  static HashMap<String,String> rejectList = new HashMap<String,String>(); // <AID,reason for rejection>
    // HITs currently *submitted* are subject to rejection if the worker's ID is in this list
    // NOTE: this is NOT a filtering list, and all answers are written to file
  static boolean relistRejected;
  static int firstPage;
  static int lastPage;

  static TreeMap<String,TreeMap<Integer,Integer>> batchProcessedInfo = new TreeMap<String,TreeMap<Integer,Integer>>();
    // TreeMap<batchName,TreeMap<HIT#,count>>
    // batchName format:
    //   COLLECTIONID#batch:N#task:TSK#langs:yz-yz

  static TreeSet<String> seenAssignments = new TreeSet<String>();
    // TreeSet<assignmentInfo>
    // assignmentInfo format:
    //   COLLECTIONID#batch:N#task:TSK#langs:yz-yz###AXXXXXXXXX


//  static String collectionID;

  static final DecimalFormat f2 = new DecimalFormat("###0.00");
  static final DecimalFormat f4 = new DecimalFormat("###0.0000");

  static public void main(String[] args) throws Exception
  {

    String mturkProperties = "../../mturk.properties";
    service = new RequesterService(new PropertiesClientConfig(mturkProperties));

//    collectionID = "emplus2010-c01";

    String answerInfoFile = args[0]; // "answers.emplus2010-c01.log";
    if (!answerInfoFile.startsWith("/") && !answerInfoFile.startsWith("C:")) {
      answerInfoFile = fullPath("../../../",answerInfoFile);
    }
    String searchField = args[1];
    String searchQuery = args[2];
    String isDecisionPass_str = args[3];
    String rejectListFileName = args[4];
    String relistRejected_str = args[5];
    String firstPage_str = args[6];
    String lastPage_str = args[7];

    if (args[0].equals("${answers}") || args[1].equals("${field}") || args[2].equals("${query}")) {

      println("Usage:");
      println("    ant retriever -Danswers=answers.log");
      println("                  -Dfield={title|keywords|description}");
      println("                  -Dquery=SearchSubtring");
      println("                  [-DdecisionPass={true|false}; default is false]");
      println("                  [-DrejectList=rejectList.txt]");
      println("                  [-Drelist={true|false}]");
      println("                  [-DfirstPage=min_page; default is 1]");
      println("                  [-DlastPage=max_page; default is infinity!]");
      println("");
      println("Note: if answers.log already exists, first the contained answers");
      println("      will be processed, and then retrieved answers will be");
      println("      appended to the file.");

      System.exit(9);

    }

    keyword = searchQuery;
    if (searchField.equals("title")) { searchField_int = 1; }
    else if (searchField.equals("keywords")) { searchField_int = 2; }
    else if (searchField.equals("description")) { searchField_int = 3; }
    else { println("Unknown search field " + searchField + "; must be either title, keywords, or description."); System.exit(99); }

    if (!isDecisionPass_str.equals("${decisionPass}")) {
      if (isDecisionPass_str.toLowerCase().equals("true")) { isDecisionPass = true; }
      else if (isDecisionPass_str.toLowerCase().equals("false")) { isDecisionPass = false; }
      else { println("Unknown boolean value " + isDecisionPass_str + "; must be either true or false."); System.exit(99); }
    } else {
      isDecisionPass = false;
    }

    if (isDecisionPass) {
      println("This is a decision pass (all Assignments currently in a Submitted state will be either approved or rejected)");
    } else {
      println("This is a review pass (no Assignment will be approved or rejected)");
    }

    if (!rejectListFileName.equals("${rejectList}")) {
      if (!rejectListFileName.startsWith("/") && !rejectListFileName.startsWith("C:")) {
	// TODO(spenceg): hardcoded path in the maise distro. Yuck.
        rejectListFileName = fullPath("../../../",rejectListFileName);
      }

      if (fileExists(rejectListFileName)) {
        BufferedReader inFile = new BufferedReader(new InputStreamReader(new FileInputStream(new File(rejectListFileName)), "utf8"));

	for (String line; (line = inFile.readLine()) != null;) {
	  String[] toks = line.split("\t");
	  if (toks.length != 2) throw new RuntimeException("Malformed line in file: " + line);
          String AID = toks[0];
          String reason = toks[1];
          rejectList.put(AID,reason);
        }
	inFile.close();

        if (!relistRejected_str.equals("${relist}")) {
          if (relistRejected_str.toLowerCase().equals("true")) { relistRejected = true; }
          else if (relistRejected_str.toLowerCase().equals("false")) { relistRejected = false; }
          else { println("Unknown boolean value " + relistRejected_str + "; must be either true or false."); System.exit(99); }
        } else {
          println("Please specify a value for the relist parameter."); System.exit(99);
        }

      } else {
        println("Rejection list file " + rejectListFileName + " cannot be found!");
        System.exit(5);
      }
    }

    if (!firstPage_str.equals("${firstPage}")) {
      firstPage = Integer.parseInt(firstPage_str);
    } else {
      firstPage = 1;
    }

    if (!lastPage_str.equals("${lastPage}")) {
      lastPage = Integer.parseInt(lastPage_str);
    } else {
      lastPage = -1; // represents infinity
    }

    try {


if (true) {

      if (fileExists(answerInfoFile)) {
        processAssignmentsInFile(answerInfoFile);
        println("Finished reading existing assignments.");
      }


boolean doScan = false;

if (doScan) {

      println("Performing HIT scan...");

      for (int pageNumber = 190; pageNumber <= 310; pageNumber += 1) {

        println("Results page #" + pageNumber);

        SearchHITsResult res = service.searchHITs(SortDirection.Ascending,SearchHITsSortProperty.Title,pageNumber,100,null);

        HIT[] hits = null;

        if (res.getNumResults() == 0) {
          println("(empty page)");
          break; // from for (pageNumber) loop
        } else {
          hits = res.getHIT();
          println("Retrieved " + hits.length + " HITs (" + res.getNumResults() + " according to getNumResults).");

          HIT H = hits[0];
          String HITid = H.getHITId();

          println("HIT[0] on page #" + pageNumber + " has HITId " + HITid);
          println("  Title: " + H.getTitle());
          println("");
        }

	  } // for (pageNumber)

}



      FileOutputStream outStream_answers = new FileOutputStream(answerInfoFile, true); // append
      OutputStreamWriter outStreamWriter_answers = new OutputStreamWriter(outStream_answers, "utf8");
      BufferedWriter outFile_answers = new BufferedWriter(outStreamWriter_answers);

      int matchHITCount = 0;
      int matchHITWithAssgnsCount = 0;
      int matchAssgnCount = 0;
      int matchAssgnApproveCount = 0;
      int matchAssgnRejectCount = 0;
      int matchAssgnSubmitCount = 0;

//      for (int pageNumber = 1; pageNumber <= 0; ++pageNumber) { // to skip entirely
//      for (int pageNumber = 190; pageNumber <= 295; ++pageNumber) { // dialect (with CCB) as of February 27, 2011
//      for (int pageNumber = 21; true; ++pageNumber) { // select (with WMT Admin)
//      for (int pageNumber = 1; true; ++pageNumber) {
      for (int pageNumber = firstPage; (pageNumber <= lastPage || lastPage == -1); ++pageNumber) {

        println("Results page #" + pageNumber);

        SearchHITsResult res = service.searchHITs(SortDirection.Ascending,SearchHITsSortProperty.Title,pageNumber,100,null);

        HIT[] hits = null;
        int numHITs = 0;
        if (res.getNumResults() == 0) {
          println("(empty page)");
          break; // from for (pageNumber) loop
        } else {
          hits = res.getHIT();
          println("Retrieved " + hits.length + " HITs (" + res.getNumResults() + " according to getNumResults).");
          numHITs = hits.length;
        }

Thread.currentThread().sleep(3000);

        for (int i = 0; i < numHITs; ++i) {

          String HITid = hits[i].getHITId();

//          HIT H = service.getHIT(HITid);
          HIT H = hits[i];

          Assignment[] assgns = null;

          if (showDetails || i == 0) {
            assgns = service.getAllAssignmentsForHIT(HITid);
            println("HIT[" + i + "] on page #" + pageNumber + " has HITId " + HITid);
            println("  HITStatus: " + H.getHITStatus());
            println("  Keywords: " + H.getKeywords());
            println("  Title: " + H.getTitle());
            println("  with " + assgns.length + " assignments");
          }

          boolean match = false;

          if (searchField_int == 1) {
            if (H.getTitle().contains(keyword)) match = true;
          } else if (searchField_int == 2) {
            if (H.getKeywords().contains(keyword)) match = true;
          } else if (searchField_int == 3) {
            if (H.getDescription().contains(keyword)) match = true;
          }

          if (match) {

            matchHITCount += 1;
            assgns = service.getAllAssignmentsForHIT(HITid);

            if (assgns.length > 0) {

              println("    HIT[" + i + "] on page #" + pageNumber + " has " + assgns.length + " assignments");
              matchHITWithAssgnsCount += 1;
              matchAssgnCount += assgns.length;

              for (int k = 0; k < assgns.length; ++k) {
                println("      assgn " + k + ": assgnID=" + assgns[k].getAssignmentId() + "; AID=" + assgns[k].getWorkerId() + "; existing status=\"" + assgns[k].getAssignmentStatus() + "\"");

//                println("      answer:");
//                println(assgns[k].getAnswer());

                String assignmentId = assgns[k].getAssignmentId();
                String assignmentStatus = (assgns[k].getAssignmentStatus()).getValue();
                Date startTime_date = (assgns[k].getAcceptTime()).getTime();
                Date endTime_date = (assgns[k].getSubmitTime()).getTime();
                String startTime_str = startTime_date.toString();
                String endTime_str = endTime_date.toString();
                long startTime = (assgns[k].getAcceptTime()).getTimeInMillis();
                long endTime = (assgns[k].getSubmitTime()).getTimeInMillis();

                long workTime = (endTime - startTime)/1000;

                println("      HIT work time: " + workTime);


                TreeMap<String,String> ansMap = processAnswer(assgns[k].getAnswer());
                println("      ansMap.size: " + ansMap.size());
                ansMap.put("HIT-ID",HITid);
                ansMap.put("AssignmentID",assignmentId);
                ansMap.put("StartTime",""+startTime);
                ansMap.put("StartTime_str",startTime_str);
                ansMap.put("EndTime",""+endTime);
                ansMap.put("EndTime_str",endTime_str);
                ansMap.put("WorkTime",""+workTime);

///*
                if (assignmentStatus.equals("Submitted")) {
                  if (!isDecisionPass) {
                    ++matchAssgnSubmitCount;
                  } else {
//                    String assignmentId = assgns[k].getAssignmentId();
                    String AID = ansMap.get("AID");

                    if (rejectList.containsKey(AID) || rejectList.containsKey(assignmentId)) {
                      assignmentStatus = "Rejected";
                      ++matchAssgnRejectCount;
                      println("        Sending rejection request for " + assignmentId);
                      service.rejectAssignment(assignmentId,rejectList.get(AID)); // second String is requester feedback; here we use the reason
                      if (relistRejected) {
                        service.extendHIT(HITid,1,(long)60); // first 1: assignment increment
                                                             // second1: expiration time increment, in seconds (60 is the minimum value)
                      }
                    } else {
                      assignmentStatus = "Approved";
                      ++matchAssgnApproveCount;
                      println("        Sending approval request for " + assignmentId);
                      service.approveAssignment(assignmentId,null); // second String is requester feedback
                    }
                  }
                } // if (Submitted)


                ansMap.put("HITStatus",assignmentStatus); // could be "Submitted", if isDecisionPass is false

//*/

                processTaskAssignment(ansMap,outFile_answers);
                  // add info to batchProcessedInfo, and write to file if previously unseen

              } // for (k)

            } // if (assgns.length > 0)

          } // if (match)

        } // for (i)

        println("");

      } // for (pageNumber)

      println("");

      println("# matching HITs: " + matchHITCount);
      println("# matching HITs with at least one assignment: " + matchHITWithAssgnsCount);
      println("# matching assignments: " + matchAssgnCount);
      println("# matching assignments that were just approved: " + matchAssgnApproveCount);
      println("# matching assignments that were just rejected: " + matchAssgnRejectCount);
      println("# matching assignments that are in a submitted state: " + matchAssgnSubmitCount);

      println("");

      outFile_answers.close();

      for (String batchName : batchProcessedInfo.keySet()) {
        TreeMap<Integer,Integer> batchCountInfo = batchProcessedInfo.get(batchName);
        println("Batch " + batchName + " has " + batchCountInfo.size() + " (at least partially) completed HITs");
      }

      println("");

      for (String batchName : batchProcessedInfo.keySet()) {
        TreeMap<Integer,Integer> batchCountInfo = batchProcessedInfo.get(batchName);

        int maxAssgnCount = 0;
        for (Integer h : batchCountInfo.keySet()) {
          if (batchCountInfo.get(h) > maxAssgnCount) maxAssgnCount = batchCountInfo.get(h);
        }

        println("In batch " + batchName + ":");
        for (int assgnCount = 1; assgnCount <= maxAssgnCount; ++assgnCount) {
          int HITCount = 0;
          for (Integer h : batchCountInfo.keySet()) {
            if (batchCountInfo.get(h) == assgnCount) ++HITCount;
          }
          println("  " + HITCount + " HIT" + (HITCount > 1 ? "s were " : " was ") + "completed " + assgnCount + " time" + (assgnCount > 1 ? "s" : ""));
        }
      }

} // if (true)


    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in main(String[]): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in main(String[]): " + e.getMessage());
      System.exit(99902);
    } catch (InterruptedException e) {
      System.err.println("InterruptedException in main(String[]): " + e.getMessage());
      System.exit(99902);
    }

    System.exit(0);

  } // main(String[] args)

  static private TreeMap<String,String> processAnswer(String ans)
  {
//println(ans);
    TreeMap<String,String> ansMap = new TreeMap<String,String>();

    String[] A = ans.split("\\n");

    for (int i = 0; i < A.length; ++i) {
      String str = A[i];
      if (str.startsWith("<QuestionIdentifier>")) {
        int key_start = "<QuestionIdentifier>".length();
        int key_end = str.length() - "</QuestionIdentifier>".length();
        String key = str.substring(key_start,key_end);
        ++i;
        str = A[i];
        int val_start = "<FreeText>".length();
        int val_end = str.length() - "</FreeText>".length();
        String val = "";
        if (val_start < val_end) val = str.substring(val_start,val_end);
        val = val.replaceAll("%23","#");
        val = val.replaceAll("%3A",":");
try {
        val = URLDecoder.decode(val,"UTF-8");
} catch (Exception e) {
}
if (showDetails)        println("Putting (" + key + "," + val + ")");
        ansMap.put(key,val);
      }
    } // for (i)

    return ansMap;
  }

  static private void writeAnswersToFile(TreeMap<String,String> ansMap, BufferedWriter outFile)
  {
    for (String key : ansMap.keySet()) {
      String val = ansMap.get(key);
      writeLine(key,outFile);
      writeLine(sepKeyVal,outFile);
      writeLine(val,outFile);
      writeLine(sepEntEnt,outFile);
    }
    writeLine(sepSetSet,outFile);
  }


  static private double balance() {
    return service.getAccountBalance();
  }

  static private void processTaskAssignment(TreeMap<String,String> ansMap, BufferedWriter outFile)
  {
    String HIT_info = ansMap.get("HIT_info");
      // e.g. "emplus2010-c01#batch:1#task:RNK#langs:en-cz#HIT:17"

    String workerID = ansMap.get("AID");
      // e.g. "AXXXXXXXXX"

    String assignmentInfo = HIT_info + "###" + workerID;
      // e.g. "emplus2010-c01#batch:1#task:RNK#langs:en-cz#HIT:17###AXXXXXXXXX"

    if (!seenAssignments.contains(assignmentInfo)) {
      seenAssignments.add(assignmentInfo);

      if (HIT_info == null || !HIT_info.contains("#") || !HIT_info.contains("task:")) {

        if (outFile != null) {
          writeAnswersToFile(ansMap,outFile);
        }

      } else {

        String batchName = HIT_info.substring(0,HIT_info.indexOf("#HIT:"));
        int HITOrder = Integer.parseInt(HIT_info.substring(HIT_info.indexOf("#HIT:")+5)); // e.g. 17
        println("Processing HIT #" + HITOrder + " from batch " + batchName + " (by worker " + workerID + ")");

        TreeMap<Integer,Integer> batchCountInfo = batchProcessedInfo.get(batchName);
        if (batchCountInfo == null) batchCountInfo = new TreeMap<Integer,Integer>();

        int count = 0;
        if (batchCountInfo.containsKey(HITOrder)) count = batchCountInfo.get(HITOrder);
        ++count;
        batchCountInfo.put(HITOrder,count);

        batchProcessedInfo.put(batchName,batchCountInfo);

        println("Batch " + batchName + " now has " + batchCountInfo.size() + " (at least partially) processed HITs.");

        if (outFile != null) {
          writeAnswersToFile(ansMap,outFile);
        }

      }

    } // if unprocessed assignment

  } // processTaskAssignment



  static private void processAssignmentsInFile(String inFileName)
  {
    try {
      BufferedReader inFile = new BufferedReader(new InputStreamReader(new FileInputStream(new File(inFileName)), "utf8"));
      for (String line; (line = inFile.readLine()) != null;) {
        TreeMap<String,String> ansMap = new TreeMap<String,String>();
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
          ansMap.put(key, val.trim());
          line = inFile.readLine(); // read next key (or possibly sepSetSet)
        } // while (line != sepSetSet)

        // null = don't write anything
        processTaskAssignment(ansMap, null);
      }
      inFile.close();

    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in processAssignmentsInFile(String): " + e.getMessage());
      System.exit(99901);
    } catch (IOException e) {
      System.err.println("IOException in processAssignmentsInFile(String): " + e.getMessage());
      System.exit(99902);
    }
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

  static private void writeLine(String line, BufferedWriter writer)
  {
    try {
      writer.write(line, 0, line.length());
      writer.newLine();
      writer.flush();
    } catch (IOException e) {
      System.err.println("IOException in writeLine(String,BufferedWriter): " + e.getMessage());
      System.exit(99902);
    }
  }

  static private void println(Object obj) { System.out.println(obj); }
  static private void print(Object obj) { System.out.print(obj); }

}
