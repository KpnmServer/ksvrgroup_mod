
package com.github.kpnmserver.ksvrgroup_mod.storage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import com.github.kpnmserver.ksvrgroup_mod.KSvrGroupMod;
import com.github.kpnmserver.ksvrgroup_mod.net.Connect;
import com.github.kpnmserver.ksvrgroup_mod.storage.ServerItem;
import com.github.kpnmserver.ksvrgroup_mod.storage.KeyPairStorage;

public final class Config{
	public static final Config INSTANCE = new Config();
	private Config(){}

	private boolean enable = true;
	private String id = "id_for_server";
	private String name = "name_for_server";
	private String description = "A Minecraft Server";
	private String host = "127.0.0.1";
	private int port = 25566;
	private final File keypath = new File(KSvrGroupMod.INSTANCE.getDataFolder(), "keys");
	private byte authmods = Connect.AUTH_NONE;
	private List<ServerItem> serverlist = new ArrayList<>();

	public void reset(){
		this.enable = true;
		this.id = "id_for_server";
		this.name = "name_for_server";
		this.description = "A Minecraft Server";
		this.host = "127.0.0.1";
		this.port = 25566;
		this.authmods = Connect.AUTH_NONE;
		this.serverlist.clear();
	}

	public boolean getEnable(){
		return this.enable;
	}

	public void setEnable(final boolean enable){
		this.enable = enable;
	}

	public String getId(){
		return this.id;
	}

	public void setId(final String id){
		this.id = id;
	}

	public String getName(){
		return this.name;
	}

	public void setName(final String name){
		this.name = name;
	}

	public String getDesc(){
		return this.description;
	}

	public void setDesc(final String description){
		this.description = description;
	}

	public String getHost(){
		return this.host;
	}

	public void getHost(final String host){
		this.host = host;
	}

	public int getPort(){
		return this.port;
	}

	public void getPort(final int port){
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

	public List<ServerItem> getServerList(){
		return this.serverlist;
	}

	public void reload(){
		this.reset();
		final File file = new File(KSvrGroupMod.INSTANCE.getDataFolder(), "config.json");
		if(!file.exists()){
			return;
		}
		try(
			FileReader freader = new FileReader(file);
			JsonReader jreader = new JsonReader(freader)
		){
			jreader.beginObject();
			while(jreader.hasNext()){
				final String key = jreader.nextName();
				switch(key){
					case "enable":{
						this.enable = jreader.nextBoolean();
					}break;
					case "id":{
						this.id = jreader.nextString();
					}break;
					case "name":{
						this.name = jreader.nextString();
					}break;
					case "description":{
						this.description = jreader.nextString();
					}break;
					case "host":{
						this.host = jreader.nextString();
					}break;
					case "port":{
						this.port = jreader.nextInt();
					}break;
					case "authmods":{
						this.authmods = (byte)(jreader.nextInt());
					}break;
					case "serverlist":{
						jreader.beginArray();
						while(jreader.hasNext()){
							this.serverlist.add(ServerItem.fromJson(jreader));
						}
						jreader.endArray();
					}break;
					default:
						jreader.skipValue();
				}
			}
			jreader.endObject();
		}catch(IOException e){
			KSvrGroupMod.LOGGER.error("load config file error:", e);
			return;
		}
		KeyPairStorage.INSTANCE.reload();
	}

	public void save(){
		final File file = new File(KSvrGroupMod.INSTANCE.getDataFolder(), "config.json");
		if(!file.exists()){
			try{
				file.createNewFile();
			}catch(IOException e){
				KSvrGroupMod.LOGGER.error("create config file error:", e);
				return;
			}
		}
		try(
			FileWriter fwriter = new FileWriter(file);
			JsonWriter jwriter = new JsonWriter(fwriter)
		){
			jwriter.setIndent("  ");
			jwriter.beginObject();
			jwriter.name("enable").value(this.enable);
			jwriter.name("id").value(this.id);
			jwriter.name("name").value(this.name);
			jwriter.name("description").value(this.description);
			jwriter.name("host").value(this.host);
			jwriter.name("port").value(this.port);
			jwriter.name("authmods").value(this.authmods);
			jwriter.name("serverlist").beginArray();
			for(ServerItem svr: this.serverlist){
				svr.toJson(jwriter);
			}
			jwriter.endArray();
			jwriter.endObject();
		}catch(IOException e){
			KSvrGroupMod.LOGGER.error("save config file error:", e);
		}
	}
}