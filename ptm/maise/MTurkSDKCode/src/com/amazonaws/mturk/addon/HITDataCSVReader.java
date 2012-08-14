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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class HITDataCSVReader implements HITDataInput {

    protected static Logger log = Logger.getLogger(HITDataCSVReader.class);

    List<String[]> rows = new ArrayList<String[]>();
    String[] fieldNames = null;
    
    public HITDataCSVReader(String fileName) throws IOException {
      this(fileName, '\t');
    }
    
    public HITDataCSVReader(String fileName, char separator) throws IOException {
        super();
        InputStreamReader input = new InputStreamReader(new FileInputStream(fileName), "UTF-8");
        CSVReader csvReader = new CSVReader(input, separator, CSVWriter.DEFAULT_QUOTE_CHARACTER);
        String[] row = null;
        
        try {
          // read all lines but skip header rows that got appended repetitively
          while ((row = csvReader.readNext()) != null) {
            if (fieldNames == null) {
              fieldNames = row;
              rows.add(fieldNames); // header is at index 0
            }
            else if (!Arrays.equals(fieldNames, row)) {
              rows.add(row);
            }
          }
        }
        finally {
          csvReader.close();
        }
    }
    
    public String[] getFieldNames() {
        return fieldNames;
    }

    public int getNumRows() {
        return rows.size();
    }

    public Map< String, String > getRowAsMap( int rowNum ) {
        if( this.fieldNames == null || this.fieldNames.length == 0 ) {
            log.info("No field names were found in your HIT Input." +
                    " The first row of your input file must contain field names for each column.");
            return null;
        }
        String[] row = this.rows.get( rowNum );
        if( row == null ) {
            return null;
        }
        
        HashMap<String,String> rowValueMap = new HashMap<String,String>();
        for (int i = 0; i < fieldNames.length; i++) {
            rowValueMap.put(fieldNames[i], row[i]);
        }

        return rowValueMap;
    }

    public String[] getRowValues( int rowNum ) {
        if( this.rows != null && this.getNumRows() > rowNum ) {
            return rows.get( rowNum );
        }
        return null;
    }

}
