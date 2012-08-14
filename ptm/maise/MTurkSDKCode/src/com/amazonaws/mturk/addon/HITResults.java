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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.mturk.dataschema.QuestionFormAnswers;
import com.amazonaws.mturk.dataschema.QuestionFormAnswersType;
import com.amazonaws.mturk.requester.Assignment;
import com.amazonaws.mturk.requester.AssignmentStatus;
import com.amazonaws.mturk.requester.HIT;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.util.ClientConfig;

/**
 * The HITResults class provides a way to hold Assignment results for a particular HIT.
 */
public class HITResults 
{
  public static final String NO_ANSWER = "none";
  public static final String EMPTY_ANSWER = "emptyanswer";
  public static final String EMPTY = "";
  public static final char DELIMITER = '\t';
  public static final String MULTI_ANSWER_DELIMITER = "|";
  
  private HIT hit;
  private Assignment[] assignments;
  
  protected ClientConfig config;

  private static Assignment[] NO_ASSIGNMENTS = new Assignment[] { new Assignment() };
  private final String EXCEL_COMPLIANT_DATE_FORMAT = "EEE MMM dd HH:mm:ss zzz yyyy";
  private final SimpleDateFormat DATE_FORMATTER = 
    new SimpleDateFormat(EXCEL_COMPLIANT_DATE_FORMAT);

  public HITResults(HIT hit, Assignment[] assignments, ClientConfig config) {
    this.hit = hit;
    this.assignments = assignments;
    this.config = config;
  }

  public HIT getHIT() {
    return this.hit;
  }

  public Assignment[] getAssignments() {
    return this.assignments;
  }

  public void writeResults(HITDataOutput writer) throws IOException {

    Map<String,String> hitResults = this.getHITResults();
    Map<String,String> assignmentResults;
    
    if (assignments == null || assignments.length == 0) {
      assignments = NO_ASSIGNMENTS;     // Feature 1826272 (output empty assignment data even when HIT has no assignments)
    }

    for (Assignment assignment : this.assignments) {

      assignmentResults = this.getAssignmentResults(assignment);

      assignmentResults.putAll( hitResults );

      writer.writeValues( assignmentResults ); // append

    }
  }

  public Map<String,String> getHITResults() {

    // Add standard HIT results
    Map<String,String> results = new HashMap<String,String>( HITTypeResults.HIT_HEADERS.length );

    results.put( HITProperties.HITField.HitId.getFieldName(), hit.getHITId());
    results.put( HITProperties.HITField.HitTypeId.getFieldName(), hit.getHITTypeId());
    results.put( HITProperties.HITField.Title.getFieldName(), hit.getTitle());
    results.put( HITProperties.HITField.Description.getFieldName(), hit.getDescription());
    results.put( HITProperties.HITField.Keywords.getFieldName(), hit.getKeywords());
    results.put( HITProperties.HITField.Reward.getFieldName(), hit.getReward().getFormattedPrice());

    String creationTime = DATE_FORMATTER.format(hit.getCreationTime().getTime());
    results.put( HITProperties.HITField.CreationTime.getFieldName(), creationTime);
    results.put( HITProperties.HITField.MaxAssignments.getFieldName(), 
      hit.getMaxAssignments().toString());
    results.put( HITProperties.HITField.NumAvailableAssignments.getFieldName(), 
      hit.getNumberOfAssignmentsAvailable().toString());
    results.put( HITProperties.HITField.NumPendingAssignments.getFieldName(), 
      hit.getNumberOfAssignmentsPending().toString());
    results.put( HITProperties.HITField.NumCompletedAssignments.getFieldName(), 
      hit.getNumberOfAssignmentsCompleted().toString());

    String status = hit.getHITStatus() != null ? hit.getHITStatus().getValue() : "";
    results.put( HITProperties.HITField.Status.getFieldName(), status);

    String reviewStatus = hit.getHITReviewStatus() != null 
      ? hit.getHITReviewStatus().getValue() : "";
    results.put( HITProperties.HITField.ReviewStatus.getFieldName(), reviewStatus);
    results.put( HITProperties.HITField.Annotation.getFieldName(), hit.getRequesterAnnotation());
    results.put( HITProperties.HITField.AssignmentDuration.getFieldName(), 
      hit.getAssignmentDurationInSeconds().toString());
    results.put( HITProperties.HITField.AutoApprovalDelay.getFieldName(), 
      hit.getAutoApprovalDelayInSeconds().toString());
    results.put( HITProperties.HITField.Lifetime.getFieldName(), 
      DATE_FORMATTER.format(hit.getExpiration().getTime()));
    String viewHITUrl = this.config.getRequesterWebsiteURL() + "/mturk/manageHIT?HITId=" + hit.getHITId() ; 
    results.put( HITProperties.HITField.ViewHITUrl.getFieldName(), viewHITUrl);
          
          
    return results;
  }

