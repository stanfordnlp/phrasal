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


package com.amazonaws.mturk.service.exception;

import java.util.List;

/**
 * 
 * This Exception is used by the Retry Filter for retrying
 *
 */
@SuppressWarnings("serial")
public class InternalServiceException extends ServiceException {
    
    //List of errors set in the exception
    private List<String> errorCodes;
    
    public InternalServiceException(Throwable throwable, List<String> errorCodes) {
        super(throwable);
        this.errorCodes = errorCodes;
    }
    
    public InternalServiceException(String message, List<String> errorCodes) {
        super(message);
        this.errorCodes = errorCodes;
    }
    
    public InternalServiceException(String message) {
        super(message);
    }

    public InternalServiceException(String message, Throwable throwable) {
        super(message, throwable);
    }
    
    public InternalServiceException(Throwable throwable) {
        super(throwable);
    }
    
    public List<String> getErrorCodes() {
        return errorCodes;
    }
}
