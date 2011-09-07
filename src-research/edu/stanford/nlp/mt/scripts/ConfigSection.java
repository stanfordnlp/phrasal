package edu.stanford.nlp.mt.scripts;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

public class ConfigSection {
  public ConfigSection(String sectionName, List<String> lines) {
    this.sectionName = sectionName;
    this.lines = lines;
  }
  
  public void output(BufferedWriter output) 
    throws IOException
  {
    if (sectionName != null && !sectionName.equals("")) {
      output.write("[" + sectionName + "]");
      output.newLine();
    }
    for (String line : lines) {
      output.write(line);
      output.newLine();
    }
  }
  
  public void update(List<String> lines) {
    this.lines = lines;
  }
  
  final String sectionName;
  public String getSectionName() { return sectionName; }
  
  public List<String> getLines() { return lines; }
  
  List<String> lines;
}
