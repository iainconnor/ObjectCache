package com.iainconnor.objectcache;

import android.os.AsyncTask;
import com.google.gson.Gson;

import java.io.IOException;
import java.lang.reflect.Type;
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

	public Object get ( String key, Class objectClass, Type objectType ) {
		Object result = null;
		String internalKey = getInternalKey(key, objectClass);

		CachedObject runtimeCachedObject = runtimeCache.get(internalKey);
		if (runtimeCachedObject != null && !runtimeCachedObject.isExpired()) {
			result = new Gson().fromJson(runtimeCachedObject.getPayload(), objectType);
		} else if (runtimeCachedObject != null && runtimeCachedObject.isSoftExpired()) {
			result = new SoftCachedObject<Object>(new Gson().fromJson(runtimeCachedObject.getPayload(), objectType));
		} else {
			try {
				String json = diskCache.getValue(internalKey);
				if (json != null) {
					CachedObject cachedObject = new Gson().fromJson(json, CachedObject.class);
					if (!cachedObject.isExpired()) {
						runtimeCache.put(internalKey, cachedObject);
						result = new Gson().fromJson(cachedObject.getPayload(), objectType);
					} else {
						if (cachedObject.isSoftExpired()) {
							result = new SoftCachedObject<Object>(new Gson().fromJson(cachedObject.getPayload(), objectType));
						}

						// To avoid cache rushing, we insert the value back in the cache with a longer expiry
						// Presumably, whoever received this expiration result will have inserted a fresh value by now
						putAsync(key, new Gson().fromJson(cachedObject.getPayload(), objectType), CACHE_RUSH_SECONDS, false, new PutCallback() {
							@Override
							public void onSuccess () {

							}

							@Override
							public void onFailure ( Exception e ) {

							}
						});
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				// Do nothing, return null
			}
		}

		return result;
	}

	public void getAsync ( String key, Class objectClass, Type objectType, GetCallback getCallback ) {
		new GetAsyncTask(key, objectClass, objectType, getCallback).execute();
	}

	public boolean put ( String key, Object object ) {
		return put(key, object, -1, false);
	}

	public boolean put ( String key, Object object, int expiryTimeSeconds, boolean allowSoftExpiry ) {
		boolean result = false;
		String internalKey = getInternalKey(key, object);

		try {
			String payloadJson = new Gson().toJson(object);
			CachedObject cachedObject = new CachedObject(payloadJson, expiryTimeSeconds, allowSoftExpiry);
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
		putAsync(key, object, -1, false, putCallback);
	}

	public void putAsync ( String key, Object object, int expiryTimeSeconds, boolean allowSoftExpiry, PutCallback putCallback ) {
		new PutAsyncTask(key, object, expiryTimeSeconds, allowSoftExpiry, putCallback).execute();
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
	private class GetAsyncTask extends AsyncTask<Void, Void, Object> {
		private final String key;
		private final GetCallback callback;
		private final Type objectType;
		private final Class objectClass;
		private Exception e;

		private GetAsyncTask ( String key, Class objectClass, Type objectType, GetCallback callback ) {
			this.callback = callback;
			this.key = key;
			this.objectType = objectType;
			this.objectClass = objectClass;
		}

		@Override
		protected Object doInBackground ( Void... voids ) {
			Object result = null;
			String internalKey = getInternalKey(key, objectClass);

			CachedObject runtimeCachedObject = runtimeCache.get(internalKey);
			if (runtimeCachedObject != null && !runtimeCachedObject.isExpired()) {
				result = new Gson().fromJson(runtimeCachedObject.getPayload(), objectType);
			} else if (runtimeCachedObject != null && runtimeCachedObject.isSoftExpired()) {
				result = new SoftCachedObject<Object>(new Gson().fromJson(runtimeCachedObject.getPayload(), objectType));
			} else {
				try {
					String json = diskCache.getValue(internalKey);
					if (json != null) {
						CachedObject cachedObject = new Gson().fromJson(json, CachedObject.class);

						if (!cachedObject.isExpired()) {
							result = new Gson().fromJson(cachedObject.getPayload(), objectType);
							runtimeCache.put(internalKey, cachedObject);
						} else {
							if (cachedObject.isSoftExpired()) {
								result = new SoftCachedObject<Object>(new Gson().fromJson(cachedObject.getPayload(), objectType));
							}

							// To avoid cache rushing, we insert the value back in the cache with a longer expiry
							// Presumably, whoever received this expiration result will have inserted a fresh value by now
							putAsync(key, new Gson().fromJson(cachedObject.getPayload(), objectType), CACHE_RUSH_SECONDS, false, new PutCallback() {
								@Override
								public void onSuccess () {

								}

								@Override
								public void onFailure ( Exception e ) {

								}
							});
						}
					}
				} catch (Exception e) {
					this.e = e;
				}
			}

			return result;
		}

		@Override
		protected void onPostExecute ( Object object ) {
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
		private final boolean allowSoftExpiry;
		private Exception e;

		private PutAsyncTask ( String key, Object payload, int expiryTimeSeconds, boolean allowSoftExpiry, PutCallback callback ) {
			this.key = key;
			this.callback = callback;
			this.payload = payload;
			this.expiryTimeSeconds = expiryTimeSeconds;
			this.allowSoftExpiry = allowSoftExpiry;
		}

		@Override
		protected Void doInBackground ( Void... voids ) {
			String internalKey = getInternalKey(key, payload);

			try {
				String payloadJson = new Gson().toJson(payload);
				CachedObject cachedObject = new CachedObject(payloadJson, expiryTimeSeconds, allowSoftExpiry);
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
}
