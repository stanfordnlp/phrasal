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


import junit.textui.TestRunner;
import com.amazonaws.mturk.requester.Comparator;
import com.amazonaws.mturk.requester.DataPoint;
import com.amazonaws.mturk.requester.EventType;
import com.amazonaws.mturk.requester.GetAccountBalanceResult;
import com.amazonaws.mturk.requester.GetBlockedWorkersResult;
import com.amazonaws.mturk.requester.GetHITsForQualificationTypeResult;
import com.amazonaws.mturk.requester.GetReviewResultsForHITResult;
import com.amazonaws.mturk.requester.HIT;
import com.amazonaws.mturk.requester.HelpRequestHelpType;
import com.amazonaws.mturk.requester.Information;
import com.amazonaws.mturk.requester.NotificationSpecification;
import com.amazonaws.mturk.requester.ParameterMapEntry;
import com.amazonaws.mturk.requester.PolicyParameter;
import com.amazonaws.mturk.requester.QualificationRequirement;
import com.amazonaws.mturk.requester.QualificationType;
import com.amazonaws.mturk.requester.QualificationTypeStatus;
import com.amazonaws.mturk.requester.RequesterStatistic;
import com.amazonaws.mturk.requester.ReviewPolicy;
import com.amazonaws.mturk.requester.ReviewPolicyLevel;
import com.amazonaws.mturk.requester.SearchHITsResult;
import com.amazonaws.mturk.requester.SearchHITsSortProperty;
import com.amazonaws.mturk.requester.SearchQualificationTypesResult;
import com.amazonaws.mturk.requester.SearchQualificationTypesSortProperty;
import com.amazonaws.mturk.requester.TimePeriod;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.service.exception.InvalidParameterValueException;
import com.amazonaws.mturk.service.exception.ObjectDoesNotExistException;
import com.amazonaws.mturk.service.exception.ServiceException;
import com.amazonaws.mturk.service.exception.ParseErrorException;
import com.amazonaws.mturk.util.ClientConfig;
import com.amazonaws.mturk.service.axis.AsyncCallback;
import com.amazonaws.mturk.service.axis.AsyncReply;

public class TestRequesterServiceRaw extends TestBase {
  
  public static void main(String[] args) {
    TestRunner.run(TestRequesterServiceRaw.class);
  }

  public TestRequesterServiceRaw(String arg0) {
    super(arg0);
  }

  public void testCreateHIT() throws ServiceException {
    createHIT();
  }
  
  public void testCreateHITIdempotency() throws ServiceException {
    try {
      String uniqueRequestToken = "testCreateHITIdempotency" + unique;
      for (int i = 0; i < 2; i++) {
        service.createHIT(
            null, // hitTypeId
            defaultHITTitle + unique,
            defaultHITDescription,
            null, // keywords
            RequesterService.getBasicFreeTextQuestion(defaultQuestion),
            defaultReward,
            defaultAssignmentDurationInSeconds,
            defaultAutoApprovalDelayInSeconds,
            defaultLifetimeInSeconds,
            defaultMaxAssignments,
            null, // requesterAnnotation
            null, // qualificationRequirements
            null, // responseGroup
            uniqueRequestToken,
            null, // assignmentReviewPolicy
            null); // hitReviewPolicy
      }
      fail("Re-use of uniqueRequestToken did not cause an error");
    } catch (ServiceException e) {
      // expected
      assertTrue("createHIT failed, but not because of uniqueRequestToken re-use",
          e.getMessage().contains("AWS.MechanicalTurk.HITAlreadyExists"));
    }
  }
  
  public void testCreateHITWithInvalidQualTypeId() throws ServiceException {
    try {
      QualificationRequirement qualRequirement = new QualificationRequirement();
      qualRequirement.setQualificationTypeId("0000000000000INVALID");
      qualRequirement.setComparator(Comparator.GreaterThanOrEqualTo);
      qualRequirement.setIntegerValue(0);
      createHIT(qualRequirement);
      
      fail("Expected ObjectDoesNotExistException.");
    } catch (ObjectDoesNotExistException e) {
      // Expected exception
    } catch (InvalidParameterValueException e ) {
      // Expected exception
    }
  }
  
