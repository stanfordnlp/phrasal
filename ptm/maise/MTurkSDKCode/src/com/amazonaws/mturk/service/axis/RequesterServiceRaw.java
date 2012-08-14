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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.amazonaws.mturk.filter.ErrorProcessingFilter;
import com.amazonaws.mturk.filter.RetryFilter;
import com.amazonaws.mturk.requester.AWSMechanicalTurkRequester;
import com.amazonaws.mturk.requester.AWSMechanicalTurkRequesterLocator;
import com.amazonaws.mturk.requester.ApproveAssignmentRequest;
import com.amazonaws.mturk.requester.ApproveRejectedAssignmentRequest;
import com.amazonaws.mturk.requester.AssignQualificationRequest;
import com.amazonaws.mturk.requester.Assignment;
import com.amazonaws.mturk.requester.AssignmentStatus;
import com.amazonaws.mturk.requester.BlockWorkerRequest;
import com.amazonaws.mturk.requester.ChangeHITTypeOfHITRequest;
import com.amazonaws.mturk.requester.CreateHITRequest;
import com.amazonaws.mturk.requester.CreateQualificationTypeRequest;
import com.amazonaws.mturk.requester.DataPoint;
import com.amazonaws.mturk.requester.DisableHITRequest;
import com.amazonaws.mturk.requester.DisposeHITRequest;
import com.amazonaws.mturk.requester.EventType;
import com.amazonaws.mturk.requester.ExtendHITRequest;
import com.amazonaws.mturk.requester.ForceExpireHITRequest;
import com.amazonaws.mturk.requester.GetAccountBalanceRequest;
import com.amazonaws.mturk.requester.GetAccountBalanceResult;
import com.amazonaws.mturk.requester.GetAssignmentRequest;
import com.amazonaws.mturk.requester.GetAssignmentResult;
import com.amazonaws.mturk.requester.GetAssignmentsForHITRequest;
import com.amazonaws.mturk.requester.GetAssignmentsForHITResult;
import com.amazonaws.mturk.requester.GetAssignmentsForHITSortProperty;
import com.amazonaws.mturk.requester.GetBlockedWorkersRequest;
import com.amazonaws.mturk.requester.GetBlockedWorkersResult;
import com.amazonaws.mturk.requester.GetBonusPaymentsRequest;
import com.amazonaws.mturk.requester.GetBonusPaymentsResult;
import com.amazonaws.mturk.requester.GetFileUploadURLRequest;
import com.amazonaws.mturk.requester.GetFileUploadURLResult;
import com.amazonaws.mturk.requester.GetHITRequest;
import com.amazonaws.mturk.requester.GetHITsForQualificationTypeRequest;
import com.amazonaws.mturk.requester.GetHITsForQualificationTypeResult;
import com.amazonaws.mturk.requester.GetQualificationRequestsRequest;
import com.amazonaws.mturk.requester.GetQualificationRequestsResult;
import com.amazonaws.mturk.requester.GetQualificationRequestsSortProperty;
import com.amazonaws.mturk.requester.GetQualificationScoreRequest;
import com.amazonaws.mturk.requester.GetQualificationTypeRequest;
import com.amazonaws.mturk.requester.GetQualificationsForQualificationTypeRequest;
import com.amazonaws.mturk.requester.GetQualificationsForQualificationTypeResult;
import com.amazonaws.mturk.requester.GetRequesterStatisticRequest;
import com.amazonaws.mturk.requester.GetRequesterWorkerStatisticRequest;
import com.amazonaws.mturk.requester.GetReviewResultsForHITRequest;
import com.amazonaws.mturk.requester.GetReviewResultsForHITResult;
import com.amazonaws.mturk.requester.GetReviewableHITsRequest;
import com.amazonaws.mturk.requester.GetReviewableHITsResult;
import com.amazonaws.mturk.requester.GetReviewableHITsSortProperty;
import com.amazonaws.mturk.requester.GetStatisticResult;
import com.amazonaws.mturk.requester.GrantBonusRequest;
import com.amazonaws.mturk.requester.GrantQualificationRequest;
import com.amazonaws.mturk.requester.HIT;
import com.amazonaws.mturk.requester.HITLayoutParameter;
import com.amazonaws.mturk.requester.HelpRequest;
import com.amazonaws.mturk.requester.HelpRequestHelpType;
import com.amazonaws.mturk.requester.Information;
import com.amazonaws.mturk.requester.NotificationSpecification;
import com.amazonaws.mturk.requester.NotifyWorkersRequest;
import com.amazonaws.mturk.requester.Price;
import com.amazonaws.mturk.requester.Qualification;
import com.amazonaws.mturk.requester.QualificationRequirement;
import com.amazonaws.mturk.requester.QualificationStatus;
import com.amazonaws.mturk.requester.QualificationType;
import com.amazonaws.mturk.requester.QualificationTypeStatus;
import com.amazonaws.mturk.requester.RegisterHITTypeRequest;
import com.amazonaws.mturk.requester.RegisterHITTypeResult;
import com.amazonaws.mturk.requester.RejectAssignmentRequest;
import com.amazonaws.mturk.requester.RejectQualificationRequestRequest;
import com.amazonaws.mturk.requester.RequesterStatistic;
import com.amazonaws.mturk.requester.ReviewPolicy;
import com.amazonaws.mturk.requester.ReviewPolicyLevel;
import com.amazonaws.mturk.requester.ReviewableHITStatus;
import com.amazonaws.mturk.requester.RevokeQualificationRequest;
import com.amazonaws.mturk.requester.SearchHITsRequest;
import com.amazonaws.mturk.requester.SearchHITsResult;
import com.amazonaws.mturk.requester.SearchHITsSortProperty;
import com.amazonaws.mturk.requester.SearchQualificationTypesRequest;
import com.amazonaws.mturk.requester.SearchQualificationTypesResult;
import com.amazonaws.mturk.requester.SearchQualificationTypesSortProperty;
import com.amazonaws.mturk.requester.SendTestEventNotificationRequest;
import com.amazonaws.mturk.requester.SetHITAsReviewingRequest;
import com.amazonaws.mturk.requester.SetHITTypeNotificationRequest;
import com.amazonaws.mturk.requester.SortDirection;
import com.amazonaws.mturk.requester.TimePeriod;
import com.amazonaws.mturk.requester.UnblockWorkerRequest;
import com.amazonaws.mturk.requester.UpdateQualificationScoreRequest;
import com.amazonaws.mturk.requester.UpdateQualificationTypeRequest;
import com.amazonaws.mturk.service.exception.ObjectDoesNotExistException;
import com.amazonaws.mturk.service.exception.ServiceException;
import com.amazonaws.mturk.util.ClientConfig;
import com.amazonaws.mturk.util.PropertiesClientConfig;

/**
 * The RequesterServiceRaw class provides a set of APIs, which maps to the Mechanical Turk
 * Requester WSDL.  Its contructor is protected, so the client cannot instantiate this class
 * directly.  The client should instead use a child instance that inherits these raw APIs. 
 * 
 * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReferenceArticle.html
 */
public class RequesterServiceRaw extends FilteredAWSService
{

  //-------------------------------------------------------------
  // Constants - Private
  //-------------------------------------------------------------

  private static final String PORT_NAME = "AWSMechanicalTurkRequesterPort";
  private static final String SERVICE_NAME = "AWSMechanicalTurkRequester";
  private static final String PACKAGE_PREFIX = "com.amazonaws.mturk.requester.";  
  

