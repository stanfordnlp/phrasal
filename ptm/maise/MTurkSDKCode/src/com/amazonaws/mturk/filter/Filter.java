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


package com.amazonaws.mturk.filter;

import java.util.List;

import com.amazonaws.mturk.service.exception.ServiceException;

/**
 * Abstract class which handles execution of Filters
 * and also linking them together. User should subclass this for creating their own Filters 
 */
public abstract class Filter {
    
    private Filter nextFilter;
    
    /**
     * retun true if it can be removed from the chain of filters else false
     */
    public boolean isRemovable() {
        return true;
    }
    
    /**
     * Sets the nextfilter on each of the Filters
     * @param filterList - list of filters which need to be linked together to form the filter chain
     * 
     */
    public static void linkFilters(List<Filter> filterList) {
        for (int i = 0; i < filterList.size() - 1; i++) {
            filterList.get(i).nextFilter = filterList.get(i+1);
        }
    }
        
    /**
     * 
     * @param m - the request message that should be passed to the next filter
     * @return result of the next filter's execution. 
     * throws ServiceException on errors 
     */
    protected Reply passMessage(Message m) throws ServiceException {
        return nextFilter.execute(m);
    }
    
    /**
     * Implement this to perform processing on Message and Reply in your Filter 
     * @param m - request message got from previous filter 
     * @return result of the execution of this filter
     * throws ServiceException on errors 
     */
    public abstract Reply execute(Message m) throws ServiceException;
    
}
