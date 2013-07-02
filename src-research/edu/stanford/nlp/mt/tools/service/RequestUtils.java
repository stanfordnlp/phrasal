package edu.stanford.nlp.mt.tools.service;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

public class RequestUtils {

  public static boolean writeJavascriptResponse(HttpServletResponse response, String responseString) {
    if (responseString == null) {
      return false;
    }
    try {
      response.getWriter().println(responseString);
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    response.setContentType("application/x-javascript;charset=utf-8");     
    response.setStatus(HttpServletResponse.SC_OK);
    return true;
  }
}
