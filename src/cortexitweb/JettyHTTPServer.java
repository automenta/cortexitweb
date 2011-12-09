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
import java.net.URLDecoder;
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

    final static char[] bytes = new char[4096*16];
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

        //return cortexitHost + "/" + url;
        return url;
        
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
            out.write(bytes, 0, read);
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
    static URL defaultTarget;

    static {
        try {
            defaultTarget = new URL("http://cortexit.org");
        } catch (Exception e) {
        }
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

        AbstractHandler handler = new AbstractHandler() {

            private void writeCortexitTemplate(PrintWriter out) {
                try {
                    out.print("<html>");
                    readFileInto(out, "./web/cortexit.html");
                } catch (Exception ex) {
                    Logger.getLogger(JettyHTTPServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            protected void respondCortexit(String request, PrintWriter out) {
                writeCortexitTemplate(out);

                try {
                    if ((request.length() == 0) || (request.equals("/"))) {
                        writeLocalPage(out, "./web/about.html", "About Cortexit", false);
                    } else if (request.equals("/bookmarklet")) {
                        writeLocalPage(out, "./web/bookmarklet.html", "Cortexit Bookmarklet", false);
                    } else if (request.equals("/privacy")) {
                        writeLocalPage(out, "./web/privacy.html", "Privacy Policy", false);
                    } else if (request.equals("/contact")) {
                        writeLocalPage(out, "./web/contact.html", "Contact Us", false);
                    } else if (request.equals("/support")) {
                        writeLocalPage(out, "./web/support.html", "Support", false);
                    } else if (request.equals("/go")) {
                        writeLocalPage(out, "./web/go.html", "Go", false);
                    } else {
                        try {
                            writeRemotePage(out, request.substring(1));
                        } catch (UnknownHostException x) {
                            final String y = "Unknown site: " + request.substring(1);
                            writeCortexitPage(out, "Cortexit: Page Not Found", "addFrame(\"" + frameEscape(y) + "\");");
                        }
                    }
                } catch (Exception e) {
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
            public void writePageRaw(PrintWriter out, URL target, String title, Document doc) throws Exception {

                StringBuffer commands = new StringBuffer();

                commands.append("_f(\"" + frameEscape(doc.toString()) + "\");\n");
                
                if (target == null) {
                    target = defaultTarget;
                }
                
                commands.append("setOriginal(\"" + target + "\");\n");

                writeCortexitPage(out, title, commands.toString());
                
            }

            public void writePageCleaned(PrintWriter out, URL target, String title, Document doc) throws Exception {
                
                if (target != null) {
                    Elements links = doc.select("a[href]");
                    for (Element e : links) {
                        e.prepend("{{a href=\"" + escape(cortexifyURL(target.getHost(), escape(e.attr("href")))) + "\" target=\"_blank\"}}");
                        e.append("{{/a}}");
                    }
                }
                else {
                    Elements links = doc.select("a[href]");
                    for (Element e : links) {
                        e.prepend("{{a href=\"" + links.attr("href") + "\" target=\"_blank\"}}");
                        e.append("{{/a}}");
                    }                    
                }

                Elements imgs = doc.select("img[src]");
                for (Element e : imgs) {
                    e.prepend("{{img src=\"" + e.attr("src") + "\"/}}");
                }
                final String liBreak = "{{br}}";
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
                    commands.append("_f(\"" + frameEscape(s) + "\");\n");
                }
                if (target == null) {
                    target = defaultTarget;
                }
                commands.append("setOriginal(\"" + target + "\");\n");

                writeCortexitPage(out, title, commands.toString());

            }

            public void writeLocalPage(PrintWriter out, String path, String title, boolean filtered) throws Exception {

                StringBuffer sb = new StringBuffer();
                readFileInto(path, sb);

                Document doc = Jsoup.parse(sb.toString());
                
                if (filtered)
                    writePageCleaned(out, null, path, doc);
                else
                    writePageRaw(out, null, title, doc);
                    

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
                writePageCleaned(out, target, title, doc);
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
                b.write("<title>Cortexit" + ((title != null) ? (" - " + title) : "") + "</title>");
                b.write("</html>");

            }

            public void handle(String target, HttpServletRequest r, HttpServletResponse response, int dispatch)
                    throws IOException, ServletException {

                r.setCharacterEncoding("UTF-8");

                String request = r.getRequestURI();

                final PrintWriter out = response.getWriter();

                if (request.equals("/favicon.ico")) {
                    response.setContentType("text/jpg");
                    response.setStatus(HttpServletResponse.SC_OK);
                    readFileInto(out, "./web/favicon.ico");
                    ((Request) r).setHandled(true);
                } else {
                    response.setContentType("text/html");
                    response.setStatus(HttpServletResponse.SC_OK);

                    String url = r.getParameter("url");
                    if (url != null) {
                        url = URLDecoder.decode(url, "UTF-8");
                    } else {
                        url = "http://cortexit.org";
                    }

                    String a = r.getParameter("text");
                    if (a != null) {
                        try {
                            a = URLDecoder.decode(a, "UTF-8");
                        } catch (IllegalArgumentException e) {
                            //a = a;
                        }

                        writeCortexitTemplate(out);
                        Document doc = Jsoup.parse(a);

                        //TODO clean this and merge with above code for obtaining string of url
                        URL u;
                        try {
                            u = new URL(url);
                        } catch (MalformedURLException e) {
                            u = defaultTarget;
                        }

                        try {
                            writePageCleaned(out, u, null, doc);
                        } catch (Exception ex) {
                            Logger.getLogger(JettyHTTPServer.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    } else {
                        respondCortexit(request, response.getWriter());
                    }

                    ((Request) r).setHandled(true);
                }

            }
        };

        Server server = new Server(8182);

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{fileHandler, handler});

        server.setHandler(handlers);

        server.start();
        server.join();

    }
}