  public void testCreateHITWithReviewPolicy() throws ServiceException {
    /* Create some review policies.
     * The policy details are nonsense and are not asserted on -- this test
     * just verifies that (1) the act of plugging in a review policy does not
     * cause createHIT to blow up, and that (2) said review policies appear in
     * the HIT's review results. */
    PolicyParameter[] skaParams = {
        new PolicyParameter("AnswerKey", null,
            new ParameterMapEntry[] { new ParameterMapEntry("1", new String[] {"correct answer"}) })
    };
    ReviewPolicy assignmentReviewPolicy = new ReviewPolicy("ScoreMyKnownAnswers/2011-09-01", skaParams);
    PolicyParameter[] phrParams = {
        new PolicyParameter("QuestionIds", new String[] {"1"}, null),
        new PolicyParameter("QuestionAgreementThreshold", new String[] {"50"}, null),
    };
    ReviewPolicy hitReviewPolicy = new ReviewPolicy("SimplePlurality/2011-09-01", phrParams);
    
    // Create the HIT with review policies
    HIT hit = service.createHIT(
        null, // hitTypeId
        defaultHITTitle + unique,
        defaultHITDescription,
        null, // keywords
        RequesterService.getBasicFreeTextQuestion(defaultQuestion),
        defaultReward,
        defaultAssignmentDurationInSeconds,
        defaultAutoApprovalDelayInSeconds,
        defaultLifetimeInSeconds,
        defaultMaxAssignments,
        null, // requesterAnnotation
        null, // qualificationRequirements
        null, // responseGroup
        null, // uniqueRequestToken
        assignmentReviewPolicy,
        hitReviewPolicy);
    
    // Make sure the policies were actually added to the created HIT
    GetReviewResultsForHITResult results = service.getReviewResultsForHIT(
        hit.getHITId(),
        new ReviewPolicyLevel[] {ReviewPolicyLevel.Assignment, ReviewPolicyLevel.HIT},
        true, // retrieveActions
        true, // retrieveResults
        1, // pageNumber
        1000, // pageSize
        null); // responseGroup
    assertEquals("Unexpected assignment policy", results.getAssignmentReviewPolicy().getPolicyName(), "ScoreMyKnownAnswers/2011-09-01");
    assertEquals("Unexpected HIT policy", results.getHITReviewPolicy().getPolicyName(), "SimplePlurality/2011-09-01");
  }
  
  public void testCreateHITWithInvalidReviewPolicy() throws ServiceException {
    // create a SKA policy that is missing required parameter AnswerKey 
    ReviewPolicy assignmentReviewPolicy = new ReviewPolicy("ScoreMyKnownAnswers/2011-09-01",
        new PolicyParameter[] {});
    
    try {
      service.createHIT(
          null, // hitTypeId
          defaultHITTitle + unique,
          defaultHITDescription,
          null, // keywords
          RequesterService.getBasicFreeTextQuestion(defaultQuestion),
          defaultReward,
          defaultAssignmentDurationInSeconds,
          defaultAutoApprovalDelayInSeconds,
          defaultLifetimeInSeconds,
          defaultMaxAssignments,
          null, // requesterAnnotation
          null, // qualificationRequirements
          null, // responseGroup
          null, // uniqueRequestToken
          assignmentReviewPolicy,
          null); // hitReviewPolicy
      fail("createHIT succeeded, despite having an invalid review policy");
    } catch (ServiceException e) {
      // expected
      assertTrue("createHIT failed, but not because of an invalid review policy",
          e.getMessage().contains("The AnswerKey parameter is mandatory."));
    }
  }
  
