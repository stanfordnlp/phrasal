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


package com.amazonaws.mturk.service.axis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.apache.log4j.Logger;
import org.xml.sax.InputSource;

import com.amazon.mechanicalturk.common.domain.XslTransformer;
import com.amazonaws.mturk.addon.BatchItemCallback;
import com.amazonaws.mturk.addon.HITDataInput;
import com.amazonaws.mturk.addon.HITDataOutput;
import com.amazonaws.mturk.addon.HITDataReader;
import com.amazonaws.mturk.addon.HITDataWriter;
import com.amazonaws.mturk.addon.HITProperties;
import com.amazonaws.mturk.addon.HITQuestion;
import com.amazonaws.mturk.addon.HITResults;
import com.amazonaws.mturk.addon.HITTypeResults;
import com.amazonaws.mturk.addon.MTurkConstants;
import com.amazonaws.mturk.addon.QAPValidator;
import com.amazonaws.mturk.dataschema.ObjectFactory;
import com.amazonaws.mturk.dataschema.QuestionFormAnswers;
import com.amazonaws.mturk.dataschema.QuestionFormAnswersType;
import com.amazonaws.mturk.requester.Assignment;
import com.amazonaws.mturk.requester.AssignmentStatus;
import com.amazonaws.mturk.requester.EventType;
import com.amazonaws.mturk.requester.GetAccountBalanceResult;
import com.amazonaws.mturk.requester.GetAssignmentsForHITResult;
import com.amazonaws.mturk.requester.GetAssignmentsForHITSortProperty;
import com.amazonaws.mturk.requester.GetQualificationRequestsResult;
import com.amazonaws.mturk.requester.GetQualificationRequestsSortProperty;
import com.amazonaws.mturk.requester.GetQualificationsForQualificationTypeResult;
import com.amazonaws.mturk.requester.GetReviewableHITsResult;
import com.amazonaws.mturk.requester.GetReviewableHITsSortProperty;
import com.amazonaws.mturk.requester.HIT;
import com.amazonaws.mturk.requester.HITLayoutParameter;
import com.amazonaws.mturk.requester.NotificationSpecification;
import com.amazonaws.mturk.requester.NotificationTransport;
import com.amazonaws.mturk.requester.Qualification;
import com.amazonaws.mturk.requester.QualificationRequest;
import com.amazonaws.mturk.requester.QualificationStatus;
import com.amazonaws.mturk.requester.QualificationType;
import com.amazonaws.mturk.requester.QualificationTypeStatus;
import com.amazonaws.mturk.requester.ReviewableHITStatus;
import com.amazonaws.mturk.requester.SearchHITsResult;
import com.amazonaws.mturk.requester.SearchHITsSortProperty;
import com.amazonaws.mturk.requester.SearchQualificationTypesResult;
import com.amazonaws.mturk.requester.SearchQualificationTypesSortProperty;
import com.amazonaws.mturk.requester.SortDirection;
import com.amazonaws.mturk.service.exception.ServiceException;
import com.amazonaws.mturk.service.exception.ValidationException;
import com.amazonaws.mturk.util.ClientConfig;
import com.amazonaws.mturk.util.FileUtil;
import com.amazonaws.mturk.util.VelocityUtil;

/**
 * The RequesterService class provides a set of APIs and convenience methods to perform Amazon Mechanical
 * Turk operations. It extends the RequesterServiceRaw class.
 * <p>
 * Most methods throw a ServiceException. This is a generic exception that can occur for many reasons, 
 * including:
 *  <ul>
 *    <li>Invalid credentials</li>
 *    <li>Insufficient funds</li>
 *    <li>Invalid parameter</li>
 *    <li>Internal service exception</li>
 *    <li>Network problems</li>
 *  </ul>
 * You should handle all situations in your application. 
 */
public class RequesterService extends RequesterServiceRaw {

  //-------------------------------------------------------------
  // Constants
  //-------------------------------------------------------------

  /**
   * The page number of the results set. Default is 1. 
   */
  public static final int DEFAULT_PAGE_NUM = 1;
  
  /**
   * The number of returned results per page. Default is 10.
   */  
  public static final int DEFAULT_PAGE_SIZE = 10;
  
  /**
   * The direction of the sort. Default is Ascending.
   */
  public static final SortDirection DEFAULT_SORT_DIRECTION = SortDirection.Ascending;
  
  /**
   * Specifies whether to load all HITs in a file. Default is no. Use this as the 
   * numHITToLoad parameter of createHITs if you want to load all the HITs in the 
   * input file.
   */
  public static final int LOAD_ALL = -1;
  private static final int MAX_BATCH = 500;     // maximum batch size for batch chunk

  /**
   * The amount of time in seconds that the Worker has to complete the 
   * assignment after accepting it. Default is 3600 (1 hour).
   */
  public static final long DEFAULT_ASSIGNMENT_DURATION_IN_SECONDS = (long) 60 * 60; // 1 hour
  
  /**
   * The amount of time in seconds before HITs are automatically approved. Default 
   * is 1296000 (15 days).
   */
  public static final long DEFAULT_AUTO_APPROVAL_DELAY_IN_SECONDS = (long) 60 * 60 * 24 * 15; // 15 days
  
  /**
   * The amount of time in seconds that the HIT can be available for Workers to accept. 
   * Default is 259200 (3 days).
   */
  public static final long DEFAULT_LIFETIME_IN_SECONDS = (long) 60 * 60 * 24 * 3; // 3 days

  // QualificationTypeIds for System Qualifications
  /**
   * Qualification type Id for the Worker_PercentAssignmentsAbandoned system qualification.
   * It specifies the percentage of assignments the Worker has abandoned. Use this Id when you
   * create HITs with system qualifications. 
   */
  public static final String ABANDONMENT_RATE_QUALIFICATION_TYPE_ID = "00000000000000000070";
  
  /**
   * Qualification type Id for the Worker_PercentAssignmentsApproved system qualification.
   * It specifies the percentage of assignments the Worker has submitted that were subsequently 
   * approved by the Requester, over all assignments the Worker has submitted. Use this Id when 
   * you create HITs with system qualifications.   
   */
  public static final String APPROVAL_RATE_QUALIFICATION_TYPE_ID = "000000000000000000L0";
  
  /**
   * Qualification type Id for the Worker_PercentAssignmentsRejected system qualification.
   * It specifies the percentage of assignments the Worker has submitted that were subsequently 
   * rejected by the Requester, over all assignments the Worker has submitted. Use this Id when 
   * you create HITs with system qualifications. 
   */
  public static final String REJECTION_RATE_QUALIFICATION_TYPE_ID = "000000000000000000S0";
  
  /**
   * Qualification type Id for the Worker_PercentAssignmentsReturned system qualification.
   * It specifies percentage of assignments the Worker has returned, over all 
   * assignments the Worker has accepted. Use this Id when you create HITs with system 
   * qualifications.
   */
  public static final String RETURN_RATE_QUALIFICATION_TYPE_ID = "000000000000000000E0";
  
  /**
   * Qualification type Id for the Worker_PercentAssignmentsSubmitted  system qualification.
   * It specifies the percentage of assignments the Worker has submitted, over all assignments 
   * the Worker has accepted. Use this Id when you create HITs with system qualifications.   
   */
  public static final String SUBMISSION_RATE_QUALIFICATION_TYPE_ID = "00000000000000000000";
  
  /**
   * Qualification type Id for the Worker_Locale system qualification.
   * It specifies the location of the Worker from the Worker's mailing address. Use this 
   * Id when you create HITs with system qualifications.
   */
  public static final String LOCALE_QUALIFICATION_TYPE_ID = "00000000000000000071";

  private static final String DATASCHEMA_PACKAGE_PREFIX = "com.amazonaws.mturk.dataschema";

  private static final AssignmentStatus[] DEFAULT_ASSIGNMENT_STATUS = new AssignmentStatus[] { 
    AssignmentStatus.Approved, 
    AssignmentStatus.Rejected,
    AssignmentStatus.Submitted
  };

  private static final AssignmentStatus[] SUBMITTED_ASSIGNMENT_STATUS = new AssignmentStatus[] { 
    AssignmentStatus.Submitted
  };  

  private static final String[] DEFAULT_ASSIGNMENT_RESPONSE_GROUP = new String [] { 
    "Minimal", 
    "AssignmentFeedback" 
  };

  private static final String[] DEFAULT_HIT_RESPONSE_GROUP = new String [] { 
    "Minimal", 
    "HITDetail", 
    "HITQuestion", 
    "HITAssignmentSummary" 
  };

  //-------------------------------------------------------------
  // Variables - Private
  //-------------------------------------------------------------

  private static Logger log = Logger.getLogger(RequesterService.class);

  //-------------------------------------------------------------
  // Constructors
  //-------------------------------------------------------------
  @Deprecated
  public RequesterService() {
    super();
  }
  
