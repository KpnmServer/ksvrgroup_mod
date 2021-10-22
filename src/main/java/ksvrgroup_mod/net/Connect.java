
package com.github.kpnmserver.ksvrgroup_mod.net;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.SecureRandom;
import java.security.Key;
import java.security.GeneralSecurityException;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import com.github.kpnmserver.ksvrgroup_mod.KSvrGroupMod;
import com.github.kpnmserver.ksvrgroup_mod.storage.Config;
import com.github.kpnmserver.ksvrgroup_mod.storage.ServerItem;
import com.github.kpnmserver.ksvrgroup_mod.storage.KeyPairStorage;
import com.github.kpnmserver.ksvrgroup_mod.util.EncryptUtil;

public class Connect{
	public static final byte AUTH_NONE     = 0x01;
	public static final byte AUTH_KEY_PAIR = 0x02;
	public static final byte AUTH_WEB      = 0x04;

	protected final ConnectNode manager;
	protected final SocketChannel channel;
	protected final String name;
	private final boolean first;
	private final BlockingQueue<Message> sendQueue = new LinkedBlockingQueue<>();
	private final SelectionKey read_key;
	private final SelectionKey write_key;
	private NetServer netsvr = null;
	private byte authmod = 0;
	private byte shaketime = 0;
	private String remoteId = null;
	private SecretKey encryptKey = null;
	private long lastChangeKey = 0;

	private Map<String, Object> storage = new HashMap<>();
	private ByteBuffer readBuffer = ByteBuffer.allocate(4);

	Connect(final ConnectNode manager, final SocketChannel channel) throws IOException {
		this(manager, channel, false);
	}

	Connect(final ConnectNode manager, final SocketChannel channel, final boolean first) throws IOException{
		this.manager = manager;
		this.first = first;
		this.channel = channel;
		this.name = ConnectNode.chToString(this.channel);
		this.channel.configureBlocking(false);
		this.manager.beginLockReader();
		this.read_key = this.channel.register(this.manager.getReadSelector(), SelectionKey.OP_READ);
		this.manager.endLockReader();
		this.manager.beginLockWriter();
		this.write_key = this.channel.register(this.manager.getWriteSelector(), 0);
		this.manager.endLockWriter();
	}

	public final SocketChannel getChannel(){
		return this.channel;
	}

	public final boolean isFirst(){
		return this.first;
	}

	public final byte getAuthMod(){
		return this.authmod;
	}

	public final Map<String, Object> getStorage(){
		return this.storage;
	}

	final void changeToOnlyReadable(){
		this.manager.beginLockWriter();
		this.write_key.interestOps(0);
		this.manager.endLockWriter();
	}

	final void changeToWritable(){
		this.manager.beginLockWriter();
		this.write_key.interestOps(SelectionKey.OP_WRITE);
		this.manager.endLockWriter();
	}

	public boolean sendMessage(final Message msg){
		if(this.sendQueue.offer(msg)){
			this.changeToWritable();
			return true;
		}
		return false;
	}

	final void onRead() throws IOException{
		Message msg;
		int n;
		while((n = this.channel.read(this.readBuffer)) > 0){
			if(this.readBuffer.remaining() == 0){
				final int mark = this.readBuffer.position();
				this.readBuffer.flip();
				this.readBuffer.mark();
				final int length = this.readBuffer.getInt();
				if(this.readBuffer.remaining() == length){
					try{
						if((msg = Message.parseMsg(this, (this.encryptKey == null) ?this.readBuffer
							:ByteBuffer.wrap(EncryptUtil.decryptAES(this.readBuffer.array(),
							this.readBuffer.arrayOffset() + 4, this.readBuffer.remaining(), this.encryptKey)))).isDirect()){
							this.onMessage(msg);
						}else{
							this.manager.sendMessage(msg);
						}
					}catch(GeneralSecurityException e){
						KSvrGroupMod.LOGGER.error("Error when decrypt SGCN packet: " + e.getMessage());
						this.close();
						return;
					}
					this.readBuffer = ByteBuffer.allocate(4);
				}else if(this.readBuffer.capacity() < 4 + length){
					this.readBuffer = ByteBuffer.allocate(4 + length).put((ByteBuffer)(this.readBuffer.reset()));
				}else{
					this.readBuffer.position(mark);
				}
			}
		}
		if(n == -1){
			this.close(true);
		}
	}

