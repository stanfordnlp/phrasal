package cleaner;

import java.util.*;
import java.io.*;

import com.amazonaws.mturk.requester.*;
import com.amazonaws.mturk.addon.*;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.util.PropertiesClientConfig;

public class Cleaner
{
  static private RequesterService service;
  static int searchField_int; // 1: title, 2: keywords, 3: description
  static String keyword; // aka searchQuery
  static int firstPage;
  static int lastPage;

  static public void main(String[] args)
  {

    String mturkProperties = "../../mturk.properties";
    service = new RequesterService(new PropertiesClientConfig(mturkProperties));

    boolean deleteAssignable = Boolean.valueOf(args[0]);
    boolean deleteCompleted = Boolean.valueOf(args[1]);
    String searchField = args[2];
    String searchQuery = args[3];
    String firstPage_str = args[4];
    String lastPage_str = args[5];

    if (   args[0].equals("${delAssignable}") || args[1].equals("${delCompleted}")
        || args[2].equals("${field}") || args[3].equals("${query}")) {

      println("Usage:");
      println("    ant cleaner -DdelAssignable={true|false}");
      println("                -DdelCompleted={true|false}");
      println("                -Dfield={title|keywords|description}");
      println("                -Dquery=SearchSubtring");
      println("                [-DfirstPage=min_page]");
      println("                [-DlastPage=max_page]");

      System.exit(9);

    } else {

      keyword = searchQuery;
      if (searchField.equals("title")) { searchField_int = 1; }
      else if (searchField.equals("keywords")) { searchField_int = 2; }
      else if (searchField.equals("description")) { searchField_int = 3; }
      else { println("Unknown search field " + searchField + "; must be one of title, keywords, and description."); System.exit(99); }

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

      if (deleteAssignable && deleteCompleted) {
        deleteAllHITs();
      } else if ( deleteAssignable && !deleteCompleted) {
        deleteAssignableHITs();
      } else if (!deleteAssignable &&  deleteCompleted) {
        deleteCompletedHITs();
      } else {
        println("Cleaner called with both delAssignable and delCompleted set to false; doing nothing.");
      }

    }

    System.exit(0);

  } // main(String[] args)


  static private double balance() {
    return service.getAccountBalance();
  }


  static private void deleteCompletedHITs()
  {
    deleteHITs(false,true);
  }

  static private void deleteAssignableHITs()
  {
    deleteHITs(true,false);
  }

  static private void deleteAllHITs()
  {
    deleteHITs(true,true);
  }

  static private void deleteHITs(boolean deleteAssignable, boolean deleteCompleted)
  {
    try {

      int globalDeletedCount = 0;
      int globalAssgnCount = 0;


HashSet<String> toDispose = new HashSet<String>();

boolean fileList = false;
if (fileList) {
        String filename = "deleteList.txt";
        InputStream inStream = new FileInputStream(new File(filename));
        BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));
        String line = inFile.readLine();
        int i = 1;
        while (line != null) {
          print("" + i + ": " + line);
          HIT H = service.getHIT(line);
          String HITStatus = "" + H.getHITStatus();
          println("   " + HITStatus);
          if (HITStatus.equals("Reviewable")) {
            toDispose.add(line);
          }
          line = inFile.readLine();
          ++i;
        }
        globalDeletedCount = toDispose.size();
} else {
        for (int pageNumber = firstPage; (pageNumber <= lastPage || lastPage == -1); ++pageNumber) {

          print("Page: " + pageNumber + " ");

          SearchHITsResult res = service.searchHITs(SortDirection.Ascending,SearchHITsSortProperty.Title,pageNumber,100,null); //**//

          if (res.getNumResults() > 0) { //**//
            HIT[] hits = res.getHIT(); //**//
            println("Retrieved " + hits.length + " HITs.");
            for (int i = 0; i < hits.length; ++i) {
              String HITid = hits[i].getHITId();

//              HIT H = service.getHIT(HITid);
              HIT H = hits[i];

//              System.out.println("  Title: " + H.getTitle());

              String HITStatus = "" + H.getHITStatus();


              if ((HITStatus.equals("Reviewable") && deleteCompleted) || (HITStatus.equals("Assignable") && deleteAssignable)) {
                // H should be considered for deletion

                boolean match = false;

                if (searchField_int == 1) {
                  if (H.getTitle().contains(keyword)) match = true;
                } else if (searchField_int == 2) {
                  if (H.getKeywords().contains(keyword)) match = true;
                } else if (searchField_int == 3) {
                  if (H.getDescription().contains(keyword)) match = true;
                }

                if (match) {

//                  System.out.println("HIT[" + i + "] has HITId " + HITid);
//                  System.out.println("  HITStatus: " + H.getHITStatus());
//                  System.out.println("  HITReviewStats: " + H.getHITReviewStatus());
//                  System.out.println("  Keywords: " + H.getKeywords());
//                  System.out.println("  Title: " + H.getTitle());

                  Assignment[] assgns = service.getAllAssignmentsForHIT(HITid);
///*
                  for (int k = 0; k < assgns.length; ++k) {
                    String assgnStatus = (assgns[k].getAssignmentStatus()).getValue();
                    if (assgnStatus.equals("Submitted")) {
                      match = false;
                      break; // from for (k) loop
                    }
                  } // for (k)
//*/

                  if (match) { // no Assignment was in Submitted state
///*
                    if (HITStatus.equals("Assignable")) {
                      service.forceExpireHIT(HITid);
                    }
//*/
//                    println("Adding HIT to disposable set");
                    toDispose.add(HITid);

                    globalAssgnCount += assgns.length;
                    ++globalDeletedCount;
                    if (globalDeletedCount % 10 == 0) print(".");
                  } // if (match) for no Assignments in Submitted state

                } // if (match) for search query match

              } // if shouldBeConsideredForDeletion

            } // for (i)

          } else { // i.e. if (numResults == 0)

            break; // from for (pageNumber) loop

          }

        } // for (pageNumber)
}

        println("Marked " + globalDeletedCount + " HITs for deletion.");
        println("  (corresponding to " + globalAssgnCount + " assignments)");

        int disposalProgress = 0;
        if (toDispose.size() > 0) {
          println("About to dispose " + toDispose.size() + " HITs");
          for (String HITid : toDispose) {

            service.disposeHIT(HITid);

            ++disposalProgress;
            if (disposalProgress % 100 == 0) println("+");
            else if (disposalProgress % 10 == 0) print(".");
          } // for (HITid)
        }

    } catch (Exception e) {
      System.err.println("PROBLEM: " + e.getLocalizedMessage());
    }

  }

  static private String fullPath(String dir, String fileName)
  {
    File dummyFile = new File(dir,fileName);
    return dummyFile.getAbsolutePath();
  }

  static private void println(Object obj) { System.out.println(obj); }
  static private void print(Object obj) { System.out.print(obj); }

}
