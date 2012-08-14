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

/**
 * The HITResults class provides a way to hold HIT results for a particular HIT Type.
 */
public class HITTypeResults 
{

  private HITResults[] hitResults;
  private HITDataOutput resultsWriter;
  public final static String[] HIT_HEADERS = buildHITHeaders();
  public final static String[] ASSIGNMENT_HEADERS = buildAssignmentHeaders();

  public HITTypeResults()
  {
    this.hitResults = new HITResults[0];
    this.resultsWriter = null;    
  }
  
  public HITTypeResults(HITResults[] hits)
  {
    this.hitResults = hits;
    this.resultsWriter = null;    
  }

  private static String[] buildHITHeaders() {

    String[] fields = new String[HITProperties.HIT_FIELDS.length];

    for (HITProperties.HITField field : HITProperties.HIT_FIELDS) {
      fields[ field.ordinal() ] = field.getFieldName();
    }

    return fields;
  }

  private static String[] buildAssignmentHeaders() {

    String[] fields = new String[HITProperties.ASSIGNMENT_FIELDS.length];

    for (HITProperties.AssignmentField field : HITProperties.ASSIGNMENT_FIELDS) {
      fields[ field.ordinal() ] = field.getFieldName();
    }

    return fields;
  }

  public void writeResults() throws IllegalStateException, IOException {
    writeResultsHeader();
    for (int i = 0; i < this.hitResults.length; i++) {
      HITResults hit = this.hitResults[i];
      hit.writeResults(this.resultsWriter);
    }
  }
  
  public void setHITResults(HITResults[] hits) {
    this.hitResults = hits;
  }
  
  public void setHITDataOutput(HITDataOutput writer) {
    this.resultsWriter = writer;
  }
  
  public HITDataOutput getHITDataWriter() {
    return this.resultsWriter;
  }

  public int getResultCount() {
    return hitResults.length;
  }

  public HITResults getHITResults( int index ) {
    return hitResults[ index ];
  }

  public void writeResultsHeader() throws IllegalStateException, IOException {
    int numHITHeaders = HIT_HEADERS.length;
    int numAssignmentHeaders = ASSIGNMENT_HEADERS.length;

    String[] headers = new String[numHITHeaders + numAssignmentHeaders];

    for (int i = 0; i < numHITHeaders; i++) {
      headers[i] = HIT_HEADERS[i];
    }

    for (int i = 0; i < numAssignmentHeaders; i++) {
      headers[i + numHITHeaders] = ASSIGNMENT_HEADERS[i];
    }

    // Note that we always append to the results file and this
    //   can cause it to have header lines in the middle of the file
    if (this.resultsWriter == null) {
      throw new IllegalStateException("No writer found");
    }
    this.resultsWriter.setFieldNames( headers );
  }
}