	final void onWrite() throws IOException{
		Message msg;
		ByteBuffer writeBuf = ByteBuffer.allocate(4);
		boolean flag = false;
		synchronized(this.sendQueue){
			while((msg = this.sendQueue.poll()) != null){
				if(writeBuf.clear().limit() < 4 + msg.length()){
					writeBuf = ByteBuffer.allocate(4 + msg.length());
				}
				writeBuf.position(4);
				if(this.encryptKey != null){
					try{
						writeBuf.put(EncryptUtil.encryptAES(msg.toByteArray(), this.encryptKey));
					}catch(GeneralSecurityException e){
						KSvrGroupMod.LOGGER.error("Error when encrypt SGCN packet: " + e.getMessage());
						this.close();
						return;
					}
				}else{
					msg.writeToByteBuffer(writeBuf);
				}
				writeBuf.flip();
				writeBuf.putInt(writeBuf.remaining() - 4);
				writeBuf.position(0);
				this.channel.write(writeBuf);
				flag = true;
			}
			this.sendQueue.notify();
		}
		if(flag){
			this.changeToOnlyReadable();
		}
	}

	public void close(){
		this.close(false);
	}

	public void close(final boolean force){
		KSvrGroupMod.LOGGER.info("Closing connect " + this.name);
		try{
			this.read_key.cancel();
			if(!force){
				try{
					synchronized(this.sendQueue){
						if(!this.sendQueue.isEmpty()){
							this.sendQueue.wait();
						}
					}
				}catch(InterruptedException e){
				}
			}
			this.write_key.cancel();
			this.channel.close();
		}catch(ClosedChannelException e){
		}catch(IOException e){
		}finally{
			this.manager.onConnClose(this);
		}
	}

	@Override
	public String toString(){
		return "Connect(" + this.name + ")";
	}

	public boolean canTrust(){
		return this.shaketime == -1;
	}

	private void changeEncryptKey(final SecretKey key){
		this.encryptKey = key;
		this.lastChangeKey = System.currentTimeMillis();
	}

