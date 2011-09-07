package edu.stanford.nlp.mt.scripts;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConfigFile {
  public ConfigFile(List<ConfigSection> sections) {
    this.sections = sections;
  }
  
  public void outputFile(String filename) 
    throws IOException
  {
    FileWriter fout = new FileWriter(filename);
    outputFile(fout);
    fout.close();
  }
  
  public void outputFile(Writer output) 
    throws IOException
  {
    BufferedWriter buffered = new BufferedWriter(output);
    outputFile(buffered);
    buffered.flush();
  }
  
  public void outputFile(BufferedWriter output)
    throws IOException
  {
    for (ConfigSection section : sections) {
      section.output(output);
    }
  }
  
  public void removeSection(String sectionName) {
    for (ConfigSection section : sections) {
      if (sectionName.equals(sectionName)) {
        sections.remove(section);
        break;
      }
    }
  }

  public void updateSection(String sectionName, String ... lines) {
    updateSection(sectionName, Arrays.asList(lines));
  }
  
  public void updateSection(String sectionName, List<String> lines) {
    for (ConfigSection section : sections) {
      if (sectionName.equals(section.getSectionName())) {
        section.update(lines);
        return;
      }
    }
    sections.add(new ConfigSection(sectionName, lines));
  }
  
  public List<String> getSection(String sectionName) {
    for (ConfigSection section : sections) {
      if (sectionName.equals(section.getSectionName())) {
        return section.getLines();
      }
    }
    return null;
  }
  
  List<ConfigSection> sections;

  public static ConfigFile readConfigFile(String filename) 
    throws IOException
  {
    return readConfigFile(new FileReader(filename));
  }

  public static ConfigFile readConfigFile(Reader input) 
    throws IOException
  {
    return readConfigFile(new BufferedReader(input));
  }

  public static ConfigFile readConfigFile(BufferedReader input) 
    throws IOException
  {
    List<ConfigSection> sections = new ArrayList<ConfigSection>();
    
    String lastKey = "";
    List<String> lines = new ArrayList<String>();
    
    String line;
    while ((line = input.readLine()) != null) {
      String trimmed = line.trim();
      if (trimmed.length() == 0 || line.charAt(0) == '#') {
        lines.add(line);
        continue;
      }
      if (trimmed.charAt(0) == '[' && 
          trimmed.charAt(trimmed.length() - 1) == ']') {
        sections.add(new ConfigSection(lastKey, lines));
        lastKey = line.substring(1, line.length() - 1);
        lines = new ArrayList<String>();
      } else {
        lines.add(line);
      }
    }
    sections.add(new ConfigSection(lastKey, lines));
    return new ConfigFile(sections);
  }
}
