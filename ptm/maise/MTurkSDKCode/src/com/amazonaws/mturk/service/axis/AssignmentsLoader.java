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


package com.amazonaws.mturk.service.axis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.amazonaws.mturk.requester.Assignment;
import com.amazonaws.mturk.requester.AssignmentStatus;
import com.amazonaws.mturk.requester.GetAssignmentsForHITResult;
import com.amazonaws.mturk.requester.GetAssignmentsForHITSortProperty;
import com.amazonaws.mturk.requester.SortDirection;

/**
 * Loads all assignments for a hit
 */
class AssignmentsLoader implements AsyncCallback {
  private RequesterService service;
  private Integer pageNumber=1;
  private String hitId;
  private SortDirection sortDirection;
  private AssignmentStatus[] status;  
  private GetAssignmentsForHITSortProperty sortProperty;
  private String[] responseGroup;
  private Integer pageSize;
  private List<Assignment> allResults = new ArrayList<Assignment>();
  private AsyncReply firstReply = null;
  private Exception exception = null;
  
  public AssignmentsLoader(RequesterService service, String hitId, SortDirection sortDirection, 
      AssignmentStatus[] status, GetAssignmentsForHITSortProperty sortProperty, 
      String[] responseGroup, Integer pageSize) {
    this.service = service;
    this.hitId = hitId;
    this.sortDirection = sortDirection;
    this.status = status;
    this.sortProperty = sortProperty;
    this.responseGroup = responseGroup;
    this.pageSize = pageSize;
  }
  
  /**
   * Starts loading the assignments for the HIT by queuing up an assignments request
   * in the work queue
   */
  public void start() {
    firstReply = service.getAssignmentsForHITAsync(hitId, 
        SortDirection.Ascending,
        status,
        sortProperty,
        pageNumber,
        pageSize,
        responseGroup,
        this);
  }
  
  private boolean addResults(GetAssignmentsForHITResult currentResults) {
    Assignment[] assignments = currentResults.getAssignment();

    if (assignments != null) {
      // Add the results
      Collections.addAll(allResults, assignments);
      
      // if there are more results, load them
      if (assignments.length == pageSize && currentResults.getTotalNumResults() > pageSize) {
        return true;
      }
    }
    
    return false;
  }

  /**
   * Adds the assignments received from the current call to the assignments for the HIT.
   * If there are more assignments that need to be loaded, loads these on the current executing
   * worker thread
   */
  public void processResult(Object axisRequestMessage, Object axisResult) {
    
    GetAssignmentsForHITResult result = ((GetAssignmentsForHITResult[])axisResult)[0];
    while (addResults(result)) {
      // load the remaining assignments on the worker thread
      pageNumber++;
      result = service.getAssignmentsForHIT(hitId, sortDirection, status, sortProperty, pageNumber, pageSize, responseGroup);
    }
  }

  public void processFailure(Object axisRequestMessage, Exception axisFailure) {
    exception = axisFailure;
  }
  
  /**]
   * Returns the assignments for the HIT to the caller
   */
  public Assignment[] getResults() throws Exception {
    // make sure worker thread is done loading assignments
    firstReply.getResult();
    
    if (exception != null) {
      throw exception;
    }
    
    return (Assignment[]) allResults.toArray(new Assignment[allResults.size()]);
  }
}
