package com.digitaluppercut.dmssplugin;

import java.util.logging.Logger;
import java.lang.Thread;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

/**
*
* @author Clinton Alexander & Jake Clarke
*/
public class DeadMansSwitchServer extends Plugin  {
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
	}

	// Sends a message to all players!
	public void broadcast(String message) {
		for (Player p : etc.getServer().getPlayerList()) {
			p.sendMessage(message);
		}
	}
	
	private class DMSControlThread extends Thread {
		
		private boolean threadEnabled;
		private int 	waited 			= 0, 
		private long 	waitMax;
		private long 	waitMS			= 100;
		private String 	domain;
		private int 	port;
		private String	file;
		
		public void run()
		{
			// Load properties
			PropertiesFile props = new PropertiesFile("DeadMansSwitchServer.properties");
			waitMax = (long) props.getInt("message-freq");
			domain = props.getString("message-domain");
			port   = props.getInt("message-port");
			file   = props.getString("message-file");
			key    = props.getString("message-key");
			server = props.getString("message-serverid");
			
			this.notifyServer("alive", waitMax);
			
			threadEnabled = true;
			while(threadEnabled)
			{
				if(waitMax <= waited)
				{
					// run the check in code.
					try {
						this.checkIn();
					} catch (DeadManNotifyException e) {
						log.error("[DeadMansSwitch] Host Unknown! Cannot tell server");
					}
					
					waited = 0;
				}
				
				// We have finished running we should wait for a while. 
				Thread.sleep(waitMS);
				// add the amount of time waited to the total
				waited += waitMS;
			}
			
		}
		
		/**
		* Check in with the server
		*
		* @return	void
		*/
		private void checkIn() {
			long time = System.currentTimeMillis / 1000;
			// Create url with all parameters
			String params = "status=alive&time=" + time + "&server=" + server
							+ "&key=" + key;
			this.sendRequest(params);
		}
		
		/**
		* Notify the server that we are starting the plugin
		*
		* @param	String		Status
		* @param	long		Time between requests
		* @return	void
		*/
		private void notifyServer(String status, long time) {
			String params = "status=" + status + "&wait=" + time;
			this.sendRequest(params);
		}
		
		/**
		* Notify the server that we're shutting the plugin
		*
		* @param	String		Status
		* @return	void
		*/
		private void notifyServer(String status) {
			String params = "status=" + status;
		}
		
		/**
		* A simple request sender for our server URL
		*
		* @param	String		URL query string.
		* @return	void
		*/
		private void sendRequest(String params) {
			if(params.length > 0) {
				params = "&" + params;
			}
			params = "?serverid=" + server + "&key=" + key + params
			
			Url url = new Url(host + ":" + port + file + params);
			HttpURLConnection con = new HttpURLConnection(url);
			
			con.setRequest("GET");
			con.setInstanceFollowRedirects(true);
			con.setDoOutput(false);
			
			con.connect();
			con.disconnect();
			
			if(con.getResponseCode() != 200) {
				throw new DeadManNotifyException("Could not notify server");
			}
			
		}
		
		public void haltThread()
		{
			threadEnabled = false;
			this.notifyServer("halt");
		}
	}
	
	private class DeadManNotifyException extends Exception {}
}