  //  This enum type associates the request method name with the result type name.
  private enum ResultMatch {
      CreateHIT("HIT"),
      ChangeHITTypeOfHIT("ChangeHITTypeOfHITResult"),
      RegisterHITType("RegisterHITTypeResult"),
      SetHITTypeNotification("SetHITTypeNotificationResult"),
      SendTestNotification("SendTestNotificationResult"),
      GetReviewableHITs("GetReviewableHITsResult"),
      GetHIT("HIT"),
      GetHITsForQualificationType("GetHITsForQualificationTypeResult"),
      GetQualificationsForQualificationType("GetQualificationsForQualificationTypeResult"),
      SetHITAsReviewing("SetHITAsReviewingResult"),
      ApproveAssignment("ApproveAssignmentResult"),
      RejectAssignment("RejectAssignmentResult"),
      DisposeHIT("DisposeHITResult"),
      GetAssignmentsForHIT("GetAssignmentsForHITResult"),
      ExtendHIT("ExtendHITResult"),
      ForceExpireHIT("ForceExpireHITResult"),
      GetFileUploadURL("GetFileUploadURLResult"),
      SearchHITs("SearchHITsResult"),
      GrantBonus("GrantBonusResult"),
      GetBonusPayments("GetBonusPaymentsResult"),
      CreateQualificationType("QualificationType"),
      GetQualificationRequests("GetQualificationRequestsResult"),
      RejectQualificationRequest("RejectQualificationRequestResult"),
      GrantQualification("GrantQualificationResult"),
      AssignQualification("AssignQualificationResult"),
      RevokeQualification("RevokeQualificationResult"),
      GetQualificationType("QualificationType"),
      GetQualificationScore("Qualification"),
      UpdateQualificationScore("UpdateQualificationScoreResult"),
      SearchQualificationTypes("SearchQualificationTypesResult"),
      UpdateQualificationType("QualificationType"),
      GetAccountBalance("GetAccountBalanceResult"),
      GetRequesterStatistic("GetStatisticResult"),
      NotifyWorkers("NotifyWorkersResult"),
      DisableHIT("DisableHITResult"),
      SetWorkerAcceptLimit("SetWorkerAcceptLimitResult"),
      GetWorkerAcceptLimit("GetWorkerAcceptLimitResult"),
      BlockWorker("BlockWorkerResult"),
      UnblockWorker("UnblockWorkerResult"),
      Help("Information"),
      GetReviewResultsForHIT("GetReviewResultsForHITResult"),
      GetRequesterWorkerStatistic("GetStatisticResult"),
      ApproveRejectedAssignment("ApproveRejectedAssignmentResult"),
      GetAssignment("GetAssignmentResult"),
      GetBlockedWorkers("GetBlockedWorkersResult");

    private String resultTypeName;

    private ResultMatch(String resultTypeName) {
      this.resultTypeName = resultTypeName;
    }

    public String getResultTypeName() {
      return this.resultTypeName;
    }
  }

  //-------------------------------------------------------------
  // Variables - Protected
  //-------------------------------------------------------------

  protected AWSMechanicalTurkRequester service;

  /**
   * Port stubs are not thread safe, so let's cache it per thread in a thread local
   */
  protected ThreadLocal<Object> stubCache = new ThreadLocal<Object>();
  

  //-------------------------------------------------------------
  // Variables - Public
  //-------------------------------------------------------------

  public final static String NOTIFICATION_VERSION = "2006-05-05";

  //-------------------------------------------------------------
  // Constructor - Protected
  //-------------------------------------------------------------
  @Deprecated
  protected RequesterServiceRaw() {
    this(new PropertiesClientConfig());
  }
  
  protected RequesterServiceRaw( ClientConfig config ) {
    super(config);
    
    try {
      
      // instantiate port for main thread to fail-fast in case it is misconfigured
      AWSMechanicalTurkRequesterLocator locator = new AWSMechanicalTurkRequesterLocator();
      locator.setEndpointAddress(PORT_NAME, this.config.getServiceURL());
      
      getPort();      

      // Read the access keys from config
      this.setAccessKeyId(this.config.getAccessKeyId());
      this.setSigner(this.config.getSecretAccessKey());
      //add default Retry Filter to list of filters
      this.addFilter(new ErrorProcessingFilter());
      this.addFilter(new RetryFilter(config.getRetriableErrors(), config.getRetryAttempts(), config.getRetryDelayMillis()));
      
    } catch (Exception e) {
      throw new RuntimeException("Invalid configuration for port", e);
    }
  }

  //-------------------------------------------------------------
  // Methods - Protected
  //-------------------------------------------------------------
  
  @Override
  protected synchronized Object getPort() {
    // The following is a workaround for two Axis threading issues 
    // https://issues.apache.org/jira/browse/AXIS-2284
    //  Implemented in 1.4 and suggests to use the locator as a factory 
    //  for stubs which can then be cached in a thread local    
    // http://issues.apache.org/jira/browse/AXIS-2498
    //  Not implemented in 1.4 - the only workaround is to create a
    //  new locator before the stub for the thread is created.
    //  (although this only occurs on multi-CPU machines)
    Object stub = stubCache.get();
    if (stub == null) {
      try {        
        AWSMechanicalTurkRequesterLocator loc = new AWSMechanicalTurkRequesterLocator();
        loc.setEndpointAddress(PORT_NAME, this.config.getServiceURL());
        stub = loc.getAWSMechanicalTurkRequesterPort();
        appendApplicationSignature(null, stub);
        
        stubCache.set(stub);
      }
      catch (javax.xml.rpc.ServiceException e) {
        throw new IllegalStateException("Invalid configuration for locator", e);
      }
    }
    
    return stub;
  }

  @Override
  protected String getServiceName() {
    return SERVICE_NAME;    
  }

  @Override
  protected String getPackagePrefix() {
    return PACKAGE_PREFIX;
  }

  protected static void appendApplicationSignature(String signature, Object port) {

    if (signature != null) {
      String headerValue = AWSService.httpHeaders.get(AWSService.HTTP_HEADER_AMAZON_SOFTWARE);

      AWSService.httpHeaders.put(AWSService.HTTP_HEADER_AMAZON_SOFTWARE, 
          headerValue + "," + signature);
    }

    if (port instanceof org.apache.axis.client.Stub) {
      ((org.apache.axis.client.Stub) port)._setProperty(
          org.apache.axis.transport.http.HTTPConstants.REQUEST_HEADERS,
          AWSService.httpHeaders);
    }
  }
  
  private CreateHITRequest wrapHITParams(String hitTypeId, String title, String description, String keywords, 
      String question, Double reward, Long assignmentDurationInSeconds, Long autoApprovalDelayInSeconds, 
      Long lifetimeInSeconds, Integer maxAssignments, String requesterAnnotation, 
      QualificationRequirement[] qualificationRequirements, String[] responseGroup,
      String uniqueRequestToken, ReviewPolicy assignmentReviewPolicy, ReviewPolicy hitReviewPolicy,
      String hitLayoutId, HITLayoutParameter[] hitLayoutParameters) {
    CreateHITRequest request = new CreateHITRequest();
  
    if (question != null)         request.setQuestion(question);
    if (lifetimeInSeconds != null)request.setLifetimeInSeconds(lifetimeInSeconds);
    if (hitTypeId != null)        request.setHITTypeId(hitTypeId);
    if (title != null)            request.setTitle(title);
    if (description != null)      request.setDescription(description);
    if (keywords != null)         request.setKeywords(keywords);
    if (maxAssignments != null)   request.setMaxAssignments(maxAssignments);
    if (responseGroup != null)    request.setResponseGroup(responseGroup);
    if (hitReviewPolicy != null)  request.setHITReviewPolicy(hitReviewPolicy);
    if (hitLayoutId != null)      request.setHITLayoutId(hitLayoutId);
    if (requesterAnnotation != null)        request.setRequesterAnnotation(requesterAnnotation);
    if (assignmentDurationInSeconds != null)request.setAssignmentDurationInSeconds(assignmentDurationInSeconds);
    if (autoApprovalDelayInSeconds != null) request.setAutoApprovalDelayInSeconds(autoApprovalDelayInSeconds);
    if (qualificationRequirements != null)  request.setQualificationRequirement(qualificationRequirements);
    if (assignmentReviewPolicy != null)     request.setAssignmentReviewPolicy(assignmentReviewPolicy);
    if (uniqueRequestToken != null)         request.setUniqueRequestToken(uniqueRequestToken);
    if (hitLayoutParameters != null)        request.setHITLayoutParameter(hitLayoutParameters);
    
    if (reward != null) {
      Price p = new Price();
      p.setAmount(new BigDecimal(reward));
      p.setCurrencyCode("USD");
      request.setReward(p);
    }
    
    return request;
  }

