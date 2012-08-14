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

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.mturk.requester.ErrorsError;
import com.amazonaws.mturk.service.exception.AccessKeyException;
import com.amazonaws.mturk.service.exception.ExceedsMaxAssignmentsPerWorkerException;
import com.amazonaws.mturk.service.exception.InsufficientFundsException;
import com.amazonaws.mturk.service.exception.InternalServiceException;
import com.amazonaws.mturk.service.exception.InvalidParameterValueException;
import com.amazonaws.mturk.service.exception.InvalidStateException;
import com.amazonaws.mturk.service.exception.InvalidTransportEndpointException;
import com.amazonaws.mturk.service.exception.NoHITsAvailableException;
import com.amazonaws.mturk.service.exception.ObjectAlreadyExistsException;
import com.amazonaws.mturk.service.exception.ObjectDoesNotExistException;
import com.amazonaws.mturk.service.exception.ParseErrorException;
import com.amazonaws.mturk.service.exception.PermissionDeniedException;
import com.amazonaws.mturk.service.exception.QualificationTypeRetryException;
import com.amazonaws.mturk.service.exception.ServiceException;
/**
 * Default Filter provided for handling errors got from wsdl operation calls
 *
 */

public class ErrorProcessingFilter extends Filter {

    @Override
    public Reply execute(Message m) {
        Reply reply = passMessage(m);
        if (reply.getErrors() != null) {
            processErrors(reply.getErrors(), reply.getRequestId());
        }
        return reply;
    }
    
    /**
     * Process errors by throwing ServiceExceptions.
     * 
     * @param errorObject
     * @param requestId
     */
    protected void processErrors(Object errorObject, String requestId) {
      if (!(errorObject instanceof ErrorsError[])) {
        throw new ServiceException("Unknown error type: "
            + errorObject.getClass());
      }

      ErrorsError[] errors = (ErrorsError[]) errorObject;
      
      StringBuffer errorList = new StringBuffer();
      List<String> errorCodeList = new ArrayList<String>();
      for (int i = 0; i < errors.length; i++) {
        ErrorsError error = errors[i];
        
        if (i > 0) {
          errorList.append('\n');
        }
        
        errorList.append("Error #");
        errorList.append(i + 1);
        errorList.append(" for RequestId: ");
        errorList.append(requestId);
        errorList.append(" - ");
        errorList.append(error.getCode());
        errorList.append(": ");
        errorList.append(error.getMessage());        
        
        errorCodeList.add(error.getCode());
        
        if (error.getCode() != null) {
          if (error.getCode().equals("AWS.BadCredentialSupplied")
              || error.getCode().equals("AWS.NotAuthorized")
              || error.getCode().equals("AWS.BadClaimsSupplied")) {
            throw new AccessKeyException(errorList.toString());
              }
          
          if (error.getCode().equals("AWS.MechanicalTurk.NoMoreWorkableHITsInGroupException")
              || error.getClass().equals("AWS.MechanicalTurk.NoHITsAvailableInGroupException")
              || error.getCode().equals("AWS.MechanicalTurk.NoHITsAvailableForIterator")) {
            throw new NoHITsAvailableException(errorList.toString());
              }

          if (error.getCode().equals("AWS.MechanicalTurk.QualificationDoesNotExist")
              || error.getCode().equals("AWS.MechanicalTurk.QualificationRequestDoesNotExist")
              || error.getCode().equals("AWS.MechanicalTurk.QualificationTypeDoesNotExist")
              || error.getCode().equals("AWS.MechanicalTurk.AssignmentDoesNotExist")
              || error.getCode().equals("AWS.MechanicalTurk.HITDoesNotExist")) {
            throw new ObjectDoesNotExistException(errorList.toString());
              }

          if (error.getCode().equals("AWS.MechanicalTurk.InvalidHITState")
              || error.getCode().equals("AWS.MechanicalTurk.InvalidQualificationTypeState")
              || error.getCode().equals("AWS.MechanicalTurk.InvalidQualificationState")
              || error.getCode().equals("AWS.MechanicalTurk.InvalidQualificationRequestState")
              || error.getCode().equals("AWS.MechanicalTurk.InvalidAssignmentState")
              || error.getCode().equals("AWS.MechanicalTurk.HITAlreadyPassedReview")) {
            throw new InvalidStateException(errorList.toString());
              }

          if (error.getCode().equals("AWS.MechanicalTurk.AssignmentAlreadyExists")
              || error.getCode().equals("AWS.MechanicalTurk.QualificationTypeAlreadyExists")
              || error.getCode().equals("AWS.MechanicalTurk.QualificationAlreadyExists")) {
            throw new ObjectAlreadyExistsException(errorList.toString());
              }

          if (error.getCode().equals("AWS.MechanicalTurk.PermissionDeniedException")
              || error.getCode().equals("AWS.MechanicalTurk.DoesNotMeetRequirements")) {
            throw new PermissionDeniedException(errorList.toString());
              }

          if (error.getCode().equals("AWS.MechanicalTurk.QualificationTypeRetryDelayNotElapsed")
              || error.getCode().equals("AWS.MechanicalTurk.QualificationTypeDoesNotAllowRetake")) {
            throw new QualificationTypeRetryException(errorList.toString());
              }

          if (error.getCode().equals("AWS.MechanicalTurk.XMLParseError")
              || error.getCode().equals("AWS.MechanicalTurk.XHTMLParseError")) {
            throw new ParseErrorException(errorList.toString());
              }

          if (error.getCode().equals("AWS.MechanicalTurk.InvalidTransportEndpoint")
              || error.getCode().equals("AWS.MechanicalTurk.InvalidTransportEndpoint")) {
            throw new InvalidTransportEndpointException(errorList.toString());
              }

          if (error.getCode().equals("AWS.MechanicalTurk.InvalidParameterValue")) {
            throw new InvalidParameterValueException(errorList.toString());
          }

          if (error.getCode().equals("AWS.MechanicalTurk.ExceedsMaxAssignmentsPerWorker")) {
            throw new ExceedsMaxAssignmentsPerWorkerException(errorList.toString());
          }

          if (error.getCode().equals("AWS.MechanicalTurk.InsufficientFunds")) {
            throw new InsufficientFundsException(errorList.toString());
          }          
        }
      }
      
      //If its not a known error throw InternalServiceException with list of error codes set
      throw new InternalServiceException("Error executing operation: "
          + errorList.toString(), errorCodeList);
    }
}
