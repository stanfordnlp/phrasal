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

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.amazonaws.mturk.filter.Filter;
import com.amazonaws.mturk.filter.FinalFilter;
import com.amazonaws.mturk.filter.Message;
import com.amazonaws.mturk.service.exception.ServiceException;
import com.amazonaws.mturk.util.ClientConfig;

/**
 * This maintains the list of Filters which processes operation requests and replies 
 * It has methods to add, remove and clear filters. Also handles execution of the filters 
 *
 */
public abstract class FilteredAWSService extends AWSService {
    
    /**
     * 
     * @param filterList - List of Filters that should be executed.
     * Also appends the FinalFilter, which makes the wsdl call, to the List
     */
    public FilteredAWSService(ClientConfig config, List<Filter> filterList) {
        super(config);
        this.filterList = new LinkedList<Filter>(filterList);
        this.filterList.addLast(new FinalFilter(this));
        Filter.linkFilters(this.filterList);
    }
    
    public FilteredAWSService(ClientConfig config) {
        this(config, Collections.EMPTY_LIST);
    }
    
    private LinkedList<Filter> filterList;


    /**
     * 
     * @return - List of filters
     */public List<Filter> getFilterList() {
        return filterList;
    }
       
    /**
     * 
     * @param filter - Adds the Filter implementaion to head of the List of Filters
     */
    public void addFilter(Filter filter) {
        this.filterList.addFirst(filter);
        Filter.linkFilters(this.filterList);
    }

    /**
     * 
     * @param filter - Removes the Filter from List of Filters, if it is remavable and found in the list
     * Throws RuntimeException if filter not found in list or not removable
     */
    public void removeFilter(Filter filter) {
        if (!filter.isRemovable()) 
            throw new RuntimeException("Filter cannot be removed" + filter);
        
        if (!this.filterList.remove(filter))
            throw new RuntimeException("Filter not found in list" + filter);
        
        Filter.linkFilters(this.filterList);
    }
    
    /**
     * Clears all filters which are removable from the List of Filters
     *
     */
    public void clearFilters() {
        for (Iterator<Filter> iterator = filterList.iterator(); iterator.hasNext();) {
            Filter filter = iterator.next();
            if (filter.isRemovable()) {
                iterator.remove();
            }
        }
        Filter.linkFilters(this.filterList);
    }
    
    
    /**
     * Sends an AWS request to the service and returns results.
     * 
     * @param request
     * @param axisMethodName
     * @param resultAccessorName
     * @return Result
     * @throws ServiceException
     */
    protected Object executeRequest(Object request, String axisMethodName,
        String resultAccessorName)
      throws ServiceException {

      return executeRequest(request, axisMethodName, resultAccessorName, null);
    }
    
    /**
     * Sets the common parameters, sends an array of AWS requests to the service, 
     * and returns results.
     * 
     * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2006-10-31/ApiReference_CommonParametersArticle.html
     * @param requests
     * @param axisMethodName
     * @param resultAccessorName
     * @return Result
     * @throws ServiceException
     */
    protected Object [] executeRequests(Object requests, String axisMethodName,
        String resultAccessorName)
      throws ServiceException {
      return executeRequests(requests, axisMethodName,resultAccessorName, null);
    }
    
    /**
     * Sends an AWS request to the service and returns results.
     * 
     * @param request
     * @param axisMethodName
     * @param resultAccessorName
     * @param credential
     * @return Result
     * @throws ServiceException
     */
    protected Object executeRequest(Object request, String axisMethodName,
        String resultAccessorName, String credential)
      throws ServiceException {
      Object requestArray = Array.newInstance(request.getClass(), 1);
      Array.set(requestArray, 0, request);

      Object[] results = executeRequests(requestArray, axisMethodName,
          resultAccessorName, credential);
      return results[0];
    }
    
    /**
     * Sets the common parameters, sends an array of AWS requests to the service, 
     * and returns results.
     * 
     * @see http://docs.amazonwebservices.com/AWSMechanicalTurkRequester/2006-10-31/ApiReference_CommonParametersArticle.html
     * @param requests
     * @param axisMethodName
     * @param resultAccessorName
     * @param credential
     * @return Result
     * @throws ServiceException
     */
    protected Object [] executeRequests(Object requests, String axisMethodName,
            String resultAccessorName, String credential) {
        Message message = new Message(requests, axisMethodName, resultAccessorName, credential); 
        return this.filterList.getFirst().execute(message).getResults();
    }
    
    /**
     * Enqueues the request in the Axis worker queue for asynchronous sending.
     * The result can be obtained through the future contained in the AsyncReply
     *       
     * The work queue is using a pool of daemon threads to process the submitted tasks.
     * To guarantee that all work submitted to the queue was processed before the JVM
     * exits, this requires to wait for all future results of the submitted work items.
     * This can conveniently be done using the getResult() method of the AsyncReply
     * object returned by this method. A typical usage pattern would be to first submit
     * all requests to the work queue, store the AsyncReply objects in an array and then
     * call getResult() for each of the objects in the array.
     * 
     * @param request
     * @param axisMethodName
     * @param resultAccessorName
     * @param callback	Callback interface to invoke when the request has been processed (optional)
     * @return
     */
    protected AsyncReply executeAsyncRequest(Object request, String axisMethodName,
            String resultAccessorName, AsyncCallback callback) {
        
    	Object requestArray = Array.newInstance(request.getClass(), 1);
        Array.set(requestArray, 0, request);
        
        return executeAsyncRequests(requestArray, axisMethodName, 
        		resultAccessorName, callback);
    }           
    
    /**
     * Enqueues the request array in the Axis work queue for asynchronous sending.
     * The result can be obtained through the future contained in the AsyncReply
     * 
     * The work queue is using a pool of daemon threads to process the submitted tasks.
     * To guarantee that all work submitted to the queue was processed before the JVM
     * exits, this requires to wait for all future results of the submitted work items.
     * This can conveniently be done using the getResult() method of the AsyncReply
     * object returned by this method. A typical usage pattern would be to first submit
     * all requests to the work queue, store the AsyncReply objects in an array and then
     * call getResult() for each of the objects in the array.
     *  
     * @param requests
     * @param axisMethodName
     * @param resultAccessorName
     * @param callback	Callback interface to invoke when the request has been processed (optional)
     * @return
     */    
    protected AsyncReply executeAsyncRequests(Object requests, String axisMethodName,
            String resultAccessorName, AsyncCallback callback) {
        
    	Message message = new Message(requests, axisMethodName, resultAccessorName, null);
    	
    	return WorkQueue.submit(new AsyncRequest(message, this.filterList.getFirst(), callback));
    }        
}
