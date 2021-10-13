
package com.github.kpnmserver.ksvrgroup_mod.net;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

public class NetServer{
	private static final Map<UUID, NetServer> ALL_LINES = new HashMap<>();
	private static final Map<String, UUID> NAME_MAP = new HashMap<>();

	private final UUID uuid;
	private Connect conn;
	private String name;
	private Map<UUID, NetServer> lines;
	private int ping = Integer.MAX_VALUE;

	NetServer(final UUID uuid, final String name){
		this(uuid, null, name);
	}

	NetServer(final UUID uuid, final Connect conn, final String name){
		if(name == null){
			throw new IllegalArgumentException("name == null");
		}
		this.uuid = uuid;
		this.conn = conn;
		this.name = name;
		this.lines = new HashMap<>();
		// if(lines != null){
		// 	final Iterator<NetServer> iter = lines.iterator();
		// 	while(iter.hasNext()){
		// 		this.addLine(iter.next());
		// 	}
		// }
	}

	public UUID getUuid(){
		return this.uuid;
	}

	public Connect getConn(){
		return this.conn;
	}

	void setConn(final Connect conn){
		this.conn = conn;
	}

	public String getName(){
		return this.name;
	}

	boolean setName(final String name){
		if(this.name.equals(name)){
			return true;
		}
		NetServer.NAME_MAP.remove(this.name);
		if(NetServer.NAME_MAP.containsKey(name)){
			return false;
		}
		this.name = name;
		NetServer.NAME_MAP.put(this.name, this.uuid);
		return true;
	}

	public Set<NetServer> getLines(){
		return new HashSet<>(this.lines.values());
	}

	public int getPing(){
		return this.ping;
	}

	void setPing(final int ping){
		this.ping = ping;
	}

	public int hashCode(){
		return this.uuid.hashCode();
	}

	public boolean equals(Object obj){
		return obj == this;
	}

	static final NetServer onServerJoin(final UUID uuid, final String name){
		if(NetServer.ALL_LINES.containsKey(uuid) || NetServer.NAME_MAP.containsKey(name)){
			return null;
		}
		final NetServer svr = new NetServer(uuid, name);
		NetServer.ALL_LINES.put(uuid, svr);
		NetServer.NAME_MAP.put(name, uuid);
		return svr;
	}

	static final void onServerLeave(final NetServer svr){
		NetServer.ALL_LINES.remove(svr.uuid);
		NetServer.NAME_MAP.remove(svr.name);
	}

	NetServer addLine(final UUID uuid, final String name, final Connect conn){
		final NetServer svr = NetServer.onServerJoin(uuid, name);
		if(svr == null){
			return null;
		}
		svr.setConn(conn);
		this.lines.put(uuid, svr);
		return svr;
	}

	void removeLine(final NetServer svr){
		NetServer.onServerLeave(svr);
		this.lines.remove(svr.uuid);
	}

	void onMessage(final Message msg){
		//
	}
}
