package com.iainconnor.objectcache;

public interface PutCallback {
	public void onSuccess ();

	public void onFailure ( Exception e );
}
