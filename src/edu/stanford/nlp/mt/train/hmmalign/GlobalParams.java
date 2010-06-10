package edu.stanford.nlp.mt.train.hmmalign;

/**
 * This class will hold some static parameters that will be used by many classes
 */

import java.io.File;
import java.util.Calendar;
import java.util.TimeZone;

public class GlobalParams {
  static boolean verbose = false;
  static boolean useETagsT = false;// use etags for translation probabilities
  static boolean useETagsA = false;
  static boolean useFTagsA = false;
  static boolean useFTagsT = true;
  static boolean useETagsC = false;
  static boolean windows = false;
  static int saveFreq = 3;//save frequency for translation and alignment tables
  static String eVcb;
  static String fVcb;
  //static String fVcb="c:\\MT\\BG_te\\f.vcb";
  //static String resultPath="c:\\MT\\models\\";
  static String resultPath;
  static boolean dumpAlignments = true;
  static boolean tagsOnly = false;
  static String time;

  public GlobalParams() {
    settime();
    if (windows) {
      eVcb = "c:\\MT\\Small_ef\\e.vcb";
      fVcb = "c:\\MT\\Small_ef\\f.vcb";
      resultPath = "c:\\MT\\models\\";
    } else {
      eVcb = "/nlp/scr4/kristina/MT/corpora/50K_och_tef/e.vcb";
      fVcb = "/nlp/scr4/kristina/MT/corpora/50K_och_tef/f.vcb";
      resultPath = "/dfs/ah/1/tmp/kristina";

    }

    if (dumpAlignments) {
      File f = new File(resultPath + time);
      f.mkdirs();
    }
    if (!windows) {
      resultPath = resultPath + time + "/";
    } else {
      resultPath = resultPath + time + "\\";
    }
  }


  public void settime() {
    /*
    ** on some JDK, the default TimeZone is wrong
    ** we must set the TimeZone manually!!!
    **   Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("EST"));
    */
    Calendar cal = Calendar.getInstance(TimeZone.getDefault());

    String DATE_FORMAT = "MM.dd.HH.mm.ss";
    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(DATE_FORMAT);
    /*
    ** on some JDK, the default TimeZone is wrong
    ** we must set the TimeZone manually!!!
    **     sdf.setTimeZone(TimeZone.getTimeZone("EST"));
    */
    sdf.setTimeZone(TimeZone.getDefault());

    time = sdf.format(cal.getTime());
    System.out.println("Now : " + time);
  }


  public static void main(String[] args) {
    new GlobalParams();
  }


}
