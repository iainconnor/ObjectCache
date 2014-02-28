package com.iainconnor.objectcache;

public class SoftCachedObject<T> {
	T object;

	public SoftCachedObject ( T object ) {
		this.object = object;
	}

	public T getObject () {
		return object;
	}
}
