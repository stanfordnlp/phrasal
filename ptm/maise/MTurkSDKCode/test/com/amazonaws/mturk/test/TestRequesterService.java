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


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.textui.TestRunner;

import com.amazonaws.mturk.addon.HITProperties;
import com.amazonaws.mturk.addon.HITQuestion;
import com.amazonaws.mturk.addon.QAPValidator;
import com.amazonaws.mturk.dataschema.QuestionFormAnswers;
import com.amazonaws.mturk.dataschema.QuestionFormAnswersType;
import com.amazonaws.mturk.requester.HIT;
import com.amazonaws.mturk.requester.Qualification;
import com.amazonaws.mturk.requester.QualificationType;
import com.amazonaws.mturk.requester.QualificationTypeStatus;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.service.exception.ServiceException;
import com.amazonaws.mturk.service.exception.ValidationException;

public class TestRequesterService extends TestBase {
  
  public static void main(String[] args) {
    QuestionFormAnswers qfa = RequesterService.parseAnswers(DEFAULT_ANSWER_XML);
    List<QuestionFormAnswersType.AnswerType> answers = 
      (List<QuestionFormAnswersType.AnswerType>) qfa.getAnswer();
    
    TestRunner.run(TestRequesterService.class);
  }

  public TestRequesterService(String arg0) {
    super(arg0);
  }

  public void testUpdateHITsAsync() {

    String newHitTypeId = service.registerHITType(defaultAutoApprovalDelayInSeconds,
        defaultAssignmentDurationInSeconds, defaultReward * 2, defaultHITTitle, null,
        defaultHITDescription + " - revised", NULL_QUAL_REQUIREMENTS);

    HIT h1 = service.createHIT(null, // HITTypeId 
        "AsyncUpdate1_" + unique, 
        "AsyncUpdate1_" + unique, 
        "AsyncUpdate1_" + unique, // keywords 
        RequesterService.getBasicFreeTextQuestion(defaultQuestion), defaultReward, 
        defaultAssignmentDurationInSeconds, defaultAutoApprovalDelayInSeconds, 
        defaultLifetimeInSeconds, defaultMaxAssignments, null, // requesterAnnotation 
        null,
        null  // responseGroup
    );

    HIT h2 = service.createHIT(null, // HITTypeId 
        "AsyncUpdate2_" + unique, 
        "AsyncUpdate2_" + unique, 
        "AsyncUpdate2_" + unique, // keywords 
        RequesterService.getBasicFreeTextQuestion(defaultQuestion), defaultReward, 
        defaultAssignmentDurationInSeconds, defaultAutoApprovalDelayInSeconds, 
        defaultLifetimeInSeconds, defaultMaxAssignments, null, // requesterAnnotation 
        null,
        null  // responseGroup
    );		

    String[] hitIDs = { h1.getHITId(), h2.getHITId() };

    String[] successfulUpdates = service.updateHITs(hitIDs, newHitTypeId);

    assertTrue(successfulUpdates.length==2);
    assertEquals(successfulUpdates[0], h1.getHITId());
    assertEquals(successfulUpdates[1], h2.getHITId());

    HIT h2cur = service.getHIT(h1.getHITId());
    HIT h3cur = service.getHIT(h2.getHITId());

    assertEquals(h2cur.getHITTypeId(), newHitTypeId);
    assertEquals(h3cur.getHITTypeId(), newHitTypeId);
  }
  
  public void testCreateHITFreeText() throws ServiceException {
    HIT hit = service.createHIT(defaultHITTitle + unique, defaultHITDescription, defaultReward, 
        RequesterService.getBasicFreeTextQuestion(defaultQuestion), defaultMaxAssignments);
    
    assertNotNull(hit);
    assertNotNull(hit.getHITId());
  }
  
  public void testCreateHITFullResponse() throws ServiceException {
    HIT hit = service.createHIT(defaultHITTitle + unique, defaultHITDescription, defaultReward, 
        RequesterService.getBasicFreeTextQuestion(defaultQuestion), 
        defaultMaxAssignments, true);
    
    assertNotNull(hit);
    assertNotNull(hit.getHITId());
    assertNotNull(hit.getQuestion());
    assertNotNull(hit.getCreationTime());
    assertNotNull(hit.getNumberOfAssignmentsCompleted());
  }
  
