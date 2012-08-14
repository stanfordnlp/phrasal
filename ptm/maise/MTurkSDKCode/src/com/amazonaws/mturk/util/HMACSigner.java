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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.axis.encoding.Base64;

/**
 *  The HMACSigner class contains methods to sign strings using HMAC-SHA1 algorithm.
 *  
 *  @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2006-10-31/MakingRequests_RequestAuthenticationArticle.html
 */
public class HMACSigner {

  //-------------------------------------------------------------
  // Constants - Private
  //-------------------------------------------------------------

  private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
  private static final SimpleDateFormat gmtFormat;

  //-------------------------------------------------------------
  // Variables - Private
  //-------------------------------------------------------------

  private Mac mac;

  static {
    // This format must match the Axis format exactly
    gmtFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    gmtFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  //-------------------------------------------------------------
  // Constructors
  //-------------------------------------------------------------

  /**
   * @param key Key must be ASCII and correct.
   */
  public HMACSigner(String key) {
    // Get an hmac_sha1 key from the raw key bytes
    SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), 
        HMAC_SHA1_ALGORITHM);

    // Get an hmac_sha1 Mac instance and initialize with the signing key
    Mac mac = null;

    try {
      mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);

      mac.init(signingKey);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    } catch (InvalidKeyException e) {
      throw new RuntimeException(e);
    }

    this.mac = mac;
  }


  //-------------------------------------------------------------
  // Methods - Public
  //-------------------------------------------------------------

  /**
   * Creates a signature based on the given parameters.
   * 
   * @param service Name of the AWS service
   * @param operation Name of the operation as defined in the WSDL
   * @param timestamp Time instance that is included in the request
   * @return A signature
   */
  public String sign(String service, String operation, Calendar timestamp) {
    String stringToSign = service + operation + gmtFormat.format(timestamp.getTime());

    return sign(stringToSign);
  }

  /**
   * Creates a signature based on the given parameters.
   * 
   * @param toSign A string to sign
   * @return A signature
   */
  public String sign(String toSign) {
    // compute the hmac on input data bytes
    byte[] rawHmac = mac.doFinal(toSign.getBytes());

    // base64-encode the hmac
    return Base64.encode(rawHmac);
  }
}
