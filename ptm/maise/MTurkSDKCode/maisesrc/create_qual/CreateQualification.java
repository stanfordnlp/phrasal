package create_qual;

import java.util.*;
import java.io.*;
import java.text.DecimalFormat;
import java.net.URLDecoder;

import com.amazonaws.mturk.requester.*;
import com.amazonaws.mturk.addon.*;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.util.PropertiesClientConfig;

public class CreateQualification
{
  static private RequesterService service;

  static public void main(String[] args)
  {

    String mturkProperties = "../../mturk.properties";
    service = new RequesterService(new PropertiesClientConfig(mturkProperties));

    String newQualName = "WMT11Qual";
    String keywords = "wmt11, manual, evaluation, machine, translation";
    String description = "This is a qualification for participation in WMT11's manual evaluation.";

    String qualID = createQual(newQualName,keywords,description);
    println(qualID);

    System.exit(0);

  } // main(String[] args)

  static QualificationType toQualificationTypeObject(String qualName)
  {
    QualificationType[] QTA = service.getAllQualificationTypes();

    if (QTA == null) {
      return null;
    }

    int match_q = -1;

    for (int q = 0; q < QTA.length; ++q) {
      if (qualName.equals(QTA[q].getName())) {
        match_q = q;
        break; // from for (q)
      }
    }

    QualificationType retQT = null;

    if (match_q != -1) {
      retQT = service.getQualificationType(QTA[match_q].getQualificationTypeId());
    }

    return retQT;
  }

  static private String createQual(String qualName, String keywords, String description)
  {
    // returns qualification type ID
    println("Creating qualification type " + qualName);

    QualificationType dummy = toQualificationTypeObject(qualName);

    if (dummy == null) {
      QualificationType newQual = null;
      try {
        // using the method from RequesterService
//        newQual = service.createQualificationType(qualName,keywords,description);

        // using the method from RequesterServiceRaw
        newQual = service.createQualificationType(qualName,keywords,description,
        QualificationTypeStatus.Active,(long)3600,fileToString("test_def.txt"),fileToString("answerKey_def.txt"),
        (long)3600,false,null);

/*
    public QualificationType createQualificationType(String name, String keywords, String description,
        QualificationTypeStatus status, Long retryDelayInSeconds, String test, String answerKey,
        Long testDurationInSeconds, Boolean autoGranted, Integer autoGrantedValue) 
*/

      } catch (com.amazonaws.mturk.service.exception.ObjectAlreadyExistsException e) {
        // could happen if qualification type was created but was made inactive
        println("In createQual, a qualification type with name " + qualName + " already exists (either recently created or has been made inactive).");
        newQual = null;
        return null;
      }

      try {
        Thread.currentThread().sleep(3000);
      } catch (InterruptedException e) {
        System.err.println("InterruptedException in createQual: " + e.getLocalizedMessage());
      }

      if (newQual != null) {
        println("New qualification type created:");
        println("  Name:            " + newQual.getName());
        println("  ID:              " + newQual.getQualificationTypeId());
        println("  Keywords:        " + newQual.getKeywords());
        println("  Description:     " + newQual.getDescription());
        println("NOTE: please allow some time for this qualification to be indexed before using it.");
        return newQual.getQualificationTypeId();
      } else {
        println("Could not create qualification type " + qualName + " (RequesterService.createQualificationType returned null).");
        return null;
      }
    } else {
      println("In createQual, a qualification type with name " + qualName + " already exists.");
      return dummy.getQualificationTypeId();
    }
  }

  static private String fileToString(String filename)
  {
    String retStr = "";
    try {

      InputStream inStream = new FileInputStream(new File(filename));
      BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

      String inLine = inFile.readLine();

      while (inLine != null) {
        retStr += inLine + "\n";
        inLine = inFile.readLine();
      }

      inFile.close();
		
    } catch (Exception e) {
      System.err.println("Exception in fileToString: " + e.getLocalizedMessage());
      System.exit(9);
    }

    return retStr;
  }

  static private void println(Object obj) { System.out.println(obj); }
  static private void print(Object obj) { System.out.print(obj); }

}

