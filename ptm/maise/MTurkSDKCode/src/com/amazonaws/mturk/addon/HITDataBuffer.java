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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HITDataBuffer extends AbstractHITDataOutput implements HITDataOutput, HITDataInput {

    protected List<String[]> rows;
    
    public HITDataBuffer() {}
    
    public synchronized void setRows( List<String[]> rows ) {
        this.rows = rows;
        setFieldNames( this.rows.get( 0 ));
    }

    public synchronized void writeLine( String[] fieldValues ) throws IOException {
        if( rows == null ) {
            rows = new ArrayList<String[]>();
        }
        rows.add( fieldValues );
    }

    public void writeValues( Map< String, String > values ) throws IOException {
        writeLine(getValuesByFieldName(values));
    }

    public synchronized int getNumRows() {
        return (this.rows == null) ? 0 : this.rows.size();
    }

    public Map< String, String > getRowAsMap( int rowNum ) {
        String[] rowValues = this.getRowValues(rowNum);

        if (this.getFieldNamesSize() == 0 || rowValues == null || rowValues.length == 0)
        {
          return null;
        }

        HashMap<String,String> rowValueMap = new HashMap<String,String>();
        int index = 0;
        for (String fieldName : this.getFieldNames()) {
          rowValueMap.put(fieldName, rowValues[index++]);
        }

        return rowValueMap;
    }

    public synchronized String[] getRowValues( int rowNum ) {
        return this.rows.get( rowNum );
    }
    
}