  public void testCreateHITs() throws Exception {
    HIT[] hits = service.createHITs(defaultHITInput, defaultHITProperties, 
        defaultHITQuestion, null, null);

    assertNotNull(hits);
    assertTrue(hits.length > 1);
    assertNotNull(hits[1]);
    assertNotNull(hits[1].getHITId());
  }

  public void testCreateHITsOneHIT() throws Exception {
    HIT[] hits = service.createHITs(defaultHITInput, defaultHITProperties, 
        defaultHITQuestion, 1, null, null);

    assertNotNull(hits);
    assertTrue(hits.length == 1);
    assertNotNull(hits[0]);
    assertNotNull(hits[0].getHITId());
  }

  public void testCreateHITsNoHIT() throws Exception {
    HIT[] hits = service.createHITs(defaultHITInput, defaultHITProperties, 
        defaultHITQuestion, 0, null, null);

    assertNotNull(hits);
    assertEquals( hits.length, 0 );
  }
  
  public void testCreateHITWithInvalidHitLayoutId() throws ServiceException {
    // We only test a failure case here, because testing it with a real
    // HitLayoutId would require the user to manually get a valid HitLayoutId
    // from the requester UI.
    String layoutId = "INVALIDHITLAYOUTID";
    
    Map<String,String> layoutParameters = new HashMap<String,String>();
    layoutParameters.put("param1", "a test value");
    
    try {
      service.createHIT(
          defaultHITTitle + unique,
          defaultHITDescription,
          defaultReward,
          defaultMaxAssignments,
          layoutId,
          layoutParameters);
      fail("createHIT succeeded, despite having an invalid HIT layout ID");
    } catch (ServiceException e) {
      // expected
      assertContains("Cause of createHIT failure was not an invalid HIT layout ID",
          "HITLayout " + layoutId + " does not exist", e.getMessage());
    }
  }
  
  public void testUpdateHITTextAttributes() throws ServiceException {
    HIT hit = service.createHIT(defaultHITTitle + unique, defaultHITDescription, defaultReward, 
        RequesterService.getBasicFreeTextQuestion(defaultQuestion), 
        defaultMaxAssignments, true);

    String newHITTypeId = service.updateHIT(hit.getHITId(), 
                      hit.getTitle() + " amended", 
		      hit.getDescription() + " amended",
		      "new, updated, improved, amended", null);

    HIT newHIT = service.getHIT(hit.getHITId());

    assertFalse(newHITTypeId.equals(hit.getHITTypeId()));
    assertEquals(newHITTypeId, newHIT.getHITTypeId());
    assertEquals(hit.getHITId(), newHIT.getHITId());
     assertTrue(newHIT.getTitle().endsWith(" amended"));
    assertTrue(newHIT.getDescription().endsWith(" amended"));
    assertTrue(newHIT.getKeywords().endsWith(" amended"));
    assertEquals(hit.getReward(), newHIT.getReward());
  }

  public void testUpdateHITReward() throws ServiceException {
    HIT hit = service.createHIT(defaultHITTitle + unique, defaultHITDescription, defaultReward, 
        RequesterService.getBasicFreeTextQuestion(defaultQuestion), 
        defaultMaxAssignments, true);

    String newHITTypeId = service.updateHIT(hit.getHITId(), 
		      null, null, null, 0.50);

    HIT newHIT = service.getHIT(hit.getHITId());

    assertFalse(newHITTypeId.equals(hit.getHITTypeId()));
    assertEquals(newHITTypeId, newHIT.getHITTypeId());
    assertEquals(hit.getHITId(), newHIT.getHITId());
    assertEquals(hit.getTitle(), newHIT.getTitle());
    assertEquals(hit.getDescription(), newHIT.getDescription());
    assertEquals(hit.getKeywords(), newHIT.getKeywords());
    assertEquals(0.50, newHIT.getReward().getAmount().doubleValue());
  }

  public void testValidateExternalQuestion() throws Exception {
    HITQuestion htmlQuestion = new HITQuestion(defaultExternalQuestionFileName);

    service.previewHIT(defaultHITInput, defaultHITProperties, htmlQuestion);
  }
  
