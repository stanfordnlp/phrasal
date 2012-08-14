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

import java.util.concurrent.Future;

import com.amazonaws.mturk.filter.Message;
import com.amazonaws.mturk.service.exception.ServiceException;

/**
 * The reply for an asynchronous request placed in the Axis work queue
 */
public class AsyncReply {

  private Future<Object> future;
  private Message requestMsg;

  /**
   * Constructs a new reply once the message is submitted to the Axis work
   * queue
   * 
   * @param msg
   *            The request message to send
   * @param f
   *            The future contain the result of the asynchronous call
   */
  public AsyncReply(Message msg, Future<Object> f) {
    this.future = f;
    this.requestMsg = msg;
  }

  /**
   * Future containing the result of the asynchronous call
   * 
   * @return
   */
  public Future<Object> getFuture() {
    return future;
  }

  /**
   * Returns the request for this reply
   * 
   * @return
   */
  public Message getRequestMessage() {
    return requestMsg;
  }

  /**
   * Returns the result of the asynchronous request (waits, if not yet
   * available)
   * 
   * @return
   * @throws ServiceException
   */
  public Object getResult() throws ServiceException {

    try {
      return future.get();
    }
    catch (Exception ex) {
      if (ex.getCause() instanceof ServiceException) {
        throw (ServiceException)ex.getCause();
      }
      else {
        throw new ServiceException(ex.getCause());
      }
    }
  }

  /**
   * Returns true if the request has been processed (either successfully or failing).
   */
  public boolean isDone() {
    return future.isDone();
  }

}
