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

import java.util.concurrent.Callable;
import org.apache.log4j.Logger;

import com.amazonaws.mturk.filter.Filter;
import com.amazonaws.mturk.filter.Message;

/**
 * An asynchronous request that waits in the Axis work queue to be processed 
 */
public class AsyncRequest implements Callable<Object> {

  private static Logger log = Logger.getLogger(AsyncRequest.class);

  private Message message;
  private Filter firstFilter;	
  private AsyncCallback cb;

  /**
   * Creates a new asynchronous request
   * 
   * @param msg		The request to send to the requester endpoint
   * @param f			The first filter to invoke
   * @param callback  (Optional) A callback handler to invoke when the work queue processed the request
   */
  public AsyncRequest(Message msg, Filter f, AsyncCallback callback) {
    this.message = msg;
    this.firstFilter = f;
    this.cb = callback;
  }

  /**
   * Returns the message that is/was send to the requester service
   */
  public Message getMessage() {
    return message;
  }

  /**
   * Executes the request on the filter chain and calls the callback handler (if defined)
   */
  public Object call() throws Exception {

    Object ret = null;

    try {			
      ret = firstFilter.execute(message).getResults();
      
      if (cb != null) {
        // notify callback
        try {
          cb.processResult(message, ret);
        }
        catch (Exception callbackEx) {
          log.warn("Failed to invoke callback function on success", callbackEx);
        }		
      }
    }
    catch (Exception axisEx) {
      if (cb != null) {
        // notify failure to callback
        try {
          cb.processFailure(message, axisEx);
        }
        catch (Exception callbackEx) {
          log.warn("Failed to invoke callback function on success", callbackEx);
        }			
      }

      // reraise the axis exception to the executor
      throw axisEx;
    }
    finally {
      // request is done decrement current queue size
      WorkQueue.taskComplete();
    }

    return ret;
  }
}