  public void testValidateInvalidExternalQuestion() throws Exception {
    HITQuestion htmlQuestion = new HITQuestion(defaultInvalidExternalQuestionFileName);

    try {
      service.previewHIT(defaultHITInput, defaultHITProperties, htmlQuestion);

      fail("Expected ValidationException when previewing HIT with invalid external question." );
    }
    catch (ValidationException e) {
      // Expected exception
    }
  }
  
  public void testValidateHTMLQuestion() throws Exception {
    // Note that the SDK does not validate the HTML CDATA;
    // it only validates the surrounding XML.
    QAPValidator.validateFile(defaultHTMLQuestionFileName);
  }
  
  public void testValidateInvalidHTMLQuestion() throws Exception {
    // HTMLQuestion has invalid FrameHeight
    try {
      QAPValidator.validateFile(defaultInvalidHTMLQuestionFileName);
      fail("Expected ValidationException when previewing a HIT with an invalid HTMLQuestion");
    } catch (ValidationException e) {
      // Expected exception
      assertContains("ValidationFailure was not caused by an invalid frame height.",
          "'I am not a number; I am a free man!' is not a valid value for 'integer'",
          e.getMessage());
    }
  }
  
  public void testCreateFormattedContentHIT() throws Exception {
    HITQuestion htmlQuestion = new HITQuestion(defaultFormattedContentQuestionFileName);

    HIT[] hits = service.createHITs(defaultHITInput, defaultHITProperties, 
                                    htmlQuestion, null, null);
    
    assertNotNull(hits);
    assertTrue("should have succeeded with more than 1, but was " + hits.length, hits.length > 1);
    assertNotNull(hits[1]);
    assertNotNull(hits[1].getHITId());
  }

  public void testCreateInvalidFormattedContentHIT() throws Exception {
    HITQuestion htmlQuestion = new HITQuestion(defaultInvalidFormattedContentQuestionFileName);

    try {
      service.previewHIT(defaultHITInput, defaultHITProperties, htmlQuestion);

      fail("Expected ValidationException when creating HIT with invalid formatted content." );
    }
    catch (ValidationException e) {
      // Expected exception
    }
  }

  public void testCreateScriptFormattedContentHIT() throws Exception {
    HITQuestion htmlQuestion = new HITQuestion(defaultScriptFormattedContentQuestionFileName);

    try {
      service.previewHIT(defaultHITInput, defaultHITProperties, htmlQuestion);

      fail("Expected ValidationException when creating HIT with script in formatted content." );
    }
    catch (ValidationException e) {
      // Expected exception
    }
  }
  
  public void testPreviewHIT() throws Exception {
    // This test works when run from the command line 
    // It fails when run directly in Eclipse do to a problem finding the schema file
    
    service.previewHIT(defaultPreviewFileName, defaultHITInput, 
        defaultHITProperties, defaultHITQuestion);
  }

  public void testPreviewHITFile() throws Exception {
    // This test works when run from the command line 
    // It fails when run directly in Eclipse do to a problem finding the schema file

    String preview = service.previewHIT(defaultHITInput, defaultHITProperties, 
          defaultHITQuestion);

    assertNotNull(preview);
  }

  public void testPreviewHITFileNullProperties() throws ServiceException {
    HITProperties nullProps = null;
    try {
      service.previewHIT(defaultHITInput, nullProps, defaultHITQuestion);
    } catch (Exception e) {
      // expected
    }
  }

  public void testPreviewHITFileNullQuestion() throws ServiceException {
    try {
      service.previewHIT(defaultHITInput, defaultHITProperties, null);
    } catch (Exception e) {
      // expected
    }
  }

  public void testGetHITFullResponse() throws ServiceException {
    String hitId = getTestHITId();
    HIT hit = service.getHIT(hitId);
    
    assertNotNull(hit);
    assertTrue(hit.getHITId().equals(hitId));
    assertNotNull(hit.getHITId());
    assertNotNull(hit.getQuestion());
    assertNotNull(hit.getCreationTime());
    assertNotNull(hit.getNumberOfAssignmentsCompleted());
  }
  
  public void testUpdateQualificationType() throws ServiceException {
    QualificationType qualType = service.updateQualificationType(
        getTestQualificationTypeId(), null, QualificationTypeStatus.Active);
    
    assertNotNull(qualType);
  }
  
