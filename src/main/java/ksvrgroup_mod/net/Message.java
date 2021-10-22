
package com.github.kpnmserver.ksvrgroup_mod.net;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.Collection;
import java.util.Set;
import java.util.HashSet;

import com.github.kpnmserver.ksvrgroup_mod.util.ZipUtil;

public class Message{
	public static final UUID NIL_UUID = new UUID(0L, 0L);

	private final UUID sender;
	private final Connect directer;
	private final Set<UUID> recevers;
	private final ByteBuffer data;


	Message(final UUID sender, final ByteBuffer data){
		this(sender, data, null);
	}

	Message(final UUID sender, final ByteBuffer data, final Collection<UUID> recevers){
		this(sender, null, data, recevers);
	}

	Message(final UUID sender, final Connect directer, final ByteBuffer data, final Collection<UUID> recevers){
		if(sender == null && directer == null){ // meaning is direct message
			throw new IllegalArgumentException("sender == null && directer == null");
		}
		if(data == null){
			throw new IllegalArgumentException("sender == null");
		}
		this.sender = sender;
		this.directer = directer;
		if(this.sender == null){
			this.recevers = null;
		}else{
			this.recevers = recevers == null ?new HashSet<>() :new HashSet<>(recevers);
			this.recevers.remove(this.sender);;
		}
		this.data = data.asReadOnlyBuffer();
	}

	public UUID getSender(){
		return this.sender;
	}

	public boolean isDirect(){
		return this.sender == null;
	}

	public Connect getDirecter(){
		return this.directer;
	}

	public Set<UUID> getRecevers(){
		if(this.sender == null){
			throw new IllegalStateException();
		}
		return this.recevers;
	}

	public Message addRecever(final UUID recever){
		if(this.sender == null){
			throw new IllegalStateException();
		}
		this.recevers.add(recever);
		return this;
	}

	public ByteBuffer getData(){
		return this.data.slice();
	}

	public int hashCode(){
		int hash = 0;
		hash = sender.hashCode();
		for(UUID r: this.recevers){
			hash = hash * 31 + r.hashCode();
		}
		hash = hash * 31 + data.hashCode();
		return hash;
	}

	public int length(){
		return 1 + 16 + ((this.recevers == null) ?0 :(this.recevers.size() * 16)) + this.data.remaining();
	}

	public ByteBuffer toByteBuffer(){
		final int leng = this.length() - 1;
		final ByteBuffer buffer = ByteBuffer.allocate(1 + leng);
		buffer.put((byte)(0));
		if(this.sender == null){
			buffer.putLong(0L).putLong(0L);
		}else{
			buffer.putLong(this.sender.getMostSignificantBits())
			      .putLong(this.sender.getLeastSignificantBits());
			buffer.putInt(this.recevers.size());
			for(UUID u: this.recevers){
				buffer.putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits());
			}
		}
		buffer.put(this.getData());
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

	public byte[] toByteArray(){
		return this.toByteBuffer().array();
	}

	public ByteBuffer writeToByteBuffer(final ByteBuffer buffer){
		if(buffer.remaining() < this.length()){
			throw new IllegalArgumentException("buffer.remaining() < this.length()");
		}
		final int leng = this.length() - 1;
		buffer.mark();
		buffer.put((byte)(0));
		if(this.sender == null){
			buffer.putLong(0L).putLong(0L);
		}else{
			buffer.putLong(this.sender.getMostSignificantBits()).putLong(this.sender.getLeastSignificantBits());
			buffer.putInt(this.recevers.size());
			for(UUID u: this.recevers){
				buffer.putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits());
			}
		}
		buffer.put(this.getData());
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
		}/*else{
			buffer = ByteBuffer.allocate(buffer.remaining()).put(buffer);
			buffer.flip();
		}*/
		final UUID sender = new UUID(buffer.getLong(), buffer.getLong());
		if(NIL_UUID.equals(sender)){
			return Message.createDirect(directer, buffer);
		}
		int leng = buffer.getInt();
		for(;leng > 0 ;leng--){
			recevers.add(new UUID(buffer.getLong(), buffer.getLong()));
		}
		return new Message(sender, directer, buffer, recevers);
	}

	public static Message createDirect(final Connect directer, final ByteBuffer data){
		return new Message(null, directer, data, null);
	}

	public static Message createDirect(final Connect directer, final byte[] data){
		return new Message(null, directer, ByteBuffer.wrap(data), null);
	}

	public static Message createDirect(final Connect directer, final byte[] data, final int offset, final int length){
		return new Message(null, directer, ByteBuffer.wrap(data, offset, length), null);
	}
}
