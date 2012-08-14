package com.amazonaws.mturk.test;
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


import java.util.ArrayList;
import java.util.Properties;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import com.amazonaws.mturk.addon.HITDataBuffer;
import com.amazonaws.mturk.addon.HITProperties;
import com.amazonaws.mturk.addon.HITQuestion;
import com.amazonaws.mturk.requester.Comparator;
import com.amazonaws.mturk.requester.EventType;
import com.amazonaws.mturk.requester.HIT;
import com.amazonaws.mturk.requester.NotificationSpecification;
import com.amazonaws.mturk.requester.NotificationTransport;
import com.amazonaws.mturk.requester.QualificationRequirement;
import com.amazonaws.mturk.requester.QualificationType;
import com.amazonaws.mturk.requester.QualificationTypeStatus;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.service.exception.ServiceException;
import com.amazonaws.mturk.util.PropertiesClientConfig;

public abstract class TestBase extends TestCase {
  
  protected static RequesterService service = 
      new RequesterService(new PropertiesClientConfig("etc/test/requesterTest.properties"));

  protected static String luckyWorker = "A1DTY366E1E1JO";
  protected static String testHITId = null;
  protected static String testHITTypeId = null;
  protected static String testQualTypeId = null;
 
  protected static String unique;
  
  protected static String defaultHITTitle;
  protected static String defaultHITDescription;
  protected static String defaultHITKeywords;
  protected static long defaultAssignmentDurationInSeconds;
  protected static long defaultAutoApprovalDelayInSeconds;
  protected static long defaultLifetimeInSeconds;
  protected static int defaultMaxAssignments;
  protected static double defaultReward;
  protected static String defaultAnnotation;
  protected static String defaultQuestion;
  protected static String defaultAnswer;
  protected static QualificationRequirement[] defaultQualRequirements;
  
  protected static String defaultQualificationName;
  protected static String defaultQualificationDescription;
  protected static String defaultQuery;
  protected static long defaultRetryDelayInSeconds;
  
  protected static String defaultQuestionIdentifier;
  
  protected static int defaultMaxAssignmentsIncrement; 
  protected static long defaultExpirationIncrementInSeconds;
  protected static String defaultReason;
  
  protected static Integer defaultPageNum;
  protected static Integer defaultPageSize;
  protected static NotificationSpecification defaultNotificationSpec;
  protected static int defaultWorkerAcceptLimit;
  
  protected static HITDataBuffer defaultHITInput;
  protected static HITProperties defaultHITProperties;
  protected static HITQuestion defaultHITQuestion;
  protected static HITDataBuffer defaultSuccess;
  
  protected static String defaultTestDir;
  protected static String defaultQuestionFileName;
  protected static String defaultInvalidQuestionFileName;
  protected static String defaultFormattedContentQuestionFileName;
  protected static String defaultInvalidFormattedContentQuestionFileName;
  protected static String defaultExternalQuestionFileName;
  protected static String defaultInvalidExternalQuestionFileName;
  protected static String defaultHTMLQuestionFileName;
  protected static String defaultInvalidHTMLQuestionFileName;
  protected static String defaultScriptFormattedContentQuestionFileName;
  protected static String defaultPreviewFileName;
  protected static String defaultInputFileName;
  protected static String defaultNoHeaderInputFileName;
  protected static String defaultOutputFileName;
  protected static String defaultPlaceholder;
  protected static String defaultUTF8PropertiesFileName;
  protected static String defaultUTF8QuestionFileName;
  
  protected static ArrayList<String[]> defaultHITInputData;
  protected static String[] defaultHITInputFields;

  protected final static String BOGUS_STR = "BOGUSVALUE";
  protected final static String BOGUS_HITID = "BOGUS9ZF5WWA6JCBOGUS";
  protected final static QualificationRequirement[] NULL_QUAL_REQUIREMENTS = null;
  protected final static String SYSTEM_QUAL_TYPE_ID = 
    "00000000000000000000";  // HIT submission rate %
  protected final static String DEFAULT_ANSWER_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
    + "<QuestionFormAnswers xmlns=\"http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2005-10-01/QuestionFormAnswers.xsd\">"
    + "<Answer>"
    + "<QuestionIdentifier>freeText</QuestionIdentifier>"
    + "<FreeText>freeText_answer</FreeText>"
    + "</Answer>"
    + "<Answer>"
    + "<QuestionIdentifier>otherSelection</QuestionIdentifier>"
    + "<OtherSelectionText>otherSelection_answer</OtherSelectionText>"
    + "</Answer>"
    + "<Answer>"
    + "<QuestionIdentifier>url</QuestionIdentifier>"
    + "<UploadedFileSizeInBytes>100</UploadedFileSizeInBytes>"
    + "<UploadedFileKey>uploadedFileKey</UploadedFileKey>"
    + "</Answer>"
    + "<Answer>"
    + "<QuestionIdentifier>selectionIdentifier</QuestionIdentifier>"
    + "<SelectionIdentifier>selection_answer</SelectionIdentifier>"
    + "<SelectionIdentifier>selection_answer2</SelectionIdentifier>"
    + "</Answer>"
    + "</QuestionFormAnswers>";
  
