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

import java.io.StringWriter;
import java.util.Map;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

public class VelocityUtil {

  public static String doMerge(String strToMerge, Map map) {
    if (strToMerge == null)
      return null;

    try {

      // ask Velocity to evaluate it.
      Velocity.init();
      StringWriter w = new StringWriter();
      VelocityContext context = new VelocityContext(map);
      Velocity.evaluate(context, w, "logTag", strToMerge);
      return w.getBuffer().toString();

    } catch (Exception e) {
      return null;
    }
  }
}
