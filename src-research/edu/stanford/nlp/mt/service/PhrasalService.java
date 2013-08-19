package edu.stanford.nlp.mt.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import edu.stanford.nlp.mt.service.PhrasalLogger.LogName;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Top-level class that loads the Phrasal servlet.
 * 
 *
 * @author Spence Green
 */
public final class PhrasalService {

  private static String DEBUG_URL = "127.0.0.1";
  private static int DEFAULT_HTTP_PORT = 8017;

  private PhrasalService() {}

  private static Map<String, Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = new HashMap<String,Integer>();
    optionArgDefs.put("p", 1);
    optionArgDefs.put("dl", 0);
    optionArgDefs.put("m", 0);
    optionArgDefs.put("l", 0);
    optionArgDefs.put("u", 1);
    return optionArgDefs;
  }

  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append(String.format("Usage: java %s [OPTS] phrasal_ini%n%n", PhrasalService.class.getName()));
    sb.append("Options:").append(nl);
    sb.append(" -p       : Port (default: ").append(DEFAULT_HTTP_PORT).append(")").append(nl);
    sb.append(" -dl      : Debug logging level").append(nl);
    sb.append(" -l       : Run on localhost").append(nl);
    sb.append(" -m       : Load mock servlet").append(nl);
    sb.append(" -u file  : UI to load (html file)").append(nl);
    return sb.toString();
  }

  public static void main(String[] args) {
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    int port = PropertiesUtils.getInt(options, "p", DEFAULT_HTTP_PORT);
    boolean debugLogLevel = PropertiesUtils.getBool(options, "dl", false);
    boolean loadMockServlet = PropertiesUtils.getBool(options, "m", false);
    boolean localHost = PropertiesUtils.getBool(options, "l", false);
    String uiFile = options.getProperty("u", "debug.html");

    // Parse arguments
    String argList = options.getProperty("",null);
    String[] parsedArgs = argList == null ? null : argList.split("\\s+");
    if (parsedArgs == null || parsedArgs.length != 1) {
      System.out.println(usage());
      System.exit(-1);
    }
    String phrasalIniFile = parsedArgs[0];
    
    // Setup the jetty server
    Server server = new Server();

    // Jetty 8 way of configuring the server
//    Connector connector = new SelectChannelConnector();
//    connector.setPort(port);
//    server.addConnector(connector);

//  Jetty9 way of configuring the server
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(port);
    server.addConnector(connector);

    if (localHost) {
      connector.setHost(DEBUG_URL);
    }
    if (debugLogLevel) {    
      PhrasalLogger.logLevel = Level.INFO;
    } else {
      PhrasalLogger.disableConsoleLogger();
    }
    Logger logger = Logger.getLogger(PhrasalService.class.getName());
    PhrasalLogger.attach(logger, LogName.Service);
    
    // Setup the servlet context
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
 
    // Add Phrasal servlet
    PhrasalServlet servlet = loadMockServlet ? new PhrasalServlet() : new PhrasalServlet(phrasalIniFile);
    context.addServlet(new ServletHolder(servlet), "/t");

    // Add debugging web-page
    ResourceHandler resourceHandler = new ResourceHandler();
    resourceHandler.setWelcomeFiles(new String[]{ uiFile });
    resourceHandler.setResourceBase(".");

    HandlerList handlers = new HandlerList();
    handlers.setHandlers(new Handler[] { resourceHandler, context });
    server.setHandler(handlers);
    
    // Start the service
    try {
      logger.info("Starting PhrasalService on port " + String.valueOf(port));
      server.start();
      server.join();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
