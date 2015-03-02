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

import edu.stanford.nlp.mt.util.SystemLogger;
import edu.stanford.nlp.mt.util.SystemLogger.LogName;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Lightweight Jetty-based container for the PhrasalServlet.
 * 
 *
 * @author Spence Green
 */
public final class PhrasalService {

  private static String DEBUG_URL = "127.0.0.1";
  private static int DEFAULT_HTTP_PORT = 8017;
  private static String SERVLET_ROOT = "/x";

  private PhrasalService() {}

  /**
   * Specify the command-line arguments.
   * 
   * @return
   */
  private static Map<String, Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = new HashMap<>();
    optionArgDefs.put("p", 1);
    optionArgDefs.put("d", 1);
    optionArgDefs.put("m", 0);
    optionArgDefs.put("l", 0);
    optionArgDefs.put("u", 1);
    optionArgDefs.put("r", 1);
    return optionArgDefs;
  }

  /**
   * Generate a usage message.
   * 
   * @return
   */
  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append(String.format("Usage: java %s [OPTS] phrasal_ini%n%n", PhrasalService.class.getName()));
    sb.append("Options:").append(nl);
    sb.append(" -p       : Port (default: ").append(DEFAULT_HTTP_PORT).append(")").append(nl);
    sb.append(" -d level : Logging level from java.util.logging.Level (default: WARNING)").append(nl);
    sb.append(" -l       : Run on localhost").append(nl);
    sb.append(" -m       : Load mock servlet").append(nl);
    sb.append(" -u file  : UI to load (html file)").append(nl);
    sb.append(" -r path  : Static resource base path").append(nl);
    return sb.toString();
  }

  /**
   * Start the service.
   * 
   * @param args
   */
  public static void main(String[] args) {
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    int port = PropertiesUtils.getInt(options, "p", DEFAULT_HTTP_PORT);
    Level logLevel = options.containsKey("d") ? Level.parse(options.getProperty("d")) : Level.WARNING;
    boolean loadMockServlet = PropertiesUtils.getBool(options, "m", false);
    boolean localHost = PropertiesUtils.getBool(options, "l", false);
    String uiFile = options.getProperty("u", "debug.html");
    String resourcePath = options.getProperty("r", ".");

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
    SystemLogger.setLevel(LogName.SERVICE, logLevel);
    SystemLogger.disableConsoleLogger();
    Logger logger = Logger.getLogger(PhrasalService.class.getName());
    SystemLogger.attach(logger, LogName.SERVICE);
    
    // Setup the servlet context
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
 
    // Add Phrasal servlet
    PhrasalServlet servlet = loadMockServlet ? new PhrasalServlet() : new PhrasalServlet(phrasalIniFile);
    context.addServlet(new ServletHolder(servlet), SERVLET_ROOT);

    // TODO(spenceg): gzip compression causes an encoding problem for unicode characters
    // on the client. Not sure if the compression or decompression is the problem.
//    EnumSet<DispatcherType> dispatches = EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC);
//    context.addFilter(new FilterHolder(new IncludableGzipFilter()), "/t", dispatches);

    // Add debugging web-page
    ResourceHandler resourceHandler = new ResourceHandler();
    resourceHandler.setWelcomeFiles(new String[]{ uiFile });
    resourceHandler.setResourceBase(resourcePath);

    HandlerList handlers = new HandlerList();
    handlers.setHandlers(new Handler[] { resourceHandler, context });
    server.setHandler(handlers);
    
    // Start the service
    try {
      logger.info("Starting PhrasalService on port: " + String.valueOf(port));
      server.start();
      server.join();
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Servlet crashed. Service shutting down.", e);
    }
  }
}