  static {
    
    defaultHITTitle = "Test HIT";
    defaultHITDescription = "This is a test HIT description.";
    defaultHITKeywords = "test";
    defaultAssignmentDurationInSeconds = 30;
    defaultAutoApprovalDelayInSeconds = 30;
    defaultLifetimeInSeconds = 30;
    defaultMaxAssignments = 1;
    defaultReward = 0.01;
    defaultAnnotation = "testannotation";
    defaultQuestion = "This is a test question";
    defaultAnswer = "Test Answer";
    QualificationRequirement q = new QualificationRequirement();
    q.setQualificationTypeId(SYSTEM_QUAL_TYPE_ID);
    q.setComparator(Comparator.GreaterThanOrEqualTo);
    q.setIntegerValue(0);
    defaultQualRequirements = new QualificationRequirement[] { q };
    
    defaultQualificationName = "Test qualification name";
    defaultQualificationDescription = "This is a test qualification.";
    defaultQuery = "test";
    defaultRetryDelayInSeconds = 1;
    
    defaultMaxAssignmentsIncrement = 1;
    defaultExpirationIncrementInSeconds = 60 * 60; // 1 hour
    defaultReason = "This is a test reason.";
    
    defaultPageNum = 1;
    defaultPageSize = 10;
    defaultWorkerAcceptLimit = 11;
    
    String[] headers = new String[] { "testheader1","testheader2"};
    String[] line1 = new String[] { "testvalue1_1","testvalue1_2"};
    String[] line2 = new String[] { "testvalue2_1","testvalue2_2"};
    String[] line3 = new String[] { "testvalue3_1","testvalue3_2"};
    defaultHITInputData = new ArrayList<String[]>();
    defaultHITInputData.add( headers );
    defaultHITInputData.add( line1 );
    defaultHITInputData.add( line2 );
    defaultHITInputData.add( line3 );
    defaultHITInput = new HITDataBuffer();
    defaultHITInput.setRows(defaultHITInputData);

    defaultPlaceholder = "testheader";
    
    defaultSuccess = new HITDataBuffer();
    ArrayList<String[]> defaultSuccessData = new ArrayList<String[]>();
    defaultSuccessData.add( new String[] { "hitid","hittypeid"} );
    defaultSuccessData.add( new String[] { "test_hitid1","test_hittypeid1"} );
    defaultSuccessData.add( new String[] { "test_hitid2","test_hittypeid2"} );
    defaultSuccess.setRows( defaultSuccessData );
    
    defaultTestDir = "etc/test";
    defaultPreviewFileName = defaultTestDir + "/testPreview.html";
    defaultInputFileName = defaultTestDir + "/testInput.tab";
    defaultNoHeaderInputFileName = defaultTestDir + "/testNoHeaderInput.tab";
    defaultOutputFileName = "testHITDataWriter.tmp";
    defaultQuestionFileName = defaultTestDir + "/testQuestion.txt";
    defaultInvalidQuestionFileName = defaultTestDir + "/testInvalidQuestion.txt";
    defaultFormattedContentQuestionFileName = defaultTestDir + "/testFormattedContentQuestion.txt";
    defaultInvalidFormattedContentQuestionFileName = defaultTestDir + "/testInvalidFormattedContentQuestion.txt";
    defaultScriptFormattedContentQuestionFileName = defaultTestDir + "/testScriptFormattedContent.txt";
    defaultExternalQuestionFileName = defaultTestDir + "/testExternalQuestion.txt";
    defaultInvalidExternalQuestionFileName = defaultTestDir + "/testInvalidExternalQuestion.txt";
    defaultHTMLQuestionFileName = defaultTestDir + "/testHTMLQuestion.txt";
    defaultInvalidHTMLQuestionFileName = defaultTestDir + "/testInvalidHTMLQuestion.txt";
    defaultUTF8PropertiesFileName = defaultTestDir + "/testUTF8.properties";
    defaultUTF8QuestionFileName = defaultTestDir + "/testUTF8Question.xml";
    
    Properties props = new Properties();
    props.setProperty(HITProperties.HITField.Title.getFieldName(), defaultHITTitle);
    props.setProperty(HITProperties.HITField.Description.getFieldName(), defaultHITDescription);
    props.setProperty(HITProperties.HITField.Keywords.getFieldName(), defaultHITKeywords);
    props.setProperty(HITProperties.HITField.AssignmentDuration.getFieldName(), 
        Long.toString(defaultAssignmentDurationInSeconds));
    props.setProperty(HITProperties.HITField.AutoApprovalDelay.getFieldName(), 
        Long.toString(defaultAutoApprovalDelayInSeconds));
    props.setProperty(HITProperties.HITField.Lifetime.getFieldName(), 
        Long.toString(defaultLifetimeInSeconds));
    props.setProperty(HITProperties.HITField.MaxAssignments.getFieldName(), 
        Long.toString(defaultMaxAssignments));
    props.setProperty(HITProperties.HITField.Reward.getFieldName(), 
        Double.toString(defaultReward));
    props.setProperty(HITProperties.HITField.Annotation.getFieldName(), defaultAnnotation);
    defaultHITProperties = new HITProperties(props);
    defaultHITProperties.setQualificationComparator(1, "greaterthan");
    defaultHITProperties.setQualificationType(1, RequesterService.APPROVAL_RATE_QUALIFICATION_TYPE_ID);
    defaultHITProperties.setQualificationValue(1, "25");
    
    defaultHITQuestion = new HITQuestion();
    defaultHITQuestion.setQuestion( RequesterService.getBasicFreeTextQuestion( defaultQuestion) );
    
    defaultNotificationSpec = new NotificationSpecification();
    defaultNotificationSpec.setTransport(NotificationTransport.REST);
    defaultNotificationSpec.setVersion(RequesterService.NOTIFICATION_VERSION);
    defaultNotificationSpec.setEventType(new EventType[] { EventType.AssignmentAbandoned,
        EventType.AssignmentReturned });
    defaultNotificationSpec.setDestination("http://example.com:8080/mt/notifications.cgi");
  }
  
