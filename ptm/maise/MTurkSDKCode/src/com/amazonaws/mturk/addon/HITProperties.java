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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.amazonaws.mturk.requester.Comparator;
import com.amazonaws.mturk.requester.Locale;
import com.amazonaws.mturk.requester.QualificationRequirement;
import com.amazonaws.mturk.util.VelocityUtil;

/**
 * The HITProperties class provides a structured way to read HIT properties
 * from a file. 
 */
public class HITProperties {

  protected static Logger log = Logger.getLogger(HITProperties.class);

  private Map inputMap = new HashMap();

  private final int MAX_NUM_QUALIFICATIONS_SUPPORTED = 5;

  protected HITField title = HITField.Title;
  protected HITField description = HITField.Description;
  protected HITField keywords = HITField.Keywords;
  protected HITField rewardAmount = HITField.Reward;
  protected HITField assignmentDuration = HITField.AssignmentDuration;
  protected HITField autoApprovalDelay = HITField.AutoApprovalDelay;
  protected HITField lifetime = HITField.Lifetime;
  protected HITField maxAssignments = HITField.MaxAssignments;
  protected HITField annotation = HITField.Annotation;

  protected String[] qualificationType = new String[MAX_NUM_QUALIFICATIONS_SUPPORTED];
  protected String[] qualificationComparator = new String[MAX_NUM_QUALIFICATIONS_SUPPORTED];
  protected String[] qualificationValue = new String[MAX_NUM_QUALIFICATIONS_SUPPORTED];
  protected String[] qualificationPrivate = new String[MAX_NUM_QUALIFICATIONS_SUPPORTED];
  protected String[] qualificationLocale = new String[MAX_NUM_QUALIFICATIONS_SUPPORTED];  

  public final static String QUAL_FIELD = "qualification";
  public final static String QUAL_VALUE_FIELD = "qualification.value";
  public final static String QUAL_COMPARATOR_FIELD = "qualification.comparator";
  public final static String QUAL_LOCALE_FIELD = "qualification.locale";
  public final static String QUAL_PRIVATE_FIELD = "qualification.private";

  public final static HITField[] HIT_FIELDS = { HITField.HitId, HITField.HitTypeId,
    HITField.Title, HITField.Description, HITField.Keywords, HITField.Reward, HITField.CreationTime, 
    HITField.MaxAssignments, HITField.NumAvailableAssignments, HITField.NumPendingAssignments,
    HITField.NumCompletedAssignments, HITField.Status, HITField.ReviewStatus, 
    HITField.Annotation, HITField.AssignmentDuration, HITField.AutoApprovalDelay, HITField.Lifetime, HITField.ViewHITUrl 
  };

  public final static AssignmentField[] ASSIGNMENT_FIELDS = { AssignmentField.AssignmentId, 
    AssignmentField.WorkerId, AssignmentField.Status, AssignmentField.AutoApprovalTime, 
    AssignmentField.AcceptTime, AssignmentField.SubmitTime, AssignmentField.ApprovalTime, 
    AssignmentField.RejectionTime, AssignmentField.Deadline, AssignmentField.RequesterFeedback,
    AssignmentField.Answers, AssignmentField.RejectFlag 
  }; 

  public static enum HITField {
    HitId("hitid"),
      HitTypeId("hittypeid"),
      Title("title"),
      Description("description"),
      Keywords("keywords"),
      Reward("reward"),
      CreationTime("creationtime"),
      MaxAssignments("assignments"),
      NumAvailableAssignments("numavailable"),
      NumPendingAssignments("numpending"),
      NumCompletedAssignments("numcomplete"),
      Status("hitstatus"),
      ReviewStatus("reviewstatus"),
      Annotation("annotation"),
      AssignmentDuration("assignmentduration"),
      AutoApprovalDelay("autoapprovaldelay"),
      Lifetime("hitlifetime"),
      ViewHITUrl("viewhit");

    private String fieldName;
    private String fieldValue;

    private HITField(String fieldName) {
      this.fieldName = fieldName;
    }

    public String getFieldName() {
      return this.fieldName;
    }

    public String getFieldValue() {
      return this.fieldValue;
    }

    public void setFieldValue(String value) {
      this.fieldValue = value;
    }
  }

  public static enum AssignmentField {
    AssignmentId("assignmentid"),
      WorkerId("workerid"),
      Status("assignmentstatus"),
      AcceptTime("assignmentaccepttime"),
      SubmitTime("assignmentsubmittime"),
      AutoApprovalTime("autoapprovaltime"),
      ApprovalTime("assignmentapprovaltime"),
      RejectionTime("assignmentrejecttime"),
      Deadline("deadline"),
      RequesterFeedback("feedback"),
      RejectFlag("reject"),
      Answers("answers[question_id answer_value]");

