package edu.stanford.nlp.mt.service.handlers;

import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.reflect.TypeToken;

import edu.stanford.nlp.mt.service.Messages.Language;
import edu.stanford.nlp.mt.service.Messages.Request;
import edu.stanford.nlp.mt.service.Messages.RuleQueryReply;
import edu.stanford.nlp.mt.service.Messages.RuleQueryRequest;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;


/**
 * Mock handler for rule query requests.
 * 
 * @author Spence Green
 *
 */
public class RuleQueryRequestHandlerMock implements RequestHandler {

  @Override
  public ServiceResponse handle(Request request) {
    RuleQueryRequest ruleRequest = (RuleQueryRequest) request;
    
    List<RuleQuery> queriedRules = new LinkedList<>();
    Sequence<IString> source = IStrings.tokenize(ruleRequest.text);
    final int sourceLength = source.size();
    for (int i = 0; i < sourceLength; ++i) {
      for (int j = i+1; j <= sourceLength; ++j) {
        List<String> sourceSide = Sequences.toStringList(source.subsequence(i, j));
        queriedRules.add(new RuleQuery(sourceSide, null, 0.6));
      }
    }
    RuleQueryReply reply = new RuleQueryReply(queriedRules);
    Type t = new TypeToken<RuleQueryReply>() {}.getType();
    ServiceResponse response = new ServiceResponse(reply, t);

    return response;
  }

  @Override
  public void handleAsynchronous(Request baseRequest,
      HttpServletRequest request, HttpServletResponse response) {
    throw new UnsupportedOperationException("Asynchronous call to synchronous handler");
  }

  @Override
  public boolean validate(Request request) {
    if (request.src == Language.UNK || request.tgt == Language.UNK)
      return false;
    if (request.text == null || request.text.length() == 0)
      return false;
    return true;
  }
}
