package com.vonhof.webi.websockets;

import com.vonhof.babelshark.BabelShark;
import com.vonhof.babelshark.language.JsonLanguage;
import com.vonhof.webi.file.FileRequestHandler;
import com.vonhof.webi.HttpMethod;
import com.vonhof.webi.Webi;
import com.vonhof.webi.annotation.Body;
import com.vonhof.webi.annotation.Parm;
import com.vonhof.webi.annotation.Path;
import com.vonhof.webi.rest.RESTServiceHandler;
import org.eclipse.jetty.websocket.api.Session;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;


public class WebiServer {
    public static void main(String[] args) throws Exception {
        //Add JSON to BabelShark
        // - Webi uses BabelShark to serialize and deserialize and so this will allow Webi to talk JSON.
        BabelShark.register(new JsonLanguage());
        
        //Tell webi to bind to port 8081 - it wont start listening until you call start();
        final Webi webi = new Webi(8081, 100, 0, 50);
        //Initialize websocket service
        WebSocketFilter webSocketFilter = new WebSocketFilter();
        webSocketFilter.add("/socket/",new SocketService<SocketTest>(SocketTest.class));
        webi.add("/socket/",webSocketFilter);
        
        //Init simple file handler
        FileRequestHandler fileHandler = webi.add("/",FileRequestHandler.getStandardFileHandler());
        fileHandler.setDocumentRoot(System.getProperty("user.home"));
        
        //Init ther REST request handler
        final RESTServiceHandler restHandler = webi.add("/rest/", new RESTServiceHandler());
        
        //Expose the hallo service. You can expose services both before and after you've started webi.
        restHandler.expose(new HalloService());
        
        //Start the webi webserver
        webi.start();
    }
    
    /**
     * Tell webi to serve this class on the path /hallo/
     * The @Path annotation applies to types and methods
     */
    @Path("hallo")
    public static class HalloService {
        
        @Inject
        private SocketService<SocketTest> socketService;
        
        /**
         * Handle a GET request to /hallo/world/
         * Outputs a serialized string (JSON: "world")
         * @return 
         */
        public String world() {
            return "world";
        }
        
        /**
         * Handle a GET request to /hallo/complex/
         * Outputs the serialized map
         * @return 
         */
        @Path("complex")
        public Map<String,Object> map() {
            Map<String,Object> out = new HashMap<String, Object>();
            out.put("id", 1);
            out.put("name", "The Dude");
            out.put("age", 43);
            out.put("alive", true);
            return out;
        }
        
        /**
         * Handle a POST request to /hello/world/
         * The POST body will be deserialized into the body argument
         * @param body
         * @return 
         */
        @Path(value="world",method=HttpMethod.POST)
        public String worldPOST(@Body Map<String,Object> body) {
            return "world";
        }
        
        /**
         * All arguments are considered optional GET parameters if nothing else has been specified
         * Handles GET requests to /hello/parms/?test=world&other=stuff
         * @param text
         * @param other
         * @return 
         */
        public String parms(String text,String other) {
            return "hello "+ text + " other: "+ other;
        }
        
        /**
         * All arguments are considered optional GET parameters if nothing else has been specified
         * Handles GET requests to /hello/parms/?test=world&other=stuff
         * @param text
         * @return 
         */
        public void broadcast(@Parm(required=true) String text) {
            socketService.broadcast("msg", new SocketMsg("ChatBot", text));
        }
        
    }
    
    public static class SocketTest extends SocketService.Client<SocketTest> {
        private String name; 
        
        @Inject
        private HalloService halloService;
        
        
        @EventHandler
        public void register(String name) throws Exception {
            this.name = name;
            broadcast("welcome",name);
        }
        
        @EventHandler
        public void write(String text) throws Exception {
            broadcast("msg",new SocketMsg(name, text));
        }
        
        
        @EventHandler
        public void broadcast(String text) throws Exception {
            halloService.broadcast(text);
        }
        
        @EventHandler
        public void pm(String name,String text) throws Exception {
            List<SocketTest> clients = getService().getClients();
            for(SocketTest c:clients) {
                if (c.name.equalsIgnoreCase(name)) {
                    send(c,"msg",new SocketMsg(name, text));
                    break;
                }
            }
        }


        @Override
        public void onWebSocketConnect(Session session) {
            super.onWebSocketConnect(session);
            broadcast("entered", this);
            send("ready");
        }

        @Override
        public void onWebSocketClose(int closeCode, String message) {
            broadcast("left", this);
        }
    }
    
    public static class SocketMsg {
        public String name;
        public String msg;
        public Date time = new Date();

        public SocketMsg() {
            
        }

        public SocketMsg(String name, String msg) {
            this.name = name;
            this.msg = msg;
        }
    }
}
