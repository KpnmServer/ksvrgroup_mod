
package com.github.kpnmserver.ksvrgroup_mod.net;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.SecureRandom;
import java.security.Key;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.crypto.KeyGenerator;

import com.github.kpnmserver.ksvrgroup_mod.KSvrGroupMod;
import com.github.kpnmserver.ksvrgroup_mod.storage.Config;
import com.github.kpnmserver.ksvrgroup_mod.storage.ServerItem;

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
	private byte[] encryptKey = null;
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
		int n;
		while((n = this.channel.read(this.readBuffer)) > 0){
			if(this.readBuffer.remaining() == 0){
				final int mark = this.readBuffer.position();
				this.readBuffer.flip();
				this.readBuffer.mark();
				final int length = this.readBuffer.getInt();
				if(this.readBuffer.remaining() == length){
					this.manager.sendMessage(Message.parseMsg(this, this.readBuffer));
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
				msg.writeToByteBuffer(writeBuf);
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

	void onMessage(final Message msg){
		try{
			if(!this.canTrust()){
				final ByteBuffer buffer = ByteBuffer.wrap(msg.getData());
				final byte pkgid = buffer.get();
				switch(pkgid){
					case 0x00:{
						if(this.first || this.shaketime != 0){ throw new IllegalStateException("Wrong package id: " + pkgid); }
						final byte authmods = buffer.get();
						{
							final byte idlen = buffer.get();
							final byte[] idbuf = new byte[idlen];
							buffer.get(idbuf);
							this.remoteId = new String(idbuf);
						}
						final byte allowed = authmods & Config.INSTANCE.getAuthmods();
						/*if(allowed & AUTH_WEB != 0){
							this.authmod = AUTH_WEB;
						}else*/ if(allowed & AUTH_KEY_PAIR != 0){
							this.authmod = AUTH_KEY_PAIR;
							final byte[] idb = Config.INSTANCE.getId().getBytes();
							final byte[] rd1 = new byte[8];
							new Random(System.currentTimeMillis() << 8 + System.nanoTime()).nextBytes(rd1);
							this.storage.put("!shake.rd1", rd1);
							final byte[] rd1b = EncryptUtil.encryptRSAByPriKey(rd1, KeyPairStorage.INSTANCE.getSelfPriKey());
							final ByteBuffer buf = ByteBuffer.allocate(1 + 1 + idb.length + 2 + rd1b.length);
							buf.put((byte)(0x01));
							buf.put((byte)(idb.length));
							buf.put(idb);
							buf.putShort((short)(rd1b.length));
							buf.put(rd1b);
							this.sendMessage(this.manager.createMessage(buf.array()));
						}else if(allowed & AUTH_NONE != 0){
							this.authmod = AUTH_NONE;
							final byte[] idb = Config.INSTANCE.getId().getBytes();
							final byte[] msg = new byte[1 + 1 + idb.length];
							msg[0] = 0x07;
							msg[1] = (byte)(idb.length)
							System.arraycopy(idb, 0, msg, 2, idb.length);
							this.sendMessage(this.manager.createMessage(msg));
						}else{
							throw new IllegalStateException("Not allowed mods: " + authmods);
						}
						this.shaketime++;
					}break;
				}
				this.close();
				return;
			}
			this.manager.getNetSvr().onMessage(msg);
		}catch(Throwable e){
			KSvrGroupMod.LOGGER.error("Error on SGCN Connect:", e);
			this.close();
		}
	}

	void initialize(final ServerItem svr){
		final byte[] idb = Config.INSTANCE.getId().getBytes();
		final byte[] msg = new byte[1 + 1 + 1 + idb.length];
		msg[0] = 0x00;
		msg[1] = svr.getAuthmods();
		msg[2] = (byte)(idb.length);
		System.arraycopy(idb, 0, msg, 3, idb.length);
		this.sendMessage(this.manager.createMessage(msg));
	}
}
