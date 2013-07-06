package edu.stanford.nlp.mt.tools.service;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import edu.stanford.nlp.util.Generics;

/**
 * Top-level logging interface.
 * 
 * @author Spence Green
 *
 */
public final class PhrasalLogger {

  // TODO(spenceg): Add more logs
  public static enum LogName {Service};
  
  private PhrasalLogger() {}
  
  // Static methods for setting up a global logger
  // Other classes should attach() to this log handler
  private static Map<LogName,Handler> handlers = Generics.newHashMap(LogName.values().length);
  
  public static boolean disableConsole = true;
  private static boolean isConsoleDisabled = false;
  
  // Should be set by a main() method
  public static Level logLevel = Level.INFO;
  
  // Default prefix of the logger filename
  public static String prefix = PhrasalLogger.now();
  
  public static String now() {
    DateFormat df = new SimpleDateFormat("MM-dd-yyyy-HH:mm:ss");
    Date today = Calendar.getInstance().getTime();        
    return df.format(today);
  }
  
  private static void disableConsoleLogger() {
    // Disable default console logger
    Logger globalLogger = Logger.getLogger("global");
    Handler[] handlers = globalLogger.getHandlers();
    for(Handler handler : handlers) {
      globalLogger.removeHandler(handler);
    }    
    isConsoleDisabled = true;
  }
  
  private static void initLogger(LogName logName) {
    if (disableConsole &&  ! isConsoleDisabled) disableConsoleLogger();
    
    // Setup the file logger
    String logFileName = String.format("%s.%s.log", prefix, logName.toString().toLowerCase());
    try {
      Handler logHandler = new FileHandler(logFileName);
      logHandler.setFormatter(new SimpleFormatter()); //Plain text
      handlers.put(logName, logHandler);
    } catch (SecurityException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void attach(Logger logger, LogName logName) {
    // Disable the console logger, then attach to the file logger.
    logger.setUseParentHandlers(false);
    if ( ! handlers.containsKey(logName)) {
      initLogger(logName);
    }
    logger.addHandler(handlers.get(logName));    
    logger.setLevel(logLevel);
  }

}
