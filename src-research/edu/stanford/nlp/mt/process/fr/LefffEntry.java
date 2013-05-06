package edu.stanford.nlp.mt.process.fr;


/**
 * An entry in the lefff extensional lexicon.
 * 
 * E.g.,
 * 
 * petite  100 adj [pred="petit_____1<Suj:(sn),Objde:(de-scompl|de-sinf|de-sn),Objà:(à-scompl|à-sinf|à-sn)>",@pers,cat=adj,@fs]  petit_____1 Default fs  %adj_personnel
 * 
 * See: http://alpage.inria.fr/~sagot/lefff.html
 * 
 * @author kevinreschke
 *
 */
public class LefffEntry {

  String word; //Eg. petite
  String pos;  //Eg. adj
  String baseTerm; //Eg. petit
  String gender; // f | m 
  String number; // s | p
  String person; // 1 | 2 | 3
  String verbCode; //P,F,I,J,T,Y,Z,S,C,K, G, W
  
  public static LefffEntry fromLine(String line) {
    LefffEntry le = new LefffEntry();
    
    String[] fields = line.split("\\s");
    
    le.word = fields[0];
    le.pos = fields[2];
    le.baseTerm = fields[4].split("_____")[0];
    
    String tag = fields[6];
    
    if(tag.contains("f")) le.gender = "f";
    else if(tag.contains("m")) le.gender = "m";
    
    if(tag.contains("s")) le.number = "s";
    else if(tag.contains("p")) le.number = "p";
    
    if(tag.contains("1")) le.person = "1";
    else if(tag.contains("2")) le.person = "2";
    else if(tag.contains("3")) le.person = "3";
    
    if(tag.contains("P")) le.verbCode = "P";
    else if(tag.contains("F")) le.verbCode = "F";
    else if(tag.contains("I")) le.verbCode = "I";
    else if(tag.contains("J")) le.verbCode = "J";
    else if(tag.contains("T")) le.verbCode = "T";
    else if(tag.contains("Y")) le.verbCode = "Y";
    else if(tag.contains("Z")) le.verbCode = "Z";
    else if(tag.contains("S")) le.verbCode = "S";
    else if(tag.contains("C")) le.verbCode = "C";
    else if(tag.contains("K")) le.verbCode = "K";
    else if(tag.contains("G")) le.verbCode = "G";
    else if(tag.contains("W")) le.verbCode = "W";

    return le;
  }

  @Override
  public String toString() {
    return "LefffEntry [word=" + word + ", pos=" + pos + ", baseTerm="
        + baseTerm + ", gender=" + gender + ", number=" + number
        + ", person=" + person + ", verbCode=" + verbCode + "]";
  }
  
  
}
