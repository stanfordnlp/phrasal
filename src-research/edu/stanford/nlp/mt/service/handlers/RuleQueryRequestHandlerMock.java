package edu.stanford.nlp.mt.service.handlers;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.reflect.TypeToken;

import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.process.ProcessorFactory.Language;
import edu.stanford.nlp.mt.service.Messages.Request;
import edu.stanford.nlp.mt.service.Messages.RuleQueryReply;
import edu.stanford.nlp.mt.service.Messages.RuleQueryRequest;
import edu.stanford.nlp.util.Generics;

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
    
    List<RuleQuery> queriedRules = Generics.newLinkedList();
    Sequence<IString> source = IStrings.tokenize(ruleRequest.text);
    final int sourceLength = source.size();
    for (int i = 0; i < sourceLength; ++i) {
      for (int j = i+1; j <= sourceLength; ++j) {
        String sourceSide = source.subsequence(i, j).toString();
        String targetSide = sourceSide.toLowerCase();
        queriedRules.add(new RuleQuery(sourceSide, targetSide, 0.6, ""));
        List<String> tgtReverse = Arrays.asList(targetSide.split("\\s+"));
        Collections.reverse(tgtReverse);
        String targetSideRev = Sentence.listToString(tgtReverse);
        queriedRules.add(new RuleQuery(sourceSide, targetSideRev, 0.5, ""));
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
