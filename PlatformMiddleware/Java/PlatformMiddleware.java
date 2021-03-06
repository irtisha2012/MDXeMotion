import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;


/*
 *  Main module for motion platform middleware
 *  Handles messages from clients queued in PlatformItf thread.
 *  After data is processed for gain and washout, raw muscle lengths
 *  are calculated from rotational data and sent to the MDX platform 
 */

public class PlatformMiddleware {
    public static LinkedList<PlatformApi.msgFields> msgQueue; // the incoming movement requests
    private final Lock lock = new ReentrantLock();  // lock for the incoming queue
    private PlatformApi api;  // handles incoming messages
    private PlatfromTransforms transform; // handles movement transforms
    private EffectorInterface effectorItf; // interface to mover and watchers
    private String effectorName;
    
    private static InetAddress effectorIp = null;;
    private static int effectorPort = 10003;
    private static int watcherPort = 10004; 

    
    public PlatformMiddleware() {
        this.effectorItf = new EffectorInterface(); 
        this.transform = new PlatfromTransforms();
        this.api = new PlatformApi(lock);       
    }
     
    private void begin(InetAddress ip, int port, int watcherPort){
       // Connect to effector (and watchers) on the given ports...
       // this loops until it can connect to chair      
       while(true) { 	
          EffectorInterface.effectorDef def = effectorItf.begin(ip, port, watcherPort);
          if(def != null) {        
             transform.begin(def);
             effectorName = new String(def.name);
             break;
          }
          else {        	  
        	  System.out.println(String.format("Unable to connect to effector at: %s, retrying... (is the server running?)", ip.getHostAddress()));
        	   
           }     
       }    
       
       api.begin();
       Gui.guiBegin(api.cfg);
       Gui.updateLabels("out", effectorName + " Connected");
       //this.api.getConfigArray(); 
       System.out.println("Waiting for client (restart client if already running)");
       return;
    }
    
    private void end(){
       api.end();
       effectorItf.end();
       Gui.updateLabels("out","Not Connected to a Platform");
      
    }
    
    private void processMsg(PlatformApi.msgFields msg) {
        // System.out.format("pre shape: %f,%f,%f, %f,%f,%f\n",msg.v[0], msg.v[1], msg.v[2],msg.v[3], msg.v[4], msg.v[5]);
           
    	   if(msg.isActivate){  // activate or deactivate the platform
    		   PlatformApi.activateMsg m = (PlatformApi.activateMsg)msg;
    		   if( m.isActive){
    			   System.out.println("activating");
    			   effectorItf.activateEffector(true);
    		   }
    		   else{
    			   System.out.println("deactivating");
    			   effectorItf.activateEffector(false);
    		   }
    			   
    	   }
           else {
        	   // its a movement message - todo add error checking here
               msg = PlatformApi.shapeData((PlatformApi.xyzMsg)msg); // apply gain and washout only if xyzMsg          
               transform.applyTranslationAndRotation(new PVector(msg.v[0], msg.v[1], msg.v[2]),
                                                     new PVector(msg.v[3], msg.v[4], msg.v[5]));
               System.out.format("middleware out: %f,%f,%f, %f,%f,%f\n",msg.v[0], msg.v[1], msg.v[2],msg.v[3], msg.v[4], msg.v[5]);                      
               float rawVal[] = new float[6];
               for(int i=0; i < 6; i++ ) {
                   rawVal[i] = transform.getRawLength(i);
                   System.out.print(i); System.out.print("="); System.out.println(rawVal[i]);
               }               
               effectorItf.sendMoveEvent( rawVal, msg.v);
               
               Gui.updateActuators(rawVal);
           }     
    }   
    
    private void sendTestMsgs() {
        PlatformApi.xyzMsg msg1; 
        float x= (float)0.0;
        float y= (float)0.0; 
        float z= (float)0.0; 
        msg1=  api.createTestMsg(x,y,z);
        processMsg(msg1);
        z= (float).999;
        msg1=  api.createTestMsg(x,y,z);
        processMsg(msg1);
        z= (float)-0.999;
        msg1=  api.createTestMsg(x,y,z);
        processMsg(msg1);
        x=y=z= (float)0.999;
        msg1=  api.createTestMsg(x,y,z);
        processMsg(msg1);
        PlatformApi.activateMsg msg;
        try{
           msg=  api.createTestMsg(false);        
           processMsg(msg);		
           Thread.sleep(3000);
           msg=  api.createTestMsg(true);        
           processMsg(msg);		
           Thread.sleep(3000);
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }   		
        x=y=z= (float)-0.999;
        msg1=  api.createTestMsg(x,y,z);
        processMsg(msg1);
      
        
    }
    
    private static int tryParseInt(String value, int defaultVal) { 
        if( value != null)
         try {  
             return Integer.parseInt(value);  
          } catch(NumberFormatException nfe) {          
              return defaultVal;
          }  
          return defaultVal;
    }
    
    private static boolean readConfigFile() {
        JSONParser parser = new JSONParser();        
        try {
            effectorIp = InetAddress.getLocalHost(); // default
        } catch (UnknownHostException e1) {         
            e1.printStackTrace();
        } 
        
        try { 
            Object obj = parser.parse(new FileReader("./middleware.cfg"));
 
            JSONObject jsonObject = (JSONObject) obj;
 
            String ip = (String) jsonObject.get("effectorIp");
            String eport = (String) jsonObject.get("effectorPort");
            String wport = (String) jsonObject.get("watcherPort");            
        
            if(ip != null ) {
                try  {
                  effectorIp = InetAddress.getByName(ip);          
                }
                catch ( UnknownHostException e )  {
                  System.out.println("Could not find IP address for: " + ip);                
                }
            }
            effectorPort = tryParseInt(eport, effectorPort);
            watcherPort = tryParseInt(wport, watcherPort);
   
                
 
        } catch(FileNotFoundException e){
            System.out.print("middleware.cfg file not found, using defualts - ");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(String.format("Primary Effector %s:%d, watcher port %d",
                effectorIp.getHostAddress(),effectorPort, watcherPort ));
        return true; // todo return false if error reading file ???
    }   
   
    public static void main(String[] args) {        
        if(!readConfigFile()){  // get optional ip address overrides 
             System.exit(-1);
        }                
        PlatformMiddleware pm = new PlatformMiddleware();       
        pm.begin(effectorIp, effectorPort, watcherPort); 
        //pm.sendTestMsgs();
        try {
            msgQueue = new LinkedList<PlatformApi.msgFields>();
            

            while (true) {
                PlatformApi.msgFields msg = null;
                pm.lock.lock();
                if (PlatformMiddleware.msgQueue.size() > 0) {
                    msg = (PlatformApi.msgFields) PlatformMiddleware.msgQueue.getFirst();
                    PlatformMiddleware.msgQueue.removeFirst();
                }
                pm.lock.unlock();
                if (msg != null) {              
                    pm.processMsg(msg);
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } finally {
            pm.end();
            System.out.println("Finished");

        }
    }

}