  /**
   * Creates a new instance of this class using the configuration information 
   * from the ClientConfig class.
   */
  public RequesterService(ClientConfig config) {
    super(config);
  }


  //-------------------------------------------------------------
  // Methods - Public
  //-------------------------------------------------------------

  /**
   * Creates a HIT, using defaults for the HIT properties not given as 
   * parameters.
   * <p>
   * Default values: 
   * <ul>
   *  <li>ResponseGroup: Minimal</li>
   *  <li>LifetimeInSeconds:  3 days</li>
   *  <li>AssignmentDurationInSeconds: 1 hour</li>
   *  <li>AutoApprovalDelayInSeconds:  15 days</li>
   *  <li>Keywords: null</li>
   *  <li>QualificationRequirement: null</li>
   * </ul>
   * 
   * @param title             the title of the HIT. The title appears in search results
   *                          and everywhere the HIT is mentioned.
   * @param description       the description of the HIT. The description should include
   *                          enough information for a Worker to evaluate the HIT before
   *                          accepting it.
   * @param reward            the amount to pay for the completed HIT
   * @param question          the data the Worker uses to produce the results
   * @param maxAssignments    the number of times the HIT can be accepted and completed
   *                          before it becomes unavailable
   * @return the created HIT  
   * @throws ServiceException 
   */
  public HIT createHIT(String title, String description, double reward, String question, 
      int maxAssignments) throws ServiceException {
    return this.createHIT(
        title, 
        description, // description
        reward,
        question,
        maxAssignments,
        false
    );
  }
  
  public HIT createHIT(String title, String description, double reward, int maxAssignments,
      String layoutId, Map<String,String> layoutParameters) {
    Set<HITLayoutParameter> parameterObjects = new HashSet<HITLayoutParameter>();
    for (String key : layoutParameters.keySet()) {
      parameterObjects.add(new HITLayoutParameter(key, layoutParameters.get(key)));
    }
    
    return super.createHIT(
        null, // hitTypeId
        title,
        description,
        null, // keywords
        reward,
        DEFAULT_ASSIGNMENT_DURATION_IN_SECONDS,
        DEFAULT_AUTO_APPROVAL_DELAY_IN_SECONDS,
        DEFAULT_LIFETIME_IN_SECONDS,
        maxAssignments,
        null, // requesterAnnotation
        null, // qualificationRequirements
        null, // responseGroup
        null, // uniqueRequestToken
        null, // assignmentReviewPolicy
        null, // hitReviewPolicy
        layoutId,
        (HITLayoutParameter[]) parameterObjects.toArray(new HITLayoutParameter[0]));
  }
  
  /**
   * Creates a HIT using defaults for the HIT properties not given as 
   * parameters.
   * <p>
   * Default values: 
   * <ul>
   *  <li>LifetimeInSeconds:  3 days</li>
   *  <li>AssignmentDurationInSeconds: 1 hour</li>
   *  <li>AutoApprovalDelayInSeconds:  15 days</li>
   *  <li>Keywords: null</li>
   *  <li>QualificationRequirement: null</li>
   * </ul>
   *                 
   * @param title              the title of the HIT. The title appears in search results
   *                           and everywhere the HIT is mentioned.
   * @param description        the description of the HIT. The description should include
   *                           enough information for a Worker to evaluate the HIT before
   *                           accepting it.
   * @param reward             the amount to pay for the completed HIT
   * @param question           the data the Worker uses to produce the results
   * @param maxAssignments     the number of times the HIT can be accepted and completed
   *                           before it becomes unavailable
   * @param getFullResponse    if true, all information about the HIT is returned. If false, only 
   *                           HIT Id and HIT type Id are returned. 
   * @return the created HIT
   * @throws ServiceException
   */
  public HIT createHIT(String title, String description, double reward, String question, 
      int maxAssignments, boolean getFullResponse) throws ServiceException {

    // Include HIT detail, HIT Question, and Assignment summary in response
    String[] responseGroup = null;
    if (getFullResponse == true) {
      responseGroup = new String [] { "Minimal", "HITDetail", 
          "HITQuestion", "HITAssignmentSummary" };
    }

    return super.createHIT(
        null, // HITTypeId
        title, 
        description, // description
        null, // keywords 
        question, 
        reward,
        DEFAULT_ASSIGNMENT_DURATION_IN_SECONDS,
        DEFAULT_AUTO_APPROVAL_DELAY_IN_SECONDS,
        DEFAULT_LIFETIME_IN_SECONDS,
        maxAssignments,
        null, //requesterAnnotation
        null, // qualificationRequirements
        responseGroup,
        null, // uniqueRequesterToken
        null, // assignmentReviewPolicy
        null  // hitReviewPolicy
    );
  }

  /**
   * Updates a HIT with new title, description, keywords, and reward. If new values are 
   * not specified, the values from the original HIT are used. The following default values 
   * are used:
   * <ul>
   *  <li>AssignmentDurationInSeconds: 1 hour</li>
   *  <li>AutoApprovalDelayInSeconds:  15 days</li>
   *  <li>QualificationRequirement: null</li>
   * </ul>
   * 
   * @param hitId        the Id of the HIT to update
   * @param title        the title of the updated HIT. If null, the current title 
   *                     of the HIT is used.
   * @param description  the description of the updated HIT. If null, the current description 
   *                     of the HIT is used.
   * @param keywords     one or more words or phrases to describe the updated HIT. If null, 
   *                     the current keywords are used.                  
   * @param reward       the amount to pay for the updated HIT when completed. If null, the 
   *                     the current reward of the HIT is used.
   * @return the new HITType Id
   * @throws ServiceException
   */
  public String updateHIT(String hitId, String title, String description, String keywords,
      Double reward) throws ServiceException {
    if (title == null || description == null || keywords == null || reward == null) {
      HIT currentHIT = this.getHIT(hitId);
      if (title == null) {
        title = currentHIT.getTitle();
      }
      if (description == null) {
        description = currentHIT.getDescription();
      }
      if (keywords == null) {
        keywords = currentHIT.getKeywords();
      }
      if (reward == null) {
        reward = currentHIT.getReward().getAmount().doubleValue();
      }
    }
    String newHITTypeId = this.registerHITType(
        DEFAULT_AUTO_APPROVAL_DELAY_IN_SECONDS,
        DEFAULT_ASSIGNMENT_DURATION_IN_SECONDS,
        reward,
        title,
        keywords,
        description,
        null); // qualificationRequirements
    this.changeHITTypeOfHIT(hitId, newHITTypeId);
    return newHITTypeId;
  }

  /**
   * Updates multiple HITs to a new HIT type.
   * 
   * @param hitIds         an array of Ids of HITs to update
   * @param newHITTypeId   the new HITTypeId 
   * @return array of hitIds that were successfully updated
   * @throws ServiceException
   */
  public String[] updateHITs(String[] hitIds, String newHITTypeId) throws ServiceException {
    List<String> successes = new ArrayList<String>(hitIds.length);
    
    // split work
    log.debug(String.format("Updating %d HITs with max memory %d", hitIds.length, Runtime.getRuntime().maxMemory()));
    AsyncReply[] replies = new AsyncReply[MAX_BATCH];
    int numBatches = hitIds.length / MAX_BATCH;
    for (int curBatch=0; curBatch<=numBatches; curBatch++) {
      int iStart = curBatch * MAX_BATCH;
      int iEnd = iStart + MAX_BATCH;
      if (iEnd > hitIds.length) {
        iEnd = hitIds.length;
      }
      
      log.debug(String.format("Processing batch %d (%d to %d)", curBatch, iStart, iEnd)); 
            
      // submit to work queue
      for (int i=iStart; i<iEnd; i++) {
        replies[i-iStart] = super.changeHITTypeOfHITAsync(hitIds[i], newHITTypeId, null);
      }

      // wait for results
      for (int i=iStart; i<iEnd; i++) {
        try {
          replies[i-iStart].getResult();
          successes.add(hitIds[i]);
        } catch (ServiceException e) {
          // don't add it to the success list
          log.error("Error updating HIT " + hitIds[i] + " to HIT type " + newHITTypeId + ": " + e.getLocalizedMessage());
        }
      }      
    }

    return successes.toArray(new String[successes.size()]);
  }

