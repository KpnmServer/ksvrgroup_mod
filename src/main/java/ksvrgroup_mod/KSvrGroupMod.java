
package com.github.kpnmserver.ksvrgroup_mod;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.LiteralText;

import com.github.kpnmserver.kpi.KpiMod;
import com.github.kpnmserver.ksvrgroup_mod.net.ConnectNode;
import com.github.kpnmserver.ksvrgroup_mod.storage.Config;
import com.github.kpnmserver.ksvrgroup_mod.storage.ServerItem;

public class KSvrGroupMod{
	public static KSvrGroupMod INSTANCE = new KSvrGroupMod();
	public static final Logger LOGGER = LogManager.getLogger("KSvrGroup");

	private MinecraftServer server = null;
	private File folder;

	private ConnectNode connnode = null;

	private KSvrGroupMod(){
		this.folder = KpiMod.INSTANCE.getDataFolder("servergroup");
	}

	public MinecraftServer getServer(){
		return this.server;
	}

	public File getDataFolder(){
		return this.folder;
	}

	public void onStarting(MinecraftServer server){
		this.server = server;
		this.onReload();
	}

	public void onStarted(MinecraftServer server){
	}

	public void onRegisterCommands(CommandDispatcher<ServerCommandSource> dispatcher, boolean dedicated){
	}

	public void onReload(){
		if(this.connnode != null){
			this.connnode.close();
			this.connnode = null;
		}
		Config.INSTANCE.reload();
		if(Config.INSTANCE.getEnable()){
			try{
				if(Config.INSTANCE.getHost() == null){
					this.connnode = new ConnectNode(Config.INSTANCE.getName());
				}else{
					this.connnode = new ConnectNode(Config.INSTANCE.getName(), Config.INSTANCE.getHost(), Config.INSTANCE.getPort());
				}
				for(ServerItem item: Config.INSTANCE.getServerList()){
					this.connnode.tryConnect(item);
				}
			}catch(IOException e){
				KSvrGroupMod.LOGGER.error("Init selector error:", e);
			}
		}
	}

	public void onSave(){
		Config.INSTANCE.save();
	}

	public void onStopping(MinecraftServer server){
		if(this.connnode != null){
			this.connnode.close();
			this.connnode = null;
		}
		this.onSave();
	}

	public void onStopped(MinecraftServer server){
		this.server = null;
	}

	public static Text createText(final String text){
		return new LiteralText(text);
	}
}
