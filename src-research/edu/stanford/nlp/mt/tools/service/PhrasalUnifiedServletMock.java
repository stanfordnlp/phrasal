package edu.stanford.nlp.mt.tools.service;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import edu.stanford.nlp.mt.tools.service.Messages.MessageType;
import edu.stanford.nlp.mt.tools.service.Messages.PTMBaseRequest;
import edu.stanford.nlp.mt.tools.service.Messages.PTMDoneRequest;
import edu.stanford.nlp.mt.tools.service.Messages.PTMOOVRequest;
import edu.stanford.nlp.mt.tools.service.Messages.PTMOOVResponse;
import edu.stanford.nlp.mt.tools.service.Messages.PTMPredictionRequest;
import edu.stanford.nlp.mt.tools.service.Messages.PTMPredictionResponse;
import edu.stanford.nlp.mt.tools.service.Messages.PTMStatusOk;
import edu.stanford.nlp.util.Pair;

/**
 * Mock servlet for PhrasalUnifiedServlet
 * 
 * @author Spence Green
 *
 */
public class PhrasalUnifiedServletMock extends HttpServlet
{
  private static final long serialVersionUID = -478292236846080310L;

  public static final String DEBUG_PROPERTY = PhrasalUnifiedServletMock.class.getSimpleName();
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));
  
  private final Gson gson = new Gson();
  
  public PhrasalUnifiedServletMock(){
    if (DEBUG) {
      System.err.println("Debug mode MOCK Servlet: " + PhrasalUnifiedServletMock.class.getName());
    } else {
      System.err.println("Loading MOCK Servlet: " + PhrasalUnifiedServletMock.class.getName());
    }
  }
  
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
  {
    String responseString = null;
    Pair<MessageType,PTMBaseRequest> message = Messages.requestToMessage(request);
    MessageType messageType = message.first();
    PTMBaseRequest baseRequest = message.second();

    if(DEBUG) {
      System.err.println("Received request: " + messageType.toString());
    }
    
    if (messageType == MessageType.INIT) {
      String[] oovList = {"oov-test1", "oov-test2"};
      List<String> oovStringsList = new ArrayList<String>(Arrays.asList(oovList));
      PTMOOVResponse ptmResponse = new PTMOOVResponse(oovStringsList);
      Type t = new TypeToken<PTMOOVResponse>() {}.getType();           
      responseString = gson.toJson(ptmResponse, t);
    
    } else if (messageType == MessageType.SEND_OOV) {
      PTMOOVRequest ptmRequest = (PTMOOVRequest) baseRequest;
      if (DEBUG) {
        System.err.println("PTMOOVRequest: " + gson.toJson(ptmRequest));
      }
      responseString = gson.toJson(new PTMStatusOk());  
    
    } else if (messageType == MessageType.PREDICTION) {       
      PTMPredictionRequest ptmRequest = (PTMPredictionRequest) baseRequest;
      if (DEBUG) {
        System.err.println("PTMPredictionRequest: " + gson.toJson(ptmRequest)); 
      }

      List<Prediction> predictions = new ArrayList<Prediction>();
      Prediction pred1 = new Prediction("0-1-2-3", "testing testing2 testing3", "4-5-6");
      predictions.add(pred1);
      Prediction pred2 = new Prediction("0-1","debug debug2","3-4");
      predictions.add(pred2);
      Prediction pred3 = new Prediction("1-2", "hello", "5");
      predictions.add(pred3);
      
      PTMPredictionResponse ptmResponse = new PTMPredictionResponse(ptmRequest.prefix, predictions);
      Type t = new TypeToken<PTMPredictionResponse>() {}.getType();           
      responseString = gson.toJson(ptmResponse, t);
    
    } 
//    else if (hasParameter(request, "ptmUserSelection")) {
//      PTMCompletionSelectionRequest ptmRequest = gson.fromJson(request.getParameter("ptmUserSelection"), PTMCompletionSelectionRequest.class);
//      if (DEBUG) {
//        System.err.println("PTMCompletionSelectonRequest: " + gson.toJson(ptmRequest));
//      }
//      //responseString = wrapResponse("ptmUserSelectionResponse", gson.toJson(new PTMStatusOk()));
//      responseString = gson.toJson(new PTMStatusOk());      
//    
//    } 
    else if (messageType == MessageType.DONE) {
      PTMDoneRequest ptmRequest = (PTMDoneRequest) baseRequest;
      if (DEBUG) {
        System.err.println("PTMDoneRequest: " + gson.toJson(ptmRequest));
      }
      System.out.printf("LOG: %s\n", ptmRequest);
      //responseString = wrapResponse("ptmDoneResponse", gson.toJson(new PTMStatusOk()));
      responseString = gson.toJson(new PTMStatusOk());       
    } 
    
    RequestUtils.writeJavascriptResponse(response, responseString);
  }
}