  /**
   * Deletes multiple HITs at one time.
   * 
   * @param hitIds         an array of Ids of HITs to delete
   * @param approve	       specifies whether to approve assignments that have been submitted and have
   *                       not been approved or rejected. Assignments that are in the review or reviewing 
   *                       states cannot be deleted. 
   * @param expire         specifies whether to expire any HITs that are still available to Workers.
   *                       Live HITs cannot be deleted.
   * @param callback       your callback method. This method is called asynchronously as each 
   *                       HIT is successfully deleted. This method can track the progress of the batch so
   *                       you do not exit the process until the batch is completed.                       
   *                       
   */
  public void deleteHITs(String[] hitIds, boolean approve, boolean expire, BatchItemCallback callback) {
    if (hitIds != null && hitIds.length > 0) {

      DeleteHITCommand[] commands = new DeleteHITCommand[MAX_BATCH];

      log.debug(String.format("Deleting %d HITs with max memory %d", hitIds.length, Runtime.getRuntime().maxMemory()));

      int successCount=0;
      int failureCount=0;
      int numBatches = hitIds.length / MAX_BATCH;

      for (int curBatch=0; curBatch<=numBatches; curBatch++) {
        int iStart = curBatch * MAX_BATCH;
        int iEnd = iStart + MAX_BATCH;
        if (iEnd > hitIds.length) {
          iEnd = hitIds.length;
        }

        log.debug(String.format("Processing batch %d (%d to %d)", curBatch, iStart, iEnd));

        for (int i=iStart; i<iEnd; i++) {
          commands[i-iStart] = new DeleteHITCommand(i+1, hitIds[i], approve, expire, this, callback);
          commands[i-iStart].execute();
        }

        // calculate results
        for (int i=iStart; i<iEnd; i++) {
          if (commands[i-iStart].hasSucceeded()) {
            successCount++;        
          }
          else {
            failureCount++;
          }          
        } 
      }
      
      if (callback == null) {
        log.info("Deleted "+successCount+" HITs. " + failureCount + " HITs failed to delete.");
      }
    }
  }

  /**
   * Increases the maximum number of assignments, or extends the expiration date, of multiple HITs.
   * 
   * @param hitIds                       an array of Ids of HITs to extend
   * @param maxAssignmentsIncrement      the number of assignments by which to increment the HIT's 
   *                                     MaxAssignments property.
   * @param expirationIncrementInSeconds the amount of time by which to extend the expiration date, 
   *                                     in seconds.
   * @param callback                     your callback method. This method is called asynchronously as 
   *                                     each HIT is successfully extended. This method can track the 
   *                                     progress of the batch so you do not exit the process until the 
   *                                     batch is completed.
   * @throws ServiceException                                                                   
   */
  public void extendHITs(String[] hitIds, Integer maxAssignmentsIncrement, Long expirationIncrementInSeconds,
      BatchItemCallback callback) 
  throws ServiceException { 

    if (hitIds == null || hitIds.length == 0) {
      return;
    }

    if (maxAssignmentsIncrement == null && expirationIncrementInSeconds==null) {
      throw new ServiceException("Neither maxAssignmentsIncrement nor expirationIncrementInSeconds are specified");
    }
    
    log.debug(String.format("Extending %d HITs with max memory %d", hitIds.length, Runtime.getRuntime().maxMemory()));

    AsyncReply[] replies = new AsyncReply[MAX_BATCH];
    int numBatches = hitIds.length / MAX_BATCH;
    for (int curBatch=0; curBatch<=numBatches; curBatch++) {
      int iStart = curBatch * MAX_BATCH;
      int iEnd = iStart + MAX_BATCH;
      if (iEnd > hitIds.length) {
        iEnd = hitIds.length;
      }

      log.debug(String.format("Processing batch %d (%d to %d)", curBatch, iStart, iEnd));
      
      // submit requests to work queue      
      for (int i=iStart; i<iEnd; i++) { 
        replies[i-iStart] = super.extendHITAsync(hitIds[i], maxAssignmentsIncrement, expirationIncrementInSeconds, null);
      }

      // wait for results
      for (int i=iStart; i<iEnd; i++) {
        try {
          Object result = replies[i-iStart].getResult();

          if (callback != null) {
            callback.processItemResult(hitIds[i], true, result, null);
          }
          else {
            log.info(String.format("[%s] Successfully extended HIT (%d/%d)", 
                hitIds[i], i, hitIds.length)); 
          }
        } catch (ServiceException e) {
          if (callback != null) {
            callback.processItemResult(hitIds[i], false, null, e);
          }
          else {
            log.error(String.format("[%s] Failed to extend HIT (%d/%d): %s", 
                hitIds[i], i, hitIds.length, e.getLocalizedMessage()));
          }
        }
      }      
    }
  }

  /***
   * Approves all assignments using the Axis worker thread pool.
   * 
   * @param assignmentIds       array of assignments to approve
   * @param requesterFeedback   feedback (comments) for the assignments
   * @param defaultFeedback     feedback used when no requesterFeedback is specified for an assignment ID
   * @param callback            your callback method. This method is called asynchronously as each HIT
   *                            Assignment is successfully approved. This method can track the progress of 
   *                            the batch so you do not exit the process until the batch is completed.
   * @throws ServiceException
   */
  public void approveAssignments(String[] assignmentIds, String[] requesterFeedback, String defaultFeedback,
      BatchItemCallback callback) 
  throws ServiceException { 

    if (assignmentIds == null || assignmentIds.length == 0) {
      return;
    }

    if (requesterFeedback != null && requesterFeedback.length != assignmentIds.length) {
      throw new ServiceException("Number of assignments to approve must match number of approval comments (requester feedback)");
    }

    // preprocess feedback comments    
    if (requesterFeedback == null) {
      requesterFeedback = new String[assignmentIds.length];
    }

    if (defaultFeedback != null) {
      for (int i=0; i<assignmentIds.length; i++) {
        if (requesterFeedback[i]==null) {
          requesterFeedback[i] = defaultFeedback;
        }
      }    
    }
    
    log.debug(String.format("Approving %d assignments with max memory %d", assignmentIds.length, Runtime.getRuntime().maxMemory()));

    AsyncReply[] replies = new AsyncReply[MAX_BATCH];
    int numBatches = assignmentIds.length / MAX_BATCH;
    for (int curBatch=0; curBatch<=numBatches; curBatch++) {
      int iStart = curBatch * MAX_BATCH;
      int iEnd = iStart + MAX_BATCH;
      if (iEnd > assignmentIds.length) {
        iEnd = assignmentIds.length;
      }

      log.debug(String.format("Processing batch %d (%d to %d)", curBatch, iStart, iEnd));
      
      // submit requests to work queue
      for (int i=iStart; i<iEnd; i++) {    
        replies[i-iStart] = super.approveAssignmentAsync(assignmentIds[i], requesterFeedback[i], null);
      }

      // wait for results
      for (int i=iStart; i<iEnd; i++) {
        try {
          Object result = replies[i-iStart].getResult();

          if (callback != null) {
            callback.processItemResult(assignmentIds[i], true, result, null);
          }
          else {
            log.info("[" + assignmentIds[i] + "] Assignment successfully approved " + 
                (requesterFeedback[i] != null && requesterFeedback[i].length() > 0 ? " with comment (" + requesterFeedback[i] + ")" : ""));
          }
        } catch (ServiceException e) {
          if (callback != null) {
            callback.processItemResult(assignmentIds[i], false, null, e);
          }
          else {
            log.error("Error approving assignment " + assignmentIds[i] + 
                (requesterFeedback[i] != null && requesterFeedback[i].length() > 0 ? " with comment (" + requesterFeedback[i] + ")" : "") +
                ": " + e.getLocalizedMessage());
          }
        }
      }
    }
  }   

  /**
   * Retrieves the properties for a HIT. 
   *  
   * @param hitId   the Id of the HIT to retrieve
   * @return a HIT object
   * @throws ServiceException
   */
  public HIT getHIT(String hitId) throws ServiceException {

    // Include HIT detail, HIT Question, and Assignment summary in response
    return super.getHIT(hitId, DEFAULT_HIT_RESPONSE_GROUP);
  }

  /**
   * Creates a Qualification type using default values for the properties 
   * not given as parameters. This method and assigns Test, AnswerKey, 
   * TestDurationInSeconds, AutoGranted, and AutoGrantedValue to null.
   * 
   * @param name         the name of the new Qualification type
   * @param keywords     one or more words or phrases that describe the Qualification type, 
   *                     separated by commas. A type's Keywords make the type easier to find 
   *                     using a search.
   * @param description  a long description for the Qualification type. This description is
   *                     displayed when a user examines a Qualification type. 
   * @return the created QualificationType
   * @throws ServiceException
   */
  public QualificationType createQualificationType(String name, String keywords, String description) throws ServiceException {
    return super.createQualificationType(name, keywords, description,
        QualificationTypeStatus.Active,
        (long) 0, // retryDelayInSeconds
        null,
        null,
        null, // testDurationInSeconds
        null, // autoGranted
        null // autoGrantedValue
    );
  }

