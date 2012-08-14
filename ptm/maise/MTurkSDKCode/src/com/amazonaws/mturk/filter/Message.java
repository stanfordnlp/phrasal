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
 * Data Structure used for passing requests through the Filters 
 *
 */
public class Message {
    
    /*
     * the requests Array object   
     */
    private Object requests;
    
    /*
     * the axis method name 
     */
    private String methodName;
    
    /*
     * the name by which the result is obtained
     */
    private String resultAccessorName;
    
    /*
     * credential that is passed while making WSDL call
     */
    private String credential;
    
    public Message(Object requests, String methodName, String resultAccessorName, String credential) {
        this.requests = requests;
        this.methodName = methodName;
        this.resultAccessorName = resultAccessorName;
        this.credential = credential;
    }
    
    public String getMethodName() {
        return methodName;
    }
    
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
    
    public Object getRequests() {
        return requests;
    }
    
    public void setRequests(Object requests) {
        this.requests = requests;
    }
    
    public String getResultAccessorName() {
        return resultAccessorName;
    }
    
    public void setResultAccessorName(String resultAccessorName) {
        this.resultAccessorName = resultAccessorName;
    }
    
    public String getCredential() {
        return credential;
    } 
}
