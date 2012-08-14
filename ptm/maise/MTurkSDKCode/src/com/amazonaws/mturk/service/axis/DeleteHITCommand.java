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

import org.apache.log4j.Logger;

import com.amazonaws.mturk.addon.BatchItemCallback;
import com.amazonaws.mturk.requester.Assignment;
import com.amazonaws.mturk.requester.AssignmentStatus;
import com.amazonaws.mturk.requester.HIT;
import com.amazonaws.mturk.requester.HITStatus;
import com.amazonaws.mturk.service.exception.InternalServiceException;
import com.amazonaws.mturk.service.exception.ServiceException;
import com.amazonaws.mturk.util.ClientConfig;

/**
 * Attempts to "delete" a HIT
 */
class DeleteHITCommand implements AsyncCallback {

  private int hitIndex = -1; 
  private String hitId;
  private boolean approve;
  private boolean expire;
  private RequesterService service;
  private boolean deleted = false;
  private AsyncReply reply = null;
  private BatchItemCallback callback = null;
  
  private static Logger log = Logger.getLogger(DeleteHITCommand.class);
  
  /**
   * Attempts to delete a HIT by disposing it. If exceptions occur, additional API calls are being invoked
   * on the same thread to load the hit, expire it or approve its assignments prior to trying to dispose it
   * again. 
   * 
   * @param index       Zero-based index of the hit to delete (e.g. the row in an input file)
   * @param hitId       The ID of the HIT to delete
   * @param approve     If true, allows the command to approve any submitted assignments for the HIT prior to disposing it
   * @param expire      If true, allows the command to expire the hit prior to disposing it
   * @param service     The requester service to use for the API methods the command invokes.
   */
  public DeleteHITCommand(int index, String hitId, boolean approve, boolean expire, RequesterService service,
      BatchItemCallback callback) {
    this.hitId = hitId;
    this.approve = approve;
    this.expire = expire;
    this.service = service;
    this.hitIndex = index;
    this.callback = callback;
  }
  
  private void logInfo(String msg) {
    if (callback == null) {
      log.info("[" + hitId + "] " + msg + ((hitIndex == -1) ? "" : " (Index: "+hitIndex+")"));
    }
    else {
      callback.processItemResult(hitId, true, msg, null);
    }    
  }
  
  private void logFailure(Exception e) {
    if (callback == null) {
      log.error("[" + hitId + "] FAILURE Deleting HIT (" + e.getLocalizedMessage() + ")" + 
          ((hitIndex == -1) ? "" : " (Index: "+hitIndex+")"));
    }
    else {
      callback.processItemResult(hitId, false, null, e);
    }      
  }
  
  private void logSuccess() {
    deleted=true;
    logInfo("Successfully deleted HIT"); 
  }
  
  private void disposeHit() {
    service.disposeHIT(hitId);
    logSuccess();   
  }
    
  public void execute() {
    reply = service.disposeHITAsync(hitId, this);
  }
  
  public boolean hasSucceeded() throws ServiceException {
    if (reply == null) {
      throw new ServiceException("execute() not called prior to retrieving the result of the operation");
    }
    
    try {
      reply.getResult();
    } catch (ServiceException e) {
      // exception was handled in callback      
    }
    
    return deleted;
  }
  
  /**
   * Handles/logs throttling errors in the sandbox environment
   * @param ex
   */
  private void handleException(Exception ex) throws InternalServiceException {
    if (ex instanceof InternalServiceException &&
        service.getConfig().getServiceURL().equalsIgnoreCase(ClientConfig.SANDBOX_SERVICE_URL)) {
      logFailure(ex);
      
      throw (InternalServiceException)ex;
    }
  }
  
  /**
   * Callback for errors on the initial disposeHIT call.
   */
  public void processFailure(Object axisRequestMessage, Exception axisFailure) {
    
    // Tries to dispose a HIT by going through a sequence of
    // getHIT/forceExpireHIT/get/approveAssignments
    // If throttling errors occur, this sequence is aborted
    
    HIT hit = null;
    try {
      try {
        hit = service.getHIT(hitId);
      }
      catch (Exception e2) {
        handleException(e2);
      }

      if (hit == null) {
        logFailure(new ServiceException("HIT not found. The hitId may be invalid."));
        return;
      } 
      else if (hit.getHITStatus() != null 
          && hit.getHITStatus().equals(HITStatus.Disposed)) {

        logInfo("HIT already deleted. Skipping HIT");
        deleted = true;

      } 
      else if (expire) { 
        // Try to expire the HIT if we've been given permission
        // then redispose of it
        try {
          service.forceExpireHIT(hitId);
        } catch (Exception e2) {
          handleException(e2);
        }

        try {
          disposeHit();
        } catch (Exception e2) {
          handleException(e2);                                                   
        }
      }

      if (!deleted) { 
        if (approve) { 
          // we've been given permission to approve all the assignments 
          try {
            Assignment[] assigns = service.getAllSubmittedAssignmentsForHIT(hitId);

            for (int j = 0; assigns != null && j < assigns.length; j++) {
              try {
                if (assigns[j] != null && assigns[j].getAssignmentStatus().equals(AssignmentStatus.Submitted)) {                
                  String assignsId = assigns[j].getAssignmentId();
                  service.approveAssignment(assignsId, null); // null for RequesterFeedback
                }
              } catch (Exception e3) {
                // Gobble these up, although if they happen then (in theory) we won't be able to dispose.
              }
            }
          } catch (Exception e2) {
            // Don't sweat this. 
          }

          try {
            // Now re-try disposing it
            disposeHit();
          } catch (Exception e2) {
            if (hit.getHITStatus().equals(HITStatus.Unassignable)) {
              logFailure(new ServiceException("HIT still being worked on. Not deleted."));
            } else {
              logFailure(e2);
            }
          }
        } 
        else  {
          // We weren't given permission to approve, so just fail.
          if (hit.getHITStatus().equals(HITStatus.Unassignable)) {
            logFailure(new ServiceException("HIT still being worked on. Not deleted."));
          } else {
            logFailure(axisFailure);
          }
        }
      }
    }
    catch (InternalServiceException svcEx) {
      // do nothing: throttling error occurred in command sequence
      // and was already handled and logged
    }
  }

  public void processResult(Object axisRequestMessage, Object axisResult) {
    logSuccess();     
  }
}
