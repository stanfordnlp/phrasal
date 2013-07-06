package edu.stanford.nlp.mt.tools.service;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.stanford.nlp.mt.tools.service.Messages.MessageType;
import edu.stanford.nlp.mt.tools.service.Messages.Request;
import edu.stanford.nlp.mt.tools.service.PhrasalLogger.LogName;
import edu.stanford.nlp.mt.tools.service.handlers.TranslationRequestHandler;
import edu.stanford.nlp.mt.tools.service.handlers.RequestHandler;
import edu.stanford.nlp.mt.tools.service.handlers.ServiceResponse;
import edu.stanford.nlp.util.Pair;

/**
 * Debug servlet.
 * 
 * @author Spence Green
 *
 */
public class PhrasalServletMock extends HttpServlet {
  
  private static final long serialVersionUID = -478292236846080310L;
  
  private final RequestHandler[] requestHandlers;
  
  private final Logger logger;
  
  public PhrasalServletMock(){
    logger = Logger.getLogger(PhrasalServletMock.class.getName());
    PhrasalLogger.attach(logger, LogName.Service);
    
    // Setup mock request handlers
    requestHandlers = new RequestHandler[MessageType.values().length];
    for (MessageType type : MessageType.values()) {
      if (type == MessageType.TRANSLATION_REQUEST) {
        requestHandlers[type.ordinal()] = new TranslationRequestHandler();
      }
      // TODO(spenceg): Add more handlers
    }
  }
  
  protected void doGet(HttpServletRequest request, 
      HttpServletResponse response) throws ServletException, IOException {

    System.err.println("Recv:");
    System.err.println(request.toString());
    
    // Parse the message
    Pair<MessageType,Request> message = Messages.requestToMessage(request);
    MessageType messageType = message.first();
    Request baseRequest = message.second();
    logger.info(String.format("Recv: %s %s", messageType.toString(), baseRequest.toString()));

    Object result = request.getAttribute("result");
    if (result == null && baseRequest.isAsynchronous()) {
      // Asynchronous request that will be suspended by the handler
      requestHandlers[messageType.ordinal()].mockHandleAsynchronous(baseRequest, request, response);
//
//      threadpool.submit(new  RemoteServiceHandler() implements Runnable {
//        public void run() {
//          Object result  = makeRemoteWebService(...);
//          request.setAttribute("result", result);   
//          continuation.resume(); // Re-dispatch/ resume to generate response
//        }
//      });

    } else if (result == null) {
      // Synchronous message
      ServiceResponse r = requestHandlers[messageType.ordinal()].handle(baseRequest);
      r.writeInto(response);      
      logger.info(String.format("Send: %s", r.getReply().toString()));
      
    } else {
      // Request that has been re-generated after asynchronous processing
      ServiceResponse r = (ServiceResponse) result;
      // Generate the response
      r.writeInto(response);
      logger.info(String.format("Send: %s", r.getReply().toString()));
    }
    
    
    
//    if (messageType == MessageType.INIT) {
//      String[] oovList = {"oov-test1", "oov-test2"};
//      List<String> oovStringsList = new ArrayList<String>(Arrays.asList(oovList));
//      PTMOOVResponse ptmResponse = new PTMOOVResponse(oovStringsList);
//      Type t = new TypeToken<PTMOOVResponse>() {}.getType();           
//      responseString = gson.toJson(ptmResponse, t);
//    
//    } else if (messageType == MessageType.SEND_OOV) {
//      PTMOOVRequest ptmRequest = (PTMOOVRequest) baseRequest;
//      if (DEBUG) {
//        System.err.println("PTMOOVRequest: " + gson.toJson(ptmRequest));
//      }
//      responseString = gson.toJson(new PTMStatusOk());  
//    
//    } else if (messageType == MessageType.PREDICTION) {       
//      PTMPredictionRequest ptmRequest = (PTMPredictionRequest) baseRequest;
//      if (DEBUG) {
//        System.err.println("PTMPredictionRequest: " + gson.toJson(ptmRequest)); 
//      }
//
//      List<Prediction> predictions = new ArrayList<Prediction>();
//      Prediction pred1 = new Prediction("0-1-2-3", "testing testing2 testing3", "4-5-6");
//      predictions.add(pred1);
//      Prediction pred2 = new Prediction("0-1","debug debug2","3-4");
//      predictions.add(pred2);
//      Prediction pred3 = new Prediction("1-2", "hello", "5");
//      predictions.add(pred3);
//      
//      PTMPredictionResponse ptmResponse = new PTMPredictionResponse(ptmRequest.prefix, predictions);
//      Type t = new TypeToken<PTMPredictionResponse>() {}.getType();           
//      responseString = gson.toJson(ptmResponse, t);
//    
//    } 
//    else if (messageType == MessageType.DONE) {
//      PTMDoneRequest ptmRequest = (PTMDoneRequest) baseRequest;
//      if (DEBUG) {
//        System.err.println("PTMDoneRequest: " + gson.toJson(ptmRequest));
//      }
//      System.out.printf("LOG: %s\n", ptmRequest);
//      //responseString = wrapResponse("ptmDoneResponse", gson.toJson(new PTMStatusOk()));
//      responseString = gson.toJson(new PTMStatusOk());       
//    }     
//    RequestUtils.writeJavascriptResponse(response, responseString);
  }
}
