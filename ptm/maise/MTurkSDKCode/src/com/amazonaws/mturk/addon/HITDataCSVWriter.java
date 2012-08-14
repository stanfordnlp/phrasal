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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import au.com.bytecode.opencsv.CSVWriter;

public class HITDataCSVWriter extends AbstractHITDataOutput {

    private final Collection<String> IGNORED_FIELDS = new HashSet<String>(Arrays.asList(
        HITProperties.AssignmentField.Answers.getFieldName()
    ));
    
    protected CSVWriter csvWriter = null;
    private String fileName = null;
    private char separator = '\t';
    private boolean appendToFile = true;
    private boolean useQuotes = true;
    
    /**
     * Creates a new tab-delimited, appending CSV writer
     * 
     * @param fileName  Name of file to write to
     */
    public HITDataCSVWriter(String fileName) {
        this(fileName, '\t', true);
    }
    
    /**
     * Creates a new CSV writer
     * 
     * @param fileName          Name of file to write to
     * @param separator         Separator character to use
     * @param appendToFile      If true, append to existing file or create new file
     * @param useQuotes         If true, wraps values in quotes
     */
    public HITDataCSVWriter(String fileName, char separator, boolean appendToFile, boolean useQuotes) {
      if (fileName == null) {
          throw new NullPointerException("fileName is null.");
      }
      this.fileName = fileName;
      this.separator = separator;
      this.appendToFile = appendToFile;
      this.useQuotes = useQuotes;
    }    
    
    /**
     * Closes the underlying writer.
     * @throws IllegalStateException if the close fails.
     */
    public synchronized void close() {
      try {
        if (this.csvWriter != null) {
          try {
            this.csvWriter.close();
          }
          catch (IOException ioe) {
            throw new IllegalStateException("Failed to close file " + fileName, ioe);
          }
        }
      }
      finally {
        this.csvWriter = null;
      }
    }
    
    /**
     * Creates a new CSV writer
     * 
     * @param fileName          Name of file to write to
     * @param separator         Separator character to use
     * @param appendToFile      If true, append to existing file or create new file
     */
    public HITDataCSVWriter(String fileName, char separator, boolean appendToFile) {
      this(fileName, separator, appendToFile, true);
    }    
    
    protected synchronized CSVWriter getWriter() throws IOException {
        if (this.csvWriter == null) {
            FileOutputStream fos = new FileOutputStream(this.fileName, appendToFile); // always append, 
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
            this.csvWriter = new CSVWriter( writer,
                separator,
                useQuotes ? CSVWriter.DEFAULT_QUOTE_CHARACTER : CSVWriter.NO_QUOTE_CHARACTER,
                System.getProperty("line.separator"));
        }
        return this.csvWriter;
    }
    
    public synchronized void setFieldNames( String[] fieldNames ) {
        super.setFieldNames(fieldNames);
        try {
            writeLine(fieldNames);
        }
        catch (IOException ioe) {
            throw new IllegalStateException("Could not write fieldNames to disk.", ioe);
        }
    }

    public synchronized void writeLine( String[] fieldValues ) throws IOException {
        if (fieldValues == null || fieldValues.length == 0) {
            throw new IllegalArgumentException("fieldValues is empty.");
        }
        getWriter().writeNext( fieldValues );
        getWriter().flush();
    }
    
    public synchronized void writeValues( Map< String, String > values ) throws IOException {
        writeLine( getValuesByFieldName(values, IGNORED_FIELDS) );
    }


}
