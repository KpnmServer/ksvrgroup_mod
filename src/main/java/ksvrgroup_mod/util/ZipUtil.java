
package com.github.kpnmserver.ksvrgroup_mod.util;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;

public final class ZipUtil{
	private ZipUtil(){}

	public static byte[] gzipEncode(final byte[] data){
		return gzipEncode(data, 0, data.length);
	}

	public static byte[] gzipEncode(final byte[] data, final int offset, final int length){
		final ByteArrayOutputStream baout = new ByteArrayOutputStream();
		try{
			final GZIPOutputStream gout = new GZIPOutputStream(baout);
			gout.write(data, offset, length);
			gout.finish();
		}catch(IOException e){
			// impossible
		}
		return baout.toByteArray();
	}


	public static byte[] gzipDecode(final byte[] data){
		return gzipDecode(data, 0, data.length);
	}

	public static byte[] gzipDecode(final byte[] data, final int offset, final int length){
		try{
			final GZIPInputStream gin = new GZIPInputStream(new ByteArrayInputStream(data, offset, length));
			byte[] buf = new byte[length > 128 ? length * 4 / 2:128];
			int off = 0;
			int n;
			while((n = gin.read(buf, off, buf.length - off)) > 0){
				if((off += n) == buf.length){
					final byte[] old = buf;
					buf = new byte[old.length * 3 / 2];
					System.arraycopy(old, 0, buf, 0, old.length);
				}
			}
			if(buf.length > off){
				final byte[] old = buf;
				buf = new byte[off];
				System.arraycopy(old, 0, buf, 0, off);
			}
			return buf;
		}catch(IOException e){
			// impossible
		}
		return null;
	}

	
}