	void onMessage(final Message msg){
		try{
			if(msg.isDirect()){
				final ByteBuffer buffer = msg.getData();
				final byte pkgid = buffer.get();
				if(this.canTrust()){
					switch(pkgid){
						default: throw new IllegalStateException("Unknown package id: " + pkgid);
					}
				}else{
					switch(pkgid){
						case 0x00:{
							if(this.first || this.shaketime != 0){ throw new IllegalStateException("Wrong package id: " + pkgid); }
							final byte authmods = buffer.get();
							{
								final byte[] idbuf = new byte[(int)(buffer.get())]; buffer.get(idbuf);
								this.remoteId = new String(idbuf, StandardCharsets.UTF_8);
							}
							final byte allowed = (byte)(authmods & Config.INSTANCE.getAuthmods());
							/*if((allowed & AUTH_WEB) != 0){
								this.authmod = AUTH_WEB;
							}else*/ if((allowed & AUTH_KEY_PAIR) != 0){
								this.authmod = AUTH_KEY_PAIR;
								final byte[] idb = Config.INSTANCE.getId().getBytes(StandardCharsets.UTF_8);
								final byte[] rd1 = new byte[8];
								new Random(System.currentTimeMillis() * 1000000 + System.nanoTime()).nextBytes(rd1);
								this.storage.put("!shake.rd", rd1);
								final byte[] rd1b = EncryptUtil.encryptRSAByPubKey(rd1, KeyPairStorage.INSTANCE.getPubKey(this.remoteId));
								final ByteBuffer mb = ByteBuffer.allocate(4 + idb.length + rd1b.length); // (1 + 1 + idb.length + 2 + rd1b.length)
								mb.put((byte)(0x01));
								mb.put((byte)(idb.length)).put(idb);
								mb.putShort((short)(rd1b.length)).put(rd1b);
								this.sendMessage(Message.createDirect(this, mb));
							}else if((allowed & AUTH_NONE) != 0){
								this.authmod = AUTH_NONE;
								final byte[] idb = Config.INSTANCE.getId().getBytes(StandardCharsets.UTF_8);
								final byte[] mb = new byte[2 + idb.length]; // (1 + 1 + idb.length)
								mb[0] = 0x07;
								mb[1] = (byte)(idb.length);
								System.arraycopy(idb, 0, mb, 2, idb.length);
								this.sendMessage(Message.createDirect(this, mb));
							}else{
								throw new IllegalStateException("Not allowed mods: " + authmods);
							}
							this.shaketime++;
						}break;
						case 0x01:{
							if(!this.first || this.shaketime != 0 || (this.authmod & AUTH_KEY_PAIR) == 0){
								throw new IllegalStateException("Wrong package id: " + pkgid);
							}
							this.authmod = AUTH_KEY_PAIR;
							{
								final byte[] idbuf = new byte[(int)(buffer.get())]; buffer.get(idbuf);
								this.remoteId = new String(idbuf, StandardCharsets.UTF_8);
							}
							final byte[] rd1b = new byte[(int)(buffer.getShort())]; buffer.get(rd1b);
							final byte[] rd1 = EncryptUtil.decryptRSAByPriKey(rd1b, KeyPairStorage.INSTANCE.getSelfPriKey());
							final byte[] rd2 = new byte[8];
							new Random(System.currentTimeMillis() * 1000000 + System.nanoTime()).nextBytes(rd2);
							this.storage.put("!shake.rd", rd2);
							final byte[] rd12 = new byte[2 + rd1.length + rd2.length]; // (1 + rd1.length + 1 + rd2.length)
							rd12[0] = (byte)(rd1.length);
							System.arraycopy(rd1, 0, rd12, 1, rd1.length);
							rd12[1 + rd1.length] = (byte)(rd2.length);
							System.arraycopy(rd2, 0, rd12, 2 + rd1.length /*(1 + rd1.length + 1)*/, rd2.length);
							final byte[] rd12b = EncryptUtil.encryptRSAByPubKey(rd12, KeyPairStorage.INSTANCE.getPubKey(this.remoteId));
							EncryptUtil.encryptRSAByPubKey(rd2, KeyPairStorage.INSTANCE.getPubKey(this.remoteId));
							final ByteBuffer mb = ByteBuffer.allocate(3 + rd12b.length); // (1 + 2 + rd12b.length)
							mb.put((byte)(0x02));
							mb.putShort((short)(rd12b.length)).put(rd12b);
							this.sendMessage(Message.createDirect(this, mb));
							this.shaketime++;
						}break;
						case 0x02:{
							if(this.first || this.shaketime != 1 || this.authmod != AUTH_KEY_PAIR){
								throw new IllegalStateException("Wrong package id: " + pkgid);
							}
							final byte[] rd12b = new byte[(int)(buffer.getShort())]; buffer.get(rd12b);
							final byte[] rd12 = EncryptUtil.decryptRSAByPriKey(rd12b, KeyPairStorage.INSTANCE.getSelfPriKey());
							final byte[] rd1 = new byte[(int)(rd12[0])];
							System.arraycopy(rd12, 1, rd1, 0, rd1.length);
							final byte[] rd2 = new byte[(int)(rd12[1 + rd1.length])];
							System.arraycopy(rd12, 2 + rd1.length /*(1 + rd1.length + 1)*/, rd2, 0, rd2.length);
							if(!rd1.equals((byte[])(this.storage.remove("!shake.rd")))){
								throw new IllegalStateException("Authorize failed (Random Key mismatch)");
							}
							this.changeEncryptKey(EncryptUtil.genAESKey(256));
							final byte[] encryptkey = this.encryptKey.getEncoded();
							final byte[] rd2aes = new byte[3 + rd2.length + encryptkey.length]; // (1 + rd2.length + 2 + encryptkey.length)
							rd2aes[0] = (byte)(rd2.length);
							System.arraycopy(rd2, 0, rd2aes, 1, rd2.length);
							rd2aes[1 + rd2.length] = (byte)(encryptkey.length >> 8);
							rd2aes[2 + rd2.length] = (byte)(encryptkey.length);
							System.arraycopy(encryptkey, 0, rd2aes, 3 + rd2.length /*(1 + rd2.length + 2)*/, encryptkey.length);
							final byte[] rd2aesb = EncryptUtil.encryptRSAByPubKey(rd2aes, KeyPairStorage.INSTANCE.getPubKey(this.remoteId));
							final ByteBuffer mb = ByteBuffer.allocate(5 + rd2aesb.length); // (1 + 4 + rd2aesb.length)
							mb.put((byte)(0x03));
							mb.putInt(rd2aesb.length).put(rd2aesb);
							this.sendMessage(Message.createDirect(this, mb));
							this.shaketime++;
						}break;
						case 0x03:{
							if(!this.first || this.shaketime != 1 || this.authmod != AUTH_KEY_PAIR){
								throw new IllegalStateException("Wrong package id: " + pkgid);
							}
							final byte[] rd2aesb = new byte[buffer.getInt()]; buffer.get(rd2aesb);
							final byte[] rd2aes = EncryptUtil.decryptRSAByPriKey(rd2aesb, KeyPairStorage.INSTANCE.getSelfPriKey());
							final byte[] rd2 = new byte[(int)(rd2aes[0])];
							System.arraycopy(rd2aes, 1, rd2, 0, rd2.length);
							if(!rd2.equals((byte[])(this.storage.remove("!shake.rd")))){
								throw new IllegalStateException("Authorize failed (Random Key mismatch)");
							}
							final byte[] encryptkey = new byte[
								((int)(rd2aes[1 + rd2.length] & 0xff) << 8) + (int)(rd2aes[2 + rd2.length] & 0xff)];
							System.arraycopy(rd2aes, 3 + rd2.length, encryptkey, 0, encryptkey.length);
							this.changeEncryptKey(EncryptUtil.encodeAESKey(encryptkey));
							final byte[] nameb = Config.INSTANCE.getName().getBytes(StandardCharsets.UTF_8);
							final byte[] descb = Config.INSTANCE.getDesc().getBytes(StandardCharsets.UTF_8);
							final ByteBuffer mb = ByteBuffer.allocate(1 + 4 + nameb.length + 4 + descb.length);
							mb.put((byte)(0x10));
							mb.putInt(nameb.length).put(nameb);
							mb.putInt(descb.length).put(descb);
							this.sendMessage(Message.createDirect(this, mb));
							this.shaketime++;
						}break;
						case 0x07:{
							if(!this.first || this.shaketime != 0 || (this.authmod & AUTH_NONE) == 0){
								throw new IllegalStateException("Wrong package id: " + pkgid);
							}
							{
								final byte[] idbuf = new byte[(int)(buffer.get())]; buffer.get(idbuf);
								this.remoteId = new String(idbuf, StandardCharsets.UTF_8);
							}
							this.authmod = AUTH_NONE;
							final byte[] nameb = Config.INSTANCE.getName().getBytes(StandardCharsets.UTF_8);
							final byte[] descb = Config.INSTANCE.getDesc().getBytes(StandardCharsets.UTF_8);
							final ByteBuffer mb = ByteBuffer.allocate(1 + 4 + nameb.length + 4 + descb.length);
							mb.put((byte)(0x10));
							mb.putInt(nameb.length).put(nameb);
							mb.putInt(descb.length).put(descb);
							this.sendMessage(Message.createDirect(this, mb));
							this.shaketime++;
						}break;
						case 0x10:{
							if(this.first){
								throw new IllegalStateException("Wrong package id: " + pkgid);
							}
							switch(this.authmod){
								case AUTH_NONE: if(this.shaketime != 1){
									throw new IllegalStateException("Wrong package id: " + pkgid);
								}break;
								case AUTH_KEY_PAIR: if(this.shaketime != 2 || this.encryptKey == null){
									throw new IllegalStateException("Wrong package id: " + pkgid);
								}break;
								// case AUTH_WEB:
								default: throw new IllegalStateException("Wrong package id: " + pkgid);
							}
							final byte[] rnameb = new byte[buffer.getInt()]; buffer.get(rnameb);
							final byte[] rdescb = new byte[buffer.getInt()]; buffer.get(rdescb);
							new String(rnameb, StandardCharsets.UTF_8);
							new String(rdescb, StandardCharsets.UTF_8);
							final byte[] nameb = Config.INSTANCE.getName().getBytes(StandardCharsets.UTF_8);
							final byte[] descb = Config.INSTANCE.getDesc().getBytes(StandardCharsets.UTF_8);
							final ByteBuffer mb = ByteBuffer.allocate(1 + 4 + nameb.length + 4 + descb.length);
							mb.put((byte)(0x11));
							mb.putInt(nameb.length).put(nameb);
							mb.putInt(descb.length).put(descb);
							this.sendMessage(Message.createDirect(this, mb));
							this.shaketime = -1;
						}break;
						case 0x11:{
							if(!this.first){
								throw new IllegalStateException("Wrong package id: " + pkgid);
							}
							switch(this.authmod){
								case AUTH_NONE: if(this.shaketime != 1){
									throw new IllegalStateException("Wrong package id: " + pkgid);
								}break;
								case AUTH_KEY_PAIR: if(this.shaketime != 2 || this.encryptKey == null){
									throw new IllegalStateException("Wrong package id: " + pkgid);
								}break;
								// case AUTH_WEB:
								default: throw new IllegalStateException("Wrong package id: " + pkgid);
							}
							final byte[] rnameb = new byte[buffer.getInt()]; buffer.get(rnameb);
							final byte[] rdescb = new byte[buffer.getInt()]; buffer.get(rdescb);
							new String(rnameb, StandardCharsets.UTF_8);
							new String(rdescb, StandardCharsets.UTF_8);
							this.shaketime = -1;
						}break;
						default: throw new IllegalStateException("Unknown package id: " + pkgid);
					}
				}
				return;
			}
			if(!this.canTrust()){
				throw new IllegalStateException("not a can trust message");
			}
			this.manager.getNetSvr().onMessage(msg);
		}catch(Throwable e){
			KSvrGroupMod.LOGGER.error("Error on SGCN Connect: " + e.getMessage());
			this.close();
		}
	}

	void initialize(final ServerItem svr){
		if(this.shaketime != 0){ throw new IllegalStateException(); }
		this.authmod = svr.getAuthmods();
		final byte[] idb = Config.INSTANCE.getId().getBytes(StandardCharsets.UTF_8);
		final byte[] msg = new byte[1 + 1 + 1 + idb.length];
		msg[0] = 0x00;
		msg[1] = this.authmod;
		msg[2] = (byte)(idb.length);
		System.arraycopy(idb, 0, msg, 3, idb.length);
		this.sendMessage(this.manager.createMessage(msg));
	}
}
