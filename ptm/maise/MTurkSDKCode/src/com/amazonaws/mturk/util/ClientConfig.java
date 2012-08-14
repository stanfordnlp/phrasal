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


package com.amazonaws.mturk.util;

import java.util.Properties;
import java.util.Set;

import org.apache.log4j.PropertyConfigurator;

/**
 *  The ClientConfig class reads from the configuration file and holds the
 *  configuration values for the RequesterService instance to use.    
 */
public class ClientConfig {

  //-------------------------------------------------------------
  // Constants - Protected
  //-------------------------------------------------------------

  public final static String SANDBOX_SERVICE_URL = 
      "https://mechanicalturk.sandbox.amazonaws.com/?Service=AWSMechanicalTurkRequester";
  public final static String PRODUCTION_SERVICE_URL = 
      "https://mechanicalturk.amazonaws.com/?Service=AWSMechanicalTurkRequester";
  
  protected final static String PROD_WORKER_WEBSITE_URL = "https://www.mturk.com";
  protected final static String SANDBOX_WORKER_WEBSITE_URL = "https://workersandbox.mturk.com";
  
  protected final static String PROD_REQUESTER_WEBSITE_URL = "https://requester.mturk.com";
  protected final static String SANDBOX_REQUESTER_WEBSITE_URL = "https://requestersandbox.mturk.com";
  
  //-------------------------------------------------------------
  // Variables - Private
  //-------------------------------------------------------------
  
  //These members are used during the initialization of the service and cannot be modified after that.
  private String serviceName = null;
  private String accessKeyId = null;
  private String secretAccessKey = null;
  private String serviceURL = null;
  
  //These members can used for setting parameters to RetryFilter.
  private int retryAttempts = 0;
  private long retryDelayMillis = 0;
  private Set<String> retriableErrors = null;
  
  
  //-------------------------------------------------------------
  // Constructors - Public
  //-------------------------------------------------------------

    
  public ClientConfig() {
      setServiceURL( SANDBOX_SERVICE_URL ); // Default to Sandbox
  }

  //-------------------------------------------------------------
  // Methods - Public
  //-------------------------------------------------------------

  public String getServiceName() {
    return serviceName;
  }

  public String getAccessKeyId() {
    return accessKeyId;
  }

  public String getSecretAccessKey() {
    return secretAccessKey;
  }

  public String getServiceURL() {
    return serviceURL;
  }

  
  public String getWorkerWebsiteURL() {
    if (isSandbox()) {
      return SANDBOX_WORKER_WEBSITE_URL;
    }

    return PROD_WORKER_WEBSITE_URL;
  }

  public String getRequesterWebsiteURL() {
	if (isSandbox()) {
	  return SANDBOX_REQUESTER_WEBSITE_URL;
	}

	return PROD_REQUESTER_WEBSITE_URL;
  }
  
  private boolean isSandbox() {
    return serviceURL.indexOf("sandbox") > -1;
  }
  
  public int getRetryAttempts() {
    return retryAttempts;
  }
  
  public long getRetryDelayMillis() {
    return retryDelayMillis;
  }

  public Set<String> getRetriableErrors() {
    return retriableErrors;
  }
  
  public void setRetriableErrors(Set<String> retriableErrors) {
    this.retriableErrors = retriableErrors;
  }

  public void setRetryAttempts(int retryAttempts) {
    this.retryAttempts = retryAttempts;
  }
  
  public void setRetryDelayMillis(long retryDelayMillis) {
    this.retryDelayMillis = retryDelayMillis;
  }
  
  public void setSecretAccessKey(String secretAccessKey) {
    this.secretAccessKey = secretAccessKey;
  }
  
  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }
  
  public void setServiceURL(String serviceURL) {
    this.serviceURL = serviceURL;
  }
  
  /**
   * sets the log4j log level
   * @param logLevel - log4j log level 
   */
  public static void setLogLevel(String logLevel) {
    if (logLevel != null) {
      // override the default log4j respository-wide threshold
      Properties prop = new Properties();
      prop.setProperty("log4j.threshold", logLevel);
      PropertyConfigurator.configure(prop); 
    }
    
  }

  public void setAccessKeyId(String accessKeyId) {
    this.accessKeyId = accessKeyId;
  }
}
