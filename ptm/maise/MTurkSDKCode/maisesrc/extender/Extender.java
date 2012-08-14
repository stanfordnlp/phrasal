package extender;

import java.util.*;
import java.io.*;
import java.text.DecimalFormat;
import java.net.URLDecoder;

import com.amazonaws.mturk.requester.*;
import com.amazonaws.mturk.addon.*;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.util.PropertiesClientConfig;

public class Extender
{
static boolean showDetails = false;

  static private RequesterService service;
  static int searchField_int; // 1: title, 2: keywords, 3: description
  static String keyword; // aka searchQuery
  static int extraTime;
  static int firstPage;
  static int lastPage;

  static final DecimalFormat f2 = new DecimalFormat("###0.00");
  static final DecimalFormat f4 = new DecimalFormat("###0.0000");

  static public void main(String[] args) throws Exception
  {

    String mturkProperties = "../../mturk.properties";
    service = new RequesterService(new PropertiesClientConfig(mturkProperties));

    String searchField = args[0];
    String searchQuery = args[1];
    String extraTime_str = args[2];
    String firstPage_str = args[3];
    String lastPage_str = args[4];

    if (args[0].equals("${field}") || args[1].equals("${query}") || args[2].equals("${time}")) {

      println("Usage:");
      println("    ant extender -Dfield={title|keywords|description}");
      println("                 -Dquery=SearchSubtring");
      println("                 -Dtime=extra_time_seconds");
      println("                 [-DfirstPage=min_page]");
      println("                 [-DlastPage=max_page]");

      System.exit(9);

    }

    keyword = searchQuery;
    if (searchField.equals("title")) { searchField_int = 1; }
    else if (searchField.equals("keywords")) { searchField_int = 2; }
    else if (searchField.equals("description")) { searchField_int = 3; }
    else { println("Unknown search field " + searchField + "; must be either title, keywords, or description."); System.exit(99); }

    extraTime = Integer.parseInt(extraTime_str);

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

//service.extendHIT("1GRRXGYBH95P4FTP3S7U73YD8MKHWI",null,(long)60);
/*
Calendar C = Calendar.getInstance();
println(C);
println(C.getTime());
Thread.currentThread().sleep(3000);
println(C);
println(C.getTime());
Thread.currentThread().sleep(3000);
println(C);
println(C.getTime());
Thread.currentThread().sleep(3000);
C.set(2011, 0, 3, 10, 18, 0); // this is 10:18:00 AM on January 3rd, 2011 -- only month is 0-indexed(!)
println(C);
println(C.getTime());
Thread.currentThread().sleep(3000);
println(C);
println(C.getTime());
Thread.currentThread().sleep(3000);
*/
if (true) {

      int matchHITCount = 0;
Vector<String> toExtend = new Vector<String>();

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

          if (showDetails || i == 0) {
            println("HIT[" + i + "] on page #" + pageNumber + " has HITId " + HITid);
            println("  HITStatus: " + H.getHITStatus());
            println("  Keywords: " + H.getKeywords());
            println("  Title: " + H.getTitle());
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
//            service.extendHIT(HITid,null,(long)extraTime); // null: assignment increment (none)
            toExtend.add(HITid);

          } // if (match)

        } // for (i)

        println("");

      } // for (pageNumber)

      println("");

      println("# matching HITs: " + matchHITCount);

/*
      if (toExtend.size() > 0) {

        String[] toExtend_A = new String[toExtend.size()];
        for (int i = 0; i < toExtend_A.length; ++i) {
          toExtend_A[i] = toExtend.elementAt(i);
        }
        service.extendHITs(toExtend_A,null,(long)extraTime,null); // first null: assignment increment (none)
                                                                  // second null: BatchItemCallback callback (not necessary)
      }
*/

      int iteration = 0;
      int batchSize = 100;

      while (toExtend.size() > 0) {

        ++iteration;
        println("Extension iteration #" + iteration + "; about to extend " + toExtend.size() + " HITs...");

        Vector<String> toExtend_failed = new Vector<String>();
        com.amazonaws.mturk.service.axis.AsyncReply[] replies = new com.amazonaws.mturk.service.axis.AsyncReply[batchSize];

        int numBatches = toExtend.size() / batchSize;
        for (int b = 0; b <= numBatches; ++b) {
          int i1 = b * batchSize;
          int i2 = Math.min(i1 + batchSize,toExtend.size());
          // batch is [i1,i2), e.g. first batch is 0-99 (inclusive) if batchSize is 100

          println("Extending batch #" + (b+1) + "; HITs indexed [" + i1 + "," + i2 + ")");

          // submit requests to work queue
          for (int i = i1; i < i2; ++i) {
            replies[i-i1] = service.extendHITAsync(toExtend.elementAt(i), null, (long)extraTime, null);
              // first null: assignment increment (none)
              // second null: BatchItemCallback callback (not necessary)
          }

          // wait for results
          for (int i = i1; i < i2; ++i) {
            try {
              Object result = replies[i-i1].getResult();
            } catch (com.amazonaws.mturk.service.exception.ServiceException e) {
              println("  Failed to extend HIT #" + i + " (" + toExtend.elementAt(i) + ")");
              toExtend_failed.add(toExtend.elementAt(i));
            }
          } // for (i)

        } // for (b)

        toExtend.clear();

        if (toExtend_failed.size() > 0) {
          println("*** Failed to extend " + toExtend_failed.size() + " HITs in this iteration ***");
          for (String id : toExtend_failed) toExtend.add(id);
          println("");
        } else {
          println("*** All HITs extended successfully in this iteration ***");
        }

      } // while (toExtend.size() > 0)


} // if (true)


    } catch (InterruptedException e) {
      System.err.println("InterruptedException in main(String[]): " + e.getMessage());
      System.exit(99902);
    }

    System.exit(0);

  } // main(String[] args)



  static private double balance() {
    return service.getAccountBalance();
  }

  static private void println(Object obj) { System.out.println(obj); }
  static private void print(Object obj) { System.out.print(obj); }

}