  public void testRegisterHITType() throws ServiceException {
    String hitTypeId = service.registerHITType(defaultAutoApprovalDelayInSeconds, 
        defaultAssignmentDurationInSeconds, defaultReward, defaultHITTitle, null, 
        defaultHITDescription, NULL_QUAL_REQUIREMENTS);
    
    assertNotNull(hitTypeId);
  }

  public void testChangeHITTypeOfHIT() throws ServiceException {
    String hitId = createHIT().getHITId();
    String hitTypeId = service.registerHITType(defaultAutoApprovalDelayInSeconds,
        defaultAssignmentDurationInSeconds, defaultReward * 2, defaultHITTitle, null,
        defaultHITDescription + " - revised", NULL_QUAL_REQUIREMENTS);
    service.changeHITTypeOfHIT(hitId, hitTypeId);
    HIT hit = service.getHIT(hitId);
    assertEquals(hit.getHITTypeId(), hitTypeId);
  }

  

  public void testSendTestEventNotification(NotificationSpecification notification, EventType testEventType)
    throws ServiceException {
    service.sendTestEventNotification(defaultNotificationSpec, 
        EventType.AssignmentSubmitted);
  }

  public void testDisableHIT() throws ServiceException { 
    String hitId = createHIT().getHITId();
    service.disableHIT(hitId);
  }
  
  public void testGetHIT() throws ServiceException {
      String hitId = getTestHITId();
      HIT hit = service.getHIT(hitId, null);
      
      assertNotNull(hit);
      assertTrue(hit.getHITId().equals(hitId));
  }


  public void testGetHITsForQualificationType() throws ServiceException {
    createHIT( defaultQualRequirements[0] );
    
    GetHITsForQualificationTypeResult result =
      service.getHITsForQualificationType(SYSTEM_QUAL_TYPE_ID, defaultPageNum, defaultPageSize);
    
    assertNotNull(result);
    assertNotNull(result.getHIT(0));
  }

  
  public void testExtendHIT() throws ServiceException {
    service.extendHIT(getTestHITId(), defaultMaxAssignmentsIncrement, 
        defaultExpirationIncrementInSeconds);
  }

  public void testForceExpireHIT() throws ServiceException {
    String hitId = createHIT().getHITId();
    service.forceExpireHIT(hitId);

    // Don't reuse this test HIT now that it's expired 
    clearTestHITIds();
  }
  
  public void testSearchHITs() throws ServiceException {
    SearchHITsResult result = service.searchHITs(RequesterService.DEFAULT_SORT_DIRECTION,
        SearchHITsSortProperty.CreationTime, defaultPageNum, defaultPageSize, null);
    
    assertNotNull(result);
  }

  public void testSearchHITsResponseGroups() throws ServiceException {
    getTestHITId(); // make sure there is at least one HIT
    
    SearchHITsResult result = service.searchHITs(RequesterService.DEFAULT_SORT_DIRECTION,
        SearchHITsSortProperty.CreationTime, defaultPageNum, defaultPageSize,
        new String [] {"Minimal", "HITQuestion"});
    
    assertNotNull(result);
    assertNotNull(result.getHIT(0));
    HIT hit = result.getHIT(0);
    assertNotNull(hit.getQuestion());
    
    result = service.searchHITs(RequesterService.DEFAULT_SORT_DIRECTION,
        SearchHITsSortProperty.CreationTime, defaultPageNum, defaultPageSize,
        new String [] {"Minimal"});
    
    assertNotNull(result);
    assertNotNull(result.getHIT(0));
    hit = result.getHIT(0);
    assertNull(hit.getQuestion());
  }
  
  

  
  public void testCreateQualificationType() throws ServiceException { 
    createQualificationType();
  }

  
  
  public void testAssignQualification() throws ServiceException {
    String qualTypeId = getTestQualificationTypeId();

    service.assignQualification(
        qualTypeId, // qualificationTypeId
        luckyWorker, // workerId
        50, // integerValue
        false // sendNotification
      );
    
  } 

