
package com.github.kpnmserver.ksvrgroup_mod.util;

import java.security.SecureRandom;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.SecretKeySpec;

public final class EncryptUtil{
	private EncryptUtil(){}

	public static final String RSA_ALGORITHM = "RSA";
	public static final String AES_ALGORITHM = "AES";
	public static final KeyFactory RSA_FACTORY;
	static{
		try{
			RSA_FACTORY = KeyFactory.getInstance(RSA_ALGORITHM);
		}catch(NoSuchAlgorithmException e){
			throw new AssertionError(e);
		}
	}

	public static KeyPair genRSAKeyPair() throws GeneralSecurityException{
		return genRSAKeyPair(2048);
	}

	public static KeyPair genRSAKeyPair(final int size) throws GeneralSecurityException{
		final KeyPairGenerator keygen = KeyPairGenerator.getInstance(RSA_ALGORITHM);
		keygen.initialize(size, new SecureRandom());
		return keygen.generateKeyPair();
	}

	public static void genRSAKeyPair(final int size, final byte[][] keys) throws GeneralSecurityException{
		if(keys.length != 2){
			throw new IllegalArgumentException("keys.length != 2");
		}
		final KeyPair keypair = genRSAKeyPair(size);
		keys[0] = keypair.getPrivate().getEncoded();
		keys[1] = keypair.getPublic().getEncoded();
	}

	public static void genRSAKeyPair(final byte[][] keys) throws GeneralSecurityException{
		genRSAKeyPair(2048, keys);
	}

	public static SecretKey genAESKey() throws GeneralSecurityException{
		return genAESKey(256);
	}

	public static byte[][] genRSAKeyPairBytes(final int size) throws GeneralSecurityException{
		final byte[][] keys = new byte[2][];
		genRSAKeyPair(size, keys);
		return keys;
	}

	public static byte[][] genRSAKeyPairBytes() throws GeneralSecurityException{
		return genRSAKeyPairBytes(2048);
	}

	public static SecretKey genAESKey(final int size) throws GeneralSecurityException{
		final KeyGenerator keygen = KeyGenerator.getInstance(AES_ALGORITHM);
		keygen.init(size, new SecureRandom());
		return keygen.generateKey();
	}

	public static byte[] genAESKeyBytes(final int size) throws GeneralSecurityException{
		final SecretKey key = genAESKey(size);
		return key.getEncoded();
	}

	public static byte[] genAESKeyBytes() throws GeneralSecurityException{
		return genAESKeyBytes(256);
	}

	public static PrivateKey encodeRSAPrivateKey(final byte[] key) throws GeneralSecurityException{
		return RSA_FACTORY.generatePrivate(new PKCS8EncodedKeySpec(key));
	}

	public static PublicKey encodeRSAPublicKey(final byte[] key) throws GeneralSecurityException{
		return RSA_FACTORY.generatePublic(new X509EncodedKeySpec(key));
	}

	public static SecretKey encodeAESKey(final byte[] key) throws GeneralSecurityException{
		return new SecretKeySpec(key, AES_ALGORITHM);
	}

	public static byte[] encryptRSA(final byte[] data, final Key key) throws GeneralSecurityException{
		final Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
		cipher.init(Cipher.ENCRYPT_MODE, key);
		return cipher.doFinal(data);
	}

	public static byte[] decryptRSA(final byte[] data, final Key key) throws GeneralSecurityException{
		final Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
		cipher.init(Cipher.DECRYPT_MODE, key);
		return cipher.doFinal(data);
	}

	public static byte[] encryptRSAByPriKey(final byte[] data, final byte[] key) throws GeneralSecurityException{
		return encryptRSA(data, encodeRSAPrivateKey(key));
	}

	private static byte[] encryptRSAByPubKey(final byte[] data, final byte[] key) throws GeneralSecurityException{
		return encryptRSA(data, encodeRSAPublicKey(key));
	}

	public static byte[] decryptRSAByPriKey(final byte[] data, final byte[] key) throws GeneralSecurityException{
		return decryptRSA(data, encodeRSAPrivateKey(key));
	}

	public static byte[] decryptRSAByPubKey(final byte[] data, final byte[] key) throws GeneralSecurityException{
		return decryptRSA(data, encodeRSAPublicKey(key));
	}

	public static byte[] encryptAES(final byte[] data, final SecretKey key) throws GeneralSecurityException{
		final Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
		cipher.init(Cipher.ENCRYPT_MODE, key);
		return cipher.doFinal(data);
	}

	public static byte[] decryptAES(final byte[] data, final SecretKey key) throws GeneralSecurityException{
		final Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
		cipher.init(Cipher.DECRYPT_MODE, key);
		return cipher.doFinal(data);
	}

	public static byte[] encryptAES(final byte[] data, final byte[] key) throws GeneralSecurityException{
		return encryptAES(data, encodeAESKey(key));
	}

	public static byte[] decryptAES(final byte[] data, final byte[] key) throws GeneralSecurityException{
		return decryptAES(data, encodeAESKey(key));
	}
}