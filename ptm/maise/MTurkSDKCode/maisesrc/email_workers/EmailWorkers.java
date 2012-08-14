package email_workers;

import java.util.*;
import java.io.*;
import java.text.DecimalFormat;
import java.net.URLDecoder;

import com.amazonaws.mturk.requester.*;
import com.amazonaws.mturk.addon.*;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.util.PropertiesClientConfig;

public class EmailWorkers
{
  static private RequesterService service;

  static public void main(String[] args) throws Exception
  {

    String mturkProperties = "../../mturk.properties";
    service = new RequesterService(new PropertiesClientConfig(mturkProperties));

    String workerList = args[0];
    String messageFile = args[1];

    if (workerList.equals("${file}") || messageFile.equals("${message}")) {

      println("Usage:");
      println("    ant email_workers -DworkerList=workersToEmail.txt");
      println("                      -Dmessage=messageFile.txt");
      println("");
      println("Note: the first line of messageFile will be used as a subject line.");
      println("Note: the worker file is a simple list, with one AID per line.");

    } else {

      if (!workerList.startsWith("/") && !workerList.startsWith("C:")) {
        workerList = fullPath("../../../",workerList);
      }

      if (!messageFile.startsWith("/") && !messageFile.startsWith("C:")) {
        messageFile = fullPath("../../../",messageFile);
      }

      String subjectLine = "";
      String messageText = "";
      String[] recipients = null;

      Vector<String> recipients_V = new Vector<String>();
      InputStream inStream_workers = new FileInputStream(new File(workerList));
      BufferedReader inFile_workers = new BufferedReader(new InputStreamReader(inStream_workers, "utf8"));

      String line = inFile_workers.readLine();

      while (line != null) {
        recipients_V.add(line);
        line = inFile_workers.readLine();
      } // while (line != null)

      inFile_workers.close();

      recipients = new String[recipients_V.size()];
      for (int i = 0; i < recipients.length; ++i) { recipients[i] = recipients_V.elementAt(i); }

      InputStream inStream_msg = new FileInputStream(new File(messageFile));
      BufferedReader inFile_msg = new BufferedReader(new InputStreamReader(inStream_msg, "utf8"));

      subjectLine = inFile_msg.readLine(); // subject line is first line in message file
      line = inFile_msg.readLine();

      while (line != null) {
        messageText += line + "\n";
        line = inFile_msg.readLine();
      } // while (line != null)

      inFile_msg.close();

      service.notifyWorkers(subjectLine,messageText,recipients);

    }

    System.exit(0);

  } // main(String[] args)

  static private String fullPath(String dir, String fileName)
  {
    File dummyFile = new File(dir,fileName);
    return dummyFile.getAbsolutePath();
  }

  static private void println(Object obj) { System.out.println(obj); }
  static private void print(Object obj) { System.out.print(obj); }

}