  /**
   * Modifies the description and the status of an existing Qualification type.  The 
   * Qualification is updated to use default values for all other parameters.
   *  
   * @param qualificationTypeId  the Id of the Qualification type to update
   * @param description          the new description of the Qualification type
   * @param status               the new status of the Qualification type. This value can
   *                             be either Active or Inactive. 
   * @return the updated QualificationType
   * @throws ServiceException
   */
  public QualificationType updateQualificationType(String qualificationTypeId, String description, 
      QualificationTypeStatus status) throws ServiceException {

    return super.updateQualificationType(qualificationTypeId, description, status, 
        null, // test 
        null, // answerKey 
        (Long) null, // testDurationInSeconds 
        (Long) null, // retryDelayInSeconds 
        (Boolean) null, // autoGranted 
        (Integer) null // autoGrantedValue
    );
  }

  /**
   * Retrieves granted Qualifications found on the requested page for the given 
   * Qualification Type.  
   * 
   * @param qualificationTypeId
   * @param pageNum
   * @return an array of Qualifications
   * @throws ServiceException
   * @deprecated  
   */
  public Qualification[] getQualicationsForQualificationType(String qualificationTypeId, int pageNum) throws ServiceException {
    GetQualificationsForQualificationTypeResult result = 
      super.getQualificationsForQualificationType(qualificationTypeId,
          QualificationStatus.Granted,
          pageNum,
          DEFAULT_PAGE_SIZE);

    return result.getQualification();
  }

  /**
   * Retrieves all of the Qualifications granted to Workers for a given Qualification type. 
   * Results are divided into numbered "pages," and a single page of results is returned. 
   * This method uses the default page size of 10.
   * 
   * @param qualificationTypeId    the ID of the Qualification type to return. 
   * @param pageNum                the page of results to return. Once the Qualifications have been filtered, 
   *                               sorted, and divided into pages, the page corresponding to pageNum is 
   *                               returned as the results.
   * @return an array of Qualifications
   * @throws ServiceException
   */
  public Qualification[] getQualificationsForQualificationType(String qualificationTypeId, int pageNum) throws ServiceException {
    GetQualificationsForQualificationTypeResult result = 
      super.getQualificationsForQualificationType(qualificationTypeId,
          QualificationStatus.Granted,
          pageNum,
          DEFAULT_PAGE_SIZE);

    return result.getQualification();
  }  

  /**
   * Retrieves all of the Qualifications granted to Workers for a given Qualification type. 
   * 
   * @param qualificationTypeId    the ID of the Qualification type of the Qualifications to return.
   * @return an array of Qualifications 
   */
  public Qualification[] getAllQualificationsForQualificationType(String qualificationTypeId) throws Exception {
    List<Qualification> results = new ArrayList<Qualification>();

    int pageNum = 1;

    do {     
      Qualification[] qualifications = 
        this.getQualificationsForQualificationType(qualificationTypeId, pageNum);

      if (qualifications != null) {
        // Add the results
        Collections.addAll(results, qualifications);
      } 

      if (qualifications == null || qualifications.length < DEFAULT_PAGE_SIZE) {
        // Check if we're on the last page or not
        break;
      } else {
        pageNum++;
      }
    } while (true);

    return (Qualification[]) results.toArray(new Qualification[results.size()]);   
  }

  /**
   * Retrieves the first page of Qualification requests for a 
   * specified Qualification type.  The results are sorted by SubmitTime.
   * 
   * @param qualificationTypeId   the Id of the Qualification type 
   * @return an array of QualificationRequests
   * @throws ServiceException
   */
  public QualificationRequest[] getQualificationRequests(String qualificationTypeId) 
  throws ServiceException {

    GetQualificationRequestsResult result = super.getQualificationRequests(qualificationTypeId, 
        DEFAULT_SORT_DIRECTION, 
        GetQualificationRequestsSortProperty.SubmitTime,
        DEFAULT_PAGE_NUM, 
        DEFAULT_PAGE_SIZE);

    return result.getQualificationRequest();
  }

  /**
   * Retrieves all requests for Qualifications of the given 
   * Qualification Type.  The results are sorted by SubmitTime.
   * 
   * @param qualificationTypeId    the Id of the Qualification type.
   * @return an array of QualificationRequests
   * @throws ServiceException
   */
  public QualificationRequest[] getAllQualificationRequests(String qualificationTypeId) throws ServiceException {
    List<QualificationRequest> results = new ArrayList<QualificationRequest>();

    int pageNum = 1;
    QualificationRequest[] thisPage = null;

    do {
      GetQualificationRequestsResult result = super.getQualificationRequests(qualificationTypeId, 
          DEFAULT_SORT_DIRECTION, 
          GetQualificationRequestsSortProperty.SubmitTime,
          pageNum, 
          DEFAULT_PAGE_SIZE);

      if(result == null) {
        break;
      }

      thisPage = result.getQualificationRequest();

      if(thisPage != null && thisPage.length > 0) {
        Collections.addAll(results,thisPage);
      }
      pageNum++;
    } while (thisPage != null && thisPage.length >= DEFAULT_PAGE_SIZE);

    return (QualificationRequest[]) results.toArray(new QualificationRequest[results.size()]);
  }

  /**
   * Retrieves completed assignments found on the requested page for the given HIT.  
   *  
   * @param hitId            the Id of the HIT for which completed assignments are to be returned
   * @param pageNum          The page of results to return. Once the assignments have been filtered, 
   *                         sorted, and divided into pages, the page corresponding 
   *                         to pageNum is returned as the results of the operation. 
   * @return an array of Assignments
   * @throws ServiceException
   */
  public Assignment[] getAssignmentsForHIT(String hitId, int pageNum) throws ServiceException {
    return this.getAssignmentsForHIT(hitId, pageNum, false);

  }

  /**
   * Retrieves completed assignments for a HIT.  You can get assignments for a HIT at any time, 
   * even if the HIT is not yet "reviewable". If a HIT has multiple assignments, and has 
   * received some results but has not yet become "reviewable", you can still retrieve the partial 
   * results with this method.
   * 
   * The results are sorted by SubmitTime.  
   * 
   * @param hitId            the Id of the HIT for which completed assignments are to be returned
   * @param pageNum          The page of results to return. Once the assignments have been filtered, 
   *                         sorted, and divided into pages, the page corresponding 
   *                         to pageNum is returned as the results of the operation. 
   * @param getFullResponse  if true, all properties of the HIT are returned. If false, only the HIT Id 
   *                         and HIT type Id are returned.
   * @return an array of Assignments
   * @throws ServiceException
   */
  public Assignment[] getAssignmentsForHIT(String hitId, int pageNum, boolean getFullResponse) 
  throws ServiceException {

    // Include AssignmentFeedback in response
    String[] responseGroup = null;
    if (getFullResponse == true) {
      responseGroup = DEFAULT_ASSIGNMENT_RESPONSE_GROUP;
    }

    GetAssignmentsForHITResult result = super.getAssignmentsForHIT(hitId,
        DEFAULT_SORT_DIRECTION, 
        DEFAULT_ASSIGNMENT_STATUS,
        GetAssignmentsForHITSortProperty.SubmitTime,
        pageNum, DEFAULT_PAGE_SIZE, responseGroup
    );

    return result.getAssignment(); 
  }

  /**
   * Retrieves Requester's available balance.
   * 
   * @return Requester's available balance
   * @throws ServiceException
   */
  public double getAccountBalance() throws ServiceException {
    String defaultUnused = null;
    GetAccountBalanceResult result = super.getAccountBalance(defaultUnused);

    return result.getAvailableBalance().getAmount().doubleValue();
  }

  /**
   * Retrieves the Requester's reviewable HITs found on the specified page for the given HIT Type.
   * 
   * @param hitTypeId    the ID of the HIT type to consider for the query
   * @param pageNum      The page of results to return. Once the HIT types
   *                     have been filtered, sorted, and divided into pages, the page 
   *                     corresponding to pageNum is returned.
   * @return an array of Reviewable HITs
   * @throws ServiceException
   */
  public HIT[] getReviewableHITs(String hitTypeId, int pageNum) throws ServiceException {
    ReviewableHITStatus defaultStatus = ReviewableHITStatus.Reviewable;

    GetReviewableHITsResult result = super.getReviewableHITs(
        hitTypeId,
        defaultStatus,
        DEFAULT_SORT_DIRECTION, 
        GetReviewableHITsSortProperty.CreationTime,
        pageNum, 
        DEFAULT_PAGE_SIZE
    );

    return result.getHIT(); 
  }

  /**
   * Retrieves the Requester's HITs found on the specified page.  
   * 
   * @param pageNum           The page of results to return. Once the HITs
   *                          have been filtered, sorted, and divided into pages, the page 
   *                          corresponding to pageNum is returned.
   * @return an array of HITs
   * @throws ServiceException
   */
  public HIT[] searchHITs(int pageNum) throws ServiceException {
    return this.searchHITs(pageNum, false);
  }

