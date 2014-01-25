package edu.stanford.nlp.mt.base;

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
public final class SystemLogger {

  // Names of various logs
  public static enum LogName {SERVICE, WORD_CLASS, ONLINE, DECODE};
  
  private SystemLogger() {}
  
  // Static methods for setting up a global logger
  private static Map<LogName,Handler> handlers = Generics.newHashMap(LogName.values().length);
  
  private static boolean isConsoleDisabled = false;
  private static boolean shutdownHookAdded = false;
  
  private static Level logLevel = Level.WARNING;
  public static Level getLevel() { return SystemLogger.logLevel; }
  public static synchronized void setLevel(Level level) { logLevel = level; }
  
  // Default prefix of the logger filename. Changing this has no
  // effect after a call to attach().
  private static String prefix = SystemLogger.now();
  public static String getPrefix() { return SystemLogger.prefix; } 
  public static synchronized void setPrefix(String prefix) { SystemLogger.prefix = prefix; }
  
  private static String now() {
    DateFormat df = new SimpleDateFormat("MM-dd-yyyy-HH:mm:ss");
    Date today = Calendar.getInstance().getTime();        
    return df.format(today);
  }
  
  public static void disableConsoleLogger() {
    if (isConsoleDisabled) return;
    
    // Disable default console logger
    Logger globalLogger = Logger.getLogger("global");
    Handler[] handlers = globalLogger.getHandlers();
    for(Handler handler : handlers) {
      globalLogger.removeHandler(handler);
    }    
    isConsoleDisabled = true;
  }
  
  private static void initLogger(LogName logName) {
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
    
    // Add shutdown hook for closing down the loggers
    if ( ! shutdownHookAdded) {
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          for (Handler handler : handlers.values()) {
            handler.close();
          }
        }
      });
    }
    shutdownHookAdded = true;
  }

  /**
   * Attach a logger to a handler.
   * 
   * @param logger
   * @param logName
   */
  public static void attach(Logger logger, LogName logName) {
    if (isConsoleDisabled) logger.setUseParentHandlers(false);
    if ( ! handlers.containsKey(logName)) {
      initLogger(logName);
    }
    logger.addHandler(handlers.get(logName));    
    logger.setLevel(logLevel);
  }
}
