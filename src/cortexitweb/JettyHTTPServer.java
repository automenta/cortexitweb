/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cortexitweb;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.handler.HandlerList;
import org.mortbay.jetty.handler.ResourceHandler;

/**
 *
 * @author seh
 */
public class JettyHTTPServer {

    final static char[] bytes = new char[4096];
    static String cortexitHost;
    static String staticPath;
    static int maxSentenceLength = 128;
    static int requestTimeoutMS = 8000;
    static int backlog = 64;

    public static String cortexifyURL(final String host, String url) {
        URL u;
        try {
            u = new URL(url);
        } catch (MalformedURLException e) {
            try {
                if (url.startsWith("//")) {
                    url = url.substring(2);
                } else if (url.startsWith("/")) {
                    url = url.substring(1);
                }

                u = new URL("http", host, url);
            } catch (MalformedURLException ex) {
                return "/";
            }
        }

        return cortexitHost + "/" + url;
    }
    
    public static void readFileInto(String path, StringBuffer b) throws IOException {
        BufferedReader fr = new BufferedReader(new FileReader(path));
        while (fr.ready()) {
            b.append(fr.readLine());            
        }
        fr.close();
    }

    public static void readFileInto(PrintWriter out, String staticFile) throws IOException {
        FileReader fr = new FileReader(staticFile);
        
        while (fr.ready()) {
            int read = fr.read(bytes, 0, bytes.length);
            out.write(bytes, 0 ,read);
            out.flush();
        }
        fr.close();
    }
    
    public static String escape(String s) {
        return s.replace("\"", "\\\"");
    }

    public static String frameEscape(String s) {
        return escape(s).replace("\n", " ").trim();
    }
    
    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        p.load(new FileInputStream("cortexit.ini"));
        cortexitHost = p.getProperty("host");
        staticPath = p.getProperty("staticPath");

        ContextHandler fileHandler = new ContextHandler();
        fileHandler.setResourceBase("./web");
        fileHandler.addHandler(new ResourceHandler());
        fileHandler.setContextPath("/static");
        
        AbstractHandler handler=new AbstractHandler() {

            private void writeCortexitTemplate(PrintWriter out) {
                try {
                    out.print("<html>");
                    readFileInto(out, "./web/cortexit.html");
                } catch (Exception ex) {
                    Logger.getLogger(JettyHTTPServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            
            protected void respondCortexit(String request, PrintWriter out)  {
                writeCortexitTemplate(out);
                
                if ((request.length() == 0) || (request.equals("/"))) {
                    
                    //TODO cache this
                    StringBuffer homepageBuffer = new StringBuffer();
                    try {
                        readFileInto("./web/home.html", homepageBuffer);
                    } catch (Exception ex1) {
                        Logger.getLogger(JettyHTTPServer.class.getName()).log(Level.SEVERE, null, ex1);
                    }

                    writeCortexitPage(out, "Cortexit", "addFrame(\"" + frameEscape(homepageBuffer.toString()) + "\");");

                }
                else {
                    try {
                        writeRemotePage(out, request.substring(1));
                    }
                    catch (UnknownHostException x) {                        
                        final String y = "Unknown site: " + request.substring(1);
                        writeCortexitPage(out, "Cortexit: Page Not Found", "addFrame(\"" + frameEscape(y) + "\");");                        
                    }
                    catch (Exception e) {
                        //TODO write a cortexit page with error
                        //out.println(e.toString());
                        //e.printStackTrace(out);
                        
                        StringBuffer x = new StringBuffer(e.toString() + ":");
                        for (final StackTraceElement s : e.getStackTrace()) {
                            x.append(s.toString() + " ");
                        }
                        writeCortexitPage(out, "Cortexit: Error", "addFrame(\"" + frameEscape(x.toString()) + "\");");
                    }
                }
                
            }

            public void writeRemotePage(PrintWriter out, String path) throws Exception {

                URL target;
                try {
                    target = new URL(path);
                } catch (MalformedURLException e) {
                    target = new URL("http://" + path);
                }

                //Logger.getLogger(CortexitWeb.class.toString()).info("Loading remote: " + target + " : " + path);

                Document doc = Jsoup.parse(target, requestTimeoutMS);
                String title = doc.getElementsByTag("title").first().text();

                Elements links = doc.select("a[href]");
                for (Element e : links) {
                    e.prepend("{{a href=\"" + cortexifyURL(target.getHost(), escape(e.attr("href"))) + "\" target=\"_blank\"}}");
                    e.append("{{/a}}");
                }
                Elements imgs = doc.select("img[src]"); 
                for (Element e : imgs) {
                    e.prepend("{{img src=\"" + e.attr("src") + "\"/}}");
                }
                final String liBreak = "{{li}}";
                for (Element e : doc.select("li")) {
                    e.append(liBreak);
                }
                for (Element e : doc.select("tr")) {
                    e.append(liBreak);
                }
                for (Element e : doc.select("hr")) {
                    e.append(liBreak);
                }
                for (Element e : doc.select("br")) {
                    e.append(liBreak);
                }
                

                StringBuffer commands = new StringBuffer();

                String t = doc.text();
                t = t.replace(". ", ".\n");
                t = t.replace("? ", "?\n");
                t = t.replace("! ", "!\n");
                t = t.replace(liBreak, "\n");
                t = t.replace("{{", "<");
                t = t.replace("}}", ">");
                String[] sentences = t.split("\n");
                for (String s : sentences) {
                    s = s.trim();
                    if (s.length() < 1) {
                        continue;
                    }
                    if (s.length() > maxSentenceLength) {
                        //...
                    }
                    //TODO proper string escaping.. this is a HACK!
                    commands.append("addFrame(\"" + frameEscape(s) + "\");\n");
                }
                commands.append("setOriginal(\"" + target + "\");\n");

                writeCortexitPage(out, title, commands.toString());
            }

//            public String getCortexitPage(String title, String frameCommands) {
//                StringBuffer b = new StringBuffer();
//                b.append("<script>");
//
//                b.append(frameCommands);
//
//                b.append("currentFrame = 0;");
//                b.append("showFrame(currentFrame);");
//
//                b.append("</script>");
//                b.append("<title>Cortexit - " + title + "</title>");
//                b.append("</html>");
//
//                return b.toString();
//            }
            public void writeCortexitPage(PrintWriter b, String title, String frameCommands) {
                b.write("<script>");

                b.write(frameCommands);

                writePageEnd(b, title);
            }
            public void writePageEnd(PrintWriter b, String title) {

                b.write("currentFrame = 0;");
                b.write("showFrame(currentFrame);");
                
                b.write("</script>");
                b.write("<title>Cortexit - " + title + "</title>");
                b.write("</html>");
                
            }
            
            public void handle(String target, HttpServletRequest r, HttpServletResponse response, int dispatch)
                throws IOException, ServletException {
                
                
                String request = r.getRequestURI();
                
                if (request.equals("/favicon.ico")) {
                    //...
                } else {
                    response.setContentType("text/html");
                    response.setStatus(HttpServletResponse.SC_OK);
                    respondCortexit(request, response.getWriter());
                    ((Request)r).setHandled(true);
                }

            }
        };
        
        Server server = new Server(8182);
 
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { fileHandler, handler });
        
        server.setHandler(handlers);
 
        server.start();
        server.join();
        
    }

}