  //-------------------------------------------------------------
  // Methods - Public
  //-------------------------------------------------------------

  /**
   * Backwards compatibility for programs that don't specify review policies
   */
  public HIT createHIT(String hitTypeId, String title, String description, String keywords, 
      String question, Double reward, Long assignmentDurationInSeconds, Long autoApprovalDelayInSeconds, 
      Long lifetimeInSeconds, Integer maxAssignments, String requesterAnnotation, 
      QualificationRequirement[] qualificationRequirements, String[] responseGroup)
    throws ServiceException {
    
    return createHIT(hitTypeId, title, description, keywords, question, reward,
        assignmentDurationInSeconds, autoApprovalDelayInSeconds, lifetimeInSeconds,
        maxAssignments, requesterAnnotation, qualificationRequirements, responseGroup,
        null, null, null);
  } 
  
  /**
   * Support for creating HITs using HIT layouts
   * @see http://docs.amazonwebservices.com/AWSMechTurk/2012-03-25/AWSMturkAPI/ApiReference_HITLayoutArticle.html
   */
  public HIT createHIT(String hitTypeId, String title, String description, String keywords, 
      Double reward, Long assignmentDurationInSeconds, Long autoApprovalDelayInSeconds, 
      Long lifetimeInSeconds, Integer maxAssignments, String requesterAnnotation, 
      QualificationRequirement[] qualificationRequirements, String[] responseGroup,
      String uniqueRequestToken, ReviewPolicy assignmentReviewPolicy, ReviewPolicy hitReviewPolicy,
      String hitLayoutId, HITLayoutParameter[] hitLayoutParameters)
    throws ServiceException {
    
    CreateHITRequest request = wrapHITParams(hitTypeId, title, description, keywords,
        null, reward, assignmentDurationInSeconds, autoApprovalDelayInSeconds,
        lifetimeInSeconds, maxAssignments, requesterAnnotation,
        qualificationRequirements, responseGroup, uniqueRequestToken,
        assignmentReviewPolicy, hitReviewPolicy, hitLayoutId, hitLayoutParameters);
    
    HIT result = null;
    result = (HIT) executeRequest(request, 
        ResultMatch.CreateHIT.name(),
        ResultMatch.CreateHIT.getResultTypeName());
    
    if (result == null) {
      throw new ServiceException("No response");
    }
    
    return result;
  }
  
