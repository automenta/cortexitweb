/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cortexitweb;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.activation.MimetypesFileTypeMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 *
 * @author seh
 */
public class HTTPServer {

    public abstract static class HTTPHandler implements HttpHandler {

        private HttpExchange exchange;
        private Headers responseHeaders;

        public void setHeader(int code, String contentType) throws IOException {
            responseHeaders.put("Content-Type", Arrays.asList(new String[]{contentType}));
            exchange.sendResponseHeaders(code, 0);
        }

        public void handle(HttpExchange exchange) throws IOException {

            String requestMethod = exchange.getRequestMethod();
            if (requestMethod.equalsIgnoreCase("GET")) {
                this.exchange = exchange;
                this.responseHeaders = exchange.getResponseHeaders();

                OutputStream out = exchange.getResponseBody();
                
                try {
                    respond(out, exchange.getRequestURI().toString());
                } catch (Exception e) {
                    System.err.println(e);
                    e.printStackTrace();
//                    setHeader(200, "text/html");
//                    respondError(out, e);
                }
                out.close();
                
            }
        }

        abstract public void respond(OutputStream out, String request) throws Exception;

        abstract public void respondError(OutputStream out, Exception e);
    }
    
    static String cortexitHost;
    static String staticHost;
    static String staticPath;
    static int maxSentenceLength = 128;
    static int requestTimeoutMS = 8000;

    
    public static void readFileInto(String path, StringBuffer b) throws IOException {
        BufferedReader fr = new BufferedReader(new FileReader(path));
        while (fr.ready()) {
            b.append(fr.readLine().replace("%%STATICHOST%%", staticHost) + "\n");
        }
        fr.close();
    }
    public final static MimetypesFileTypeMap mimeMap = new MimetypesFileTypeMap();

    public static String getContentType(String file) {
        if (file.endsWith(".svg")) {
            return "image/svg+xml";
        }
        else if (file.endsWith(".js")) {
            return "text/javascript";
        }
        else if (file.endsWith(".css")) {
            return "text/css";
        }
        return mimeMap.getContentType(file);
    }

    final static byte[] bytes = new byte[4096];
    
    public static boolean fileExists(String path) {
            File f = new File("./web/" + path);
            return f.isFile();
    }
    
    public static void writeStaticPage(OutputStream out, String path) throws IOException {
        FileInputStream fr = new FileInputStream("./web/" + path);
        out.flush();
        while (fr.available() > 0) {
            int read = fr.read(bytes, 0, bytes.length);
            out.write(bytes, 0 ,read);
            out.flush();
        }
        fr.close();
    }

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

    public static String escape(String s) {
        return s.replace("'", "\\'");
    }

    public static String frameEscape(String s) {
        return escape(s).replace("\n", " ").trim();
    }


    public static String getRemotePage(String path) throws Exception {

        URL target;
        try {
            target = new URL(path);
        } catch (MalformedURLException e) {
            target = new URL("http://" + path);
        }

        //Logger.getLogger(CortexitWeb.class.toString()).info("Loading remote: " + target + " : " + path);

        Document doc = Jsoup.parse(target, requestTimeoutMS);
        String title = doc.getElementsByTag("title").first().text();

        Elements links = doc.select("a[href]"); // a with href
        for (Element e : links) {
            e.prepend("{a href=\"" + cortexifyURL(target.getHost(), escape(e.attr("href"))) + "\" target=\"_blank\"}");
            e.append("{/a}");
        }
        Elements imgs = doc.select("img[src]"); // a with href
        for (Element e : imgs) {
            e.prepend("{img src=\"" + e.attr("src") + "\"}");
            //e.append("}. ");
        }

        StringBuffer commands = new StringBuffer();

        String t = doc.text();
        t = t.replace(". ", ".\n");
        t = t.replace("? ", "?\n");
        t = t.replace("! ", "!\n");
        t = t.replace("{", "<");
        t = t.replace("}", ">");
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
            commands.append("addFrame('" + frameEscape(s) + "');\n");
        }

        return getCortexitPage(title, commands.toString());
    }

    public static String getCortexitPage(String title, String frameCommands) {
        StringBuffer b = new StringBuffer();

        b.append("<html>");


        b.append("<title>Cortexit - " + title + "</title>");

        try {
            readFileInto("./web/cortexit.html", b);
        } catch (Exception ex) {
            Logger.getLogger(HTTPServer.class.getName()).log(Level.SEVERE, null, ex);
        }

        b.append("<script>");


        b.append(frameCommands);

        b.append("currentFrame = 0;");
        b.append("showFrame(currentFrame);");

        b.append("</script>");
        b.append("</html>");

        return b.toString();
    }

    public static void main(String[] args) throws IOException {
        Properties p = new Properties();
        p.load(new FileInputStream("cortexit.ini"));
        cortexitHost = p.getProperty("host");
        staticHost = p.getProperty("static");
        staticPath = p.getProperty("staticPath");

        InetSocketAddress addr = new InetSocketAddress(8182);
        HttpServer server = HttpServer.create(addr, 0);

        server.createContext("/", new HTTPHandler() {

            @Override
            public void respond(OutputStream out, String request) throws Exception {
                if ((request.length() == 0) || (request.equals("/"))) {
                    setHeader(200, "text/html");
                    respondHome(out);
                } else if (request.equals("/favicon.ico")) {
                    //...
                } else if (request.startsWith("/static")) {
                    final String staticFile = request.substring("/static".length());
                    if (!fileExists(staticFile)) {
                        setHeader(404, "text/html");
                        out.write(("Not found: " + staticFile).getBytes());
                    }
                    else {                        
                        setHeader(200, getContentType(staticFile));
                        respondStatic(out, staticFile);
                    }
                } else {
                    setHeader(200, "text/html");
                    respondCortexit(out, request);
                }
            }

            protected void respondHome(OutputStream out) throws IOException {
                StringBuffer x = new StringBuffer();
                try {
                    readFileInto("./web/home.html", x);
                } catch (Exception ex1) {
                    Logger.getLogger(HTTPServer.class.getName()).log(Level.SEVERE, null, ex1);
                }

                String c = "addFrame('" + frameEscape(x.toString()) + "');";
                out.write(getCortexitPage("Cortexit", c).getBytes());
            }

            protected void respondStatic(OutputStream out, String staticFile) throws IOException {
                writeStaticPage(out, staticFile);
            }

            protected void respondCortexit(OutputStream out, String url) throws Exception {
                out.write(getRemotePage(url.substring(1)).getBytes());
            }

            @Override
            public void respondError(OutputStream out, Exception e) {
                try {
                    setHeader(200, "text/html");
                } catch (IOException ex) {
                    Logger.getLogger(HTTPServer.class.getName()).log(Level.SEVERE, null, ex);
                }
                
                String errorText = e.toString() + "<br/>";

                StringWriter sw = new StringWriter();
                sw.append("<ul>");
                for (StackTraceElement ste : e.getStackTrace()) {
                    sw.append(ste.toString() + "<br/>");
                }
                sw.append("</ul>");

                String c = "addFrame('" + frameEscape(errorText) + "<br/>" + sw.toString() + "');\n";
                try {
                    out.write(getCortexitPage("Error", c).getBytes());
                } catch (IOException ex) {
                    Logger.getLogger(HTTPServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        //server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
    }
}
