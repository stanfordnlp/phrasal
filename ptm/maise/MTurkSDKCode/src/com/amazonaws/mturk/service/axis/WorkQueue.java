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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;


/**
 * Axis work queue that controls the calls to the service endpoint using a thread pool
 */
public class WorkQueue {
  
  
  private static AtomicInteger numberOfPendingRequests=new AtomicInteger();
  private static AtomicInteger numberOfCompletedRequests=new AtomicInteger();
  
  private static int MAX_THREADS_THRESHOLD = 10;	
  private static ExecutorService pool = null;
  private static int numberOfThreads = MAX_THREADS_THRESHOLD;
  
  private static Logger log = Logger.getLogger(WorkQueue.class);

  static {
    // read the configured max threads
    String maxThreads = System.getProperty("mturk.java.workqueue.threads");
    if (maxThreads != null) {
      numberOfThreads = Integer.parseInt(maxThreads);
      if (numberOfThreads < 1 || numberOfThreads > MAX_THREADS_THRESHOLD) {
        // default to threshold
        numberOfThreads = MAX_THREADS_THRESHOLD;
      }
    }
    log.debug("Instantiating work queue with "+numberOfThreads+" threads");

    // create a thread pool with daemon threads
    pool = Executors.newFixedThreadPool(numberOfThreads, new ThreadFactory() {
        public Thread newThread(Runnable r) {
          Thread t = new Thread(r);
          t.setDaemon(true);
          
          return t;
        }
      });	
    
    // register the shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        int i = getNumberOfPendingRequests();
        
        if (i > 0) {
          log.info(String.format("WARNING: Work queue has been shutdown with %d pending Mechanical Turk requests", i));
        }        
      }
    });
  }

  /**
   * Submits a request to the work queue for execution against the requester service endpoint
   */
  public static AsyncReply submit(AsyncRequest request) {
    
    Future<Object> future = pool.submit(request);
    AsyncReply reply = new AsyncReply(request.getMessage(), future);
    
    // set stats
    numberOfPendingRequests.incrementAndGet();

    return reply;
  }
  
  /**
   * Updates the stats for a completed task
   */
  static void taskComplete() {
    numberOfPendingRequests.decrementAndGet();
    numberOfCompletedRequests.incrementAndGet();
  }
  
  /**
   * Returns the current number of pending requests in the queue
   */
  public static int getNumberOfPendingRequests() {
    return numberOfPendingRequests.get();
  }
  
  /**
   * Returns the number of requests completed in the work queue
   */
  public static int getNumberOfCompletedRequests() {
    return numberOfCompletedRequests.get();
  }  
  
  /**
   * Returns the number of worker threads 
   */
  public static int getNumberOfThreads() {
    return numberOfThreads;
  }
  
}
