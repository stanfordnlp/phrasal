package uploader;

import java.util.*;
import java.io.*;

import com.amazonaws.mturk.requester.*;
import com.amazonaws.mturk.addon.*;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.util.PropertiesClientConfig;

public class Uploader
{
  static private RequesterService service;

  static public void main(String[] args) throws Exception
  {

    String mturkProperties = "../../mturk.properties";
    service = new RequesterService(new PropertiesClientConfig(mturkProperties));

    String fileName = args[0];

    if (fileName.equals("${file}")) {

      println("Usage:");
      println("    ant uploader -Dfile=uploadInfo.txt");

    } else {

      if (!fileName.startsWith("/") && !fileName.startsWith("C:")) {
        fileName = fullPath("../../../",fileName);
      }

      InputStream inStream = new FileInputStream(new File(fileName));
      BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

      String line = inFile.readLine();

      while (line != null && line.length() > 0) {

        println(line); // print "Batch #x"

        // read names of relevant files
        String input = inFile.readLine();
        String props = inFile.readLine();
        String quest = inFile.readLine();
        uploadBatch(input,props,quest);

        Thread.currentThread().sleep(3000);

        inFile.readLine(); // skip empty line

        line = inFile.readLine();

      } // while (line != null)

    }

    System.exit(0);

  } // main(String[] args)

  static private double balance() {
    return service.getAccountBalance();
  }

  static private void uploadBatch(String inputFileName, String propertiesFileName, String questionFileName)
  {
      int expectedHITCount = countLines(inputFileName) - 1;
      try {
        HITDataInput input = new HITDataCSVReader(inputFileName);
        HITProperties props = new HITProperties(propertiesFileName);
        HITQuestion question = new HITQuestion(questionFileName);

        HIT[] hits = null;

        HITDataOutput success = new HITDataCSVWriter(inputFileName + ".success");
        HITDataOutput failure = new HITDataCSVWriter(inputFileName + ".failure");
        hits = service.createHITs(input, props, question, success, failure);

        if (hits.length != expectedHITCount) {
          println("hits.length = " + hits.length + " != " + expectedHITCount + " = expected");
          println("Attempting to re-upload failed HITs...");
          Thread.currentThread().sleep(3000);
          copyFile(inputFileName + ".failure",inputFileName+".retry");
          uploadBatch(inputFileName+".retry",propertiesFileName,questionFileName);
        } else {
          println("hits.length = " + hits.length + " = " + expectedHITCount + " = expected (GOOD)");
        }

      } catch (Exception e) {
        System.err.println("PROBLEM: " + e.getLocalizedMessage());
      }
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

  static private void renameFile(String origFileName, String newFileName)
  {
    if (fileExists(origFileName)) {
      deleteFile(newFileName);
      File oldFile = new File(origFileName);
      File newFile = new File(newFileName);
      if (!oldFile.renameTo(newFile)) {
        println("Warning: attempt to rename " + origFileName + " to " + newFileName + " was unsuccessful!");
      }
    } else {
      println("Warning: file " + origFileName + " does not exist! (in renameFile)");
    }
  }

  static private boolean fileExists(String fileName)
  {
    if (fileName == null) return false;
    File checker = new File(fileName);
    return checker.exists();
  }

  static private void deleteFile(String fileName)
  {
    if (fileExists(fileName)) {
      File fd = new File(fileName);
      if (!fd.delete()) {
        println("Warning: attempt to delete " + fileName + " was unsuccessful!");
      }
    }
  }

  static private boolean copyFile(String origFileName, String newFileName)
  {
    try {
      File inputFile = new File(origFileName);
      File outputFile = new File(newFileName);

      InputStream in = new FileInputStream(inputFile);
      OutputStream out = new FileOutputStream(outputFile);

      byte[] buffer = new byte[1024];
      int len;
      while ((len = in.read(buffer)) > 0){
        out.write(buffer, 0, len);
      }
      in.close();
      out.close();

/*
      InputStream inStream = new FileInputStream(new File(origFileName));
      BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

      FileOutputStream outStream = new FileOutputStream(newFileName, false);
      OutputStreamWriter outStreamWriter = new OutputStreamWriter(outStream, "utf8");
      BufferedWriter outFile = new BufferedWriter(outStreamWriter);

      String line;
      while(inFile.ready()) {
        line = inFile.readLine();
        writeLine(line, outFile);
      }

      inFile.close();
      outFile.close();
*/
      return true;
    } catch (FileNotFoundException e) {
      System.err.println("FileNotFoundException in MertCore.copyFile(String,String): " + e.getMessage());
      return false;
    } catch (IOException e) {
      System.err.println("IOException in MertCore.copyFile(String,String): " + e.getMessage());
      return false;
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
