package edu.stanford.nlp.mt.tools;

import edu.stanford.nlp.util.StringUtils;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.train.GIZAWordAlignment;

import java.util.*;
import java.io.*;
import java.util.zip.GZIPInputStream;

/**
 * Analyzing how some Chinese words (given in "chWordList") are aligned to English words in the f2e alignment
 *
 * @author Pi-Chuan Chang
 */

public class AnalyzeF2E {

  @SuppressWarnings("unchecked")
  public static void main(String[] args) {
    
    Properties prop = StringUtils.argsToProperties(args);
    String feAlign = prop.getProperty("feAlign");
    String efAlign = prop.getProperty("efAlign");
    String chWordList = prop.getProperty("chWordList");
    String lineLimitStr = prop.getProperty("sentLimit");

    Map<String, Set<String>> zhW2EnWs = new HashMap<String, Set<String>>();
    Map<String, Boolean> charMap = new HashMap<String, Boolean>();
    LineNumberReader feReader, // = null,
      efReader, // = null, 
      chWordReader; // = null;
    //GIZAWordAlignment sent = new GIZAWordAlignment();
    try {
      feReader = new LineNumberReader
        (new InputStreamReader(new GZIPInputStream(new FileInputStream(feAlign))));
      efReader = new LineNumberReader
        (new InputStreamReader(new GZIPInputStream(new FileInputStream(efAlign))));
      chWordReader = new LineNumberReader
        (new InputStreamReader((new FileInputStream(chWordList))));

      /* read in the char list first */
      String line; // = null;
      while((line=chWordReader.readLine())!=null) {
        line = line.trim();
        //char[] c = line.toCharArray();
        //if (c.length!=1) {
        //  System.err.println("Line format error in chWordList: "+line);
        //}
        //charMap.put(""+c[0], true);
        charMap.put(line, true);
      }
      
      String feLine1, feLine2, feLine3, efLine1, efLine2, efLine3;
      int lineCount = 0;
      int lineLimit = Integer.parseInt(lineLimitStr);

      while (true) {
        feLine1 = feReader.readLine(); efLine1 = efReader.readLine();
        if(feLine1 == null || efLine1 == null) {
          if(feLine1 != null || efLine1 != null)
            throw new IOException("Not same number of lines!");
          break;
        }
        feLine2 = feReader.readLine(); efLine2 = efReader.readLine();
        feLine3 = feReader.readLine(); efLine3 = efReader.readLine();
        GIZAWordAlignment sent = new GIZAWordAlignment(feLine1, feLine2, feLine3, efLine1, efLine2, efLine3);
        //sent.init(feLine1, feLine2, feLine3, efLine1, efLine2, efLine3);

        lineCount++;
        if (lineCount >= lineLimit) {
          System.err.println("Stopping at line "+lineCount);
          break;
        }
        
        if (lineCount % 100000 == 0) {
          System.err.println("line "+lineCount);
        }
        /*
        System.err.println("fe: "+feLine3);
        System.err.println("ef: "+efLine3);
        System.err.println(sent.toString(false));
        System.err.println(sent.toString(true));
        */
        Sequence<IString> f = sent.f();
        Sequence<IString> e = sent.e();
        for (int i = 0; i < f.size(); i++) {
          IString fw = f.get(i);
          
          if(charMap.get(fw.toString())!=null) {
            Set<String> eWs = zhW2EnWs.get(fw.toString());
            if (eWs==null) eWs = new HashSet<String>();
            
            Set<Integer> es = sent.f2e(i);
            //System.err.printf("%s: ",fw);
            for(int ei : es) {
              //System.err.printf("%s ",e.get(ei));
              eWs.add(e.get(ei).toString());
            }
            //System.err.println();
            zhW2EnWs.put(fw.toString(), eWs);
          }
        }
      }
      feReader.close();
      efReader.close();
      chWordReader.close();
      chWordReader = new LineNumberReader
        (new InputStreamReader((new FileInputStream(chWordList))));
      
      /* read in the char list first */
      String chWord; // = null;
      Set<String> totalEnWs = new HashSet<String>();
      while((chWord=chWordReader.readLine())!=null) {
        chWord = chWord.trim();
        Set<String> enWs = zhW2EnWs.get(chWord);

        if (enWs != null) {
          System.out.printf("%s\t%d\t",chWord,enWs.size());
          for(String eW : enWs) {
            System.out.printf("%s ",eW);
            totalEnWs.add(eW);
          }
          System.out.println();
        }
      }
      System.out.printf("Total aligned English words of this list: %d\n", totalEnWs.size());
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
