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

import java.io.IOException;
import junit.textui.TestRunner;
import com.amazonaws.mturk.addon.HITProperties;

public class TestHITProperties extends TestBase {
    
    public static void main(String[] args) {
        TestRunner.run(TestHITProperties.class);
    }
    
    public TestHITProperties(String arg0) {
        super(arg0);
    }
    
    protected void setUp() throws Exception {
        super.setUp();
    }
    
    public void testLoadUTF8PropertiesFile() throws IOException {
    	HITProperties p = new HITProperties(defaultUTF8PropertiesFileName);
    	assertEquals("Tàu cánh ngầm của tôi đầy lươn", p.getTitle());
    }
}
