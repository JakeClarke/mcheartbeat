//package com.digitaluppercut.dmssplugin;

import java.util.logging.Logger;
import java.lang.Thread;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.IOException;
import java.io.InputStreamReader;

/**
*
* @author Clinton Alexander
* @author Jake Clarke
*/
public class DeadMansSwitchServer extends Plugin  {
	private Listener l = new Listener(this);
	protected static final Logger log = Logger.getLogger("Minecraft");
	private String name = "DeadMansSwitchServer";
	private String version = "0.1";
	private DMSControlThread ct = null;
		
	public void enable() {
		
		if(ct == null || !ct.isAlive())
		{
			ct = new DMSControlThread();
			ct.start();
		}
		
	}
	
	public void disable() {
		
		ct.haltThread();
	}

	public void initialize() {
		log.info(name + " " + version + " initialized");
		// Watch for server shutdowns
		etc.getLoader().addListener(PluginLoader.Hook.SERVERCOMMAND,
									l, this, PluginListener.Priority.MEDIUM);
	}

	// Sends a message to all players!
	public void broadcast(String message) {
		for (Player p : etc.getServer().getPlayerList()) {
			p.sendMessage(message);
		}
	}
	
	/**
	 * The thread that keeps the DMS notices active
	 * to prevent the death signal 
	 * 
	 * @author Clinton Alexander
	 * @author Jake Clarke
	 */
	private class DMSControlThread extends Thread {
		
		private boolean threadEnabled;
		private long 	waitMax;
		private String 	domain;
		private int 	port;
		private String	file;
		private String 	key;
		
		public void run() {
			// Load properties
			PropertiesFile props = new PropertiesFile("DeadMansSwitchServer.properties");
			waitMax = (long) props.getInt("message-freq");
			domain = props.getString("message-domain");
			port   = props.getInt("message-port");
			file   = props.getString("message-file");
			key = props.getString("message-key");
			
			try {
				this.notifyServer("start", waitMax);
				threadEnabled = true;
			} catch (DeadManNotifyException e) {
				threadEnabled = false;
				log.info("[DeadManSwitch] " + e.getMessage());
			}
			
			while(threadEnabled) {
				// run the check in code.
				try {
					this.checkIn();
				} catch (DeadManNotifyException e) {
					log.info("[DeadMansSwitch] Error: Cannot tell server because: " + e.getMessage());
				}
				
				try {
					// Half of the time (in MS)
					Thread.sleep(waitMax * 900);
				} catch (InterruptedException e) {
					// Ignore this exception
					log.info("[DeadMansSwitch] Interrupted wait");
				}	
			}
		}
		
		/**
		* Check in with the server
		*
		* @return	void
		*/
		private void checkIn() throws DeadManNotifyException {
			// Create url with all parameters
			String params = "status=alive";
			String response = this.sendRequest(params);
			
			if(response == "OKAY") { 
				log.info("[DeadMansServer] Check-in OKAY.");
			} else if(response == "RECONNECT") {
				log.info("[DeadMansServer] Daemon has dropped this server. Reconnecting");
				// Reconnection
				try {
					this.notifyServer("start", waitMax);
				} catch (DeadManNotifyException e) {
					threadEnabled = false;
					log.info("[DeadManSwitch] " + e.getMessage());
				}
			} else {
				log.info("[DeadMansServer] Query sent is malformed. Response: " + response);
			}
		}
		
		/**
		* Notify the server that we are starting the plugin
		*
		* @param	String		Status
		* @param	long		Time between requests
		* @return	void
		*/
		private void notifyServer(String status, long wait) throws DeadManNotifyException {
			log.info("[DeadMansServer] Notifying DMS server of my existance.");
			long time = (System.currentTimeMillis() / 1000) + wait;
			String params = "status=" + status + "&wait=" + wait + "&time=" + time;
			this.sendRequest(params);
		}
		
		/**
		* Notify the server that we're shutting the plugin
		*
		* @param	String		Status
		* @return	void
		*/
		private void notifyServer(String status) throws DeadManNotifyException {
			log.info("[DeadMansServer] Notifying DMS server that I am closing.");
			String params = "status=" + status;
			this.sendRequest(params);
		}
		
		/**
		* A simple request sender for our server URL
		*
		* @param	String		URL query string.
		* @return	String		Response to query
		* @throws 	DeadManNotifyException
		*/
		private String sendRequest(String params) throws DeadManNotifyException {
			InputStreamReader in;
			// Response of server
			String response = "";
			if(params.length() > 0) {
				params = "&" + params;
			}
			params = "?key=" + key + params;
			//log.info("[DMSSDebug] Params: " + params);
			try {
				URL url = new URL(domain + ":" + port + file + params);
				HttpURLConnection con = (HttpURLConnection)url.openConnection();
				
				con.setRequestMethod("GET");
				con.setInstanceFollowRedirects(true);
				con.setDoOutput(true);
				
				con.connect();
				con.disconnect();
				
				// Get the response and output from the call
				int code = con.getResponseCode();
				in = new InputStreamReader(
                         con.getInputStream());
				char tmp;
				/*while((tmp = (char) in.read()) != -1) {
					response = response + tmp;
				}*/
				// 200 is "OKAY". 
				if(code != 200) {
					throw new DeadManNotifyException("Could not notify server. Code: " 
							+ code + ". Message: " + response);
				}
			// Convert to deadmannotify
			} catch (IOException e) {
				throw new DeadManNotifyException(e.getMessage());
			}
			
			return response;
		}
		
		public void haltThread()
		{
			threadEnabled = false;
			
			try {
				this.notifyServer("halt");
			} catch (DeadManNotifyException e) {
				log.info("[DeadManSwitch]" + e.getMessage());
			}
		}
	}
	
	private class DeadManNotifyException extends Exception {
		private static final long serialVersionUID = 984209840;
		public DeadManNotifyException(String msg) {
			super(msg);
		}
	}
	
	private class Listener extends PluginListener {
		private DeadMansSwitchServer s;
		/**
		 * Constructor
		 */
		public Listener(DeadMansSwitchServer s) {
			this.s = s;
		}
		
		/**
		 * When console sends "Stop" halt!
		 * Manual shutdowns aren't included 
		 * 
		 * @param 	String 	cmd
		 * @return 	boolean
		 */
		public boolean onConsoleCommand(String[] split) {
			if(split[0].equals("stop")) { 
				s.disable(); 
			}
			return false;
		}
	}
}