    private String fieldName;

    private AssignmentField(String fieldName) {
      this.fieldName = fieldName;
    }

    public String getFieldName() {
      return this.fieldName;
    }
  }

  //-------------------------------------------------------------
  // Constructors 
  //-------------------------------------------------------------

  public HITProperties(Properties props) {
    populateFields(props);
  }

  public HITProperties(String propertyFile) throws IOException {
    populateFields(loadPropertiesFile(propertyFile));
  }

  //-------------------------------------------------------------
  // Public Setters 
  //-------------------------------------------------------------

  public void setInputMap(Map inputMap) {
    this.inputMap = inputMap;
  }

  public void setAnnotation(String annotation) {
    this.annotation.setFieldValue(annotation);
  }

  public void setAssignmentDuration(String assignmentDuration) {
    this.assignmentDuration.setFieldValue(assignmentDuration);
  }

  public void setAutoApprovalDelay(String autoApprovalDelay) {
    this.autoApprovalDelay.setFieldValue(autoApprovalDelay);
  } 

  public void setLifetime(String lifetime) {
    this.lifetime.setFieldValue(lifetime);
  } 

  public void setDescription(String description) {
    this.description.setFieldValue(description);
  }

  public void setKeywords(String keywords) {
    this.keywords.setFieldValue(keywords);
  }

  public void setMaxAssignments(String maxAssignments) {
    this.maxAssignments.setFieldValue(maxAssignments);
  }

  public void setRewardAmount(String rewardAmount) {
    this.rewardAmount.setFieldValue(rewardAmount);
  } 

  public void setTitle(String title) {
    this.title.setFieldValue(title);
  } 

  public void setQualificationType(int qualNum, String qualType) {
    qualificationType[qualNum] = qualType;
  }

  public void setQualificationValue(int qualNum, String qualValue) {
    qualificationValue[qualNum] = qualValue;
  }

  public void setQualificationComparator(int qualNum, String qualComparator) {
    qualificationComparator[qualNum] = qualComparator;
  }

  public void setQualificationLocale(int qualNum, String qualLocale) {
    qualificationLocale[qualNum] = qualLocale;
  }

  public void setQualificationPrivate(int qualNum, String qualPrivate) {
    qualificationPrivate[qualNum] = qualPrivate;
  }

  //-------------------------------------------------------------
  // Public Getters 
  //-------------------------------------------------------------

  public String getAnnotation() {
    if (annotation == null) 
      throw new IllegalStateException(annotation.getFieldName() + " is not set");
    return safeTrim(VelocityUtil.doMerge(annotation.getFieldValue(), inputMap));
  } 

  public long getAssignmentDuration() {
    if (assignmentDuration == null) 
      throw new IllegalStateException(assignmentDuration.getFieldName() + " is not set");
    return Long.parseLong(assignmentDuration.getFieldValue());
  }

  public long getLifetime() {
    if (lifetime == null)
      throw new IllegalStateException(lifetime.getFieldName() + " is not set");
    return Long.parseLong(VelocityUtil.doMerge(lifetime.getFieldValue(), inputMap));
  }

  public long getAutoApprovalDelay() {
    if (autoApprovalDelay == null)
      throw new IllegalStateException(autoApprovalDelay.getFieldName() + " is not set");
    return Long.parseLong(autoApprovalDelay.getFieldValue());
  }

  public String getDescription() {
    if (description == null)
      throw new IllegalStateException(description.getFieldName() + " is not set");
    return description.getFieldValue();
  }

  public String getKeywords() {
    if (keywords == null)
      throw new IllegalStateException(keywords.getFieldName() + " is not set");
    return keywords.getFieldValue();
  }

  public int getMaxAssignments() {
    if (maxAssignments == null)
      throw new IllegalStateException(maxAssignments.getFieldName() + " is not set");

    return Integer.parseInt(VelocityUtil.doMerge(maxAssignments.getFieldValue(), inputMap));
  }

  public double getRewardAmount() {
    if (rewardAmount == null)
      throw new IllegalStateException(rewardAmount.getFieldName() + " is not set");

    return Double.parseDouble(rewardAmount.getFieldValue());
  }

  public String getTitle() {
    if (title == null) 
      throw new IllegalStateException(title.getFieldName() + " is not set");
    return title.getFieldValue();
  } 

  public QualificationRequirement[] getQualificationRequirements() {
    List<QualificationRequirement> quals = new ArrayList<QualificationRequirement>();
    for (int i = 0; i < MAX_NUM_QUALIFICATIONS_SUPPORTED; i++)
    {
      if (qualificationType[i] != null && !qualificationType[i].equals(""))
      {
        // There's a qualification type here
        QualificationRequirement thisQual = 
          new QualificationRequirement(getQualificationType(i), 
              getQualificationComparator(i), 
              getQualificationIntegerValue(i),
              getQualificationLocaleValue(i), 
              getQualificationPrivate(i));
        quals.add(thisQual);

      }
    }

    // Return the array of qualifications
    return quals.toArray(new QualificationRequirement[quals.size()]);
  }