  public void testRevokeQualification() throws ServiceException { 
    String qualTypeId = getTestQualificationTypeId();

    service.assignQualification(
        qualTypeId, // qualificationTypeId
        luckyWorker, // workerId
        50, // integerValue
        false // sendNotification
      );
    
    service.revokeQualification(
        qualTypeId, // qualificationTypeId
        luckyWorker, // workerId
        defaultReason // reason
      );
  }

  
  public void testGetQualificationType() throws ServiceException {
    String qualTypeId = getTestQualificationTypeId();
    QualificationType qualType = service.getQualificationType(qualTypeId);
    
    assertNotNull(qualType);
  }

  

  public void testSearchQualificationTypes()
    throws ServiceException {
    SearchQualificationTypesResult result = service.searchQualificationTypes(
        defaultQuery, false, // mustBeRequestable
        false, // mustBeOwnedByCaller
        RequesterService.DEFAULT_SORT_DIRECTION,
        SearchQualificationTypesSortProperty.Name,
        defaultPageNum, defaultPageSize
      );
    
    assertNotNull(result);
  }

  public void testUpdateQualificationType() throws ServiceException {
    
    QualificationType qualType = service.updateQualificationType(
        getTestQualificationTypeId(), null, // description
        QualificationTypeStatus.Active, null, // test
        null, // answerKey
        null,
        defaultRetryDelayInSeconds,
        true, // autoGranted
        0 // autoGrantedValue
    );
    
    assertNotNull(qualType);
  }

  public void testGetAccountBalance() throws ServiceException {
    GetAccountBalanceResult result = service.getAccountBalance(null);
    
    assertNotNull(result);
  }

  public void testGetRequesterStatistic() throws ServiceException {
    DataPoint[] result = service.getRequesterStatistic(
        RequesterStatistic.AverageRewardAmount,
        TimePeriod.OneDay, 1 // count
    );
    
    assertNotNull(result);
  }
  
  public void testGetRequesterWorkerStatistic() throws ServiceException {
    DataPoint[] datum = service.getRequesterWorkerStatistic(
        RequesterStatistic.NumberAssignmentsApproved,
        TimePeriod.LifeToDate,
        luckyWorker,
        null, // count
        null); // responseGroup
    assertEquals("Unexpected number of DataPoints returned", datum.length, 1);
    assertNotNull("Returned DataPoint was null", datum[0]);
  }
  
  public void testNotifyWorkers() throws ServiceException { 
    
    // This test fails intermittently with AWS.ServiceUnavailable
    //     or with AWS.MechanicalTurk.InvalidTransportEndpoint
    // This appears to be a problem with the MTS API rather than the SDK
    
    service.notifyWorkers(BOGUS_STR, // subject 
        BOGUS_STR, // messageText
        new String[] { luckyWorker } // workerId
    );
  }
  
  public void testGetBlockedWorkers() throws ServiceException {
    GetBlockedWorkersResult result = service.getBlockedWorkers(1, 10);
    assertTrue(result.getPageNumber() + " is an invalid page number (expected page 1)",
        result.getPageNumber() != null && result.getPageNumber() == 1);
    assertTrue(result.getNumResults() + " is an invalid number of results per page (expected from 0 to 10)",
        result.getNumResults() != null && result.getNumResults() >= 0 && result.getNumResults() <= 10);
    assertTrue("Total result count " + result.getTotalNumResults()
        + " is smaller than this page's count " + result.getNumResults(),
        result.getTotalNumResults() != null && result.getTotalNumResults() >= result.getNumResults());
  }
  
  public void testGetAssignment() throws ServiceException {
    try {
      service.getAssignment("INVALIDASSIGNMENTID");
      fail("getAssignment succeeded, despite not having a valid assignment ID");
    } catch (ServiceException e) {
      // expected
      assertContains("Cause of getAssignment failure wasn't an invalid assignment ID.",
          "AWS.MechanicalTurk.AssignmentDoesNotExist", e.getMessage());
    }
  }
  
