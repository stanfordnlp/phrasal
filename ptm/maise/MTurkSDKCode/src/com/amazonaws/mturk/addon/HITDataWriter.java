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
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.amazonaws.mturk.util.FileUtil;

/**
 * The HITDataWriter class provides a structured way to write HIT data to a file.
 * Each row consists of values separated by a delimeter. 
 */
@Deprecated
public class HITDataWriter extends AbstractHITDataOutput {

  public final static String DEFAULT_DELIM = "\t";

  private String fileName;
  private FileUtil file;	
  private String delim;

  public HITDataWriter(String fileName) throws IOException {
    this (fileName, DEFAULT_DELIM);
  }

  public HITDataWriter(String fileName, String delim) throws IOException {
    super();
    if (fileName == null) {
        throw new NullPointerException("fileName is null.");
    }
    this.fileName = fileName;
    this.delim = delim;
  }

  public void writeLine(String[] fieldValues) throws IOException {
     writeLinePrivate( fieldValues );
  }
  
  protected synchronized void writeLinePrivate(String[] fieldValues) throws IOException {

    if (fieldValues == null || fieldValues.length == 0) {
      throw new IllegalArgumentException("fieldValues is empty.");
    }

    String output = StringUtils.join(fieldValues, delim);

    getFile().saveString(output + "\n", true); // append
  }

  private FileUtil getFile() throws IOException {

    // Don't create the file until it is needed
    if (file==null) {
      this.file = new FileUtil(fileName);
    }

    return file;
  }

  public void setFieldNames( String[] newFieldNames ) {
    super.setFieldNames(newFieldNames);
    try {
      writeLinePrivate( newFieldNames );
    }
    catch (IOException ioe) {
        throw new IllegalStateException("Could not write field names to disk.", ioe);
    }
  }

  public void writeValues( Map< String, String > values ) throws IOException {
    writeLinePrivate( getValuesByFieldName(values) );
  }
}
