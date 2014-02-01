package com.iainconnor.objectcache;

public interface GetCallback<T> {
	public void onSuccess ( T object );
	public void onFailure ( Exception e );
}
