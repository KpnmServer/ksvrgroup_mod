
package com.github.kpnmserver.ksvrgroup_mod.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.UUID;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Hashtable;
import java.util.Set;
import java.util.HashSet;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Iterator;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.github.kpnmserver.ksvrgroup_mod.KSvrGroupMod;
import com.github.kpnmserver.ksvrgroup_mod.storage.Config;
import com.github.kpnmserver.ksvrgroup_mod.storage.ServerItem;

public final class ConnectNode extends Thread{
	public static long MAX_WAIT_TIME = 10 * 1000;

	private final NetServer netsvr;
	private final String host;
	private final int port;
	private int status = 0;
	private final Map<String, Connect> connectMap = new Hashtable<>();
	private final ServerSocketChannel serverChannel;
	private final Selector accept_selector;
	private final Selector read_selector;
	private final Selector write_selector;
	private final BlockingQueue<Message> msgQueue = new LinkedBlockingQueue<>();

	private ReadHelper rhelper = null;
	private WriteHelper whelper = null;
	private MessageHandler mhandler = null;
	private final Lock rh_lock = new ReentrantLock();
	private final Lock wh_lock = new ReentrantLock();
	private final Condition rh_cond = rh_lock.newCondition();
	private final Condition wh_cond = wh_lock.newCondition();

	private volatile boolean is_locking_r = false;
	private volatile boolean is_locking_w = false;

	public ConnectNode(final String name) throws IOException{
		super(String.format("connNode(%s)", name));
		this.netsvr = NetServer.onServerJoin(UUID.randomUUID(), name);
		if(this.netsvr == null){
			throw new IllegalStateException("Can not create new net server");
		}
		this.host = null;
		this.port = -1;
		this.accept_selector = null;
		this.read_selector = Selector.open();
		this.write_selector = Selector.open();
		this.serverChannel = null;
		this.start();
	}

	public ConnectNode(final String name, final String host, final int port) throws IOException{
		super(String.format("connNode(%s:%s:%d)", name, host, port));
		this.netsvr = NetServer.onServerJoin(UUID.randomUUID(), name);
		if(this.netsvr == null){
			throw new IllegalStateException("Can not create new net server");
		}
		this.host = (host == null || host.length() == 0) ?"0.0.0.0" :host;
		this.port = port;
		this.accept_selector = Selector.open();
		this.read_selector = Selector.open();
		this.write_selector = Selector.open();
		this.serverChannel = ServerSocketChannel.open();
		this.serverChannel.configureBlocking(false);
		this.serverChannel.socket().bind(new InetSocketAddress(this.host, this.port));
		this.start();
	}

	public NetServer getNetSvr(){
		return this.netsvr;
	}

	public int getStatus(){
		return this.status;
	}

	void beginLockReader(){
		this.is_locking_r = true;
		this.read_selector.wakeup();
	}

	void endLockReader(){
		this.is_locking_r = false;
		this.rh_lock.lock();
		this.rh_cond.signalAll();
		this.rh_lock.unlock();
	}

	void checkReaderLock() throws InterruptedException{
		if(this.is_locking_r){
			this.rh_lock.lockInterruptibly();
			try{
				if(this.is_locking_r){
					this.rh_cond.await();
				}
			}finally{
				this.rh_lock.unlock();
			}
		}
	}

	void beginLockWriter(){
		this.is_locking_w = true;
		this.write_selector.wakeup();
	}

	void endLockWriter(){
		this.is_locking_w = false;
		this.wh_lock.lock();
		this.wh_cond.signalAll();
		this.wh_lock.unlock();
	}

	void checkWriterLock() throws InterruptedException{
		if(this.is_locking_w){
			this.wh_lock.lockInterruptibly();
			try{
				if(this.is_locking_w){
					this.wh_cond.await();
				}
			}finally{
				this.wh_lock.unlock();
			}
		}
	}

	Selector getReadSelector(){
		return this.read_selector;
	}

	Selector getWriteSelector(){
		return this.write_selector;
	}

	private static final class MsgNode{
		List<NetServer> list;
		NetServer svr;
		Message msg;
		private MsgNode(final List<NetServer> list, final NetServer svr, final Message msg){
			this.list = list;
			this.svr = svr;
			this.msg = msg;
		}
		MsgNode(final NetServer svr, final Message msg){
			this(new ArrayList<NetServer>(), svr, msg);
		}
		MsgNode child(final NetServer svr){
			this.list.add(svr);
			return new MsgNode(this.list, svr, this.msg);
		}
	}

