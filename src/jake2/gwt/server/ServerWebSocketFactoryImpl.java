/*
Copyright (C) 2010 Copyright 2010 Google Inc.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
package jake2.gwt.server;

import java.io.IOException;
import java.net.InetAddress;

import jake2.qcommon.Compatibility;
import jake2.qcommon.netadr_t;
import jake2.sys.QSocket;
import jake2.sys.QSocketFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketHandler;


public class ServerWebSocketFactoryImpl implements QSocketFactory {

	public QSocket bind(String ip, int port) {
		return new ServerWebSocketImpl(port);
	}
	
}

class ServerWebSocketImpl implements QSocket {

	private Map<String, MyWebSocket> sockets = new HashMap<String, MyWebSocket>();
	private Server server;
	private final int port;

	private static LinkedList<Msg> msgQueue = new LinkedList<Msg>();

	private static class Msg {
		public byte[] fromIp;
		public int fromPort;
		public String data;

		public Msg(byte[]fromIp, int fromPort, String data) {
			this.fromIp = fromIp;
			this.fromPort = fromPort;
			this.data = data;
		}
	}
	
	public ServerWebSocketImpl(int port) {
		
		System.out.println("ServerWebSocketImpl(" + port + ")");
		
		this.port = port;
		server = new Server(port);

	    WebSocketHandler handler = new WebSocketHandler() {
	      @Override
	      protected WebSocket doWebSocketConnect(HttpServletRequest req,
	          String service) {
	        // Kind of a hack: Use the service to pass the client-side qport.
	        // This allows us to maintain a stable logical connection over
	        // multiple "real" connections.
	    	  
	    	  
	    	String addr = req.getRemoteAddr();
	    	// loopback is ipv6 on OSX
	    	
	    	System.out.println("incoming connection from " + addr + "; service: "+ service);
	    	
	    	if (addr.equals("0:0:0:0:0:0:0:1%0")) {
	    		addr = "0.0.0.0";
	    	}
	        String from = addr + ":" + service;
	        System.err.println("Connect from " + from);
	        MyWebSocket socket = sockets.get(from);
	        if (socket == null) {
	          try {
				socket = new MyWebSocket(InetAddress.getByName(addr).getAddress(), 
						  Integer.parseInt(service));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
	          sockets.put(from, socket);
	        }
	        return socket;
	      }
	    };
	    handler.setBufferSize(65536);
	    server.setHandler(handler);

	    System.out.println("Starting Server");

	    try {
	      server.start();
	    } catch (Exception e) {
	      throw new RuntimeException(e);
	    }
	    
	    System.out.println("Server started");
	}
	

	public void close() {
		server.destroy();
		server = null;
		sockets = null;
	}

	public int receive(netadr_t fromAdr, byte[] buf) throws IOException {
		synchronized (msgQueue) {
			if (msgQueue.isEmpty()) {
				return -1;
			}
		  
			Msg msg = msgQueue.removeFirst();  
			String data = msg.data;
			
			fromAdr.ip = new byte[4];
			System.arraycopy(msg.fromIp, 0, fromAdr.ip, 0, 4);
			fromAdr.port = msg.fromPort;
			
		    int len = Compatibility.stringToBytes(data, buf);
		    
//			System.out.println("receiving " + Lib.hexDump(buf, len, true));

			return len;
		}
	}
	




	public void send(netadr_t dstSocket, byte[] data, int len)
			throws IOException {

		String targetAddress = InetAddress.getByAddress(dstSocket.ip).getHostAddress() + ":" +
		    dstSocket.port;
		
		MyWebSocket target = sockets.get(targetAddress);
		
		if (target == null) {
			System.out.println("Trying to send message to " + dstSocket.toString() + 
					"; address not found. Available addresses: "+ sockets.keySet());
			return;
		}
		
//		System.out.println("sending to " + targetAddress + ": " + Lib.hexDump(data, len, true));
		
		target.sendMessage(Compatibility.bytesToString(data, len));
	}
	
	
	
	class MyWebSocket implements WebSocket {
	    private LinkedList<String> outQueue = new LinkedList<String>();
	    private Outbound outbound;
	    byte[]fromIp;
	    int fromPort;

	    public MyWebSocket(byte[] fromIp, int fromPort) {
	      this.fromIp = fromIp;
	      this.fromPort = fromPort;
	    }

	    public void onConnect(Outbound outbound) {
	      this.outbound = outbound;

	      if (!outQueue.isEmpty()) {
	        for (String msg : outQueue) {
	          sendMessage(msg);
	        }
	        outQueue.clear();
	      }

	      System.out.println("onConnect");
	    }

	    public void onDisconnect() {
	      System.out.println("onDisconnect");
	    }

	    public void onMessage(byte frame, String data) {
	      synchronized (msgQueue) {
	        msgQueue.add(new Msg(fromIp, fromPort, data));
	      }
	    }

	    public void onMessage(byte frame, byte[] data, int offset, int length) {
	      assert false : "Why is this method separate from the other onMessage()?";
	    }

	    public void sendMessage(String msg) {
	      if (outbound == null) {
	        outQueue.add(msg);
	        return;
	      }

	      try {
	        outbound.sendMessage((byte) 0, msg);
	      } catch (IOException e) {
	        System.out.println("sendMessage failed (" + fromIp + ":" + fromPort + "): " + e.getMessage());
	        outQueue.add(msg);
	        outbound = null;
	      }
	    }
	  }


	  public void Shutdown() {
	    try {
	      server.stop();
	    } catch (Exception e) {
	    }
	  }

}
