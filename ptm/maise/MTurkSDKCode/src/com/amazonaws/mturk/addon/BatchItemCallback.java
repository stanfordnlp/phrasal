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

/**
 * Interface to implement callback functionality for batches
 * when an item in the batch was processed.
 */
public interface BatchItemCallback {
  
  /**
   * Notifies the callback handler that an item was processed
   * 
   * @param itemId              Identifier for the item in the submitted batch
   * @param succeeded           true, if item was successfully processed (no exception)
   * @param result              The result returned if the item was successfully processed
   * @param itemException       The exception that was raised when the item failed to process successfully
   */
  void processItemResult(Object itemId, boolean succeeded, Object result, Exception itemException);
}
