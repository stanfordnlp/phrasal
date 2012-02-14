package edu.stanford.nlp.mt.tools.aPTM;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jetty.server.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;

import edu.berkeley.nlp.util.StringUtils;

/**
 * Embedded servlet loader for the MT webapp.
 * <p>
 * Implements the Cross-Origin Resource Sharing specification.
 * 
 * @author Spence Green
 *
 */
public class MTAppServer {

  public static final String DEBUG_PROPERTY = MTAppServer.class.getSimpleName();
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));
  
  private static int HTTP_PORT = 8017;
  
  private static boolean MOCK_SERVER = false;
  
  // CORS filter settings
  private static String ALLOWED_URIS = "http://127.0.0.1:8000";
  private static final String ALLOWED_METHODS = "*";
  private static final String ALLOWED_CREDENTIALS = "true";
  
  // MT System ini file
  private static String MT_INI = null;
  private static String MT_WA_MODEL = null;

  private static final Map<String,Integer> optionArgDefs = new HashMap<String,Integer>();
  static {
    optionArgDefs.put("-p", 1);
    optionArgDefs.put("-u", 1);
    optionArgDefs.put("-i", 1);
    optionArgDefs.put("-d", 0);
    optionArgDefs.put("-w", 1);
  }
  
  private static void setOptions(String[] args) {
    final Map<String,String[]> opts = StringUtils.argsToMap(args, optionArgDefs);
    for (String key : opts.keySet()) {
      if (key == null) {
        // Parsed options
        continue;
      } else if (key.equals("-p")) {
        HTTP_PORT = Integer.valueOf(opts.get(key)[0]);
      } else if (key.equals("-u")) {
        ALLOWED_URIS = opts.get(key)[0];
      } else if (key.equals("-i")) { 
        MT_INI = opts.get(key)[0];
      } else if (key.equals("-w")) {
        MT_WA_MODEL = opts.get(key)[0];
      } else if (key.equals("-d")) {
        MOCK_SERVER = true;
      } else {
        System.err.println(usage());
        System.exit(-1);
      }
    }
  }
  
  private static String usage() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Usage: java %s [OPTS]%n", MTAppServer.class.getName()));
    return sb.toString();
  }
  
  /**
   * Setup the servlet context. This context includes a global CORS filter.
   * 
   * @return
   */
  private static ServletContextHandler getContext() {
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
    filterHolder.setInitParameter("allowedOrigins", ALLOWED_URIS);
    filterHolder.setInitParameter("allowedMethods", ALLOWED_METHODS);
    filterHolder.setInitParameter("allowCredentials", ALLOWED_CREDENTIALS);
    EnumSet<DispatcherType> all = EnumSet.of(DispatcherType.ASYNC, DispatcherType.ERROR, DispatcherType.FORWARD,
        DispatcherType.INCLUDE, DispatcherType.REQUEST);
    context.addFilter(filterHolder, "/*" , all );
    return context;
  }

  public static void main(String[] args) {
    if (args.length > 0 && args[0].equals("-help")) {
      System.err.println(usage());
      System.exit(-1);
    }
    setOptions(args);
    
    Server server = new Server(HTTP_PORT);
    ServletContextHandler context = getContext();
    server.setHandler(context);

    // Add Servlets
    // TODO(spenceg): If we add more than one, will need to put the servlets on different URIs
    if (MOCK_SERVER) {
      // Add mock servlets here
      context.addServlet(new ServletHolder(new PhrasalUnifiedServletMock()),"/*");          
    } else {
      context.addServlet(new ServletHolder(new PhrasalUnifiedServlet(MT_INI, MT_WA_MODEL)),"/*");          
    }

    try {
      server.start();
      server.join();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