  /**
   * @see http://docs.amazonwebservices.com/AWSMechTurk/2012-03-25/AWSMturkAPI/ApiReference_CreateHITOperation.html
   */
  public HIT createHIT(String hitTypeId, String title, String description, String keywords, 
      String question, Double reward, Long assignmentDurationInSeconds, Long autoApprovalDelayInSeconds, 
      Long lifetimeInSeconds, Integer maxAssignments, String requesterAnnotation, 
      QualificationRequirement[] qualificationRequirements, String[] responseGroup,
      String uniqueRequestToken, ReviewPolicy assignmentReviewPolicy, ReviewPolicy hitReviewPolicy)
    throws ServiceException {
    
    CreateHITRequest request = wrapHITParams(hitTypeId, title, description, keywords,
        question, reward, assignmentDurationInSeconds, autoApprovalDelayInSeconds,
        lifetimeInSeconds, maxAssignments, requesterAnnotation,
        qualificationRequirements, responseGroup, uniqueRequestToken,
        assignmentReviewPolicy, hitReviewPolicy, null, null);
    
    HIT result = null;
    result = (HIT) executeRequest(request, 
        ResultMatch.CreateHIT.name(),
        ResultMatch.CreateHIT.getResultTypeName());
    
    if (result == null) {
      throw new ServiceException("No response");
    }
    
    return result;
  } 
  
  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_RegisterHITTypeOperation.html
   */ 
  public String registerHITType(Long autoApprovalDelayInSeconds, Long assignmentDurationInSeconds,
      Double reward, String title, String keywords, String description,
      QualificationRequirement[] qualRequirements)
    throws ServiceException {

    RegisterHITTypeRequest request = new RegisterHITTypeRequest();
    if (title != null)        request.setTitle(title);
    if (description != null)  request.setDescription(description);
    if (keywords != null)     request.setKeywords(keywords);

    if (qualRequirements != null)           request.setQualificationRequirement(qualRequirements);
    if (assignmentDurationInSeconds != null)request.setAssignmentDurationInSeconds(assignmentDurationInSeconds);
    if (autoApprovalDelayInSeconds != null) request.setAutoApprovalDelayInSeconds(autoApprovalDelayInSeconds);

    if (reward != null) {
      Price p = new Price();
      p.setAmount(new BigDecimal(reward));
      p.setCurrencyCode("USD");
      request.setReward(p);
    }

    RegisterHITTypeResult result = null;
    result = (RegisterHITTypeResult) executeRequest(request, 
        ResultMatch.RegisterHITType.name(),
        ResultMatch.RegisterHITType.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result.getHITTypeId();
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-06-21/ApiReference_ChangeHITTypeOfHIT.html
   */
  public void changeHITTypeOfHIT(String hitId, String newHitTypeId) 
  throws ServiceException {
    
    ChangeHITTypeOfHITRequest request = new ChangeHITTypeOfHITRequest();
    request.setHITId(hitId);
    request.setHITTypeId(newHitTypeId);
    executeRequest(request,
        ResultMatch.ChangeHITTypeOfHIT.name(),
        ResultMatch.ChangeHITTypeOfHIT.getResultTypeName());
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_SetHITTypeNotificationOperation.html
   */ 
  public void setHITTypeNotification(String hitTypeId, NotificationSpecification notification, Boolean active)
    throws ServiceException { 

    SetHITTypeNotificationRequest request = new SetHITTypeNotificationRequest();
    if (hitTypeId != null)    request.setHITTypeId(hitTypeId);
    if (notification != null) request.setNotification(notification);
    if (active != null)       request.setActive(active);

    executeRequest(request, ResultMatch.SetHITTypeNotification.name(),
        ResultMatch.SetHITTypeNotification.getResultTypeName());
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_SendTestEventNotificationOperation.html
   */ 
  public void sendTestEventNotification(NotificationSpecification notification, EventType testEventType)
    throws ServiceException { 

    SendTestEventNotificationRequest request = new SendTestEventNotificationRequest();
    if (notification != null)   request.setNotification(notification);
    if (testEventType != null)  request.setTestEventType(testEventType);

    executeRequest(request, ResultMatch.SendTestNotification.name(),
        ResultMatch.SendTestNotification.getResultTypeName());
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_DisposeHITOperation.html
   */ 
  public void disposeHIT(String hitId) throws ServiceException { 

    DisposeHITRequest request = new DisposeHITRequest();
    if (hitId != null)  request.setHITId(hitId);

    executeRequest(request, ResultMatch.DisposeHIT.name(),
        ResultMatch.DisposeHIT.getResultTypeName());
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_DisableHITOperation.html
   */ 
  public void disableHIT(String hitId) throws ServiceException { 

    DisableHITRequest request = new DisableHITRequest();
    if (hitId != null)  request.setHITId(hitId);

    executeRequest(request, ResultMatch.DisableHIT.name(),
        ResultMatch.DisableHIT.getResultTypeName());
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_GetHITOperation.html
   */ 
  public HIT getHIT(String hitId, String[] responseGroup) throws ServiceException { 

    GetHITRequest request = new GetHITRequest();
    if (hitId != null)          request.setHITId(hitId);
    if (responseGroup != null)  request.setResponseGroup(responseGroup);

    HIT result = null;

    result = (HIT) executeRequest(request, ResultMatch.GetHIT.name(),
        ResultMatch.GetHIT.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result;
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_GetReviewableHITsOperation.html
   */ 
  public GetReviewableHITsResult getReviewableHITs(String hitTypeId, ReviewableHITStatus status, 
      SortDirection sortDirection, GetReviewableHITsSortProperty sortProperty, 
      Integer pageNumber, Integer pageSize)
    throws ServiceException { 

    GetReviewableHITsRequest request = new GetReviewableHITsRequest();
    if (hitTypeId != null)      request.setHITTypeId(hitTypeId);
    if (status != null)         request.setStatus(status);
    if (pageNumber != null)     request.setPageNumber(pageNumber);
    if (pageSize != null)       request.setPageSize(pageSize);
    if (sortDirection != null)  request.setSortDirection(sortDirection);
    if (sortProperty != null)   request.setSortProperty(sortProperty);

    GetReviewableHITsResult result = null;

    result = (GetReviewableHITsResult) executeRequest(request, 
        ResultMatch.GetReviewableHITs.name(),
        ResultMatch.GetReviewableHITs.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result;
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_GetHITsForQualificationTypeOperation.html
   */ 
  public GetHITsForQualificationTypeResult getHITsForQualificationType(String qualificationTypeId, 
      Integer pageNumber, Integer pageSize)
    throws ServiceException { 

    GetHITsForQualificationTypeRequest request = new GetHITsForQualificationTypeRequest();
    if (qualificationTypeId != null)  request.setQualificationTypeId(qualificationTypeId);
    if (pageNumber != null)     request.setPageNumber(pageNumber);
    if (pageSize != null)       request.setPageSize(pageSize);

    GetHITsForQualificationTypeResult result = null;

    result = (GetHITsForQualificationTypeResult) executeRequest(request, 
        ResultMatch.GetHITsForQualificationType.name(),
        ResultMatch.GetHITsForQualificationType.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result; 
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_GetQualificationsForQualificationTypeOperation.html
   */ 
  public GetQualificationsForQualificationTypeResult getQualificationsForQualificationType(String qualificationTypeId, 
      QualificationStatus status, Integer pageNumber, Integer pageSize)
    throws ServiceException { 

    GetQualificationsForQualificationTypeRequest request = new GetQualificationsForQualificationTypeRequest();
    if (status != null)     request.setStatus(status);
    if (pageNumber != null) request.setPageNumber(pageNumber);
    if (pageSize != null)   request.setPageSize(pageSize);
    if (qualificationTypeId != null)  request.setQualificationTypeId(qualificationTypeId);

    GetQualificationsForQualificationTypeResult result = null;

    result = (GetQualificationsForQualificationTypeResult) executeRequest(request, 
        ResultMatch.GetQualificationsForQualificationType.name(),
        ResultMatch.GetQualificationsForQualificationType.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result; 
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_SetHITAsReviewingOperation.html
   */ 
  public void setHITAsReviewing(String hitId, Boolean revert) 
    throws ServiceException { 

    SetHITAsReviewingRequest request = new SetHITAsReviewingRequest();
    if (hitId != null)  request.setHITId(hitId);
    if (revert != null) request.setRevert(revert);

    executeRequest(request, ResultMatch.SetHITAsReviewing.name(),
        ResultMatch.SetHITAsReviewing.getResultTypeName());
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_ExtendHITOperation.html
   */ 
  public void extendHIT(String hitId, Integer maxAssignmentsIncrement, Long expirationIncrementInSeconds) throws ServiceException { 

    ExtendHITRequest request = new ExtendHITRequest();
    if (hitId != null)                        request.setHITId(hitId);
    if (maxAssignmentsIncrement != null)      request.setMaxAssignmentsIncrement(maxAssignmentsIncrement);
    if (expirationIncrementInSeconds != null) request.setExpirationIncrementInSeconds(expirationIncrementInSeconds);

    executeRequest(request, ResultMatch.ExtendHIT.name(),
        ResultMatch.ExtendHIT.getResultTypeName());
  }
  
  /**
   * Extends a HIT asynchronously using the Axis worker thread pool.
   * It returns an AsyncReply object, which can either be used to
   * wait for the asynchronous call to complete and to get the result
   * of the call. Alternatively, a callback handler can be passed
   * that is notified when the call has completed.
   * 
   * The work queue is using a pool of daemon threads to process the submitted tasks.
   * To guarantee that all work submitted to the queue was processed before the JVM
   * exits, this requires to wait for all future results of the submitted work items.
   * This can conveniently be done using the getResult() method of the AsyncReply
   * object returned by this method. A typical usage pattern would be to first submit
   * all requests to the work queue, store the AsyncReply objects in an array and then
   * call getResult() for each of the objects in the array.
   *    
   */  
  public AsyncReply extendHITAsync(String hitId, Integer maxAssignmentsIncrement, 
      Long expirationIncrementInSeconds,
      AsyncCallback callback) throws ServiceException { 

    ExtendHITRequest request = new ExtendHITRequest();
    if (hitId != null)                        request.setHITId(hitId);
    if (maxAssignmentsIncrement != null)      request.setMaxAssignmentsIncrement(maxAssignmentsIncrement);
    if (expirationIncrementInSeconds != null) request.setExpirationIncrementInSeconds(expirationIncrementInSeconds);

    return executeAsyncRequest(request, 
        ResultMatch.ExtendHIT.name(),
        ResultMatch.ExtendHIT.getResultTypeName(),
        callback);
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_ForceExpireHITOperation.html
   */ 
  public void forceExpireHIT(String hitId) throws ServiceException { 

    ForceExpireHITRequest request = new ForceExpireHITRequest();
    if (hitId != null)  request.setHITId(hitId);

    executeRequest(request, ResultMatch.ForceExpireHIT.name(),
        ResultMatch.ForceExpireHIT.getResultTypeName());
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_ApproveAssignmentOperation.html
   */ 
  public void approveAssignment(String assignmentId, String requesterFeedback) 
    throws ServiceException { 

    ApproveAssignmentRequest request = new ApproveAssignmentRequest();
    if (assignmentId != null) request.setAssignmentId(assignmentId);

    if (requesterFeedback != null) {
      request.setRequesterFeedback(requesterFeedback);
    }

    executeRequest(request, ResultMatch.ApproveAssignment.name(),
        ResultMatch.ApproveAssignment.getResultTypeName());
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_RejectAssignmentOperation.html
   */ 
  public void rejectAssignment(String assignmentId, String requesterFeedback) 
    throws ServiceException { 

    RejectAssignmentRequest request = new RejectAssignmentRequest();
    if (assignmentId != null) request.setAssignmentId(assignmentId);

    if (requesterFeedback != null) {
      request.setRequesterFeedback(requesterFeedback);
    }

    executeRequest(request, ResultMatch.RejectAssignment.name(),
        ResultMatch.RejectAssignment.getResultTypeName());
  }
  
  /**
   * @see http://docs.amazonwebservices.com/AWSMechTurk/2012-03-25/AWSMturkAPI/ApiReference_ApproveRejectedAssignmentOperation.html
   */
  public void approveRejectedAssignment(String assignmentId, String requesterFeedback) {
    ApproveRejectedAssignmentRequest request = new ApproveRejectedAssignmentRequest();
    if (assignmentId != null) request.setAssignmentId(assignmentId);
    if (requesterFeedback != null) request.setRequesterFeedback(requesterFeedback);
    
    executeRequest(request, ResultMatch.ApproveRejectedAssignment.name(),
        ResultMatch.ApproveRejectedAssignment.getResultTypeName());
  }
  
  /**
   * @see http://docs.amazonwebservices.com/AWSMechTurk/2012-03-25/AWSMturkAPI/ApiReference_GetAssignmentOperation.html
   */
  public GetAssignmentResult getAssignment(String assignmentId) {
    GetAssignmentRequest request = new GetAssignmentRequest();
    if (assignmentId != null) request.setAssignmentId(assignmentId);
    
    GetAssignmentResult result = (GetAssignmentResult) executeRequest(
        request, ResultMatch.GetAssignment.name(),
        ResultMatch.GetAssignment.getResultTypeName());
    
    if (result == null) {
      throw new ServiceException("No response");
    }
    return result; 
  }
  
  private GetAssignmentsForHITRequest wrapAssignmentParams(String hitId, SortDirection sortDirection, AssignmentStatus[] status, 
		  GetAssignmentsForHITSortProperty sortProperty, Integer pageNumber, Integer pageSize, String[] responseGroup) {
	  GetAssignmentsForHITRequest request = new GetAssignmentsForHITRequest();
	  if (hitId != null)          request.setHITId(hitId);
	  if (status != null)         request.setAssignmentStatus(status);
	  if (pageNumber != null)     request.setPageNumber(pageNumber);
	  if (pageSize != null)       request.setPageSize(pageSize);
	  if (sortDirection != null)  request.setSortDirection(sortDirection);
	  if (sortProperty != null)   request.setSortProperty(sortProperty);
	  if (responseGroup != null)  request.setResponseGroup(responseGroup);

	  return request;
  }
  
  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_GetAssignmentsForHITOperation.html
   */ 
  public GetAssignmentsForHITResult getAssignmentsForHIT(String hitId, SortDirection sortDirection, AssignmentStatus[] status, 
      GetAssignmentsForHITSortProperty sortProperty, Integer pageNumber, Integer pageSize, String[] responseGroup)
    throws ServiceException { 
	  
    GetAssignmentsForHITRequest request = wrapAssignmentParams(hitId, sortDirection, status, 
    	      sortProperty, pageNumber, pageSize, responseGroup);

    GetAssignmentsForHITResult result = null;

    result = (GetAssignmentsForHITResult) executeRequest(request, 
        ResultMatch.GetAssignmentsForHIT.name(),
        ResultMatch.GetAssignmentsForHIT.getResultTypeName());    

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result; 
  }
  
  public GetReviewResultsForHITResult getReviewResultsForHIT(String HITId,
      ReviewPolicyLevel[] policyLevel, Boolean retrieveActions, Boolean retrieveResults,
      Integer pageNumber, Integer pageSize, String[] responseGroup)
    throws ServiceException {
    
    GetReviewResultsForHITRequest request = new GetReviewResultsForHITRequest();
    if (HITId != null) request.setHITId(HITId);
    if (policyLevel != null) request.setPolicyLevel(policyLevel);
    if (retrieveActions != null) request.setRetrieveActions(retrieveActions);
    if (retrieveResults != null) request.setRetrieveResults(retrieveResults);
    if (pageNumber != null) request.setPageNumber(pageNumber);
    if (pageSize != null) request.setPageSize(pageSize);
    if (responseGroup != null) request.setResponseGroup(responseGroup);
    
    GetReviewResultsForHITResult result = null;
    result = (GetReviewResultsForHITResult) executeRequest(request,
        ResultMatch.GetReviewResultsForHIT.name(),
        ResultMatch.GetReviewResultsForHIT.getResultTypeName());
    if (result == null) {
      throw new ServiceException("No response");
    }
    
    return result;
  }
  
  public DataPoint[] getRequesterWorkerStatistic(
      RequesterStatistic statistic, TimePeriod timePeriod,
      String workerId, Integer count, String[] responseGroup)
  throws ServiceException {
    
    GetRequesterWorkerStatisticRequest request = new GetRequesterWorkerStatisticRequest();
    if (statistic != null) request.setStatistic(statistic);
    if (timePeriod != null) request.setTimePeriod(timePeriod);
    if (workerId != null) request.setWorkerId(workerId);
    if (count != null) request.setCount(count);
    if (responseGroup != null) request.setResponseGroup(responseGroup);
    
    GetStatisticResult result = null;
    result = (GetStatisticResult) executeRequest(request,
        ResultMatch.GetRequesterWorkerStatistic.name(),
        ResultMatch.GetRequesterWorkerStatistic.getResultTypeName());
    if (result == null) {
      throw new ServiceException("No response");
    }
    return result.getDataPoint();
  }
  
  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_GetFileUploadURLOperation.html
   */ 
  public String getFileUploadURL(String assignmentId, String questionIdentifier) 
    throws ServiceException { 

    GetFileUploadURLRequest request = new GetFileUploadURLRequest();
    if (assignmentId != null)       request.setAssignmentId(assignmentId);
    if (questionIdentifier != null) request.setQuestionIdentifier(questionIdentifier);

    GetFileUploadURLResult result = null;

    result = (GetFileUploadURLResult) executeRequest(request, 
        ResultMatch.GetFileUploadURL.name(),
        ResultMatch.GetFileUploadURL.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result.getFileUploadURL();
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_SearchHITsOperation.html
   */ 
  public SearchHITsResult searchHITs(SortDirection sortDirection, SearchHITsSortProperty sortProperty, 
      Integer pageNumber, Integer pageSize, String[] responseGroup)
    throws ServiceException { 

    SearchHITsRequest request = new SearchHITsRequest();

    if (pageNumber != null)     request.setPageNumber(pageNumber);
    if (pageSize != null)       request.setPageSize(pageSize);
    if (sortDirection != null)  request.setSortDirection(sortDirection);
    if (sortProperty != null)   request.setSortProperty(sortProperty);
    if (responseGroup != null)  request.setResponseGroup(responseGroup);

    SearchHITsResult result = null;

    result = (SearchHITsResult) executeRequest(request, 
        ResultMatch.SearchHITs.name(),
        ResultMatch.SearchHITs.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result;
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_GrantBonusOperation.html
   */ 
  public void grantBonus(String workerId, Double bonusAmount, String assignmentId, 
      String reason) throws ServiceException { 

    GrantBonusRequest request = new GrantBonusRequest();
    if (assignmentId != null) request.setAssignmentId(assignmentId);
    if (reason != null)       request.setReason(reason);
    if (workerId != null)     request.setWorkerId(workerId);

    if (bonusAmount != null) {
      Price p = new Price();
      p.setAmount(new BigDecimal(bonusAmount));
      p.setCurrencyCode("USD");
      request.setBonusAmount(p);
    }

    executeRequest(request, ResultMatch.GrantBonus.name(),
        ResultMatch.GrantBonus.getResultTypeName());
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_GetBonusPaymentsOperation.html
   */ 
  public GetBonusPaymentsResult getBonusPayments(String hitId, String assignmentId, 
      Integer pageNumber, Integer pageSize) 
    throws ServiceException { 

    GetBonusPaymentsRequest request = new GetBonusPaymentsRequest();
    if (assignmentId != null)   request.setAssignmentId(assignmentId);
    if (hitId != null)          request.setHITId(hitId);
    if (pageNumber != null)     request.setPageNumber(pageNumber);
    if (pageSize != null)       request.setPageSize(pageSize);

    GetBonusPaymentsResult result = null;

    result = (GetBonusPaymentsResult) executeRequest(request, 
        ResultMatch.GetBonusPayments.name(),
        ResultMatch.GetBonusPayments.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result;
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_CreateQualificationTypeOperation.html
   */ 
  public QualificationType createQualificationType(String name, String keywords, String description,
      QualificationTypeStatus status, Long retryDelayInSeconds, String test, String answerKey,
      Long testDurationInSeconds, Boolean autoGranted, Integer autoGrantedValue) 
    throws ServiceException { 

    CreateQualificationTypeRequest request = new CreateQualificationTypeRequest();
    if (name != null)                 request.setName(name);
    if (answerKey != null)            request.setAnswerKey(answerKey);
    if (autoGranted != null)          request.setAutoGranted(autoGranted);
    if (autoGrantedValue != null)     request.setAutoGrantedValue(autoGrantedValue);
    if (description != null)          request.setDescription(description);
    if (keywords != null)             request.setKeywords(keywords);
    if (status != null)               request.setQualificationTypeStatus(status);
    if (test != null)                 request.setTest(test);
    if (retryDelayInSeconds != null)  request.setRetryDelayInSeconds(retryDelayInSeconds);
    if (testDurationInSeconds != null)request.setTestDurationInSeconds(testDurationInSeconds);

    QualificationType result = null;

    result = (QualificationType) executeRequest(request, 
        ResultMatch.CreateQualificationType.name(),
        ResultMatch.CreateQualificationType.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result;
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_GetQualificationRequestsOperation.html
   */ 
  public GetQualificationRequestsResult getQualificationRequests(String qualificationTypeId, SortDirection sortDirection, 
      GetQualificationRequestsSortProperty sortProperty, Integer pageNumber, Integer pageSize)
    throws ServiceException { 

    GetQualificationRequestsRequest request = new GetQualificationRequestsRequest();

    if (qualificationTypeId != null)  request.setQualificationTypeId(qualificationTypeId);
    if (pageNumber != null)           request.setPageNumber(pageNumber);
    if (pageSize != null)             request.setPageSize(pageSize);
    if (sortDirection != null)        request.setSortDirection(sortDirection);
    if (sortProperty != null)         request.setSortProperty(sortProperty);

    GetQualificationRequestsResult result = null;

    result = (GetQualificationRequestsResult) executeRequest(request, 
        ResultMatch.GetQualificationRequests.name(),
        ResultMatch.GetQualificationRequests.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result;
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_RejectQualificationRequestOperation.html
   */ 
  public void rejectQualificationRequest(String qualificationRequestId, String reason) 
    throws ServiceException { 

    RejectQualificationRequestRequest request = new RejectQualificationRequestRequest();
    if (reason != null) request.setReason(reason);
    if (qualificationRequestId != null) 
      request.setQualificationRequestId(qualificationRequestId);

    executeRequest(request, ResultMatch.RejectQualificationRequest.name(),
        ResultMatch.RejectQualificationRequest.getResultTypeName());
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_GrantQualificationOperation.html
   */ 
  public void grantQualification(String qualificationRequestId, Integer integerValue) throws ServiceException { 

    GrantQualificationRequest request = new GrantQualificationRequest();
    if (integerValue != null) request.setIntegerValue(integerValue);
    if (qualificationRequestId != null)
      request.setQualificationRequestId(qualificationRequestId);

    try {
      executeRequest(request, ResultMatch.GrantQualification.name(),
          ResultMatch.GrantQualification.getResultTypeName());
    } catch (ObjectDoesNotExistException e) {
      throw e;
    } catch (Exception e) {
      throw new ServiceException("Could not execute request: " + e.getLocalizedMessage());
    }
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_AssignQualificationOperation.html
   */ 
  public void assignQualification(String qualificationTypeId, String workerId, Integer integerValue, 
      Boolean sendNotification) throws ServiceException { 

    AssignQualificationRequest request = new AssignQualificationRequest();
    if (workerId != null)     request.setWorkerId(workerId);
    if (integerValue != null) request.setIntegerValue(integerValue);
    if (qualificationTypeId != null)  request.setQualificationTypeId(qualificationTypeId);
    if (sendNotification != null) request.setSendNotification(sendNotification);
    
    executeRequest(request, ResultMatch.AssignQualification.name(),
        ResultMatch.AssignQualification.getResultTypeName());
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_RevokeQualificationOperation.html
   */ 
  public void revokeQualification(String qualificationTypeId, String subjectId, 
      String reason) throws ServiceException { 

    RevokeQualificationRequest request = new RevokeQualificationRequest();
    if (qualificationTypeId != null)  request.setQualificationTypeId(qualificationTypeId);
    if (reason != null)               request.setReason(reason);
    if (subjectId != null)            request.setSubjectId(subjectId);

    executeRequest(request, ResultMatch.RevokeQualification.name(),
        ResultMatch.RevokeQualification.getResultTypeName());
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_UpdateQualificationScoreOperation.html
   */ 
  public void updateQualificationScore(String qualificationTypeId, String subjectId, 
      Integer integerValue) throws ServiceException { 

    UpdateQualificationScoreRequest request = new UpdateQualificationScoreRequest();
    if (qualificationTypeId != null)  request.setQualificationTypeId(qualificationTypeId);
    if (integerValue != null)         request.setIntegerValue(integerValue);
    if (subjectId != null)            request.setSubjectId(subjectId);

    executeRequest(request, ResultMatch.UpdateQualificationScore.name(),
        ResultMatch.UpdateQualificationScore.getResultTypeName());
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_GetQualificationTypeOperation.html
   */ 
  public QualificationType getQualificationType(String qualificationTypeId) throws ServiceException { 

    GetQualificationTypeRequest request = new GetQualificationTypeRequest();
    if (qualificationTypeId != null)  request.setQualificationTypeId(qualificationTypeId);

    QualificationType result = null;

    result = (QualificationType) executeRequest(request, 
        ResultMatch.GetQualificationType.name(),
        ResultMatch.GetQualificationType.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result;
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_GetQualificationScoreOperation.html
   */ 
  public Qualification getQualificationScore(String qualificationTypeId, String subjectId) 
    throws ServiceException { 

    GetQualificationScoreRequest request = new GetQualificationScoreRequest();
    if (qualificationTypeId != null)  request.setQualificationTypeId(qualificationTypeId);
    if (subjectId != null)            request.setSubjectId(subjectId);

    Qualification result = null;
    result = (Qualification) executeRequest(request, 
        ResultMatch.GetQualificationScore.name(),
        ResultMatch.GetQualificationScore.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result;
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_SearchQualificationTypesOperation.html
   */ 
  public SearchQualificationTypesResult searchQualificationTypes(String query, Boolean mustBeRequestable,
      Boolean mustBeOwnedByCaller, SortDirection sortDirection, SearchQualificationTypesSortProperty sortProperty, 
      Integer pageNumber, Integer pageSize)
    throws ServiceException { 

    SearchQualificationTypesRequest request = new SearchQualificationTypesRequest();
    if (query != null)          request.setQuery(query);
    if (pageNumber != null)     request.setPageNumber(pageNumber);
    if (pageSize != null)       request.setPageSize(pageSize);
    if (sortDirection != null)  request.setSortDirection(sortDirection);
    if (sortProperty != null)   request.setSortProperty(sortProperty);
    if (mustBeOwnedByCaller != null)  request.setMustBeOwnedByCaller(mustBeOwnedByCaller);
    if (mustBeRequestable != null)    request.setMustBeRequestable(mustBeRequestable);

    SearchQualificationTypesResult result = null;

    result = (SearchQualificationTypesResult) executeRequest(request, 
        ResultMatch.SearchQualificationTypes.name(),
        ResultMatch.SearchQualificationTypes.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result; 
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_UpdateQualificationTypeOperation.html
   */ 
  public QualificationType updateQualificationType(String qualificationTypeId, String description, 
      QualificationTypeStatus status, String test, String answerKey, Long testDurationInSeconds,
      Long retryDelayInSeconds, Boolean autoGranted, Integer autoGrantedValue)
    throws ServiceException { 

    UpdateQualificationTypeRequest request = new UpdateQualificationTypeRequest();
    if (answerKey != null)        request.setAnswerKey(answerKey);
    if (autoGranted != null)      request.setAutoGranted(autoGranted);
    if (autoGrantedValue != null) request.setAutoGrantedValue(autoGrantedValue);
    if (description != null)      request.setDescription(description);
    if (status != null)           request.setQualificationTypeStatus(status);
    if (test != null)             request.setTest(test);
    if (testDurationInSeconds != null)  request.setTestDurationInSeconds(testDurationInSeconds);
    if (retryDelayInSeconds != null)    request.setRetryDelayInSeconds(retryDelayInSeconds);
    if (qualificationTypeId != null)    request.setQualificationTypeId(qualificationTypeId);

    QualificationType result = null;

    result = (QualificationType) executeRequest(request, 
        ResultMatch.UpdateQualificationType.name(),
        ResultMatch.UpdateQualificationType.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result; 
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_GetAccountBalanceOperation.html
   */ 
  public GetAccountBalanceResult getAccountBalance(String unused) throws ServiceException { 

    GetAccountBalanceRequest request = new GetAccountBalanceRequest();
    if (unused != null) request.setUnused(unused);

    GetAccountBalanceResult result = null;

    result = (GetAccountBalanceResult) executeRequest(request, 
        ResultMatch.GetAccountBalance.name(),
        ResultMatch.GetAccountBalance.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result;
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_GetRequesterStatisticOperation.html
   */ 
  public DataPoint[] getRequesterStatistic(RequesterStatistic statistic, TimePeriod timePeriod, 
      Integer count) throws ServiceException { 

    GetRequesterStatisticRequest request = new GetRequesterStatisticRequest();
    if (count != null)      request.setCount(count);
    if (statistic != null)  request.setStatistic(statistic);
    if (timePeriod != null) request.setTimePeriod(timePeriod);

    GetStatisticResult result = null;
    result = (GetStatisticResult) executeRequest(request, 
        ResultMatch.GetRequesterStatistic.name(),
        ResultMatch.GetRequesterStatistic.getResultTypeName());

    if (result == null || result.getDataPoint() == null) {
      throw new ServiceException("No response");
    }

    return result.getDataPoint();
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_NotifyWorkersOperation.html
   */ 
  public void notifyWorkers(String subject, String messageText, String[] workerId) 
    throws ServiceException { 

    NotifyWorkersRequest request = new NotifyWorkersRequest();
    if (messageText != null)  request.setMessageText(messageText);
    if (subject != null)      request.setSubject(subject);
    if (workerId != null)     request.setWorkerId(workerId);

    executeRequest(request, ResultMatch.NotifyWorkers.name(),
        ResultMatch.NotifyWorkers.getResultTypeName());
  }

  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-06-21/ApiReference_BlockWorkerOperation.html
   */ 
  public void blockWorker( String workerId, String reason ) {
      BlockWorkerRequest request = new BlockWorkerRequest();
      if (workerId != null) request.setWorkerId( workerId );
      if (reason != null) request.setReason( reason );
      
      executeRequest( request, ResultMatch.BlockWorker.name(), ResultMatch.BlockWorker.getResultTypeName());
  }
  
  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-06-21/ApiReference_UnblockWorkerOperation.html
   */ 
  public void unblockWorker( String workerId, String reason ) {
      UnblockWorkerRequest request = new UnblockWorkerRequest();
      if (workerId != null) request.setWorkerId( workerId );
      if (reason != null) request.setReason( reason );
      
      executeRequest( request, ResultMatch.UnblockWorker.name(), ResultMatch.UnblockWorker.getResultTypeName());
  }
  
  /**
   * @see http://docs.amazonwebservices.com/AWSMechTurk/2012-03-25/AWSMturkAPI/ApiReference_GetBlockedWorkersOperation.html
   */
  public GetBlockedWorkersResult getBlockedWorkers (int pageNumber, int pageSize) {
    GetBlockedWorkersRequest request = new GetBlockedWorkersRequest();
    request.setPageNumber(pageNumber);
    request.setPageSize(pageSize);
    
    GetBlockedWorkersResult result = (GetBlockedWorkersResult) executeRequest(request,
        ResultMatch.GetBlockedWorkers.name(),
        ResultMatch.GetBlockedWorkers.getResultTypeName());
    
    if (result == null) {
      throw new ServiceException("No response");
    }
    return result;
  }
  
  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_GetWorkerAcceptLimitOperation.html
   */ 
  public Information help(String[] about, HelpRequestHelpType type) throws ServiceException { 
    HelpRequest request = new HelpRequest();
    if (about != null) request.setAbout(about);
    if (type != null) request.setHelpType(type);

    Information result = null;

    result = (Information) executeRequest(request, 
        ResultMatch.Help.name(),
        ResultMatch.Help.getResultTypeName());

    if (result == null) {
      throw new ServiceException("No response");
    }

    return result;
  }

  /**
   * Creates a HIT asynchronously using the Axis worker thread pool.
   * It returns an AsyncReply object, which can either be used to
   * wait for the asynchronous call to complete and to get the result
   * of the call. Alternatively, a callback handler can be passed
   * that is notified when the call has completed.
   * 
   * The work queue is using a pool of daemon threads to process the submitted tasks.
   * To guarantee that all work submitted to the queue was processed before the JVM
   * exits, this requires to wait for all future results of the submitted work items.
   * This can conveniently be done using the getResult() method of the AsyncReply
   * object returned by this method. A typical usage pattern would be to first submit
   * all requests to the work queue, store the AsyncReply objects in an array and then
   * call getResult() for each of the objects in the array.
   *    
   * @throws ServiceException
   */
  public AsyncReply createHITAsync(String hitTypeId, String title, String description, String keywords, 
      String question, Double reward, Long assignmentDurationInSeconds, Long autoApprovalDelayInSeconds, 
      Long lifetimeInSeconds, Integer maxAssignments, String requesterAnnotation, 
      QualificationRequirement[] qualificationRequirements, String[] responseGroup,
      String uniqueRequestToken, ReviewPolicy assignmentReviewPolicy, ReviewPolicy hitReviewPolicy, AsyncCallback callback) {
    
    CreateHITRequest request = wrapHITParams(hitTypeId, title, description, keywords, 
        question, reward, assignmentDurationInSeconds, autoApprovalDelayInSeconds, 
        lifetimeInSeconds, maxAssignments, requesterAnnotation, 
        qualificationRequirements, responseGroup, uniqueRequestToken,
        assignmentReviewPolicy, hitReviewPolicy, null, null);
    
    return executeAsyncRequest(request,
        ResultMatch.CreateHIT.name(),
        ResultMatch.CreateHIT.getResultTypeName(),
        callback);
  }
  
  /**
   * Support for creating HITs using HIT layouts
   * @see http://docs.amazonwebservices.com/AWSMechTurk/2012-03-25/AWSMturkAPI/ApiReference_HITLayoutArticle.html
   */
  public AsyncReply createHITAsync(String hitTypeId, String title, String description, String keywords,
      Double reward, Long assignmentDurationInSeconds, Long autoApprovalDelayInSeconds,
      Long lifetimeInSeconds, Integer maxAssignments, String requesterAnnotation,
      QualificationRequirement[] qualificationRequirements, String[] responseGroup,
      String uniqueRequestToken, ReviewPolicy assignmentReviewPolicy, ReviewPolicy hitReviewPolicy,
      String hitLayoutId, HITLayoutParameter[] hitLayoutParameters, AsyncCallback callback) {
    
    CreateHITRequest request = wrapHITParams(hitTypeId, title, description, keywords, 
        null, reward, assignmentDurationInSeconds, autoApprovalDelayInSeconds, 
        lifetimeInSeconds, maxAssignments, requesterAnnotation, 
        qualificationRequirements, responseGroup, uniqueRequestToken,
        assignmentReviewPolicy, hitReviewPolicy, hitLayoutId, hitLayoutParameters);
    
    return executeAsyncRequest(request,
        ResultMatch.CreateHIT.name(),
        ResultMatch.CreateHIT.getResultTypeName(),
        callback);
  }
  
  /**
   * Loads a HIT asynchronously using the Axis worker thread pool.
   * It returns an AsyncReply object, which can either be used to
   * wait for the asynchronous call to complete and to get the result
   * of the call. Alternatively, a callback handler can be passed
   * that is notified when the call has completed.
   * 
   * The work queue is using a pool of daemon threads to process the submitted tasks.
   * To guarantee that all work submitted to the queue was processed before the JVM
   * exits, this requires to wait for all future results of the submitted work items.
   * This can conveniently be done using the getResult() method of the AsyncReply
   * object returned by this method. A typical usage pattern would be to first submit
   * all requests to the work queue, store the AsyncReply objects in an array and then
   * call getResult() for each of the objects in the array.
   */
  public AsyncReply getHITAsync(String hitId, String[] responseGroup, 
      AsyncCallback callback) throws ServiceException { 

    GetHITRequest request = new GetHITRequest();
    if (hitId != null)          request.setHITId(hitId);
    if (responseGroup != null)  request.setResponseGroup(responseGroup);

    return executeAsyncRequest(request, 
        ResultMatch.GetHIT.name(),
        ResultMatch.GetHIT.getResultTypeName(),
        callback);
  }  

  /**
   * Updates the hit type of a HIT asynchronously using the Axis worker thread pool.
   * It returns an AsyncReply object, which can either be used to
   * wait for the asynchronous call to complete and to get the result
   * of the call. Alternatively, a callback handler can be passed
   * that is notified when the call has completed.   
   * 
   * The work queue is using a pool of daemon threads to process the submitted tasks.
   * To guarantee that all work submitted to the queue was processed before the JVM
   * exits, this requires to wait for all future results of the submitted work items.
   * This can conveniently be done using the getResult() method of the AsyncReply
   * object returned by this method. A typical usage pattern would be to first submit
   * all requests to the work queue, store the AsyncReply objects in an array and then
   * call getResult() for each of the objects in the array.
   */
  public AsyncReply changeHITTypeOfHITAsync(String hitId, String newHITTypeId,
      AsyncCallback callback) {

    ChangeHITTypeOfHITRequest request = new ChangeHITTypeOfHITRequest();
    request.setHITId(hitId);
    request.setHITTypeId(newHITTypeId);

    return executeAsyncRequest(request,
        ResultMatch.ChangeHITTypeOfHIT.name(),
        ResultMatch.ChangeHITTypeOfHIT.getResultTypeName(),
        callback);
  }  
  
  /**
   * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2007-03-12/ApiReference_ApproveAssignmentOperation.html
   */ 
  public AsyncReply approveAssignmentAsync(String assignmentId, String requesterFeedback, 
      AsyncCallback callback) 
    throws ServiceException { 

    ApproveAssignmentRequest request = new ApproveAssignmentRequest();
    if (assignmentId != null) request.setAssignmentId(assignmentId);

    if (requesterFeedback != null) {
      request.setRequesterFeedback(requesterFeedback);
    }

    return executeAsyncRequest(request, 
        ResultMatch.ApproveAssignment.name(),
        ResultMatch.ApproveAssignment.getResultTypeName(),
        callback);
  }  

  /**
   * Loads an assignments page for a HIT asynchronously using the Axis worker thread pool.
   * It returns an AsyncReply object, which can either be used to
   * wait for the asynchronous call to complete and to get the result
   * of the call. Alternatively, a callback handler can be passed
   * that is notified when the call has completed.
   * 
   * The work queue is using a pool of daemon threads to process the submitted tasks.
   * To guarantee that all work submitted to the queue was processed before the JVM
   * exits, this requires to wait for all future results of the submitted work items.
   * This can conveniently be done using the getResult() method of the AsyncReply
   * object returned by this method. A typical usage pattern would be to first submit
   * all requests to the work queue, store the AsyncReply objects in an array and then
   * call getResult() for each of the objects in the array.
   */  
  public AsyncReply getAssignmentsForHITAsync(String hitId, SortDirection sortDirection, AssignmentStatus[] status, 
      GetAssignmentsForHITSortProperty sortProperty, Integer pageNumber, Integer pageSize, String[] responseGroup,
      AsyncCallback callback) throws ServiceException { 

    GetAssignmentsForHITRequest request = wrapAssignmentParams(hitId, sortDirection, status, 
        sortProperty, pageNumber, pageSize, responseGroup);

    return executeAsyncRequest(request, 
        ResultMatch.GetAssignmentsForHIT.name(),
        ResultMatch.GetAssignmentsForHIT.getResultTypeName(),
        callback); 
  }

  /**
   * Loads all assignment pages for a HIT using the Axis worker thread pool.
   */    
  public Assignment[] getAssignmentsForHITAsync(String hitId, SortDirection sortDirection, AssignmentStatus[] status, 
      GetAssignmentsForHITSortProperty sortProperty, Integer pageSize, String[] responseGroup,
      GetAssignmentsForHITResult firstPage,
      AsyncCallback callback) throws ServiceException { 

    Assignment[] ret = new Assignment[] {};
    GetAssignmentsForHITResult result=null;

    if (firstPage==null) {
      // get first page to find how many assignments there are
      AsyncReply first = getAssignmentsForHITAsync(hitId, sortDirection, status, sortProperty,
          1,
          pageSize, responseGroup, callback);

      result = ((GetAssignmentsForHITResult[])first.getResult())[0];
      if (result.getAssignment() != null) {
        ret = result.getAssignment();	  
      }
    }
    else {
      result = firstPage;
    }

    // check size and total size and create subsequent requests if necessary
    if (ret.length == pageSize && result.getTotalNumResults() > pageSize) {
      // there are more results
      List<Assignment> results = new ArrayList<Assignment>();
      Collections.addAll(results, ret);

      int numPages = result.getTotalNumResults()/pageSize;

      AsyncReply[] replies = new AsyncReply[numPages];
      for (int i=0; i<numPages; i++) {
        replies[i] = getAssignmentsForHITAsync(hitId, sortDirection, status, sortProperty,
            i+1,
            pageSize, responseGroup, callback);
      }

      // append results
      for (int i=0; i<numPages; i++) {
        result = ((GetAssignmentsForHITResult[])replies[i].getResult())[0];
        if (result.getAssignment() != null) {
          Collections.addAll(results, result.getAssignment());
        }
      }

      ret = (Assignment[]) results.toArray(new Assignment[results.size()]);
    }

    return ret;
  }   
  
  /**
   * Disposes a HIT asynchronously
   */ 
  public AsyncReply disposeHITAsync(String hitId, AsyncCallback callback) 
    throws ServiceException { 
    
    DisposeHITRequest request = new DisposeHITRequest();
    if (hitId != null)  request.setHITId(hitId);

    return executeAsyncRequest(request, 
        ResultMatch.DisposeHIT.name(),
        ResultMatch.DisposeHIT.getResultTypeName(),
        callback);
  }  
}

