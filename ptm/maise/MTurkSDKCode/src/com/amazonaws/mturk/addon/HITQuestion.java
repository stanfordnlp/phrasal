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

import java.io.File;
import java.io.StringWriter;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.app.tools.VelocityFormatter;

/**
 * The HITQuestion class provides a structured way to read a HIT Question XML
 * from a file. 
 */
public class HITQuestion {

  protected static Logger log = Logger.getLogger(HITQuestion.class);

  public final static String ENCODED_KEY_SUFFIX = "_urlencoded";
  public final static String RAW_KEY_SUFFIX = "_raw";	
  private Template velocityTemplate = null;
  private String question;

  public HITQuestion() {
    // Don't use a velocity template but instead 
    //   set questionXML explicitly using setQuestionXML()
  }

  public HITQuestion(String fileName) throws Exception {

    // Setup a velocity template to create templated questions
    //   using getQuestion(Map<String, String> input)

    VelocityEngine engine = new VelocityEngine();
    File f = new File(fileName);

    Properties p = new Properties();

    p.setProperty( "resource.loader", "file" );
    p.setProperty( "file.resource.loader.class", 
        "org.apache.velocity.runtime.resource.loader.FileResourceLoader" );
    p.setProperty( "file.resource.loader.path", f.getParent() == null ? "." : f.getParent());
    p.setProperty( "file.resource.loader.cache", "true");
    p.setProperty( "file.resource.loader.modificationCheckInterval", "2");
    p.setProperty( "input.encoding", "UTF-8");
    
    engine.setProperty( VelocityEngine.RUNTIME_LOG_LOGSYSTEM, log);    
    engine.init(p);

    velocityTemplate = engine.getTemplate( f.getName() );

    this.question = null;
  }

  public void setQuestion(String questionXML) {
    this.question = questionXML; 
  }

  public String getQuestion() {
    if (this.question != null)
      return this.question;

    return getQuestion(null);
  }

  public String getQuestion(Map<String, String> input) {

    // If there is not velocity template associated with this question
    if (this.velocityTemplate == null) {
      // Return the explicitly set qeustion XML
      return this.question;
    }

    try {
      VelocityContext context = new VelocityContext();

      // Add some generic helpers just in case
      context.put("formatter", new VelocityFormatter(context));
      context.put("helper", new HITQuestionHelper());
      context.put("today", new Date());     

      if (input != null && input.values() != null) {        
        Iterator iter = input.keySet().iterator();
        while (iter.hasNext()) {
          String key = (String)iter.next();

          // Make a RAW version that's untouched
          context.put(key + RAW_KEY_SUFFIX, input.get(key));

          // Make the default version a QAP-cleaned version
          if (input.get(key) != null && input.get(key) instanceof String)
            context.put(key, cleanString((String)input.get(key)));
          else
            context.put(key, input.get(key));
        }
      }

      StringWriter writer = new StringWriter();

      this.velocityTemplate.merge(context, writer);
      this.question = writer.toString();
      return question;
    }
    catch (Exception e) {
      log.error("Could not read Question", e);
      return null;
    }
  }

  private String cleanString(String strToClean) {
    if (strToClean == null) return null;

    strToClean = strToClean.replaceAll("&", "&amp;");
    strToClean = strToClean.replaceAll("<", "&lt;");
    strToClean = strToClean.replaceAll(">", "&gt;");		

    return strToClean;
  }
}