	public boolean sendMessage(final Message msg){
		final Set<UUID> recevers = new HashSet<>(msg.getRecevers());
		if(recevers.remove(this.netsvr.getUuid())){
			this.msgQueue.add(msg);
		}
		if(recevers.size() == 0){
			return true;
		}
		final List<MsgNode> msgs = new ArrayList<>();
		final ArrayDeque<MsgNode> queue = new ArrayDeque<>();
		queue.add(new MsgNode(this.netsvr, null));
		MsgNode node;
		while(recevers.size() > 0 && (node = queue.poll()) != null){
			final UUID uuid = node.svr.getUuid();
			if(recevers.contains(uuid)){
				if(node.msg == null){
					node.msg = new Message(msg.getSender(), msg.getData());
					msgs.add(node);
				}
				node.msg.addRecever(uuid);
				recevers.remove(uuid);
			}
			for(NetServer s: node.svr.getLines()){
				queue.add(node.child(s));
			}
		}
		for(MsgNode n: msgs){
			n.list.get(1).getConn().sendMessage(msg);
		}
		return recevers.size() == 0;
	}

	@Override
	public void interrupt(){
		super.interrupt();
		this.status = -1;
		if(this.accept_selector != null){
			this.accept_selector.wakeup();
		}
	}

	public void close(){
		this.interrupt();
	}

	@Override
	public void run(){
		if(this.status != 0){
			return;
		}
		this.status = 1;
		if(this.host == null || this.port < 0){
			this.status = 2;
		}
		try{
			this.rhelper = this.new ReadHelper();
			this.whelper = this.new WriteHelper();
			this.mhandler = this.new MessageHandler();
			this.rhelper.start();
			this.whelper.start();
			this.mhandler.start();

			if(this.status == 2){
				this.rhelper.join();
				this.whelper.join();
				return;
			}
			this.serverChannel.socket().setSoTimeout(1000 * 30);
			this.serverChannel.register(this.accept_selector, SelectionKey.OP_ACCEPT);

			KSvrGroupMod.LOGGER.info(String.format("Server Group connect node(SGCN) start at %s:%d",
				this.serverChannel.socket().getInetAddress().getHostName(),
				this.serverChannel.socket().getLocalPort()));
			while(!this.isInterrupted()){
				int nReady = this.accept_selector.select();
				if(nReady == 0){
					continue;
				}
				final Iterator<SelectionKey> it = this.accept_selector.selectedKeys().iterator();
				while(it.hasNext()){
					final SelectionKey key = it.next();
					it.remove();
					try{
						if(key.isValid() && key.isAcceptable()){
							this.onAccept(key);
						}
					}catch(IOException e){
						KSvrGroupMod.LOGGER.error("[SGCN] accept error:", e);
					}
				}
			}
		}catch(InterruptedException e){
		}catch(Exception e){
			KSvrGroupMod.LOGGER.error("Server Group connect node error:", e);
		}finally{
			KSvrGroupMod.LOGGER.info("Closing Server Group connect node");
			NetServer.onServerLeave(this.netsvr);
			if(this.serverChannel != null && this.serverChannel.isOpen()){
				try{
					this.serverChannel.close();
				}catch(IOException e){
				}
			}
			if(this.rhelper != null){
				this.rhelper.interrupt();
				this.read_selector.wakeup();
				this.rhelper = null;
			}
			if(this.whelper != null){
				this.whelper.interrupt();
				this.write_selector.wakeup();
				this.whelper = null;
			}
			if(this.mhandler != null){
				this.mhandler.interrupt();
				this.mhandler = null;
			}
		}
	}

	final class ReadHelper extends Thread{
		ReadHelper(){
			super(ConnectNode.this.getName() + "-RH");
		}
		@Override
		public void run(){
			final ConnectNode that = ConnectNode.this;
			try{
				while(!this.isInterrupted()){
					that.checkReaderLock();
					int nReady = that.read_selector.select();
					if(nReady == 0){
						continue;
					}
					final Iterator<SelectionKey> it = that.read_selector.selectedKeys().iterator();
					while(it.hasNext()){
						final SelectionKey key = it.next();
						it.remove();
						try{
							if(key.isValid() && key.isReadable()){
								that.onRead(key);
							}
						}catch(IOException e){
							cancelKeyChannel(key);
							KSvrGroupMod.LOGGER.error("[SGCN] read error:", e);
						}
					}
				}
			}catch(InterruptedException e){
			}catch(IOException e){
				KSvrGroupMod.LOGGER.error("[SGCN] read helper error:", e);
			}
		}
	}

