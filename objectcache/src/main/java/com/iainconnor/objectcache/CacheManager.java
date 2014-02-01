package com.iainconnor.objectcache;

import android.os.AsyncTask;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;

public class CacheManager {
	private final static int CACHE_RUSH_SECONDS = 60 * 2;
	private static CacheManager ourInstance;
	private DiskCache diskCache;
	private HashMap<String, CachedObject> runtimeCache;

	public static CacheManager getInstance ( DiskCache diskCache ) {
		if (ourInstance == null) {
			ourInstance = new CacheManager(diskCache);
		}

		return ourInstance;
	}

	private CacheManager ( DiskCache diskCache ) {
		this.diskCache = diskCache;
		runtimeCache = new HashMap<String, CachedObject>();
	}

	public <T> boolean exists ( String key, Class<T> objectClass ) {
		boolean result = false;
		String internalKey = getInternalKey(key, objectClass);

		try {
			result = diskCache.contains(internalKey);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	@SuppressWarnings ("unchecked")
	public <T> T get ( String key, Class<T> objectClass ) {
		T result = null;
		String internalKey = getInternalKey(key, objectClass);

		CachedObject runtimeCachedObject = runtimeCache.get(internalKey);
		if (runtimeCachedObject != null && !runtimeCachedObject.isExpired()) {
			result = (T) runtimeCachedObject.getPayload();
		} else {
			try {
				String json = diskCache.getValue(internalKey);
				CachedObject cachedObject = new Gson().fromJson(json, CachedObject.class);
				if (!cachedObject.isExpired()) {
					runtimeCache.put(internalKey, cachedObject);
					result = (T) cachedObject.getPayload();
				} else {
					// To avoid cache rushing, we insert the value back in the cache with a longer expiry
					// Presumably, whoever received this expiration result will have inserted a fresh value by now
					putAsync(key, (T) cachedObject.getPayload(), CACHE_RUSH_SECONDS, new PutCallback() {
						@Override
						public void onSuccess () {

						}

						@Override
						public void onFailure ( Exception e ) {

						}
					});
				}
			} catch (Exception e) {
				e.printStackTrace();
				// Do nothing, return null
			}
		}

		return result;
	}

	public <T> void getAsync ( String key, Class<T> objectClass, GetCallback<T> getCallback ) {
		new GetAsyncTask<T>(key, objectClass, getCallback).execute();
	}

	public boolean put ( String key, Object object ) {
		return put(key, object, -1);
	}

	public boolean put ( String key, Object object, int expiryTimeSeconds ) {
		boolean result = false;
		String internalKey = getInternalKey(key, object);

		try {
			CachedObject cachedObject = new CachedObject(object, expiryTimeSeconds);
			String json = new Gson().toJson(cachedObject);
			runtimeCache.put(internalKey, cachedObject);
			diskCache.setKeyValue(internalKey, json);
			result = true;
		} catch (Exception e) {
			e.printStackTrace();
			// Do nothing, return false
		}

		return result;
	}

	public void putAsync ( String key, Object object, PutCallback putCallback ) {
		putAsync(key, object, -1, putCallback);
	}

	public void putAsync ( String key, Object object, int expiryTimeSeconds, PutCallback putCallback ) {
		new PutAsyncTask(key, object, expiryTimeSeconds, putCallback).execute();
	}

	private <T> String getInternalKey ( String key, T object ) {
		return getInternalKey(key, object.getClass());
	}

	private <T> String getInternalKey ( String key, Class<T> objectClass ) {
		return key + objectClass.getCanonicalName();
	}

	public enum ExpiryTimes {
		ONE_SECOND(1),
		ONE_MINUTE(60),
		ONE_HOUR(60 * 60),
		ONE_DAY(60 * 60 * 24),
		ONE_WEEK(60 * 60 * 24 * 7),
		ONE_MONTH(60 * 60 * 24 * 30),
		ONE_YEAR(60 * 60 * 24 * 365);

		private final int seconds;

		ExpiryTimes ( int seconds ) {
			this.seconds = seconds;
		}

		public int asSeconds () {
			return seconds;
		}
	}

	@SuppressWarnings ("unchecked")
	private class GetAsyncTask<T> extends AsyncTask<Void, Void, T> {
		private final String key;
		private final GetCallback<T> callback;
		private final Class<T> objectClass;
		private Exception e;

		private GetAsyncTask ( String key, Class<T> objectClass, GetCallback callback ) {
			this.callback = callback;
			this.key = key;
			this.objectClass = objectClass;
		}

		@Override
		protected T doInBackground ( Void... voids ) {
			T result = null;
			String internalKey = getInternalKey(key, objectClass);

			CachedObject runtimeCachedObject = runtimeCache.get(internalKey);
			if (runtimeCachedObject != null && !runtimeCachedObject.isExpired()) {
				result = (T) runtimeCachedObject.getPayload();
			} else {
				try {
					String json = diskCache.getValue(internalKey);
					CachedObject cachedObject = new Gson().fromJson(json, CachedObject.class);

					if (!cachedObject.isExpired()) {
						result = (T) cachedObject.getPayload();
					} else {
						// To avoid cache rushing, we insert the value back in the cache with a longer expiry
						// Presumably, whoever received this expiration result will have inserted a fresh value by now
						putAsync(key, (T) cachedObject.getPayload(), CACHE_RUSH_SECONDS, new PutCallback() {
							@Override
							public void onSuccess () {

							}

							@Override
							public void onFailure ( Exception e ) {

							}
						});
					}
				} catch (Exception e) {
					this.e = e;
				}
			}

			return result;
		}

		@Override
		protected void onPostExecute ( T object ) {
			if (callback != null) {
				if (e == null) {
					callback.onSuccess(object);
				} else {
					callback.onFailure(e);
				}
			}
		}
	}

	private class PutAsyncTask extends AsyncTask<Void, Void, Void> {
		private final PutCallback callback;
		private final String key;
		private final Object payload;
		private final int expiryTimeSeconds;
		private Exception e;

		private PutAsyncTask ( String key, Object payload, int expiryTimeSeconds, PutCallback callback ) {
			this.key = key;
			this.callback = callback;
			this.payload = payload;
			this.expiryTimeSeconds = expiryTimeSeconds;
		}

		@Override
		protected Void doInBackground ( Void... voids ) {
			String internalKey = getInternalKey(key, payload);

			try {
				CachedObject cachedObject = new CachedObject(payload, expiryTimeSeconds);
				String json = new Gson().toJson(cachedObject);
				runtimeCache.put(internalKey, cachedObject);
				diskCache.setKeyValue(internalKey, json);
			} catch (Exception e) {
				this.e = e;
			}

			return null;
		}

		@Override
		protected void onPostExecute ( Void aVoid ) {
			if (callback != null) {
				if (e == null) {
					callback.onSuccess();
				} else {
					callback.onFailure(e);
				}
			}
		}
	}

	protected class CachedObject {
		private int expiryTimeSeconds;
		private int expiryTimestamp;
		private int creationTimestamp;
		private Object payload;

		public CachedObject ( Object payload, int expiryTimeSeconds ) {
			this.expiryTimeSeconds = expiryTimeSeconds <= 0 ? -1 : expiryTimeSeconds;
			this.creationTimestamp = (int) (System.currentTimeMillis() / 1000L);
			this.expiryTimestamp = expiryTimeSeconds <= 0 ? -1 : this.creationTimestamp + this.expiryTimeSeconds;
			this.payload = payload;
		}

		public boolean isExpired () {
			return expiryTimeSeconds >= 0 && expiryTimestamp > (int) (System.currentTimeMillis() / 1000L);
		}

		public Object getPayload () {
			return payload;
		}
	}
}
