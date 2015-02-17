package edu.stanford.nlp.mt.visualize.phrase;

import java.util.EventListener;

/**
 * 
 * @author Spence Green
 */
public interface ClickEventListener extends EventListener {

  public void handleClickEvent(ClickEvent e);

}
