package edu.stanford.nlp.mt.service.handlers;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;

import com.google.gson.reflect.TypeToken;

import edu.stanford.nlp.mt.service.Messages.Language;
import edu.stanford.nlp.mt.service.PhrasalServlet;
import edu.stanford.nlp.mt.service.Messages.Request;
import edu.stanford.nlp.mt.service.Messages.TranslationReply;
import edu.stanford.nlp.mt.service.Messages.TranslationRequest;


/**
 * Mock handler for translation requests.
 * 
 * @author Spence Green
 *
 */
public class TranslationRequestHandlerMock implements RequestHandler {

  @Override
  public ServiceResponse handle(Request request) {
    throw new UnsupportedOperationException("This is an asynchronous handler");
  }

  @Override
  public void handleAsynchronous(Request baseRequest,
      HttpServletRequest request, HttpServletResponse response) {
    // Jetty continuations
    // TODO(spenceg) Try Servlet 3.0 if this doesn't work
    Continuation continuation = ContinuationSupport.getContinuation(request);
    continuation.suspend(response); //Start Async Processing

    TranslationRequest translationRequest = (TranslationRequest) baseRequest;
    
    // Translate to uppercase!
    List<String> translation = Arrays.asList(translationRequest.text.toUpperCase().split("\\s+"));
    List<String> alignments = new ArrayList<>(2);
    alignments.add("1-2");
    alignments.add("2-1");
    TranslationQuery query = new TranslationQuery(translation, alignments, 1.0);
    List<TranslationQuery> queryList = new ArrayList<>(1);
    queryList.add(query);
    Type t = new TypeToken<TranslationReply>() {}.getType();
    TranslationReply baseResponse = new TranslationReply(queryList);

    // Simulate a long call to the MT system
    Random random = new Random();
    try {
      Thread.sleep(500 + random.nextInt(1000));
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    ServiceResponse serviceResponse = new ServiceResponse(baseResponse, t);
    request.setAttribute(PhrasalServlet.ASYNC_KEY, serviceResponse);   
    continuation.resume(); // Re-dispatch/ resume to generate response
  }

  @Override
  public boolean validate(Request baseRequest) {
    TranslationRequest request = (TranslationRequest) baseRequest;
    if (request.src == Language.UNK || request.tgt == Language.UNK)
      return false;
    if (request.text == null || request.text.length() == 0)
      return false;
    return true;
  }
}
