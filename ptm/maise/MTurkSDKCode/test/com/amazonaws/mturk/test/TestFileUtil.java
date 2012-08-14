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

import java.io.File;
import java.io.IOException;
import junit.textui.TestRunner;
import com.amazonaws.mturk.util.FileUtil;

public class TestFileUtil extends TestBase {
	
    public static void main(String[] args) {
        TestRunner.run(TestFileUtil.class);
    }
    
    public TestFileUtil(String arg0) {
        super(arg0);
    }
    
    protected void setUp() throws Exception {
        super.setUp();
    }
    
    public void testReadWriteUTF8Data() throws IOException {
    	File temp = File.createTempFile("TestFileUtil.testWriteReadData", "txt");
    	temp.deleteOnExit();
    	FileUtil f = new FileUtil(temp.getCanonicalPath());
    	f.saveString("ASCII\n", true);
    	f.saveString("Làtìn-1\n", true);
    	f.saveString("ænd bɪɑnd", true);
    	assertEquals("ASCII\nLàtìn-1\nænd bɪɑnd", f.getString());
    	String[] lines = f.getLines();
    	assertEquals("ASCII", lines[0]);
    	assertEquals("Làtìn-1", lines[1]);
    	assertEquals("ænd bɪɑnd", lines[2]);
    }
}
