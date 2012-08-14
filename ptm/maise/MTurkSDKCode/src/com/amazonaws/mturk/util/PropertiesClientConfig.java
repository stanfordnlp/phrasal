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
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import org.apache.log4j.Logger;



public class PropertiesClientConfig extends ClientConfig {
    
  public final static String ACCESS_KEY_ID = "access_key";
  public final static String SECRET_ACCESS_KEY = "secret_key";
  public final static String SERVICE_URL = "service_url";
  public final static String LOG_LEVEL = "log_level";
  public final static String RETRY_ATTEMPTS = "retry_attempts";
  public final static String RETRY_DELAY_MILLIS = "retry_delay_millis";
  public final static String RETRIABLE_ERRORS = "retriable_errors";
  public final static String DEFAULT_PROPERTIES_FILENAME = "mturk.properties";

  public final static String AUTH_ACCESS_KEY = "AccessKeyId";
  public final static String AUTH_SECRET_KEY = "SecretAccessKey";
  
  public final static String NOT_CONFIGURED_PREFIX = "[insert";
  public final static String NOT_CONFIGURED_POSTFIX = "]";

  private static Logger log = Logger.getLogger(PropertiesClientConfig.class);
  
  private static boolean isNotConfigured(String propVal) {    
    // avoid values that are obviously not configured by the user to be 
    // handled as a valid value (e.g. "[insert your access key here]")
    return propVal==null ||
      (propVal.startsWith(NOT_CONFIGURED_PREFIX) && 
       propVal.endsWith(NOT_CONFIGURED_POSTFIX));
  }
  
  private static String getTrimmedProperty(String propName, Properties props, boolean isRequired, String failsafe) {
    String prop = props.getProperty(propName);
    if (isNotConfigured(prop)) {
        prop = failsafe; 
    }
    if (prop == null) {
      if (isRequired) {
        throw new IllegalStateException(propName + " is missing!");
      }
      return null;
    }
    return prop.trim();
  }
  
  private static String getTrimmedProperty(String propName, Properties props, boolean isRequired) {
      return getTrimmedProperty( propName, props, isRequired, null );
  }

  public PropertiesClientConfig() {
      this(DEFAULT_PROPERTIES_FILENAME);
  }
  
  public PropertiesClientConfig(String propertiesFilename) {
    super();
    
    // load global defaults from $HOME/.aws/auth
    Properties global_props = new java.util.Properties();
    try {
        String aws_auth = System.getProperty("user.home") + java.io.File.separator
                          + ".aws" + java.io.File.separator + "auth";

        global_props.load(new java.io.FileInputStream(new java.io.File(aws_auth)));
        
        setAccessKeyId(getTrimmedProperty(AUTH_ACCESS_KEY,global_props,false));
        setSecretAccessKey(getTrimmedProperty(AUTH_SECRET_KEY,global_props,false));
    } catch (IOException e) {
      // Oh well, just don't initialize global defaults -- hope we've got local settings...
      log.debug("Could not initialize using global defaults", e);  
    }
    
    Properties props = new java.util.Properties();
    try {
      props.load(new java.io.FileInputStream(new java.io.File(propertiesFilename)));
    } catch (IOException e) {
      System.err.println("There was a problem reading your properties file from " + propertiesFilename );
      System.err.println("The exception was " + e.toString() );
      throw new RuntimeException("Cannot load configuration properties file from " + propertiesFilename, e);
    }
    
    // required settings
    boolean required = true;
    setAccessKeyId(getTrimmedProperty(ACCESS_KEY_ID, props, required, getAccessKeyId()));
    setSecretAccessKey(getTrimmedProperty(SECRET_ACCESS_KEY, props, required, getSecretAccessKey()));  
    setServiceURL(getTrimmedProperty(SERVICE_URL, props, required, getServiceURL()));
    
    // optional settings
    setLogLevel(getTrimmedProperty(LOG_LEVEL, props, !required));
    String retryAttemptsProp = getTrimmedProperty(RETRY_ATTEMPTS, props, !required);
    setRetryAttempts(retryAttemptsProp != null ? Integer.parseInt(retryAttemptsProp) : 3);
    
    String retryDelayProp = getTrimmedProperty(RETRY_DELAY_MILLIS, props, !required);
    setRetryDelayMillis(retryDelayProp != null ? Long.parseLong(retryDelayProp) : 500);
    
    String errorsProp = getTrimmedProperty(RETRIABLE_ERRORS, props, !required);
    String[] errors = errorsProp != null ? errorsProp.split(",") : new String[0];
    Set<String> retriableErrors = new HashSet<String>();
    for (String error : errors) {
      retriableErrors.add(error.trim());  
    }
    setRetriableErrors(retriableErrors);
    
  } 
      
}
