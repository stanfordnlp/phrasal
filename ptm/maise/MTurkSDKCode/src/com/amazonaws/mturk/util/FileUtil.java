/*
 * Copyright 2007-2008 Amazon Technologies, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * 
 * http://aws.amazon.com/apache2.0
 * 
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and
 * limitations under the License.
 */ 


package com.amazonaws.mturk.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {

  private String fileName = null;

  public FileUtil(String fileName) throws FileNotFoundException {
    this.fileName = fileName;
  }

  public String getFileName() {
    return this.fileName;
  }

  public String[] getLines() throws IOException {
    List<String> results = new ArrayList<String>();
    BufferedReader bReader = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), "UTF-8"));
    try {
      String thisRow = null;
      while ((thisRow = bReader.readLine()) != null) {
        results.add(thisRow);
      }			
    }
    finally {
      bReader.close();
    }
    return (String[]) results.toArray(new String[results.size()]);	
  }

  public String getString() throws IOException {
    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), "UTF-8"));
    StringBuilder sb = new StringBuilder();
    try {
      String newline = System.getProperty("line.separator");
      int rowNum = 0;
      String line = null;
      while ((line = br.readLine()) != null) {
        if (rowNum++ > 0) {
          sb.append(newline);
        }
        sb.append(line);
      }
    }
    finally {
      br.close();
    }
    return sb.toString();
  }

  public void saveString(String line, boolean append) throws IOException {
    if (line != null) {
      OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(this.fileName, append), "UTF-8");
      try {
        writer.write(line);  
      }
      finally {
        writer.close();
      }
    }
  }
}
