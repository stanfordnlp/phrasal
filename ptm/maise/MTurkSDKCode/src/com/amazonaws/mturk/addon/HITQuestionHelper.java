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


package com.amazonaws.mturk.addon;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.log4j.Logger;

/**
 * The HITQuestionHelper class provides helper methods for HITQuestion.
 */
public class HITQuestionHelper
{
  private final String URL_ENCODING_TYPE = "UTF-8";
  protected static Logger log = Logger.getLogger(HITQuestionHelper.class);

  public HITQuestionHelper()
  {
    // Do nothing
  }

  public String urlencode(String strToEncode)
  {
    if (strToEncode == null)
      return strToEncode;

    try
    {
      return URLEncoder.encode(strToEncode, URL_ENCODING_TYPE);
    }
    catch (UnsupportedEncodingException e)
    {
      log.error(URL_ENCODING_TYPE + " not supported as an encoding type for URLs", e);
      return strToEncode;
    }
  }
}
