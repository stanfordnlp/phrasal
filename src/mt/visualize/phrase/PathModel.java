package mt.visualize.phrase;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.event.EventListenerList;


public class PathModel {

  //TODO Make this arbitrary - fixing for now so that arbitrary color schemes need not be defined
  public static final int MAX_PATHS = 5;

  private final File oracleFilePath;
  private final File oneBestFilePath;
  private final File savedPathsFilePath;

  private final Map<Integer, List<Path>> paths;
  private final EventListenerList listenerList;
  private Path currentPath = null;

  public PathModel(File oracle, File oneBest, File savedPaths) {
    oracleFilePath = oracle;
    oneBestFilePath = oneBest;
    savedPathsFilePath = savedPaths;
    listenerList = new EventListenerList();
    paths = new HashMap<Integer, List<Path>>();
  }

  private class Path {
    public String name = null;
    public boolean enabled = false;
    public List<VisualPhrase> phrases = null;
    public int formatId = -1;
    public int transId = -1;
  }

  public boolean load() {
    //TODO Path names can be null if the user does not specify them
    //Populate the oracle and 1-best from files
    return true;
  }

  public boolean addPath(int translationId, String name) {
    if(paths.get(translationId) == null)
      paths.put(translationId, new ArrayList<Path>());

    int numPaths = paths.get(translationId).size();
    
    if(numPaths < MAX_PATHS) {
      currentPath = new Path();
      currentPath.transId = translationId;
      currentPath.enabled = true;
      currentPath.name = name.intern();
      currentPath.transId = translationId;
      currentPath.formatId = numPaths;
      currentPath.phrases = new ArrayList<VisualPhrase>();
      return true;
    }
    return false;
  }

  public int getFormatId(int translationId, String name) {
    if(currentPath.transId == translationId && currentPath.name.equals(name))
      return currentPath.formatId;
    else if(paths.get(translationId) != null)
      for(Path p : paths.get(translationId))
        if(p.name.equals(name))
          return p.formatId;

    return -1;
  }
  
  public boolean finishPath(int translationId, String name) {
    if(currentPath == null || currentPath.transId != translationId || 
        !currentPath.name.equals(name) || paths.get(translationId).size() >= MAX_PATHS)
      return false;
    
    paths.get(translationId).add(currentPath);
    currentPath = null;
    
    return true;
  }

  public Map<String,List<VisualPhrase>> getPaths(int translationId) {
    if(paths.get(translationId) == null)
      return null;
    
    Map<String,List<VisualPhrase>> ret = new HashMap<String,List<VisualPhrase>>();
    for(Path p : paths.get(translationId))
      ret.put(p.name.intern(), Collections.unmodifiableList(p.phrases));
    return ret;
  }

  public void processClick(VisualPhrase vp) {
    if(currentPath == null)
      return;
    else if(currentPath.phrases.size() == 0)
      currentPath.phrases.add(vp);
    else if(currentPath.phrases.get(currentPath.phrases.size() - 1).getId() == vp.getId())
      currentPath.phrases.remove(currentPath.phrases.size() - 1);
    else if(currentPath.phrases.contains(vp))
      return;
    else
      currentPath.phrases.add(vp);
  }

  public List<String> getPathNames(int translationId) {
    if(paths.get(translationId) != null) {
      List<String> names = new ArrayList<String>();
      for(Path p : paths.get(translationId))
        names.add(p.name.intern());
      return names;
    }
      return null;
  }

  public void setPathState(boolean isOn, int translationId, String name) {
    if(paths.get(translationId) != null)
      for(Path p : paths.get(translationId))
        if(p.name.equals(name))
          p.enabled = isOn;
  }







  //WSGDEBUG See http://java.sun.com/j2se/1.4.2/docs/api/javax/swing/event/EventListenerList.html
  public void addClickToStream(VisualPhrase vp) {
    //Do internal processing
    processClick(vp);

    //Notify subscribers
    Object[] listeners = listenerList.getListenerList();
    for (int i=0; i < listeners.length; i += 2) {
      if (listeners[i]==ClickEventListener.class) {
        ((ClickEventListener)listeners[i+1]).handleClickEvent(new ClickEvent(vp));
      }
    }
  }

  //No synchronization needed with EventListenerList class
  public void addClickEventListener(ClickEventListener listener) {
    listenerList.add(ClickEventListener.class, listener);
  }

  //No synchronization needed with EventListenerList class
  public void removeClickEventListener(ClickEventListener listener) {
    listenerList.remove(ClickEventListener.class, listener);
  }


}
