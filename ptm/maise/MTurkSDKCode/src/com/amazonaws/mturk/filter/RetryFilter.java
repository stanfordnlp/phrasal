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


package com.amazonaws.mturk.filter;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import com.amazonaws.mturk.service.exception.InternalServiceException;
import com.amazonaws.mturk.service.exception.ServiceException;

/**
 * Default Filter implementation which handles retry of operation on receiving certain retriable errors
 * which can be configured in the SDK configuration (retriable_errors)
 */
public class RetryFilter extends Filter {

    private Set<String> retriableErrors ;
    private int retryMaxAttempts;
    private long retryDelayMillis;
    private static Random rand = new Random(System.currentTimeMillis());
    private static double backoffBase = 1.1;
    
    /**
     * Use this constructor if you do not want to retry any error
     *
     */
    public RetryFilter() {
        this(new HashSet<String>(), -1, 0);
     }
    
    /**
     * 
     * @param retriableErrorSet - set of errors which should be retried
     * @param retryMaxAttempts - maximum number of retry attempts
     * @param retryDelayMillis - delay between retry attempts
     */
    public RetryFilter(Set<String> retriableErrorSet, int retryMaxAttempts, long retryDelayMillis) {
        this.retriableErrors = retriableErrorSet;
        this.retryMaxAttempts = retryMaxAttempts;
        this.retryDelayMillis = retryDelayMillis;
    }
    
    public Set<String> getRetriableErrors() {
        return retriableErrors;
    }


    /**
     * 
     * @param retriableErrors - sets retriable errors set
     */
    public void setRetriableErrors(Set<String> retriableErrors) {
        this.retriableErrors = retriableErrors;
    }

    /**
     * 
     * @param error - error code for adding to the set of retriable errors
     */
    public void addRetriableError(String error) {
        this.retriableErrors.add(error);
    }
    
    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public long getRetryDelayMillis() {
        return this.retryDelayMillis;
    }

    public void setRetryDelayMillis(long retryDelayMillis) {
        this.retryDelayMillis = retryDelayMillis;
    }

    /**
     * Calls the next filter and catches ServiceException. Checks if error is one of the retriable errors and retries.
     * throws ServiceException if it retry fails or not retriable
     */ 
    @Override
    public Reply execute(Message m) throws ServiceException {
        int retryCount = 0;
        ServiceException exception = null;
        do {
            exception = null;
            // if this is a retry, sleep for retryDelayMillis
            if (retryCount > 0) {
                try {                  
                    Thread.sleep(getDelay(retryCount));
                }
                catch (InterruptedException ie) {
                    //do nothing
                }
            }
            try {              
                return passMessage(m);
            }
            catch (ServiceException se) {
                exception = se; //set exception to most recent caught exception
            }
            
            retryCount++;
        }
        while (retryCount < this.retryMaxAttempts 
                && exception != null
                && shouldRetry(exception, m.getRequests()));
        //retry call if exception is caught, retryCount not greater than max allowed and shouldRetry returns true 
        throw exception;
    }
    
    /**
     * 
     * @param e - exception caught
     * @param requests - the requests which caused the exception
     * @return true if the request can be retried. i.e. check the retriable error set  
     */
    protected boolean shouldRetry(ServiceException e, Object requests) {
        if (e instanceof InternalServiceException) {
            InternalServiceException exception = (InternalServiceException) e;
            
            for (String errorCode : exception.getErrorCodes()) {
                if (this.retriableErrors.contains(errorCode))
                    return true;
            }
            return false;
        }
        return false;
    }
    
    /**
     * Calculates the delay during retries including a jitter
     */
    protected long getDelay(int retry) {
      return (retryDelayMillis * retry + (Math.abs(rand.nextInt()) % retryDelayMillis));
    }    
}
