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


import java.math.BigDecimal;

import junit.textui.TestRunner;

import com.amazonaws.mturk.filter.Filter;
import com.amazonaws.mturk.filter.Message;
import com.amazonaws.mturk.filter.Reply;
import com.amazonaws.mturk.requester.CreateHITRequest;
import com.amazonaws.mturk.requester.HIT;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.amazonaws.mturk.service.exception.ServiceException;



public class TestFilter extends TestBase {
    
    class CheckRewardFilter extends Filter {
        public Reply execute(Message m) {
            if (m.getMethodName().equals("CreateHIT")) {
                CreateHITRequest[] requestArray = (CreateHITRequest[]) m.getRequests();
                for (CreateHITRequest request : requestArray) {
                    if (request.getReward().getAmount().doubleValue() < 0.05) {
                        request.getReward().setAmount(new BigDecimal(0.05));
                    }
                }
            }
            return passMessage(m);
        }
    }
    public static void main(String[] args) {
        TestRunner.run(TestFilter.class);
    }
    
    public TestFilter(String arg0) {
        super(arg0);
    }
    
    protected void setUp() throws Exception {
        super.setUp();
    }
    
    public void testChangeReward() throws ServiceException {
        Filter filter = new CheckRewardFilter();
        service.addFilter(filter);
        String hitId = service.createHIT(defaultHITTitle + unique, defaultHITDescription, defaultReward, 
            RequesterService.getBasicFreeTextQuestion(defaultQuestion), defaultMaxAssignments).getHITId();
        
        HIT hit = service.getHIT(hitId);  
        assertEquals(hit.getReward().getAmount().doubleValue(), 0.05);
        service.removeFilter(filter);
    }
    
    public void testRetryFilter() throws ServiceException {
        Runnable runner = new Runnable() {
            public void run () {
                String hitId = service.createHIT(defaultHITTitle + unique, defaultHITDescription, defaultReward, 
                        RequesterService.getBasicFreeTextQuestion(defaultQuestion), defaultMaxAssignments).getHITId();
                for (int i = 0; i < 50; i++) {
                    try {
                        service.getHIT(hitId);
                    }
                    catch (ServiceException e) {
                        fail("Got exception while testing throttling handler" + e);
                    }
                }
            }
        };
        
        Thread[] tarr = new Thread[5];
        for (int i = 0; i < 5; i++) {
            Thread t = new Thread(runner);
            t.start();
            tarr[i] = t;
        }
        for (Thread t: tarr) { 
            try {
                t.join();
            }
            catch (InterruptedException e) {
                //do nothing
            }
        }
    }
    
}
