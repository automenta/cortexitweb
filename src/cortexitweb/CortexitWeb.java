/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cortexitweb;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Restlet;
import org.restlet.Server;
import org.restlet.data.Protocol;
import org.restlet.resource.Directory;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

/**
 *
 * @author me
 */
public class CortexitWeb extends ServerResource {  

   final String cortexitHost = "http://24.131.65.222:8182";
   final String staticHost = "http://24.131.65.222:8183";
    
   int maxSentenceLength = 128;

   public static void main(String[] args) throws Exception {  
        Server server = new Server(Protocol.HTTP, 8182, CortexitWeb.class);
        server.start();  
      
      // Create a component  
        Component component = new Component();  
        component.getServers().add(Protocol.HTTP, 8183);  
        component.getClients().add(Protocol.FILE);  

        // Create an application  
        Application application = new Application() {  
            @Override  
            public Restlet createInboundRoot() {  
                    return new Directory(getContext(), "file:///work/cortexitweb/web");  
            }  
        };  
          
        // Attach the application to the component and start it  
        component.getDefaultHost().attach(application);  
        component.start();  
   }

   public void readFileInto(String path, StringBuffer b) throws Exception {
        BufferedReader fr = new BufferedReader(new FileReader(path));
        while (fr.ready())
            b.append(fr.readLine().replace("%%STATICHOST%%", staticHost)+"\n");
        fr.close();       
   }
   
   public String cortexify(final String host, String url) {
       URL u;
       try {
           u = new URL(url);
       }
       catch (MalformedURLException e) {
            try {
                if (url.startsWith("//"))
                    url = url.substring(2);
                else if (url.startsWith("/"))
                    url = url.substring(1);
                
                u = new URL("http", host, url);
            } catch (MalformedURLException ex) {
                return "/";
            }
       }
       
       return cortexitHost + "/" + url;
   }
   
   @Get("html")
   public String toString() {         
        try {
            String path = getReference().getPath();
            if (path.equals("/favicon.ico"))
                return "";
            
            if (path.length() > 0) {
                path = path.substring(1);
                return getRemotePage(path);           
            }
            else {
                return getHomePage();
            }
        } catch (Exception ex) {
            return getErrorPage(ex);
        }        
   }

   public static String escape(String s) {       
       return s.replace("'", "\\'");
   }
   
   private String getHomePage() {
        
        StringBuffer x = new StringBuffer();
        try {
            readFileInto("home.html", x);
        } catch (Exception ex1) {
            Logger.getLogger(CortexitWeb.class.getName()).log(Level.SEVERE, null, ex1);
        }
        
        String c = "addFrame('" + x.toString() + "')";
        return getCortexitPage("Error", c);
    }
   
   private String getErrorPage(Exception ex) {
        String errorText = ex.toString() + "<br/>";
        
        StringWriter sw = new StringWriter();        
        sw.append("<ul>");
        for (StackTraceElement ste : ex.getStackTrace()) {
            sw.append(ste.toString() + "<br/>");
        }
        sw.append("</ul>");
        
        String c = "addFrame('" + escape(errorText) + "<br/>" + sw.toString() + "');\n";
        return getCortexitPage("Error", c);
    }
    
    private String getRemotePage(String path) throws Exception {

        Logger.getLogger(CortexitWeb.class.toString()).info("Loading remote: " + path);
        
        URL target;
        try {
            target = new URL(path);
        }
        catch (MalformedURLException e) {
            target = new URL("http://" + path); 
        }
        
        
        Document doc = Jsoup.parse(target, 8000);
        String title = doc.getElementsByTag("title").first().text();

        Elements links = doc.select("a[href]"); // a with href
        for (Element e : links) {                
            e.prepend("{a href=\"" + cortexify(target.getHost(), escape(e.attr("href"))) + "\" target=\"_blank\"}");
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
            if (s.length() < 1)
                continue;
            if (s.length() > maxSentenceLength) {
                //...
            }
            //TODO proper string escaping.. this is a HACK!
            commands.append("addFrame('" + escape(s) + "');\n");
        }
        
        return getCortexitPage(title, commands.toString());
    }

    private String getCortexitPage(String title, String frameCommands)  {
        StringBuffer b = new StringBuffer();

        b.append("<html>");


        b.append("<title>Cortexit - " + title + "</title>");
        
        try {
            readFileInto("./web/cortexit.html", b);
        } catch (Exception ex) {
            Logger.getLogger(CortexitWeb.class.getName()).log(Level.SEVERE, null, ex);
        }

        b.append("<script>");


        b.append(frameCommands);

        b.append("currentFrame = 0;");
        b.append("showFrame(currentFrame);");

        b.append("</script>");
        b.append("</html>");

        return b.toString();            
    }

    
}

