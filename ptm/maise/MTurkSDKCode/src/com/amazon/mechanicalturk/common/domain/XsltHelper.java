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



import org.apache.commons.lang.StringEscapeUtils;
import org.apache.xalan.extensions.ExpressionContext;
import org.apache.xalan.extensions.XSLProcessorContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;



/**

 * Some non-standard operations we need to embed in our XSL transforms.

 */

public class XsltHelper {

    public static String base64Encode(String toEncode) {
        return toEncode;
    }


    protected static String encodeConstraintsNode(Node constraintsNode) {
        StringBuffer encoded = new StringBuffer();
        NodeList constraints = constraintsNode.getChildNodes();

        for (int i = 0; i < constraints.getLength(); i++) {           
        	encoded.append(constraints.item(i));
        }

        return encoded.toString();
    }


    public static String encodeConstraints(ExpressionContext context,
                                           Node constraintsNode) {

        return encodeConstraintsNode(constraintsNode);
    }


    public static String encodeConstraints(ExpressionContext context,
                                           NodeList constraintsNodeList) {

        if (constraintsNodeList == null || constraintsNodeList.getLength() == 0) {
            return null;
        }

        Node constraintsNode = constraintsNodeList.item(0);
        return encodeConstraintsNode(constraintsNode);
    }





    public static String encodeConstraints(Node constraintsNode) {

        return encodeConstraintsNode(constraintsNode);

    }





    public static String encodeConstraints(NodeList constraintsNodeList) {



        if (constraintsNodeList == null || constraintsNodeList.getLength() == 0) {

            return null;

        }



        Node constraintsNode = constraintsNodeList.item(0);

        return encodeConstraintsNode(constraintsNode);

    }





    public static String encodeConstraints(Element constraintsElement) {

        return encodeConstraintsNode(constraintsElement);

    }





    public static String encodeConstraints(XSLProcessorContext context, Element constraintsElement) {

        return encodeConstraintsNode(constraintsElement);

    }



    //org.apache.xml.dtm.ref.DTMNodeListBase

    public static String getAnswerText(Object answersParam, String indexParam, String fallbackParam) {
    	return fallbackParam;
    }

    

    public static boolean isAnswerChoice(Object answersParam, String indexParam, String key) {
    	return false;
    }



    public static String encodeValidChoices(Node choicesNode) 
    {
        return "";
    }
    
    public static String escapeJavaScript(String str) 
    {
        return StringEscapeUtils.escapeJavaScript(str);
    }
}
