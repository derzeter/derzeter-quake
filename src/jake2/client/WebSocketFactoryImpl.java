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
package jake2.client;

import jake2.qcommon.Compatibility;
import jake2.qcommon.netadr_t;
import jake2.sys.QSocket;
import jake2.sys.QSocketFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.LinkedList;

import com.google.gwt.corp.websocket.CloseEvent;
import com.google.gwt.corp.websocket.MessageEvent;
import com.google.gwt.corp.websocket.OpenEvent;
import com.google.gwt.corp.websocket.WebSocket;


public class WebSocketFactoryImpl implements QSocketFactory {

	public QSocket bind(String ip, int port) {
		return new GwtWebSocketImpl(ip, port);
	}
}

class GwtWebSocketImpl implements QSocket {

	private String ip;
	private WebSocket socket;
	private boolean connected;
	private LinkedList<String> msgQueue = new LinkedList<String>();
	private int localPort;
	private int remotePort;
	private byte[] remoteIp;
	
	public GwtWebSocketImpl(String ip, int localPort)  {
		this.ip = ip;
		this.localPort = localPort;

		System.out.println("Creating GwtWebSocketImpl(" + localPort + ")");
	  
	}

	public void close() throws IOException {
		System.out.println("closing");
		if (socket != null) {
			socket.setListener(null);
		}
		if (connected) {
			socket.close();
			connected = false;
		}
	    socket = null;
	}

	public int receive(netadr_t address, byte[] buf) throws IOException {
		if (msgQueue.isEmpty()) { 
			return -1;
		}

		String s = msgQueue.remove();
		int len = Compatibility.stringToBytes(s, buf);
		
//		System.out.println("receiving: " + Lib.hexDump(buf, len, false));
		
		address.ip = new byte[4];
		System.arraycopy(remoteIp, 0, address.ip, 0, 4);
		address.port = remotePort;
		
		return len;
	}

	public void send(netadr_t adr, byte[] buf, int len) throws IOException {
		// TODO(haustein): check if addess still matches?

		if (socket == null) {
			remotePort = adr.port;
			remoteIp = new byte[4];
			System.arraycopy(adr.ip, 0, remoteIp, 0, 4);
			
			String url = "ws://" + InetAddress.getByAddress(adr.ip).getHostAddress()
			+ ":" + adr.port;
			System.out.println("connect for send to: " + url);
			
			socket = WebSocket.create(url, "" + localPort);
			
			System.out.println("socket: " + socket);

		    socket.setListener(new WebSocket.Listener() {
		      public void onOpen(WebSocket socket, OpenEvent event) {
		        connected = true;
		      }

		      public void onMessage(WebSocket socket, MessageEvent event) {
		        String data = event.getData();
		        msgQueue.add(data);
		      }

		      public void onClose(WebSocket socket, CloseEvent event) {
		        connected = false;
		      }
		    });		
		}
		
//		System.out.println("sending: " + connected+ " " + Lib.hexDump(buf, len, false));
		if (connected) {
			socket.send(Compatibility.bytesToString(buf, len));
		} 
	}
}