  public void testApproveRejectedAssignment() throws ServiceException {
    try {
      service.approveRejectedAssignment("INVALIDASSIGNMENTID", defaultReason);
      fail("approveRejectedAssignment succeeded, despite not having a valid assignment ID");
    } catch (ServiceException e) {
      // expected
      assertContains("Cause of approveRejectedAssignment wasn't an invalid assignment ID.",
          "AWS.MechanicalTurk.AssignmentDoesNotExist", e.getMessage());
    }
  }
  
  public void testHelp() throws ServiceException { 
    Information info = service.help(new String[] { "CreateHIT" }, 
        HelpRequestHelpType.Operation);
    
    assertNotNull(info);
  }
  
  protected void clearTestHITIds() {
      testHITTypeId = null;  
      testHITId = null;
    }

  public void testCreateHITAsync() {
    AsyncReply reply = service.createHITAsync(
        null, // HITTypeId
        "Async_" + defaultHITTitle + unique,
        "Async_" + defaultHITDescription,
        null, // keywords
        RequesterService.getBasicFreeTextQuestion(defaultQuestion),
        defaultReward, defaultAssignmentDurationInSeconds,
        defaultAutoApprovalDelayInSeconds, defaultLifetimeInSeconds,
        defaultMaxAssignments, null, // requesterAnnotation
        null, 
        null,	// responseGroup
        null, // uniqueRequestToken
        null, // assignmentReviewPolicy
        null, // hitReviewPolicy
        null	// callback
    );

    assertNotNull(reply);
    assertNotNull(reply.getFuture());
    assertFalse(reply.getFuture().isDone());

    // wait for result
    HIT hit = ((HIT[]) reply.getResult())[0];

    assertNotNull(hit);
    assertNotNull(hit.getHITId());
    assertTrue(reply.getFuture().isDone());
  }

  public void testCreateHITsAsync() {

    if (service.getConfig().getServiceURL().equals(ClientConfig.SANDBOX_SERVICE_URL)) {
      // since we test that async performs better, let's wait a 20 secs
      // so that we can execute the requests without throttling skewing the times
      try {
        Thread.sleep(5000);
      }
      catch (InterruptedException ie) {
        //do nothing
      }
    }

    long t1 = System.currentTimeMillis();

    AsyncReply[] replies = new AsyncReply[10];

    // submit to work queue
    for (int i = 0; i < replies.length; i++) {
      replies[i] = service.createHITAsync(
          null, // HITTypeId
          "Async_" + String.valueOf(i) + "_" + defaultHITTitle
          + unique,
          "Async_" + String.valueOf(i) + "_" + defaultHITDescription,
          null, // keywords
          RequesterService.getBasicFreeTextQuestion(defaultQuestion),
          defaultReward, defaultAssignmentDurationInSeconds,
          defaultAutoApprovalDelayInSeconds,
          defaultLifetimeInSeconds, defaultMaxAssignments, null, // requesterAnnotation
          null, 
          null,	// responseGroup
          null, // uniqueRequestToken
          null, // assignmentReviewPolicy
          null, // hitReviewPolicy
          null	// callback
      );

      assertNotNull(replies[i]);
      assertNotNull(replies[i].getFuture());
      assertFalse(replies[i].getFuture().isDone());
    }

    // get results
    for (AsyncReply reply : replies) {
      HIT hit = ((HIT[]) reply.getResult())[0];

      assertNotNull(hit);
      assertNotNull(hit.getHITId());
      assertTrue(reply.getFuture().isDone());
    }

    long t2 = System.currentTimeMillis();

    // create same amount of HITs synchronously
    for (int i = 0; i < replies.length; i++) {
      createHIT();
    }

    long t3 = System.currentTimeMillis();

    long timeAsync = t2 - t1;
    long timeSync  = t3 - t2;

    assertTrue(timeAsync < timeSync);
  }

