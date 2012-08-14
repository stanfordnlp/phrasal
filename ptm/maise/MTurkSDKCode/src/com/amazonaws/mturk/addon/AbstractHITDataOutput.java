/*
 * Copyright 2008 Amazon Technologies, Inc.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class AbstractHITDataOutput implements HITDataOutput {

    /* (non-javadoc)
     * TODO:
     * The field names has recently become a field which may change dynamically during the course
     * of retrieving results for hits.  It would be nice to change the HITDataOutput interface
     * so that the fileNames were actually a List<String> instead of an array.
     * However, that change reaches into more parts of the SDK than I (bcastill@amazon.com)
     * wanted to change at the time.
     */
    
    private ArrayList<String> fieldNames = new ArrayList<String>();
    private transient String[] fieldNamesArray;
    
    public void close() {
    }

    public synchronized int getFieldNamesSize() {
        return this.fieldNames.size();
    }
    
    public synchronized String[] getFieldNames() {
        if (this.fieldNamesArray == null) {
            this.fieldNamesArray = this.fieldNames.toArray(new String[this.fieldNames.size()]);
        }
        return this.fieldNamesArray;
    }
    
    public void setFieldNames(String[] fieldNames) {
        setFieldNames(Arrays.asList(fieldNames));
    }
    
    public synchronized void setFieldNames(List<String> fieldNames) {
        this.fieldNames.clear();
        this.fieldNames.ensureCapacity(this.fieldNames.size());
        for (String key : fieldNames) {
            addFieldName(key);
        }
        this.fieldNamesArray = null;
    }

    protected String[] getValuesByFieldName(Map<String, String> mapValues) {
        return getValuesByFieldName(mapValues, Collections.EMPTY_LIST);
    }
    
    protected synchronized void addFieldName(String key) {
        if (!this.fieldNames.contains(key)) {
            this.fieldNames.add(key);
            this.fieldNamesArray = null;
        }
    }
    
    protected synchronized String[] getValuesByFieldName(Map<String, String> mapValues, Collection<String> ignoredKeys) {
        this.fieldNames.ensureCapacity(mapValues.size());
        for (String key : mapValues.keySet()) {
            if (!ignoredKeys.contains(key)) {
                addFieldName(key);
            }
        }
        String[] values = new String[this.fieldNames.size()];
        int index = 0;
        for (String key : this.fieldNames) {
            values[index++] = mapValues.get(key);
        }
        return values;
    }
}
