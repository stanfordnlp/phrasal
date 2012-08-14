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

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.axis.AxisFault;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.contrib.ssl.StrictSSLProtocolSocketFactory;
import org.w3c.dom.Element;

import com.amazonaws.mturk.filter.Message;
import com.amazonaws.mturk.filter.Reply;
import com.amazonaws.mturk.service.exception.InternalServiceException;
import com.amazonaws.mturk.service.exception.ServiceException;
import com.amazonaws.mturk.util.ClientConfig;
import com.amazonaws.mturk.util.HMACSigner;

/**
 *  The AWSService class contains request and response handlers that are common
 *  to all AWS products.  Requests are signed using HMAC-SHA1 algorithm.
 *  Responses are screened for service errors.  If there are service errors,
 *  then corresponding exceptions get created and thrown.    
 */
public abstract class AWSService {

  //-------------------------------------------------------------
  // Constants - Private
  //-------------------------------------------------------------

  private static final String REQUEST_SUFFIX = "Request";
  private static final String RESPONSE_SUFFIX = "Response";
  private static final String GET_PREFIX = "get";

  private static final String GET_OPERATION_REQUEST_METHOD_NAME = "getOperationRequest";
  private static final String GET_REQUEST_METHOD_NAME = "getRequest";
  private static final String GET_ERRORS_METHOD_NAME = "getErrors";
  private static final String GET_REQUEST_ID_METHOD_NAME = "getRequestId";

  private static final String SET_REQUEST_METHOD_NAME = "setRequest";
  private static final String SET_AWS_ACCESS_KEY_ID_METHOD_NAME = "setAWSAccessKeyId";
  private static final String SET_VALIDATE_METHOD_NAME = "setValidate";
  private static final String SET_CREDENTIAL_METHOD_NAME = "setCredential";  
  private static final String SET_TIMESTAMP_METHOD_NAME = "setTimestamp";
  private static final String SET_SIGNATURE_METHOD_NAME = "setSignature";

  private static final Class[] STRING_CLASS_ARRAY = { String.class };
  private static final Class[] CALENDAR_CLASS_ARRAY = { Calendar.class };
  
  private static final QName AXIS_HTTP_FAULT = new QName("http://xml.apache.org/axis/","HTTP");
  private static final QName AXIS_HTTP_ERROR_CODE = new QName("http://xml.apache.org/axis/","HttpErrorCode");

  //-------------------------------------------------------------
  // Constants - Protected
  //-------------------------------------------------------------
  
  protected static String HTTP_HEADER_AMAZON_SOFTWARE = "X-Amazon-Software";

  //-------------------------------------------------------------
  // Variables - Private
  //-------------------------------------------------------------

  private String accessKeyId;
  private HMACSigner signer;

  //-------------------------------------------------------------
  // Variables - Protected Static
  //-------------------------------------------------------------

  protected static Hashtable<String, String> httpHeaders;

  static {
    httpHeaders = new Hashtable<String, String>(1);
    httpHeaders.put(HTTP_HEADER_AMAZON_SOFTWARE, "MTurkJavaSDK/1.2.1");
    
    /* We need to swap out HTTP/SSL implementations, because Axis doesn't do
     * HTTPS properly -- it doesn't check the certificate to see if it was
     * issued to the domain we're connecting to. */
    System.setProperty("axis.ClientConfigFile", "com/amazonaws/mturk/service/axis/mturk-client-config.wsdd");
    Protocol.registerProtocol("https",
            new Protocol("https", new StrictSSLProtocolSocketFactory(), 443));
  }
  
  protected ClientConfig config = null;
  //-------------------------------------------------------------
  // Constructors
  //-------------------------------------------------------------

  public AWSService(ClientConfig config) {
      this.config = config;
  }

  //-------------------------------------------------------------
  // Methods - Configuration
  //-------------------------------------------------------------

  public void setAccessKeyId(String accessKeyId) {
    this.accessKeyId = accessKeyId;

    signer = new HMACSigner(accessKeyId);
  }

  public void setSigner(String secretAccessKey) {
    signer = new HMACSigner(secretAccessKey);
  }

  //-------------------------------------------------------------
  // Methods - Protected
  //-------------------------------------------------------------

  protected String getAWSAccessKeyId() {
    return accessKeyId;
  }

  protected HMACSigner getSigner() {
    return signer;
  }

  protected abstract Object getPort();

  protected abstract String getServiceName();

  protected abstract String getPackagePrefix();