  /**
   * Retrieves the Requester's HITs found on the specified page.  
   * The request uses either the default or full responseGroup.
   * 
   * @param pageNum           The page of results to return. Once the HITs
   *                          have been filtered, sorted, and divided into pages, the page 
   *                          corresponding to pageNum is returned.
   * @param getFullResponse   if true, all properties for the HIT are returned. If false, only the
   *                          HIT Id and the HIT type Id are returned.
   * @return an array of HITs
   * @throws ServiceException
   */
  public HIT[] searchHITs(int pageNum, boolean getFullResponse) throws ServiceException {

    // Include HIT detail, HIT Question, and Assignment summary in response
    String[] responseGroup = null;
    if (getFullResponse == true) {
      responseGroup = new String [] { "Minimal", "HITDetail", 
          "HITQuestion", "HITAssignmentSummary" };
    }

    SearchHITsResult result = super.searchHITs(
        DEFAULT_SORT_DIRECTION, SearchHITsSortProperty.Expiration,
        pageNum, DEFAULT_PAGE_SIZE, responseGroup
    );

    return result.getHIT(); 
  }

  /**
   * Retrieves any Qualification Types found on the specified page. This method returns only 
   * Qualification types that the Requester created and that can be requested through the Amazon Mechanical 
   * Turk web site. Qualification types that are assigned by the system cannot be requested.
   * 
   * @param pageNum  The page of results to return. Once the Qualification types 
   *                 have been filtered, sorted, and divided into pages, the page 
   *                 corresponding to pageNum is returned.
   * @return an array of QualificationTypes
   * @throws ServiceException
   */
  public QualificationType[] searchQualificationTypes(int pageNum) throws ServiceException {

    SearchQualificationTypesResult result = super.searchQualificationTypes(
        null, // Query
        false, // mustBeRequestable
        true, // mustBeOwnedByCaller
        DEFAULT_SORT_DIRECTION,
        SearchQualificationTypesSortProperty.Name,
        pageNum, 
        DEFAULT_PAGE_SIZE);

    return result.getQualificationType(); 
  }

  //-------------------------------------------------------------
  // Implementation - Convenience API
  //-------------------------------------------------------------

  /**
   * Moves a HIT from Reviewing status to Reviewable status.
   * 
   * @param hitId  the Id of the HIT to set
   * @throws ServiceException
   */
  public void setHITAsReviewable(String hitId) throws ServiceException {
    super.setHITAsReviewing(hitId, 
        true // revert
    );
  }  

  /**
   * Moves a HIT from Reviewable status to Reviewing status.
   * 
   * @param hitId  the Id of the HIT to set
   * @throws ServiceException
   */
  public void setHITAsReviewing(String hitId) throws ServiceException {
    super.setHITAsReviewing(hitId, 
        false // revert
    );
  }   

  /**
   * Retrieves all of the Requester's active HITs. 
   *  
   * @return an array of HITs
   * @throws ServiceException
   */
  public HIT[] searchAllHITs() throws ServiceException {
    List<HIT> results = new ArrayList<HIT>();

    int numHITsInAccount = this.getTotalNumHITsInAccount();

    double numHITsInAccountDouble = new Double(numHITsInAccount);
    double pageSizeDouble = new Double(DEFAULT_PAGE_SIZE);
    double numPagesDouble = Math.ceil(numHITsInAccountDouble / pageSizeDouble);

    int numPages = (new Double(numPagesDouble)).intValue();

    for (int i = 1; i <= numPages; i = i + 1)
    {
      HIT[] hits = this.searchHITs(i, true);
      Collections.addAll(results, hits);
    }

    return (HIT[]) results.toArray(new HIT[results.size()]);	
  }

  /**
   * Retrieves all active Qualification types in the system.
   * 
   * @return an array of QualificationTypes
   * @throws ServiceException
   */
  public QualificationType[] getAllQualificationTypes() throws ServiceException {
    List<QualificationType> results = new ArrayList<QualificationType>();
    int pageNum = 1;

    do {
      QualificationType[] qt = this.searchQualificationTypes(pageNum);

      if (qt != null) {
        // Add the results
        Collections.addAll(results, qt);
      }

      // Check if we're on the last page or not
      if (qt == null || qt.length < DEFAULT_PAGE_SIZE)
        break;

      pageNum++;

    } while (true);

    return (QualificationType[]) results.toArray(new QualificationType[results.size()]);		
  }

  /**
   * Retrieves all of the Requester's reviewable HITs of the specified HIT type.
   * 
   * @param hitTypeId  the ID of the HIT type of the HITs to consider for the query
   * @return an array of Reviewable HITs
   * @throws ServiceException
   */
  public HIT[] getAllReviewableHITs(String hitTypeId) throws ServiceException {
    List<HIT> results = new ArrayList<HIT>();
    int pageNum = 1;

    do {
      HIT[] hit = this.getReviewableHITs(hitTypeId, pageNum);

      if (hit != null) {
        // Add the results
        Collections.addAll(results, hit);
      }

      // Check if we're on the last page or not
      if (hit == null || hit.length < DEFAULT_PAGE_SIZE)
        break;

      pageNum++;

    } while (true);

    return (HIT[]) results.toArray(new HIT[results.size()]);
  }

  /**
   * Retrieves all submitted assignments for a HIT.
   * 
   * @param hitId    the Id of the HIT for which the assignments are returned
   * @return an array of Assignments
   * @throws ServiceException
   */
  public Assignment[] getAllAssignmentsForHIT(String hitId) throws ServiceException {   
    return getAllAssignmentsForHIT(hitId, DEFAULT_ASSIGNMENT_STATUS);
  }

  /**
   * Retrieves all assignments for a HIT for which 
   * reviewable work has been submitted.
   * 
   * @param hitId    the Id of the HIT for which the assignments are returned
   * @return an array of Assignments
   * @throws ServiceException
   */
  public Assignment[] getAllSubmittedAssignmentsForHIT(String hitId) throws ServiceException {   
    return getAllAssignmentsForHIT(hitId, SUBMITTED_ASSIGNMENT_STATUS);
  }  

  /**
   * Retrieves all assignments that have the 
   * the specified status.
   * 
   * @param hitId   the Id of the HIT for which the assignments are returned
   * @param status  the status value of the assignments to return.
   * @return an array of Assignments
   * @throws ServiceException
   */
  public Assignment[] getAllAssignmentsForHIT(String hitId, AssignmentStatus[] status) throws ServiceException {	  

    List<Assignment> results = new ArrayList<Assignment>();
    int pageNum = 1;

    do {
      GetAssignmentsForHITResult result = super.getAssignmentsForHIT(hitId,
          DEFAULT_SORT_DIRECTION, 
          status,
          GetAssignmentsForHITSortProperty.SubmitTime,
          pageNum, DEFAULT_PAGE_SIZE, DEFAULT_ASSIGNMENT_RESPONSE_GROUP
      );
      Assignment[] assignment = result.getAssignment();

      if (assignment != null) {
        // Add the results
        Collections.addAll(results, assignment);
      }

      // Check if we're on the last page or not
      if (assignment == null || assignment.length < DEFAULT_PAGE_SIZE)
        break;

      pageNum++;

    } while (true);

    return (Assignment[]) results.toArray(new Assignment[results.size()]);    
  }

  /**
   * Creates a single checkbox Qualification Type.  The QualificationTest simply asks the Worker
   * to check the box to receive a Qualification immediately.
   * 
   * @param name          the name of the Qualification type
   * @param description   a description of the Qualification type that is displayed 
   *                      when a user examines a Qualification type
   * @param keywords      one or more words or phrases, separated by commas, that describe 
   *                      the Qualification type
   * @return the created QualificationType
   * @throws ServiceException
   */
  public QualificationType createSingleCheckboxQualificationType(String name, String description, 
      String keywords) throws ServiceException {

    return super.createQualificationType(name, keywords, description, 
        QualificationTypeStatus.Active, 
        (long) 0, // retryDelayInSeconds
        getBasicCheckboxQualificationTest(name), 
        getBasicCheckboxQualificationAnswerKey(),
        (long) 60 * 60, // testDurationInSeconds 
        null,
        null // autoGrantedValue
    );
  }

  /**
   * Sets the specified Qualification Type to be inactive.
   * 
   * @param qualificationTypeId      the Id of the Qualification type to dispose
   * @return the modified QualificationType
   * @throws ServiceException
   */
  public QualificationType disposeQualificationType(String qualificationTypeId) {

    return this.updateQualificationType(qualificationTypeId, 
        null, // don't change description 
        QualificationTypeStatus.Inactive 
    ); 
  }

  /**
   * Retrieves the total number of active HITs for the Requester.
   * 
   * @return the total number of active HITs for the Requester
   * @throws ServiceException
   */
  public int getTotalNumHITsInAccount() throws ServiceException {
    SearchHITsSortProperty defaultSortProperty = 
      SearchHITsSortProperty.Expiration;

    SearchHITsResult result = super.searchHITs(
        DEFAULT_SORT_DIRECTION, 
        defaultSortProperty,
        1, // pageNum 
        DEFAULT_PAGE_SIZE, null // responseGroup
    );

    return (result==null) ? 0 : result.getTotalNumResults();
  }	