  public void testErrorAsync() {
    AsyncReply reply = service.createHITAsync(
        null, // HITTypeId
        "Async_" + defaultHITTitle + unique,
        "Async_" + defaultHITDescription,
        null, // keywords
        "____________________INVALID_QUESTION____________________",
        defaultReward, defaultAssignmentDurationInSeconds,
        defaultAutoApprovalDelayInSeconds, defaultLifetimeInSeconds,
        defaultMaxAssignments, null, // requesterAnnotation
        null, 
        null,	// responseGroup
        null, // uniqueRequestToken
        null, // assignmentReviewPolicy
        null, // hitReviewPolicy
        null	// callback
    );

    assertNotNull(reply);
    assertNotNull(reply.getFuture());
    assertFalse(reply.getFuture().isDone());

    // wait for result
    try {
      reply.getResult();
      fail("Expected ParseErrorException");
    } catch (ParseErrorException ex) {
      // expected exception
    }
  }

  public void testCreateHITAsyncWithCallback() {

    SimpleCallback simpleCallback = new SimpleCallback();

    AsyncReply reply = service.createHITAsync(
        null, // HITTypeId
        "AsyncCB_" + defaultHITTitle + unique,
        "AsyncCB_" + defaultHITDescription,
        null, // keywords
        RequesterService.getBasicFreeTextQuestion(defaultQuestion),
        defaultReward, defaultAssignmentDurationInSeconds,
        defaultAutoApprovalDelayInSeconds, defaultLifetimeInSeconds,
        defaultMaxAssignments, null, // requesterAnnotation
        null, 
        null,			// responseGroup
        null, // uniqueRequestToken
        null, // assignmentReviewPolicy
        null, // hitReviewPolicy
        simpleCallback	// callback
    );

    assertNotNull(reply);
    assertNotNull(reply.getFuture());
    assertFalse(reply.getFuture().isDone());

    // wait for result
    reply.getResult();

    // create three failing requests to check failure callbacks
    AsyncReply[] replies = new AsyncReply[3];
    for (int i = 0; i < replies.length; i++) {
      replies[i] = service.createHITAsync(
          null, // HITTypeId
          "Async_" + String.valueOf(i) + "_" + defaultHITTitle
          + unique,
          "Async_" + String.valueOf(i) + "_" + defaultHITDescription,
          null, // keywords
          "____________________INVALID_QUESTION____________________",
          defaultReward, defaultAssignmentDurationInSeconds,
          defaultAutoApprovalDelayInSeconds,
          defaultLifetimeInSeconds, defaultMaxAssignments, null, // requesterAnnotation
          null, 
          null,	// responseGroup
          null, // uniqueRequestToken
          null, // assignmentReviewPolicy
          null, // hitReviewPolicy
          simpleCallback);

      assertNotNull(replies[i]);
      assertNotNull(replies[i].getFuture());
      assertFalse(replies[i].getFuture().isDone());
    }

    // wait for (failing) results and ignore them
    for (AsyncReply errReply : replies) {
      try {
        errReply.getResult();
      }
      catch (ParseErrorException ex) {
        // ignore expected exception
      }
    }		

    // make sure we got right number of callbacks
    // for successful and failed requests
    simpleCallback.assertResultCounts(1,replies.length);
  }	


  private class SimpleCallback implements AsyncCallback {
    int numOk = 0;
    int numFailed = 0;

    public void processResult(Object axisRequestMessage, Object axisResult) {
      numOk++;
    }

    public void processFailure(Object axisRequestMessage, Exception axisFailure) {
      numFailed++;
    }

    public void assertResultCounts(int expectedOk, int expectedFailures) {
      if (numOk != expectedOk) {
        fail("Mismatch for number of expected callbacks (Success)");
      }

      if (numFailed != expectedFailures) {
        fail("Mismatch for number of expected callbacks (Failures)");
      }
    }
  }  
}