  /**
   * 
   * @param m - Message structure which contains the details for making wsdl operation call 
   * @return Reply structure containing results and errors from the wsdl operation call 
   * @throws ServiceException
   */
  public Reply executeRequestMessage(Message m) throws ServiceException {
    String axisMethodName = m.getMethodName();
    Object requests = m.getRequests();
    String credential = m.getCredential();
    String resultAccessorName = m.getResultAccessorName();
    try {
      Class bodyClass;
      Class responseClass;
      Class requestClass;
      Object body;

      // Construct the request body
      bodyClass = Class.forName(getPackagePrefix() + axisMethodName);
      body = bodyClass.newInstance();

      responseClass = Class.forName(getPackagePrefix() + axisMethodName + RESPONSE_SUFFIX);
      requestClass = Class.forName(getPackagePrefix() + axisMethodName + REQUEST_SUFFIX);

      Class requestArrayClass = Array.newInstance(requestClass, 0).getClass();
      Object requestArray = requestArrayClass.cast(requests);

      Method setRequest = bodyClass.getMethod(SET_REQUEST_METHOD_NAME,
          new Class [] { requestArrayClass });
      setRequest.invoke(body, requestArray);

      Calendar now = null;
      String signature = null;

      synchronized(AWSService.class){
        Method setAWSAccessKeyId = bodyClass.getMethod(SET_AWS_ACCESS_KEY_ID_METHOD_NAME,
            STRING_CLASS_ARRAY);
        setAWSAccessKeyId.invoke(body, getAWSAccessKeyId());

        Method setValidate = bodyClass.getMethod(SET_VALIDATE_METHOD_NAME,
            STRING_CLASS_ARRAY);
        setValidate.invoke(body, (Object) null);

        if (credential != null && credential.length() > 0) {
          Method setCredential = bodyClass.getMethod(SET_CREDENTIAL_METHOD_NAME,
              STRING_CLASS_ARRAY);
          setCredential.invoke(body, credential);
        }

        Method setTimestamp = bodyClass.getMethod(SET_TIMESTAMP_METHOD_NAME,
            CALENDAR_CLASS_ARRAY);
        now = Calendar.getInstance();
        setTimestamp.invoke(body, now);

        // Create the signature
        Method setSignature = bodyClass.getMethod(SET_SIGNATURE_METHOD_NAME,
            STRING_CLASS_ARRAY);
        signature = getSigner().sign(getServiceName(), axisMethodName, now);

        setSignature.invoke(body, signature);
      }

      Object response = responseClass.newInstance();

      String axisClassMethodName = axisMethodName.substring(0, 1).toLowerCase()
        + axisMethodName.substring(1);
      Method axisMethod = getPort().getClass().getMethod(axisClassMethodName,
          new Class[] { bodyClass });

      try {
        // Execute the request and get a response
        response = axisMethod.invoke(getPort(), body);
      } catch (InvocationTargetException e) {
        if (e.getCause() instanceof AxisFault) {
            //errors due to throttling are inside AxisFault. Get those if present
          AxisFault fault = (AxisFault) e.getCause();
          
          String httpResponse  = fault.getFaultCode().getLocalPart();
          List<String> errorCodes = new ArrayList<String>();
          errorCodes.add(httpResponse);
          
          // When Axis encounters networking errors, it sets the fault code to
          // {http://xml.apache.org/axis/}HTTP
          // In this case it sets the error code from the http response in
          // the "HttpErrorCode" element of the fault details
          // If this is the case, add it to the error codes so the SDK
          // can be configured to retry for specific response codes
          if (AXIS_HTTP_FAULT.equals(fault.getFaultCode())) {
            Element faultElement = fault.lookupFaultDetail(AXIS_HTTP_ERROR_CODE);
            if (faultElement != null && faultElement.getFirstChild() != null) {
              errorCodes.add(faultElement.getFirstChild().getNodeValue());
            }
          }

          throw new InternalServiceException(e.getCause(), errorCodes);
        }
        throw new ServiceException(e.getCause());
      }

      // Extract the Operation Request
      Method getOperationRequest = responseClass.getMethod(GET_OPERATION_REQUEST_METHOD_NAME);
      Object operationRequest = getOperationRequest.invoke(response);

      // Extract the Errors
      Method getErrors = operationRequest.getClass().getMethod(GET_ERRORS_METHOD_NAME);
      Object errors = getErrors.invoke(operationRequest);
      Object[] results = null;
      
  
      if (errors != null) {
        return new Reply(results, errors, getRequestId(operationRequest));
      }
      
      Method getResult = responseClass.getMethod(GET_PREFIX
        + resultAccessorName);
      results = (Object[]) getResult.invoke(response);
      
      if (results == null || results.length == 0) {
        throw new ServiceException("Empty result, unknown error.");
      }
      
      for (int i = 0; i < results.length; i++) {
        Object result = results[i];
      
        Method getRequest = result.getClass().getMethod(
        GET_REQUEST_METHOD_NAME);
        Object request = getRequest.invoke(result);
      
        getErrors = request.getClass().getMethod(
        GET_ERRORS_METHOD_NAME);
        errors = getErrors.invoke(request);
      
        if (errors != null) {
          break; //get only the first error
        }
      }
      return new Reply(results, errors, getRequestId(operationRequest));
    
    } catch (ClassNotFoundException e) {
      throw new ServiceException(e);
    } catch (IllegalAccessException e) {
      throw new ServiceException(e);
    } catch (InstantiationException e) {
      throw new ServiceException(e);
    } catch (NoSuchMethodException e) {
      throw new ServiceException(e);
    } catch (InvocationTargetException e) {
      throw new ServiceException(e.getCause());
    }
  }



  //-------------------------------------------------------------
  // Methods - Private
  //-------------------------------------------------------------

  private String getRequestId(Object operationRequest)
    throws NoSuchMethodException, IllegalAccessException,
                    InvocationTargetException {
             Method getRequestId = operationRequest.getClass().getMethod(GET_REQUEST_ID_METHOD_NAME);

             return (String) getRequestId.invoke(operationRequest);
  }

  public ClientConfig getConfig() {
    return this.config;
  }
}
