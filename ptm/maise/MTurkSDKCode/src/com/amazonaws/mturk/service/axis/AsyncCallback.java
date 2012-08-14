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

/**
 * Interface to get callback notifications from an Axis worker thread
 * to notify the result of a webservice call
 */
public interface AsyncCallback {
  /**
   * Notifies the result of a successful Axis operation
   * 
   * @param axisRequestMessage	the request message	
   * @param axisResult			the service response 
   */
  void processResult(Object axisRequestMessage, Object axisResult);

  /**
   * Notifies the result of a failed Axis operation
   * 
   * @param axisRequestMessage	the request message	
   * @param axisFailure			the service failure 
   */
  void processFailure(Object axisRequestMessage, Exception axisFailure);
}
