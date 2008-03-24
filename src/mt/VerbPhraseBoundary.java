package mt;
import edu.stanford.nlp.util.IntQuadruple;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.*;

public class VerbPhraseBoundary {
  
  Map<IntQuadruple,String> phrasesBoundary = null;
  
  public Map<IntQuadruple,String> getBoundaries() {
    return phrasesBoundary;
  }

  public VerbPhraseBoundary(String sStr) {
    init(sStr);
  }
  
  public void init(String sStr) {
    System.err.println("PBDEBUG: sStr="+sStr);
    String[] spans = sStr.split(";");
    phrasesBoundary = new HashMap<IntQuadruple,String>();
    
    Pattern pattern = Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+),([^,]+P),(VP),(VP)");
    for (String span : spans) {
      String[] toks = span.split(",");
      if (toks.length != 7) continue;
      if (!span.endsWith("P,VP,VP")) continue;
      System.err.println("PBDEBUG: span="+span);
      Matcher matcher = pattern.matcher(span);
      if (!matcher.matches()) throw new RuntimeException("span format error: "+span);
      int i1 = Integer.parseInt(matcher.group(1));
      int i2 = Integer.parseInt(matcher.group(2));
      int i3 = Integer.parseInt(matcher.group(3));
      int i4 = Integer.parseInt(matcher.group(4));
      IntQuadruple iq = new IntQuadruple(i1,i2,i3,i4);
      if (phrasesBoundary.get(iq) != null) throw new RuntimeException("duplicate range");
      StringBuilder sb = new StringBuilder();
      sb.append(matcher.group(7)).append("-").append(matcher.group(5)).append(":").append(matcher.group(6));
      phrasesBoundary.put(iq,sb.toString());
    }
  }
}