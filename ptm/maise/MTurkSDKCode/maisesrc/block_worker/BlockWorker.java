package block_worker;

import java.util.*;
import java.io.*;
import java.text.DecimalFormat;
import java.net.URLDecoder;

import com.amazonaws.mturk.requester.*;
import com.amazonaws.mturk.addon.*;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.util.PropertiesClientConfig;

public class BlockWorker
{
  static private RequesterService service;

  static public void main(String[] args) throws Exception
  {

    String mturkProperties = "../../mturk.properties";
    service = new RequesterService(new PropertiesClientConfig(mturkProperties));

    String fileName = args[0];

    if (fileName.equals("${file}")) {

      println("Usage:");
      println("    ant block_worker -Dfile=workersToBlock.txt");

    } else {

      if (!fileName.startsWith("/") && !fileName.startsWith("C:")) {
        fileName = fullPath("../../../",fileName);
      }

      InputStream inStream = new FileInputStream(new File(fileName));
      BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

      String line = inFile.readLine();

      while (line != null) {

        String[] A = line.split("\t");
        String AID_toBlock = A[0];
        String reason = A[1];

        println("Blocking worker " + AID_toBlock);
        println("  Reason: " + reason);
        println("");

        service.blockWorker(AID_toBlock,reason);

        line = inFile.readLine();

      } // while (line != null)

      inFile.close();

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

