package com.iainconnor.objectcache;

class CachedObject {
	private int expiryTimeSeconds;
	private int expiryTimestamp;
	private int creationTimestamp;
	private boolean softExpiry;
	private String payload;

	public CachedObject ( String payload, int expiryTimeSeconds, boolean softExpiry ) {
		this.expiryTimeSeconds = expiryTimeSeconds <= 0 ? -1 : expiryTimeSeconds;
		this.creationTimestamp = (int) (System.currentTimeMillis() / 1000L);
		this.expiryTimestamp = expiryTimeSeconds <= 0 ? -1 : this.creationTimestamp + this.expiryTimeSeconds;
		this.payload = payload;
		this.softExpiry = softExpiry;
	}

	public boolean isExpired () {
		return expiryTimeSeconds >= 0 && expiryTimestamp < (int) (System.currentTimeMillis() / 1000L);
	}

	public String getPayload () {
		return payload;
	}

	public boolean isSoftExpired () {
		return isExpired() && softExpiry;
	}
}
