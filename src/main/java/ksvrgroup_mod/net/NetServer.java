
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
	private static final Map<String, UUID> ID_MAP = new HashMap<>();

	private final UUID uuid;
	private Connect conn;
	private final String id;
	private String name;
	private String desc;
	private Map<UUID, NetServer> lines;
	private int ping = Integer.MAX_VALUE;

	NetServer(final UUID uuid, final String id, final String name, final String desc){
		this(uuid, null, id, name, desc);
	}

	NetServer(final UUID uuid, final Connect conn, final String id, final String name, final String desc){
		if(name == null){
			throw new IllegalArgumentException("name == null");
		}
		this.uuid = uuid;
		this.conn = conn;
		this.id = id;
		this.name = name;
		this.desc = desc;
		this.lines = new HashMap<>();
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

	public String getId(){
		return this.id;
	}

	public String getName(){
		return this.name;
	}

	public void setName(final String name){
		this.name = name
	}

	public String getDesc(){
		return this.desc;
	}

	public void setDesc(final String desc){
		this.desc = desc
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

	static final NetServer onServerJoin(final UUID uuid, final String id){
		if(NetServer.ALL_LINES.containsKey(uuid) || NetServer.ID_MAP.containsKey(id)){
			return null;
		}
		final NetServer svr = new NetServer(uuid, id);
		NetServer.ALL_LINES.put(uuid, svr);
		NetServer.ID_MAP.put(id, uuid);
		return svr;
	}

	static final void onServerLeave(final NetServer svr){
		NetServer.ALL_LINES.remove(svr.uuid);
		NetServer.ID_MAP.remove(svr.id);
	}

	NetServer addLine(final UUID uuid, final String id, final Connect conn){
		final NetServer svr = NetServer.onServerJoin(uuid, id);
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
