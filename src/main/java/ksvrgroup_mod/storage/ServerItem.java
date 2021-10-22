
package com.github.kpnmserver.ksvrgroup_mod.storage;

import java.io.IOException;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import com.github.kpnmserver.ksvrgroup_mod.net.Connect;

public final class ServerItem{
	private String host;
	private int port;
	private byte authmods;

	public ServerItem(){
		this(null, -1, (byte)(0));
	}

	public ServerItem(final String host, final int port, final byte authmods){
		this.host = host;
		this.port = port;
		this.authmods = authmods;
	}

	public String getHost(){
		return this.host;
	}

	public void setHost(final String host){
		this.host = host;
	}

	public int getPort(){
		return this.port;
	}

	public void setPort(final int port){
		this.port = port;
	}

	public byte getAuthmods(){
		return this.authmods;
	}

	public void setAuthmods(final byte authmods){
		this.authmods = authmods;
	}

	public void addAuthmods(final byte authmods){
		this.authmods |= authmods;
	}

	public void delAuthmods(final byte authmods){
		this.authmods &= ~authmods;
	}

	public boolean hasAuthmods(final byte authmods){
		return (this.authmods & authmods) != 0;
	}

	public String toString(){
		return this.host + ":" + this.port;
	}

	public void toJson(final JsonWriter writer) throws IOException{
		writer.beginObject();
		writer.name("host").value(this.host);
		writer.name("port").value(this.port);
		writer.name("authmods").value(this.authmods);
		writer.endObject();
	}

	public static ServerItem fromJson(final JsonReader reader) throws IOException{
		final ServerItem item = new ServerItem();
		reader.beginObject();
		while(reader.hasNext()){
			final String key = reader.nextName();
			if("host".equals(key)){
				item.host = reader.nextString();
			}else if("port".equals(key)){
				item.port = reader.nextInt();
			}else if("authmods".equals(key)){
				item.authmods = (byte)(reader.nextInt());
			}else{
				reader.skipValue();
			}
		}
		reader.endObject();
		return item;
	}
}