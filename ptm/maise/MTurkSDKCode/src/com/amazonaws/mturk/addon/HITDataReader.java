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


package com.amazonaws.mturk.addon;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.amazonaws.mturk.util.FileUtil;

/**
 * The HITDataReader class provides a structured way to access HIT data stored in a file.
 * It assumes that the data is stored essentially as a table with rows and columns.
 * The top row is assumed to be field names, and the rows below it are data.
 */
@Deprecated
public class HITDataReader implements HITDataInput {

  public static final int HIT_ID_FIELD_IND = 0;
  public static final int HIT_TYPE_ID_FIELD_IND = 1;

  public final static String DEFAULT_DELIM = "\t";
  public final static String HIT_ID_HEADER  = "hitid";

  protected FileUtil fileImporter = null;
  protected String[] rows = null;
  protected String[] fieldNames = null; 
  protected String delim = null;

  protected static Logger log = Logger.getLogger(HITDataReader.class);

  public HITDataReader() {
    super();
    this.delim = DEFAULT_DELIM;

    // initialize rowValues only when rowValues is accessed
  }

  public HITDataReader(String fileName) throws IOException {
    // Constructor that assumes the first row contains fieldNames and the delimeter is DEFAULT_DELIM
    this(fileName, DEFAULT_DELIM);
  }

  public HITDataReader(String fileName, String delim) throws IOException {
    super();
    // Base constructor
    this.fileImporter = new FileUtil(fileName);
    this.delim = delim;

    // initialize( if we aren't given first names, then first row contains fields )
    this.setRows(fileImporter.getLines());
    this.fill();
  }

  public String getFileName() {
    if (this.fileImporter == null) {
      return null;
    }
    return this.fileImporter.getFileName();
  }

public String[] getFieldNames() {
    if (this.fieldNames == null) {
      this.fieldNames = this.getRowValues(0);
    }
    return fieldNames;
  }

public int getNumRows() {
    if (this.rows == null) {
      return 0;
    }
    return rows.length;
  }

  public String getDelimeter() {
    return this.delim;
  }

public String[] getRowValues(int rowNum) {
    String row = this.getRow(rowNum);
    if (row != null)
      return row.split(this.delim);

    return null;
  }

public Map<String, String> getRowAsMap(int rowNum) {

    String[] fieldNames = this.getFieldNames();
    String[] rowValues = this.getRowValues(rowNum);

    if (fieldNames == null || fieldNames.length == 0)
    {
      log.info("No field names were found in your HIT Input. Your first row in your input file must contain field names for each column.");
      return null;
    }

    if (rowValues == null || rowValues.length == 0)
    {
      log.info("No input rows were found in your HIT Input. Your input file must contain at least one row of input.");      
      return null;
    }

    HashMap<String,String> rowValueMap = new HashMap<String,String>();

    for (int i = 0; i < fieldNames.length; i++) {
      rowValueMap.put(fieldNames[i], rowValues[i]);
    }

    return rowValueMap;
  }

  public void setRows(String[] rows) {
    this.rows = rows;
  }

  public String getRow(int rowNum) {
    if (this.rows != null && this.rows.length >= rowNum) {
      return this.rows[rowNum];
    }

    return null;
  }

  private void fill() {
    if (this.rows != null) {
      this.fieldNames = rows[0].split(this.delim);
    }
  }
}
