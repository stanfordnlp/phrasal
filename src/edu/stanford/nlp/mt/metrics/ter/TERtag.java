package edu.stanford.nlp.mt.metrics.ter;

import java.util.HashMap;

@SuppressWarnings( {"unchecked", "unused"} )
public class TERtag {
  public String name;
  public HashMap content;
  public String rest;

  public TERtag() {
	name = "";
	content = new HashMap();
	rest = "";
  }
}