  /**
   * Sets up email notification settings for the given HIT Type.
   * 
   * @param hitTypeId     the Id of the HIT type for which to send the notification
   * @param emailAddress  the email address to send the notification
   * @param event         the event that caused the notification. The possible events are:
   *                      <ul>
   *                        <li>AssignmentAccepted</li>
   *                        <li>AssignmentAbandoned</li>
   *                        <li>AssignmentReturned</li>
   *                        <li>AssignmentSubmitted</li>
   *                        <li>HITReviewable</li>
   *                        <li>HITExpired</li>
   *                      </ul>
   * @throws ServiceException
   */
  public void sendTestEmailEventNotification(String hitTypeId, String emailAddress, EventType event) {
    NotificationSpecification ns = new NotificationSpecification();
    ns.setDestination(emailAddress);
    ns.setTransport(NotificationTransport.Email);
    ns.setVersion(RequesterServiceRaw.NOTIFICATION_VERSION);
    ns.setEventType(new EventType[] { event });
    super.setHITTypeNotification(hitTypeId, ns, true);  
  }

  /**
   * Extracts the QuestionFormAnswers object from the given answer XML.
   * 
   * @param answerXML   the answer XML string
   * @return a QuestionFormAnswers object that contains the answers
   */
  public static QuestionFormAnswers parseAnswers(String answerXML) {
    try {
      JAXBContext jc = JAXBContext.newInstance(RequesterService.DATASCHEMA_PACKAGE_PREFIX, 
          ObjectFactory.class.getClassLoader());
      Unmarshaller u = jc.createUnmarshaller();

      QuestionFormAnswers qfa = (QuestionFormAnswers) 
      u.unmarshal(new InputSource(new StringReader(answerXML)));

      return qfa;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Extracts the answer values from the given AnswerType object.  When the answer type is
   * Selections, this method returns the selections separated by the pipe character.  When the answer
   * type is UploadedFileKey, this method returns the S3 file key followed by the file's size in bytes.
   * 
   * @param assignmentId       The assignment for which the asnwers are extracted. If null, the upload 
   *                           URL might be invalid.
   * @param answer             the type of the answer
   * @return a String representation of the answer
   * @throws ServiceException
   */
  public static String getAnswerValue(String assignmentId, QuestionFormAnswersType.AnswerType answer) {
    return getAnswerValue(assignmentId, answer, false);
  }

  /**
   * Extracts the answer values from the given AnswerType object.  When the answer type is
   * Selections, this method returns the selections separated by the pipe character.  When the answer
   * type is UploadedFileKey, this method returns the S3 file key followed by the file's size in bytes.
   *  
   * @param assignmentId      The assignment for which the answers are extracted. If null, 
   *                          the upload URL might be invalid.
   * @param answer            an AnswerType structure
   * @param includeQuestionId specifies whether to prepend the answer with the associated 
   *                          QuestionIdentifier and a tab
   * @return a String representation of the answer
   */
  public static String getAnswerValue(String assignmentId, QuestionFormAnswersType.AnswerType answer, boolean includeQuestionId) {

    String result = includeQuestionId ? result = answer.getQuestionIdentifier() + "\t" : "";
    String val = "";

    if (answer.getFreeText() != null) {
      val = answer.getFreeText();
    } 
    else if (answer.getOtherSelectionText() != null) {
      val = answer.getOtherSelectionText();
    } 
    else if (answer.getSelectionIdentifier() != null
        && answer.getUploadedFileKey() == null) {

      Iterator iter = answer.getSelectionIdentifier().iterator();
      while (iter.hasNext()) {
        val += iter.next() + HITResults.MULTI_ANSWER_DELIMITER;
      }

      if (val.length() > 0) {
        val = val.substring(0, val.length() - 1);
      }
    } 
    else {

      try {     
        String url = "http://requester.mturk.com/mturk/downloadAnswer?assignmentId=" 
          + assignmentId + "&questionId=" + URLEncoder.encode(answer.getQuestionIdentifier(), "UTF-8");
        result += url;

      } catch (UnsupportedEncodingException e) {
        result += answer.getUploadedFileKey() + HITResults.MULTI_ANSWER_DELIMITER + answer.getUploadedFileSizeInBytes();

      }
    }

    if (val.length()==0) { 
      result += HITResults.EMPTY_ANSWER;   // Feature 1816806 (missing columns when value is NULL)
    }
    else {
      result += val;
    }

    return result;
  }

  /**
   * Creates multiple HITs. 
   * 
   * @param input          the input data needed for the HITs
   * @param props          the properties of the HITs
   * @param question       the question asked in the HITs
   * @return an array of HIT objects
   * @throws Exception
   * @deprecated
   */
  public HIT[] createHITs(HITDataReader input, HITProperties props, HITQuestion question) {
    return createHITs( input, props, question, RequesterService.LOAD_ALL );
  }

  /**
   * Creates multiple HITs.
   * 
   * @param input          the input data needed for the HITs
   * @param props          the properties of the HITs
   * @param question       the question in the HITs
   * @param success        the file that contains the HITId and HITTypeId for the created 
   *                       HITs
   * @param failure        the failure file
   * @return an array of HIT objects
   * @throws Exception
   */
  public HIT[] createHITs(HITDataInput input, HITProperties props, HITQuestion question,
      HITDataOutput success, HITDataOutput failure) throws Exception {
    return createHITs(input, props, question, RequesterService.LOAD_ALL, success, failure);
  }
  
  /**
   * Creates multiple HITs. 
   * 
   * @param input          the input data needed for the HITs
   * @param props          the properties of the HITs
   * @param question       the question asked in the HITs
   * @param numHITToLoad   the number of HITs to create
   * @return an array of HIT objects
   * @throws Exception
   * @deprecated
   */  
  public HIT[] createHITs(HITDataReader input, HITProperties props, HITQuestion question, int numHITToLoad) {
    String prefix = input.getFileName();
    if ( prefix == null || prefix.length() == 0 ) {
      prefix = "input";
    }

    HITDataOutput success = null;
    HITDataOutput failure = null;
    try {
      success = new HITDataWriter(prefix + ".success");
      failure = new HITDataWriter(prefix + ".failure");
      return createHITs(input,props,question,numHITToLoad,success,failure);
    }
    catch (Exception e) {
      log.error("Error loading HITs", e);
    }
    finally {
        if (success != null) {
            success.close();
        }
        if (failure != null) {
            failure.close();
        }
    }
    return null;
  }

  /**
   * Creates multiple HITs. 
   * 
   * @param input          the input data needed for the HITs
   * @param props          the properties of the HITs
   * @param question       a question structure that contains the question
   *                       asked in the HITs 
   * @param numHITToLoad   the number of HITs to load
   * @param success        the file that contains the HIT Ids and HIT type Ids of the
   *                       created HITs
   * @param failure        the failure file 
   * @return an array of HIT objects
   * @throws Exception
   */
  public HIT[] createHITs(HITDataInput input, HITProperties props, HITQuestion question, int numHITToLoad,
      HITDataOutput success, HITDataOutput failure) throws Exception {     
    // Create HITs
    List<HIT> hits = new ArrayList<HIT>();

    String[] fieldHeaders = new String[] { 
        HITProperties.HITField.HitId.getFieldName(), 
        HITProperties.HITField.HitTypeId.getFieldName()
    };

    boolean hasFailures = false;
    if( success != null ) {
      success.setFieldNames( fieldHeaders );
    }
    
    int numRecords;
    if (numHITToLoad != RequesterService.LOAD_ALL) {
      numRecords = Math.min(numHITToLoad, input.getNumRows()-1);
    }
    else {
      numRecords = input.getNumRows() - 1;
    }

    // submit hits to work pool
    AsyncReply[] replies = new AsyncReply[MAX_BATCH];

    // Map of HIT types created
    String hitTypeForBatch = null;
    HIT hit = null;

    // split work
    log.debug(String.format("Creating %d HITs with max memory %d", numRecords, Runtime.getRuntime().maxMemory()));

    int numBatches = numRecords / MAX_BATCH;
    for (int curBatch=0; curBatch<=numBatches; curBatch++) {
      int iStart = curBatch * MAX_BATCH;
      int iEnd = iStart + MAX_BATCH;
      if (iEnd > numRecords) {
        iEnd = numRecords;
      }

      log.debug(String.format("Processing batch %d (%d to %d)", curBatch, iStart, iEnd)); 

      for (int i = iStart; i < iEnd; i++) {
        // Merge the input with the question
        // Start from the second line since the first line contains the field names
        Map<String, String> inputMap = input.getRowAsMap(i + 1);   

        // Merge the input with the properties
        props.setInputMap(inputMap);

        // we need to make sure to not create multiple hittypes for matching HITs
        // due to multithreaded calls being processed at the same time
        if (hitTypeForBatch == null) {
          hitTypeForBatch = super.registerHITType(
              props.getAutoApprovalDelay(), 
              props.getAssignmentDuration(), 
              props.getRewardAmount(), 
              props.getTitle(), 
              props.getKeywords(), 
              props.getDescription(), 
              props.getQualificationRequirements());
        }

        replies[i-iStart] = super.createHITAsync(
            hitTypeForBatch,    
            null,       // title
            null,       // description
            null,       // keywords
            question.getQuestion(inputMap), 
            null,       // reward
            null,       // assignmentDurationInSeconds
            null,       // autoApprovalDelayInSeconds
            props.getLifetime(), 
            props.getMaxAssignments(), 
            props.getAnnotation(), 
            null,       // qualification requirements 
            null,       // response group
            null,       // uniqueRequestToken
            null,       // assignmentReviewPolicy
            null,       // hitReviewPolicy
            null);      // async callback   
      }

      // wait for thread pool to finish processing these requests and evaluate results        
      for (int i = iStart; i < iEnd; i++) {
        try {
          hit = ((HIT[])replies[i-iStart].getResult())[0];         
          hits.add(hit);

          log.info("Created HIT " + (i + 1) + ": HITId=" + hit.getHITId());

          if( success != null ) {
            // Print to the success file
            HashMap<String,String> good = new HashMap<String,String>();
            good.put( fieldHeaders[0], hit.getHITId() );
            good.put( fieldHeaders[1], hit.getHITTypeId() );
            success.writeValues(good);
          }
        }
        catch (Exception e) {         
          // Validate the question
          Map<String,String> row = input.getRowAsMap(i+1);
          try {
            Map<String, String> inputMap = input.getRowAsMap(i + 1);
            QAPValidator.validate(question.getQuestion(inputMap));
            // If it passed validation, then log the exception e
            log.error("[ERROR] Error creating HIT " + (i+1) 
                + " (" + input.getRowValues(i+1)[0] + "): " + e.getLocalizedMessage());         
          }
          catch (ValidationException ve) {
            // Otherwise, log the validation exception in place of the service exception
            log.error("[ERROR] Error creating HIT " + (i+1) 
                + " (" + input.getRowValues(i+1)[0] + "): " + ve.getLocalizedMessage());
          }

          if( failure != null ) {
            // Create the failure file
            if (!hasFailures) {              
              hasFailures = true;
              failure.setFieldNames( input.getFieldNames() );
            }

            // Print to the failure file
            failure.writeValues(row);
          }
        }         
      }

    }

    if (hit != null && log.isInfoEnabled()) {
      // Print the URL at which the new HIT can be viewed at the end as well
      // so the user doesn't have to "scroll" up in case lots of HITs have been loaded
      log.info(System.getProperty("line.separator") + "You may see your HIT(s) with HITTypeId '" + hit.getHITTypeId() + "' here: ");
      log.info(System.getProperty("line.separator") + "  " + getWebsiteURL() 
          + "/mturk/preview?groupId=" + hit.getHITTypeId() + System.getProperty("line.separator"));
    }
      
    return (HIT[])hits.toArray(new HIT[hits.size()]);      
  }

  /**
   * Gets the results for specified HIT types.
   * 
   * @param success  a success file that contains the HITTypes 
   * @return a HITTypeResults object that contains the results of the HITs
   */
  public HITTypeResults getHITTypeResults(HITDataInput success) {
    HITTypeResults r = null;
    try {
      r = this.getHITTypeResults(success, null);
    } catch (IOException e) {
      // There shouldn't be any IO exception here
      log.error("IOException thrown.  Did the HIT results get printed somehow?");
    }
    return r;
  }

  /**
   * Gets the results of HITs.
   * 
   * @param success   a success file that contains the HITs to get results for
   * @param callback  your callback method. This method is called asynchronously as 
   *                  each result is retrieved. This method can track the progress of 
   *                  the batch so you do not exit the process until the batch is completed.
   */
  public void getResults(HITDataInput success, BatchItemCallback callback) {

    if (callback == null) {
      throw new IllegalArgumentException("callback may not be null");
    }

    int numRows = success.getNumRows();
    
    String[] rowValues;
    HIT hit=null;
    String hitId;

    Assignment[] assignments = null;
    AsyncReply[] hitReplies = new AsyncReply[MAX_BATCH];
    AssignmentsLoader[] assignmentLoaders = new AssignmentsLoader[MAX_BATCH];

    // split work
    log.debug(String.format("Retrieving results for %d HITs with max memory %d", 
        numRows - 1,  // take off the header row
        Runtime.getRuntime().maxMemory()));

    int numBatches = numRows / MAX_BATCH;
    for (int curBatch=0; curBatch<=numBatches; curBatch++) {
      int iStart = curBatch * MAX_BATCH;
      int iEnd = iStart + MAX_BATCH;
      if (iEnd > numRows) {
        iEnd = numRows;
      }      

      log.debug(String.format("Processing batch %d (%d to %d)", curBatch, iStart, iEnd));

      // load hits and (first) results in worker queue
      for (int i=iStart; i<iEnd; i++) {
        rowValues = success.getRowValues(i);
        hitId = rowValues[MTurkConstants.HIT_ID_FIELD_IND];
        
        // Skip header lines
        if ( hitId.equalsIgnoreCase(MTurkConstants.HIT_ID_HEADER) )
          continue;

        hitReplies[i-iStart] = super.getHITAsync(hitId, DEFAULT_HIT_RESPONSE_GROUP, null);
        
        AssignmentsLoader loader = new AssignmentsLoader(this, 
            hitId, 
            SortDirection.Ascending,
            DEFAULT_ASSIGNMENT_STATUS,
            GetAssignmentsForHITSortProperty.SubmitTime,
            DEFAULT_ASSIGNMENT_RESPONSE_GROUP,
            DEFAULT_PAGE_SIZE);      
        
        assignmentLoaders[i-iStart] = loader;
      
        // start loading the assignments for the HIT
        loader.start();
      }

      // process the results
      for (int i=iStart; i<iEnd; i++) {
        rowValues = success.getRowValues(i);
        hitId = rowValues[MTurkConstants.HIT_ID_FIELD_IND];

        // Skip header lines
        if ( hitId.equalsIgnoreCase(MTurkConstants.HIT_ID_HEADER) ) {
          continue;
        }

        try {
          hit = ((HIT[])hitReplies[i-iStart].getResult())[0];
          AssignmentsLoader loader = (AssignmentsLoader)assignmentLoaders[i-iStart];
          assignments = loader.getResults();
          callback.processItemResult(hitId, true, new HITResults(hit, assignments, this.config), null);
        }
        catch (Exception e) {
          callback.processItemResult(hitId, false, null, e);
        }
      }          
    }
  }

  /**
   * Gets the results for specified HIT types.
   * 
   * @param success a success file that contains the HIT Types
   * @param output  the output file
   * @return A HITTypeResults object
   * @throws IOException 
   * @deprecated
   */
  public HITTypeResults getHITTypeResults(HITDataInput success, HITDataOutput output) throws IOException {
    int numRows = success.getNumRows();
    String[] rowValues;
    String hitId;

    ArrayList<HITResults> hitResultsArray = new ArrayList<HITResults>(numRows); 
    Assignment[] assignments = null;
    HITResults r = null;
    HITTypeResults hitTypeResults = new HITTypeResults();

    if (output != null) {
      // Print headers
      log.debug("Print each HIT results as it's retrieved");
      hitTypeResults.setHITDataOutput(output);
      hitTypeResults.writeResultsHeader();
    } else {
      log.debug("Retrieve all HIT results and return them as HITTypeResults");
    }

    // load hits and (first) results in worker queue
    AsyncReply[] hitReplies = new AsyncReply[numRows];
    AsyncReply[] assignmentReplies = new AsyncReply[numRows];
    for (int i=0; i<numRows; i++) {
      rowValues = success.getRowValues(i);
      hitId = rowValues[MTurkConstants.HIT_ID_FIELD_IND];

      // Skip header lines
      if ( hitId.equalsIgnoreCase(MTurkConstants.HIT_ID_HEADER) )
        continue;

      hitReplies[i] = super.getHITAsync(hitId, DEFAULT_HIT_RESPONSE_GROUP, null);
      assignmentReplies[i] = super.getAssignmentsForHITAsync(hitId, 
          SortDirection.Ascending,
          DEFAULT_ASSIGNMENT_STATUS,
          GetAssignmentsForHITSortProperty.SubmitTime,
          1,
          DEFAULT_PAGE_SIZE,
          DEFAULT_ASSIGNMENT_RESPONSE_GROUP,
          null);
    }

    // process the results
    for (int i=0; i<numRows; i++) {
      rowValues = success.getRowValues(i);
      hitId = rowValues[MTurkConstants.HIT_ID_FIELD_IND];

      // Skip header lines
      if ( hitId.equalsIgnoreCase(MTurkConstants.HIT_ID_HEADER) ) {
        continue;
      }

      try {
        HIT hit = ((HIT[])hitReplies[i].getResult())[0];
        assignments = new Assignment[] {};

        GetAssignmentsForHITResult result = ((GetAssignmentsForHITResult[])assignmentReplies[i].getResult())[0];
        if (result.getAssignment() != null) {
          assignments = result.getAssignment();

          // if there are more pages load them in as well, otherwise use results from first page
          if (assignments.length == DEFAULT_PAGE_SIZE && result.getTotalNumResults() > DEFAULT_PAGE_SIZE) {
            assignments = super.getAssignmentsForHITAsync(hitId, 
                SortDirection.Ascending,
                DEFAULT_ASSIGNMENT_STATUS,
                GetAssignmentsForHITSortProperty.SubmitTime,
                DEFAULT_PAGE_SIZE,
                DEFAULT_ASSIGNMENT_RESPONSE_GROUP,
                result,
                null);
          }        		
        }

        r = new HITResults(hit, assignments, this.config);

        if (output != null) {
          r.writeResults(output);
        } 
        else {
          hitResultsArray.add(r);
        }

        log.info(String.format("Retrieved HIT %d/%d, %s", i, numRows-1, hit.getHITId()));
      }
      catch (Exception e) {
        log.error(String.format("Error retrieving HIT results for HIT %d/%d (%s): %s", i, numRows-1, hitId, e.getMessage()));
      }

    }    

    if (output != null) {
      return null;

    } else {
      return new HITTypeResults(
          hitResultsArray.toArray(
              new HITResults[hitResultsArray.size()] ) );
    }
  }

  /**
   * Creates a preview of a HIT in a file.
   * 
   * @param previewFileName  the file in which the HIT is copied
   * @param input            the input needed for the HIT
   * @param props            the properties of the HIT 
   * @param question         the question asked in the HIT
   * @throws ServiceException
   */
  public void previewHIT(String previewFileName, HITDataInput input, HITProperties props, 
      HITQuestion question) throws ServiceException {
    try {
      String previewString = previewHIT(input, props, question);

      if (previewString != null) {
        FileUtil fts = new FileUtil(previewFileName);
        fts.saveString(previewString, false); // overwrite
      }
    }
    catch (Exception e)
    {
      throw new ServiceException("Error generating preview file " + previewFileName, e);
    }
  }

  /**
   * Returns a preview of the HIT as HTML
   * 
   * @param input       the input needed for the HIT
   * @param props       the properties of the HIT 
   * @param question    the question asked in the HIT
   * @return an HTML preview of the HIT
   * @throws Exception
   */
  public String previewHIT(HITDataInput input, HITProperties props, 
      HITQuestion question) throws Exception {   

    if (props == null || question == null)
      throw new IllegalArgumentException();

    String questionXML = null;

    if (input != null) {
      Map<String, String> inputMap = input.getRowAsMap(1);
      questionXML = question.getQuestion(inputMap);
    } else {
      questionXML = question.getQuestion();
    }

    // Validate question before preview
    QAPValidator.validate(questionXML);

    String questionPreview = XslTransformer.convertQAPtoHTML(questionXML);
    InputStream headerURL = this.getClass().getResourceAsStream("previewHITHeader.xml");
    InputStream footerURL = this.getClass().getResourceAsStream("previewHITFooter.xml");

    if (headerURL == null) {
      log.error("Error reading the preview header file.");
    }

    if (footerURL == null) {
      log.error("Error reading the preview footer file.");
    }

    BufferedReader headerReader = new BufferedReader(new InputStreamReader(headerURL));
    BufferedReader footerReader = new BufferedReader(new InputStreamReader(footerURL));     

    String thisLine = null;
    String header = "";
    String footer = "";
    while ((thisLine = headerReader.readLine()) != null) { header += thisLine + System.getProperty("line.separator"); } 
    while ((thisLine = footerReader.readLine()) != null) { footer += thisLine + System.getProperty("line.separator"); }
    headerReader.close();
    footerReader.close();       

    NumberFormat rewardFormatter = NumberFormat.getInstance();
    rewardFormatter.setMaximumFractionDigits(2);
    rewardFormatter.setMinimumFractionDigits(2);        

    Map<String, String> headerMap = new HashMap<String, String>();
    headerMap.put("requester", "[Your Requester Name Here]");
    headerMap.put("title", props.getTitle());
    headerMap.put("description", props.getDescription());
    headerMap.put("keywords", props.getKeywords());
    headerMap.put("reward", rewardFormatter.format(props.getRewardAmount()));
    String mergedHeader = VelocityUtil.doMerge(header, headerMap);

    String previewString = mergedHeader + questionPreview + footer;

    return previewString;
  }

  /**
   * Appends the application signature to the request header.
   * 
   * @param signature  the application signature
   */
  public void appendApplicationSignature(String signature) {
    super.appendApplicationSignature(signature, this.getPort());
  }

  //-------------------------------------------------------------
  // Methods - Public Static
  //-------------------------------------------------------------

  /**
   * Returns the URL for the Amazon Mechanical Turk web site.
   *
   * @return the web site URL
   */
  public String getWebsiteURL() {
    return this.config.getWorkerWebsiteURL();
  }

  /**
   * Formats the given value into the currency format for US dollars.
   *
   * @param value  the value to format
   * @return the formatted value
   */
  public static String formatCurrency(double value) {
    DecimalFormat form = new DecimalFormat("0.00"); // print up to two decimal points

    return form.format(value);
  }

  /**
   * Constructs a Question XML string for a simple HIT with one question and 
   * a freetext response.
   * 
   * @param question the question to ask
   * @return a Question XML string
   */
  public static String getBasicFreeTextQuestion(String question) {
    String q = "";
    q += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    q += "<QuestionForm xmlns=\"http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2005-10-01/QuestionForm.xsd\">";
    q += "  <Question>"; 
    q += "    <QuestionIdentifier>1</QuestionIdentifier>";
    q += "    <QuestionContent>";
    q += "      <Text>" + question + "</Text>";
    q += "    </QuestionContent>"; 
    q += "    <AnswerSpecification>";
    q += "      <FreeTextAnswer/>";
    q += "    </AnswerSpecification>"; 
    q += "  </Question>";
    q += "</QuestionForm>";
    return q;
  }

  //-------------------------------------------------------------
  // Methods - Protected
  //-------------------------------------------------------------

  /**
   * Constructs a Qualification test XML string for a single checkbox.
   * 
   * @param name the name of the Qualification Test
   * @return a QualificationTest XML string
   */
  protected String getBasicCheckboxQualificationTest(String name) {
    String test = "";
    test += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    test += "<QuestionForm xmlns=\"http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2005-10-01/QuestionForm.xsd\">";
    test += "<Overview><Title>" + name + "</Title></Overview>";
    test += "<Question>";
    test += "  <QuestionIdentifier>ConfirmRequest</QuestionIdentifier>";
    test += "  <DisplayName>Confirm your request</DisplayName>";
    test += "  <IsRequired>true</IsRequired>";
    test += "  <QuestionContent><Text></Text></QuestionContent>";
    test += "  <AnswerSpecification>";
    test += "   <SelectionAnswer>";
    test += "    <StyleSuggestion>checkbox</StyleSuggestion>";
    test += "    <Selections>";
    test += "     <Selection>";
    test += "      <SelectionIdentifier>yes</SelectionIdentifier>";
    test += "      <Text>Please check the box to the left and click SUBMIT to have the qualification granted to you. If you do not want the qualification, please click CANCEL.</Text>";
    test += "     </Selection>";
    test += "    </Selections>";
    test += "   </SelectionAnswer>";
    test += "  </AnswerSpecification>";
    test += "</Question>";
    test += "</QuestionForm>";
    return test;
  }

  /**
   * Constructs a Qualification AnswerKey XML string for the single checkbox Qualification test.
   * The AnswerKey assigns a Qualification Score of 100 to the worker.
   * 
   * @return a Qualification AnswerKey XML string
   */
  protected String getBasicCheckboxQualificationAnswerKey() {
    String answerKey = "";
    answerKey += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    answerKey += "<AnswerKey xmlns=\"http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2005-10-01/AnswerKey.xsd\">";
    answerKey += "<Question>";
    answerKey += "  <QuestionIdentifier>ConfirmRequest</QuestionIdentifier>";
    answerKey += "  <AnswerOption>";
    answerKey += "    <SelectionIdentifier>yes</SelectionIdentifier>";
    answerKey += "    <AnswerScore>100</AnswerScore>";
    answerKey += "  </AnswerOption>";
    answerKey += "</Question>";
    answerKey += "</AnswerKey>";
    return answerKey;
  }


}