  public static void main(String[] args) {
    TestRunner.run(TestBase.class);
  }

  public TestBase(String arg0) {
    super(arg0);
  }

  protected void setUp() throws Exception {
    super.setUp();
    
    unique = Long.toString(System.currentTimeMillis());

  }
  protected void tearDown() throws Exception {
      super.tearDown();
  }
    
  protected String getTestHITTypeId() throws ServiceException {
      if (testHITTypeId == null) {
        getTestHITId();
      }
      
      return testHITTypeId;
  }
  protected String getTestHITId() throws ServiceException {
      if (testHITId == null) {
        
        HIT hit = createHIT();
        testHITId = hit.getHITId();
        testHITTypeId = hit.getHITTypeId();
        
      }
      
      return testHITId;
    }

  protected void clearTestHITIds() {
      testHITTypeId = null;  
      testHITId = null;
 }
    
  protected String getTestQualificationTypeId() throws ServiceException {
      testQualTypeId = createQualificationType().getQualificationTypeId();
    
    return testQualTypeId;
  }
  
  protected HIT createHIT() throws ServiceException {
    return createHIT(null);
  }

  protected HIT createHIT(QualificationRequirement qualRequirement) throws ServiceException {
    QualificationRequirement[] qualRequirements = null;
    if (qualRequirement != null) { 
      qualRequirements = new QualificationRequirement[] { qualRequirement };
    }
    
    HIT hit = service.createHIT(null, // HITTypeId 
        defaultHITTitle + unique, 
        defaultHITDescription, null, // keywords 
        RequesterService.getBasicFreeTextQuestion(defaultQuestion), defaultReward, 
        defaultAssignmentDurationInSeconds, defaultAutoApprovalDelayInSeconds, 
        defaultLifetimeInSeconds, defaultMaxAssignments, null, // requesterAnnotation 
        qualRequirements,
        null  // responseGroup
      );
      
    assertNotNull(hit);
    assertNotNull(hit.getHITId());
    
    return hit;
  }
  
  protected QualificationType createQualificationType() throws ServiceException {
    QualificationType qualType = service.createQualificationType(
        defaultQualificationName + unique, 
        null, // keywords
        defaultQualificationDescription, QualificationTypeStatus.Active, (long) 0,
        null, // test
        null, // answerKey
        null, // testDurationInSeconds
        null, // autoGranted
        null // autoGrantedValue
      );
        
    assertNotNull(qualType);
    
    return qualType;
  }
  
  protected void assertContains(String message, String substring, String full) {
    assertTrue(
        String.format("%s expected substring:<%s> but was:<%s>", message, substring, full),
        full.contains(substring));
  }
}
