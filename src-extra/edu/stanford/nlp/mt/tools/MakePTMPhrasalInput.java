package edu.stanford.nlp.mt.tools;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

//import au.com.bytecode.opencsv.CSVReader;



import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.process.ProcessorFactory;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.util.StringUtils;

/**
 * Convert the output of Spence's and Jason's wrangling pipelines
 * to tokenized input for Phrasal.
 * 
 * @author Spence Green
 *
 */
public class MakePTMPhrasalInput {

  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(SplitByInterfaceCondition.class.getName()).append(" src_lang tgt_lang sql_extract_csv > tsv_output").append(nl)
      .append(nl)
      .append(" Options:").append(nl);
    return sb.toString();
  }

  private static Map<String,Integer> argDefs() {
    Map<String,Integer> argDefs = new HashMap<>();
    return argDefs;
  }
  
  /**
   * @param args
   * @throws IOException 
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.err.print(usage());
      System.exit(-1);
    }
    Properties options = StringUtils.argsToProperties(args, argDefs());
    String[] positionalArgs = options.getProperty("").split("\\s+");

    String srcLang = positionalArgs[0];
    String tgtLang = positionalArgs[1];
    String sqlFile = positionalArgs[2];
    
    Preprocessor srcPreproc = ProcessorFactory.getPreprocessor(srcLang);
    Preprocessor tgtPreproc = ProcessorFactory.getPreprocessor(tgtLang);
    
    System.out.printf("%s\t%s\t%s\t%s\t%s\t%s\t%s%n", "doc_id", "seg_id", "username", "mt_tok", "user_tok", "s2mt_tok", "src_tok");
//    CSVReader reader = new CSVReader(new FileReader(sqlFile));
    // Skip header
    boolean seenHeader = false;
//    for (String[] fields; (fields = reader.readNext()) != null;) {
  for (String[] fields = null;;) {
      if ( ! seenHeader) {
        seenHeader = true;
        continue;
      }
//      String segId = String.format("%s:%s", fields[0], fields[1]).replace(".src.json", ".tgt");
      String tgtLine = fields[3].trim();
      String alignStr = extend(fields[5]).trim();
      String srcLine = fields[6].trim();
      SymmetricalWordAlignment s2t = new SymmetricalWordAlignment(srcLine, tgtLine, alignStr);
      SymmetricalWordAlignment s2sPrime = srcPreproc.processAndAlign(srcLine);
      SymmetricalWordAlignment t2tPrime = tgtPreproc.processAndAlign(tgtLine);
      String userTextTok = tgtPreproc.process(fields[3]).toString();
      
      // Want sprime --> tprime
      List<String> alignmentList = new LinkedList<>();
      for (int i = 0, size = s2sPrime.eSize(); i < size; ++i) {
        Set<Integer> alignments = s2sPrime.e2f(i);
        for (int j : alignments) {
          Set<Integer> alignments2 = s2t.f2e(j);
          for (int k : alignments2) {
            Set<Integer> alignments3 = t2tPrime.f2e(k);
            for (int q : alignments3) {
              alignmentList.add(String.format("%d-%d",i,q));
            }
          }
        }
      }
      System.out.printf("%s\t%s\t%s\t%s\t%s\t%s\t%s%n", fields[0], fields[1], fields[2], t2tPrime.e().toString(), userTextTok, Sentence.listToString(alignmentList), s2sPrime.e().toString());
    }
//    reader.close();
  }

  private static String extend(String aStr) {
    String[] alignments = aStr.split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (String algn : alignments) {
      if (algn.contains(",")) {
        String[] fields = algn.split("-");
        assert fields.length == 2;
        String[] tIndices = fields[1].split(",");
        for (String tIdx : tIndices) {
          sb.append(String.format(" %s-%s", fields[0], tIdx));
        }
      } else {
        if (sb.length() > 0) sb.append(" ");
        sb.append(algn);
      }
    }
    return sb.toString();
  }
}