  public void testGetQualicationsForQualificationType() throws ServiceException {
    service.getQualicationsForQualificationType(
        getTestQualificationTypeId(), defaultPageNum);
  }
  
  public void testGetAccountBalance() throws ServiceException {
    double balance = service.getAccountBalance();
    
    assertNotNull(balance);
  }
  
  public void testSearchHITs() throws ServiceException {
    service.searchHITs(defaultPageNum);
  }
  
  public void testSearchHITsFullResponse() throws ServiceException {
    createHIT();
    
    HIT[] hits = service.searchHITs(defaultPageNum, true);
    assertNotNull(hits);
    assertNotNull(hits[0]);
    
    HIT hit = hits[0];
    assertNotNull(hit);
    assertNotNull(hit.getHITId());
    assertNotNull(hit.getQuestion());
    assertNotNull(hit.getNumberOfAssignmentsCompleted());
  }
  
  public void testSearchQualificationTypes() throws ServiceException {
    QualificationType[] qualTypes = service.searchQualificationTypes(defaultPageNum);
    
    assertNotNull(qualTypes);
  }
  
  public void testSearchAllHITs() throws ServiceException {
    HIT[] hits = service.searchAllHITs();
    
    assertNotNull(hits);
  }
  
  public void testCreateSingleCheckboxQualificationType() throws ServiceException {
    QualificationType qualType = service.createSingleCheckboxQualificationType(
        defaultQualificationName + unique, defaultQualificationDescription,
        null // keywords
      );
    assertNotNull(qualType);
    assertNotNull(qualType.getQualificationTypeId());
  }
  
  public void testGetAllQualificationTypes() throws ServiceException {
    QualificationType[] qualTypes = service.getAllQualificationTypes();
    
    assertNotNull(qualTypes);
  }

  

  public void testGetAllQualificationsForQualificationType_None() throws Exception {
    String qualTypeId = getTestQualificationTypeId();
        
    Qualification[] quals = 
      service.getAllQualificationsForQualificationType( qualTypeId );

    assertNotNull(quals);
    assertTrue(quals.length == 0);
  }
  
  
  public void testCreateQualificationType() throws ServiceException {
    QualificationType qualType = service.createQualificationType(
        defaultQualificationName + unique, 
        null, // keywords
        defaultQualificationDescription
      );
        
    assertNotNull(qualType);  
    
    // set to inactive to cleanup
    try {
      service.disposeQualificationType(qualType.getQualificationTypeId());
    }
    catch (Exception ex) {
      // ignore since the test checks for creation of qualification type
    }
  }
  
  public void testDeleteQualificationType() throws ServiceException {
    String testQualTypeId = createQualificationType().getQualificationTypeId();
    QualificationType qualType = service.disposeQualificationType(testQualTypeId);
    QualificationTypeStatus qualTypeStatus = qualType.getQualificationTypeStatus();
    
    assertNotNull(qualType);
    assertNotNull(qualTypeStatus.equals(QualificationTypeStatus.Inactive));
  }
  
  public void testGetTotalNumHITsInAccount() throws ServiceException {
    int totalNum = service.getTotalNumHITsInAccount();
    
    assertNotNull(totalNum >= 0);
  }
  
  
  @SuppressWarnings("unchecked")
  public void testParseAnswers() throws ServiceException {
    QuestionFormAnswers qfa = RequesterService.parseAnswers(DEFAULT_ANSWER_XML);
    List<QuestionFormAnswersType.AnswerType> answers = 
      (List<QuestionFormAnswersType.AnswerType>) qfa.getAnswer();
    
    for (int i=0; i< answers.size(); i++) {
      QuestionFormAnswersType.AnswerType answer = answers.get(i);
      assertNotNull(null, RequesterService.getAnswerValue(null, answer));
      
      // check order
      String result = RequesterService.getAnswerValue("TEST_ASSIGNMENT_ID", answer, true);
      if (i == 0) {
        assertTrue(result.equals("freeText\tfreeText_answer"));
      } else if (i == 1) {
        assertTrue(result.equals("otherSelection\totherSelection_answer"));
      } else if (i == 2) {
        assertTrue(result.startsWith("url\t"));
      } else {
        assertTrue(result.equals("selectionIdentifier\tselection_answer|selection_answer2"));
      }
    }
  }
}