	final class WriteHelper extends Thread{
		WriteHelper(){
			super(ConnectNode.this.getName() + "-WH");
		}
		@Override
		public void run(){
			final ConnectNode that = ConnectNode.this;
			try{
				while(!this.isInterrupted()){
					that.checkWriterLock();
					int nReady = that.write_selector.select();
					if(nReady == 0){
						continue;
					}
					final Iterator<SelectionKey> it = that.write_selector.selectedKeys().iterator();
					while(it.hasNext()){
						final SelectionKey key = it.next();
						it.remove();
						if(key.isValid() && key.isWritable()){
							try{
								that.onWrite(key);
							}catch(IOException e){
								cancelKeyChannel(key);
								key.channel().close();
								KSvrGroupMod.LOGGER.error("[SGCN] write error:", e);
							}
						}
					}
				}
			}catch(InterruptedException e){
			}catch(IOException e){
				KSvrGroupMod.LOGGER.error("[SGCN] write helper error:", e);
			}
		}
	}

	final class MessageHandler extends Thread{
		MessageHandler(){
			super(ConnectNode.this.getName() + "-MH");
		}
		@Override
		public void run(){
			try{
				Message msg;
				while(!this.isInterrupted()){
					msg = ConnectNode.this.msgQueue.take();
					if(msg != null){
						msg.getDirecter().onMessage(msg);
					}
				}
			}catch(InterruptedException e){
			}
		}
	}

	private void onAccept(final SelectionKey key) throws IOException {
		final ServerSocketChannel channel = (ServerSocketChannel)(key.channel());
		final SocketChannel socketChannel = channel.accept();
		this.connectMap.put(chToString(socketChannel), new Connect(this, socketChannel));
		KSvrGroupMod.LOGGER.info("Accepted another SGCN " + chToString(socketChannel));
	}

	private void onRead(final SelectionKey key) throws IOException {
		final SocketChannel socketChannel = (SocketChannel)(key.channel());
		final Connect conn = this.connectMap.get(chToString(socketChannel));
		if(conn == null){
			return;
		}
		conn.onRead();
	}

	private void onWrite(final SelectionKey key) throws IOException {
		final SocketChannel socketChannel = (SocketChannel)(key.channel());
		final Connect conn = this.connectMap.get(chToString(socketChannel));
		if(conn == null){
			return;
		}
		conn.onWrite();
	}

	void onConnClose(final Connect conn){
		final String key = chToString(conn.getChannel());
		this.connectMap.remove(key);
	}

	public Message createMessage(final byte[] data){
		return new Message(this.netsvr.getUuid(), ByteBuffer.wrap(data));
	}

	public Message createMessage(final byte[] data, final Collection<UUID> recevers){
		return new Message(this.netsvr.getUuid(), ByteBuffer.wrap(data), recevers);
	}

	public Message createMessage(final ByteBuffer data){
		return new Message(this.netsvr.getUuid(), data);
	}

	public Message createMessage(final ByteBuffer data, final Collection<UUID> recevers){
		return new Message(this.netsvr.getUuid(), data, recevers);
	}

	public boolean tryConnect(final String host, final int port){
		return this.tryConnect(host, port, Config.INSTANCE.getAuthmods());
	}

	public boolean tryConnect(final String host, final int port, final byte authmods){
		return this.tryConnect(new ServerItem(host, port, authmods));
	}

	public boolean tryConnect(final ServerItem svr){
		try{
			final SocketChannel socketChannel = SocketChannel.open();
			socketChannel.connect(new InetSocketAddress(svr.getHost(), svr.getPort()));
			KSvrGroupMod.LOGGER.info("Connecting to " + svr.toString());
			final Connect conn = new Connect(this, socketChannel, true);
			this.connectMap.put(chToString(socketChannel), conn);
			conn.initialize(svr);
		}catch(IOException e){
			KSvrGroupMod.LOGGER.error("Try connect to " + svr.toString() + " error: " + e.getMessage());
		}
		return true;
	}

	static String chToString(final SocketChannel socketChannel){
		return socketChannel.socket().getLocalAddress().getHostName() + ":" + socketChannel.socket().getPort();
	}

	static void cancelKeyChannel(final SelectionKey key) throws IOException{
		key.cancel();
		key.selector().wakeup();
		key.channel().close();
	}
}