  public Map<String,String> getAssignmentResults(Assignment assignment) {

    // Add standard Assignment results
    Map<String,String> results = new LinkedHashMap<String,String>( HITTypeResults.ASSIGNMENT_HEADERS.length );

    results.put( HITProperties.AssignmentField.AssignmentId.getFieldName(), assignment.getAssignmentId());
    results.put( HITProperties.AssignmentField.WorkerId.getFieldName(), assignment.getWorkerId());

    AssignmentStatus status = assignment.getAssignmentStatus();
    String statusStr = status != null ? status.getValue() : EMPTY; 
    results.put( HITProperties.AssignmentField.Status.getFieldName(), statusStr);

    String autoApprovalTime = assignment.getAutoApprovalTime() != null ?
      DATE_FORMATTER.format(assignment.getAutoApprovalTime().getTime()) : EMPTY;
    results.put( HITProperties.AssignmentField.AutoApprovalTime.getFieldName(), autoApprovalTime);

    String acceptTime = assignment.getAcceptTime() != null ?
      DATE_FORMATTER.format(assignment.getAcceptTime().getTime()) : EMPTY;
    results.put( HITProperties.AssignmentField.AcceptTime.getFieldName(), acceptTime);

    String submitTime = assignment.getSubmitTime() != null ?
      DATE_FORMATTER.format(assignment.getSubmitTime().getTime()) : EMPTY;
    results.put( HITProperties.AssignmentField.SubmitTime.getFieldName(), submitTime);

    String approvalTime = assignment.getApprovalTime() != null ?
      DATE_FORMATTER.format(assignment.getApprovalTime().getTime()) : EMPTY;
    results.put( HITProperties.AssignmentField.ApprovalTime.getFieldName(), approvalTime);

    String rejectionTime = assignment.getRejectionTime() != null ?
      DATE_FORMATTER.format(assignment.getRejectionTime().getTime()) : EMPTY;
    results.put( HITProperties.AssignmentField.RejectionTime.getFieldName(), rejectionTime);

    String deadline = assignment.getDeadline() != null ?
      DATE_FORMATTER.format(assignment.getDeadline().getTime()) : EMPTY;
    results.put( HITProperties.AssignmentField.Deadline.getFieldName(), deadline);

    String requesterFeedback = assignment.getRequesterFeedback() != null ? 
      assignment.getRequesterFeedback() : EMPTY; 
    results.put( HITProperties.AssignmentField.RequesterFeedback.getFieldName(), requesterFeedback);

    String rejectFlag = status != null && status == AssignmentStatus.Rejected ? "y" : EMPTY; 
    results.put( HITProperties.AssignmentField.RejectFlag.getFieldName(), rejectFlag);
    
    // Add Assignment-specific answers
    String answers = this.getAnswers(assignment);

    results.put( HITProperties.AssignmentField.Answers.getFieldName(), answers);
      
    return results;
  }

  @SuppressWarnings("unchecked")
    private String getAnswers(Assignment assignment) {
      String result = EMPTY;

      AssignmentStatus status = assignment.getAssignmentStatus(); 
      if (status == null) {
        return NO_ANSWER;
      }

      String answerXML = assignment.getAnswer();

      QuestionFormAnswers qfa = RequesterService.parseAnswers(answerXML);
      List<QuestionFormAnswersType.AnswerType> answers = 
        (List<QuestionFormAnswersType.AnswerType>) qfa.getAnswer();

      for (QuestionFormAnswersType.AnswerType answer : answers) {

        String assignmentId = assignment.getAssignmentId();
        String answerValue = RequesterService.getAnswerValue(assignmentId, answer, true);

        if (answerValue != null) {
          result += answerValue + DELIMITER;
        }
      }

      return result;
    }
}
