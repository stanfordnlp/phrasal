package edu.stanford.nlp.mt.tools.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import edu.stanford.nlp.mt.tools.service.PhrasalLogger.LogName;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Top-level class that loads the Phrasal servlet.
 * 
 *
 * @author Spence Green
 */
public final class PhrasalService {

  private static String DEFAULT_URL = "127.0.0.1";
  private static int DEFAULT_HTTP_PORT = 8017;

  private PhrasalService() {}

  private static Map<String, Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = new HashMap<String,Integer>();
    optionArgDefs.put("-p", 1);
    optionArgDefs.put("-d", 0);
    return optionArgDefs;
  }

  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append(String.format("Usage: java %s [OPTS] phrasal_ini%n%n", PhrasalService.class.getName()));
    sb.append("Options:").append(nl);
    sb.append(" -p       : Port (default: ").append(DEFAULT_HTTP_PORT).append(")").append(nl);
    sb.append(" -d       : Debug mode mock server").append(nl);
    return sb.toString();
  }

  public static void main(String[] args) {
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    int port = PropertiesUtils.getInt(options, "p", DEFAULT_HTTP_PORT);
    boolean debug = PropertiesUtils.getBool(options, "d", false);

    // Parse arguments
    String[] parsedArgs = options.getProperty("","").split("\\s+");
    if (parsedArgs.length != 1) {
      System.out.println(usage());
      System.exit(-1);
    }
    String phrasalIniFile = parsedArgs[0];

    Logger logger = Logger.getLogger(PhrasalService.class.getName());
    
    // Setup the jetty server
    Server server = new Server();
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(port);
    server.addConnector(connector);

    // Setup the servlet context
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
 
    // Add Servlets
    if (debug) {      
      PhrasalLogger.disableConsole = false;
      PhrasalLogger.attach(logger, LogName.Service);
      logger.info("Debug mode. Loading " + PhrasalServletMock.class.getName());
      
      connector.setHost(DEFAULT_URL);

      System.err.printf("URL: http://%s:%d/debug.html%n", DEFAULT_URL, port);
      
      // Add the mock servlet
      context.addServlet(new ServletHolder(new PhrasalServletMock()), "/t");
      
      // Add debugging web-page
      ResourceHandler resourceHandler = new ResourceHandler();
      resourceHandler.setWelcomeFiles(new String[]{ "debug.html" });
      resourceHandler.setResourceBase(".");

      HandlerList handlers = new HandlerList();
      handlers.setHandlers(new Handler[] { resourceHandler, context });
      server.setHandler(handlers);
    
    } else {
      PhrasalLogger.attach(logger, LogName.Service);
      logger.info("Loading servlet " + PhrasalServlet.class.getName());      
      server.setHandler(context);
      context.addServlet(new ServletHolder(new PhrasalServlet(phrasalIniFile, null)),"/t");
    }
    
    // Start the service
    try {
      System.err.printf("Starting PhrasalService on port %d...%n", port);
      logger.info("Starting PhrasalService on port " + String.valueOf(port));
      server.start();
      server.join();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
