package com.iainconnor.objectcache;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.*;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DiskCache {
	private final String HASH_ALGORITHM = "MD5";
	private final String STRING_ENCODING = "UTF-8";
	private final int DEFAULT_CACHE_SIZE = 1024 * 1024 * 10;
	private final int VALUE_COUNT = 1;
	private DiskLruCache diskLruCache;

	public DiskCache ( File cacheDirectory, int appVersion, int cacheSizeKb ) throws IOException {
		diskLruCache = DiskLruCache.open(cacheDirectory, appVersion, VALUE_COUNT, cacheSizeKb <= 0 ? DEFAULT_CACHE_SIZE : cacheSizeKb);
	}

	public String getValue ( String key ) throws IOException {
		String value = null;
		DiskLruCache.Snapshot snapshot = null;

		try {
			snapshot = diskLruCache.get(getHashOf(key));
			if (snapshot == null) {
				return null;
			}

			value = snapshot.getString(0);
		} finally {
			if (snapshot != null) {
				snapshot.close();
			}
		}

		return value;
	}

	public boolean contains ( String key ) throws IOException {
		boolean found = false;
		DiskLruCache.Snapshot snapshot = null;

		try {
			snapshot = diskLruCache.get(getHashOf(key));
			if (snapshot != null) {
				found = true;
			}
		} finally {
			if (snapshot != null) {
				snapshot.close();
			}
		}

		return found;
	}

	public void setKeyValue ( String key, String value ) throws IOException {
		DiskLruCache.Editor editor = null;
		try {
			editor = diskLruCache.edit(getHashOf(key));
			if (editor == null) {
				return;
			}

			if (writeValueToCache(value, editor)) {
				diskLruCache.flush();
				editor.commit();
			} else {
				editor.abort();
			}
		} catch (IOException e) {
			if (editor != null) {
				editor.abort();
			}

			throw e;
		}
	}

	public void clearCache () throws IOException {
		diskLruCache.delete();
	}

	protected boolean writeValueToCache ( String value, DiskLruCache.Editor editor ) throws IOException {
		OutputStream outputStream = null;
		try {
			outputStream = new BufferedOutputStream(editor.newOutputStream(0));
			outputStream.write(value.getBytes(STRING_ENCODING));
		} finally {
			if (outputStream != null) {
				outputStream.close();
			}
		}

		return true;
	}

	protected String getHashOf ( String string ) throws UnsupportedEncodingException {
		try {
			MessageDigest messageDigest = null;
			messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
			messageDigest.update(string.getBytes(STRING_ENCODING));
			byte[] digest = messageDigest.digest();
			BigInteger bigInt = new BigInteger(1, digest);

			return bigInt.toString(16);
		} catch (NoSuchAlgorithmException e) {

			return string;
		}
	}
}
