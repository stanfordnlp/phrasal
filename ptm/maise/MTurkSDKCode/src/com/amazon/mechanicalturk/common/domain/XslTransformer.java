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


package com.amazon.mechanicalturk.common.domain;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;


public class XslTransformer {
	
	/** Creates an XSLT transformer for processing an XML document.
	 *  A new transformer, along with a style template are created 
	 *  for each document transformation. The XSLT, DOM, and 
	 *  SAX processors are based on system default parameters.
	 */ 
	
	private TransformerFactory factory;
	
	public XslTransformer() {
		factory =  TransformerFactory.newInstance();
	}
	
	public static String convertQAPtoHTML(String qap) throws Exception
	{
    XslTransformer xslt = new XslTransformer();
    
    Writer sw = new StringWriter();
    Reader sr = new StringReader(qap);
		
		// Use the local copy of HIT.xsl.  
    InputStream is = null;
		try
		{
    	is = new URL("http://www.mturk.com/xform/HIT.xsl").openStream();
		}
		catch (Exception e)
		{
      
			// If we can't pull the file online, use the local copy
			is = XslTransformer.class.getResourceAsStream("etc/xform/HIT.xsl");
		}
		
		Reader fr = new InputStreamReader(is);
    xslt.process(sr, fr, sw);
    return sw.toString();
	}
	
	/** Transform an XML and XSL document as <code>Reader</code>s,
	 *  placing the resulting transformed document in a 
	 *  <code>Writer</code>. Convenient for handling an XML 
	 *  document as a String (<code>StringReader</code>) residing
	 in memory, not on disk. The output document could easily 
	 be
	 *  handled as a String (<code>StringWriter</code>) or as a
	 *  <code>JSPWriter</code> in a JavaServer page.
	 */
	
	public void process(Reader xmlFile, Reader xslFile, Writer output) throws TransformerException 
	{
		process(new StreamSource(xmlFile),
				new StreamSource(xslFile),
				new StreamResult(output));
	}
	
	/** Transform an XML and XSL document as <code>File</code>s,
	 *  placing the resulting transformed document in a 
	 *  <code>Writer</code>. The output document could easily 
	 *  be handled as a String (<code>StringWriter</code)> or as 
	 *  a <code>JSPWriter</code> in a JavaServer page.
	 */
	public void process(File xmlFile, File xslFile, Writer output) throws TransformerException 
	{
		process(new StreamSource(xmlFile),
				new StreamSource(xslFile),
				new StreamResult(output));
	}
	
	/** Transform an XML <code>File</code> based on an XSL 
	 *  <code>File</code>, placing the resulting transformed 
	 *  document in a <code>OutputStream</code>. Convenient for 
	 *  handling the result as a <code>FileOutputStream</code> or 
	 *  <code>ByteArrayOutputStream</code>.
	 */
	
	public void process(File xmlFile, File xslFile, OutputStream out) throws TransformerException 
	{
		process(new StreamSource(xmlFile),
				new StreamSource(xslFile),
				new StreamResult(out));
	}
	
	/** Transform an XML source using XSLT based on a new template
	 *  for the source XSL document. The resulting transformed 
	 *  document is placed in the passed in <code>Result</code> 
	 *  object.
	 */
	
	public void process(Source xml, Source xsl, Result result) throws TransformerException 
	{	
		try 
		{
      Transformer transformer = factory.newTransformer(xsl);
      transformer.transform(xml, result);
		} 
		catch(TransformerConfigurationException tce) 
		{
			throw new TransformerException(tce.getMessageAndLocation());
		} 
		catch (TransformerException te) 
		{
			throw new TransformerException(te.getMessageAndLocation());
		}
	}
}
