
package com.github.kpnmserver.ksvrgroup_mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import com.github.kpnmserver.ksvrgroup_mod.KSvrGroupMod;

public final class KSvrGroupModInitializer implements ModInitializer{
	public KSvrGroupModInitializer(){}

	@Override
	public void onInitialize(){
		KSvrGroupMod.LOGGER.info("KSvrGroup is onInitialize");
		ServerLifecycleEvents.SERVER_STARTING.register(KSvrGroupMod.INSTANCE::onStarting);
		ServerLifecycleEvents.SERVER_STARTED.register(KSvrGroupMod.INSTANCE::onStarted);
		CommandRegistrationCallback.EVENT.register(KSvrGroupMod.INSTANCE::onRegisterCommands);
		ServerLifecycleEvents.SERVER_STOPPING.register(KSvrGroupMod.INSTANCE::onStopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(KSvrGroupMod.INSTANCE::onStopped);
	}
}