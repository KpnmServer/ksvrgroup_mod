
package com.github.kpnmserver.ksvrgroup_mod.storage;

import com.github.kpnmserver.ksvrgroup_mod.KSvrGroupMod;

public final class KeyPairStorage{
	public static final KeyPairStorage INSTANCE = new KeyPairStorage();
	private KeyPairStorage(){}

	public byte[] getPriKey(final String id){
		return null;
	}

	public byte[] getPubKey(final String id){
		return null;
	}

	public byte[] getSelfPriKey(){
		return getPriKey(null);
	}

	public byte[] getSelfPubKey(){
		return getPubKey(null);
	}

	public void reload(){
		//
	}
}
