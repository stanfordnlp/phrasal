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

/**
 * 
 * Data Structure used for passing the reply from the execution of each Filter.
 */
public class Reply {
    
    /*
     * the results from WSDL call
     */
    private Object[] results;
    
    /*
     * this should be com.amazonaws.mturk.requester.Errors object 
     * It contains the errors got while making the wsdl call
     */
    private Object errors;
    
    /*
     * The requestId of the wsdl operation request
     */
    private String requestId;
    
    
    public Reply(Object[] results, Object errors, String requestId) {
        this.results = results;
        this.errors = errors;
        this.requestId = requestId;
    }
    
    public Object getErrors() {
        return errors;
    }
    public void setErrors(Object errors) {
        this.errors = errors;
    }
    
    public Object[] getResults() {
        return results;
    }
    public void setResults(Object[] results) {
        this.results = results;
    }

    public String getRequestId() {
        return requestId;
    }

}
