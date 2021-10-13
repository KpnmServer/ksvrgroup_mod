
package com.github.kpnmserver.ksvrgroup_mod.net;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.Collection;
import java.util.Set;
import java.util.HashSet;

import com.github.kpnmserver.ksvrgroup_mod.util.ZipUtil;

public class Message{
	private final UUID sender;
	private final Connect directer;
	private final Set<UUID> recevers;
	private final byte[] data;

	Message(final UUID sender, final byte[] data){
		this(sender, null, data, null);
	}

	Message(final UUID sender, final byte[] data, final Collection<UUID> recevers){
		this(sender, null, data, recevers);
	}

	Message(final UUID sender, final Connect directer, final byte[] data, final Collection<UUID> recevers){
		if(sender == null){
			throw new IllegalArgumentException("sender == null");
		}
		if(data == null){
			throw new IllegalArgumentException("sender == null");
		}
		this.sender = sender;
		this.directer = directer;
		this.recevers = recevers == null ?new HashSet<>() :new HashSet<>(recevers);
		this.recevers.remove(this.sender);;
		this.data = data;
	}

	public UUID getSender(){
		return this.sender;
	}

	public Connect getDirecter(){
		return this.directer;
	}

	public Set<UUID> getRecevers(){
		return this.recevers;
	}

	public Message addRecever(final UUID recever){
		this.recevers.add(recever);
		return this;
	}

	public byte[] getData(){
		return this.data;
	}

	public int length(){
		return 1 + 16 + this.recevers.size() * 16 + this.data.length;
	}

	public ByteBuffer toByteBuffer(){
		final int leng = this.length() - 1;
		final ByteBuffer buffer = ByteBuffer.allocate(1 + leng);
		buffer.put((byte)(0));
		buffer.putLong(this.sender.getMostSignificantBits());
		buffer.putLong(this.sender.getLeastSignificantBits());
		buffer.putInt(this.recevers.size());
		for(UUID u: this.recevers){
			buffer.putLong(u.getMostSignificantBits());
			buffer.putLong(u.getLeastSignificantBits());
		}
		buffer.put(this.data);
		buffer.flip();
		if(leng > 1024 * 256){
			final byte[] ziparr = ZipUtil.gzipEncode(buffer.array(), buffer.arrayOffset() + 1, leng);
			if(ziparr.length < leng){
				buffer.clear();
				buffer.put((byte)(1)).put(ziparr).flip();
			}
		}
		return buffer;
	}

	public ByteBuffer writeToByteBuffer(final ByteBuffer buffer){
		if(buffer.remaining() < this.length()){
			throw new IllegalArgumentException("buffer.remaining() < this.length()");
		}
		final int leng = this.length() - 1;
		buffer.mark();
		buffer.put((byte)(0));
		buffer.putLong(this.sender.getMostSignificantBits());
		buffer.putLong(this.sender.getLeastSignificantBits());
		buffer.putInt(this.recevers.size());
		for(UUID u: this.recevers){
			buffer.putLong(u.getMostSignificantBits());
			buffer.putLong(u.getLeastSignificantBits());
		}
		buffer.put(this.data);
		if(leng > 1024 * 256){
			final byte[] ziparr;
			if(buffer.hasArray()){
				ziparr = ZipUtil.gzipEncode(buffer.array(), buffer.arrayOffset() + buffer.position() + 1, leng);
			}else{
				final byte[] uz = new byte[leng];
				buffer.reset().position(buffer.position() + 1);
				buffer.get(uz);
				ziparr = ZipUtil.gzipEncode(uz);
			}
			if(ziparr.length < leng){
				buffer.reset();
				buffer.put((byte)(1)).put(ziparr);
			}
		}
		return buffer;
	}

	static final Message parseMsg(final Connect directer, ByteBuffer buffer){
		final Set<UUID> recevers = new HashSet<>();
		final byte zipmode = buffer.get();
		if(zipmode == 1){
			System.out.println("get zipped data");
			if(buffer.hasArray()){
				buffer = ByteBuffer.wrap(ZipUtil.gzipDecode(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
			}else{
				final byte[] zd = new byte[buffer.remaining()];
				buffer.get(zd);
				buffer = ByteBuffer.wrap(ZipUtil.gzipDecode(zd));
			}
		}
		final UUID sender = new UUID(buffer.getLong(), buffer.getLong());
		int leng = buffer.getInt();
		for(;leng > 0 ;leng--){
			System.out.println("recever len:" + leng);
			recevers.add(new UUID(buffer.getLong(), buffer.getLong()));
		}
		final byte[] data = new byte[buffer.remaining()];
		buffer.get(data);
		System.out.println("data:" + data[0]);
		return new Message(sender, directer, data, recevers);
	}
}