  //-------------------------------------------------------------
  // Private Getters 
  //-------------------------------------------------------------

  private String getQualificationType(int qualNum) {
    return safeTrim(VelocityUtil.doMerge(qualificationType[qualNum], inputMap));
  }

  private Integer getQualificationIntegerValue(int qualNum) {
    if (qualificationValue[qualNum] == null || qualificationValue[qualNum].equals(""))
      return null;

    return new Integer(VelocityUtil.doMerge(qualificationValue[qualNum], inputMap));    
  }

  private Comparator getQualificationComparator(int qualNum) {
    String qualComparator = VelocityUtil.doMerge(qualificationComparator[qualNum], inputMap);

    if (qualComparator != null) {
      qualComparator = qualComparator.trim().toLowerCase();

      if ("lessthan".equals(qualComparator))
        return Comparator.LessThan;
      else if ("lessthanorequalto".equals(qualComparator))
        return Comparator.LessThanOrEqualTo;
      else if ("greaterthan".equals(qualComparator))
        return Comparator.GreaterThan;
      else if ("greaterthanorequalto".equals(qualComparator))
        return Comparator.GreaterThanOrEqualTo;
      else if ("equalto".equals(qualComparator))
        return Comparator.EqualTo;
      else if ("notequalto".equals(qualComparator))
        return Comparator.NotEqualTo;
      else if ("exists".equals(qualComparator))
        return Comparator.Exists;
      else {
        log.info("Your configuration file provided an unrecognized comparator: " + qualComparator);
        return null;
      }
    }
    else {
      return null;
    }
  }

  private Locale getQualificationLocaleValue(int qualNum) {
    if (qualificationLocale[qualNum] == null || qualificationLocale[qualNum].equals(""))
      return null;

    String qualLocale = safeTrim(VelocityUtil.doMerge(qualificationLocale[qualNum], inputMap));
    return new Locale(qualLocale);    
  }

  private boolean getQualificationPrivate(int qualNum) {
    String isPrivate = safeTrim(VelocityUtil.doMerge(qualificationPrivate[qualNum], inputMap));
    if (isPrivate != null)
      return "true".equals(isPrivate.toLowerCase());
    else
      return false;
  } 

  //-------------------------------------------------------------
  // Utility Methods 
  //-------------------------------------------------------------

  public String toString() {
    String returnValue = "title:" + getTitle() 
      + " description:" + getDescription() 
      + " keywords:" + getKeywords() 
      + " reward:" + getRewardAmount() 
      + " assigns:" + getMaxAssignments();
    return returnValue;
  } 

  protected void populateFields(Properties props) { 

    setTitle(props.getProperty(HITField.Title.getFieldName()));
    setDescription(props.getProperty(HITField.Description.getFieldName()));
    setKeywords(props.getProperty(HITField.Keywords.getFieldName()));
    setRewardAmount(props.getProperty(HITField.Reward.getFieldName()));
    setMaxAssignments(props.getProperty(HITField.MaxAssignments.getFieldName()));
    setAnnotation(props.getProperty(HITField.Annotation.getFieldName()));
    setAssignmentDuration(props.getProperty(HITField.AssignmentDuration.getFieldName()));
    setAutoApprovalDelay(props.getProperty(HITField.AutoApprovalDelay.getFieldName()));
    setLifetime(props.getProperty(HITField.Lifetime.getFieldName()));   

    for (int i = 1; i <= MAX_NUM_QUALIFICATIONS_SUPPORTED; i++) {
      setQualificationType(i - 1, props.getProperty(QUAL_FIELD + "." + i));
      setQualificationValue(i - 1, props.getProperty(QUAL_VALUE_FIELD + "." + i));
      setQualificationComparator(i - 1, props.getProperty(QUAL_COMPARATOR_FIELD + "." + i));
      setQualificationLocale(i - 1, props.getProperty(QUAL_LOCALE_FIELD + "." + i));
      setQualificationPrivate(i - 1, props.getProperty(QUAL_PRIVATE_FIELD + "." + i));
    }
  } 

  private String safeTrim(String strToTrim) {
    if (strToTrim == null) 
      return null;
    else
      return strToTrim.trim();
  }

  private static Properties loadPropertiesFile(String fileName) throws IOException {
    // Read properties file.
    Properties props = new Properties();
    props.load(new InputStreamReader(new FileInputStream(new java.io.File(fileName)), "UTF-8"));
    return props;
  }
}
