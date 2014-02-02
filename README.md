# ObjectCache

No matter what application I work on, sooner or later I'm stuck with the situation where I've spent time building an Object (commonly by retrieving it from a remote REST API) and I know the result of that work is valid for some period of time.

Luckily, thanks to the awesome work of some other Open Source developers, cacheing that work in Java is easy.
Let this library wrap the work involved in doing so up for you, so you can get back to building the parts of your application that matter.

## Introduction

Simply put, this library creates both a long-lived, on-disk (using the outstanding [DiskLruCache](https://github.com/JakeWharton/DiskLruCache) library) of JSON representations of your Objects (using the superb [GSON](https://code.google.com/p/google-gson/) library) and an in-memory, runtime cache of your Objects. You can optionally specify a time when those cache entries expire, and the goodness of cache-rush-mitigation is baked right into the crust.

Original credit for the base of this project goes out to [anupcowkur/Reservoir](https://github.com/anupcowkur/Reservoir), but my application required a slightly more specific implementation.

*NOTE:* Consider this untested.

## Installing in Gradle

1. Add the repository to your `build.gradle` file;

	``` groovy
	repositories {
		mavenCentral()
    	maven {
        	url 'https://raw.github.com/iainconnor/ObjectCache/master/maven/'
    	}
	}
	```
2. And add the dependency;

	``` groovy
	dependencies {
		compile 'com.iainconnor:objectcache:0.0.13-SNAPSHOT'
	}
	```

## Installing in other build tools

1. Download the `.jar` for the latest version [from this repository](https://github.com/iainconnor/ObjectCache/tree/master/maven/com/iainconnor/objectcache).
2. Add it to your project.
3. If you're building for Android, beg your boss to give you the time to switch to Gradle.

## Usage

First, you'll need to create an instance of `DiskCache ( File cacheDirectory, int appVersion, int cacheSizeKb )`. For an Android application, this is simple;

``` java
String cachePath = context.getCacheDir().getPath();
File cacheFile = new File(cachePath + File.separator + BuildConfig.PACKAGE_NAME);

DiskCache diskCache = new DiskCache(cacheFile, BuildConfig.VERSION_CODE, 1024 * 1024 * 10);
```

Then create an instance of the `CacheManager` singleton;

``` java
CacheManager cacheManager = CacheManager.getInstance(diskCache);
```

Insert an Object to be cached;

``` java
MyObject myObject = new MyObject("foo");
cacheManager.put("myKey", myObject);

MyExpiryObject myExpiryObject = new MyExpiryObject("bar");
cacheManager.put("myKeyExpiry", myExpiryObject, CacheManager.ExpiryTimes.ONE_WEEK.asSeconds());
```

And retrieve it;

``` java
Type myObjectType = new TypeToken<MyObject>(){}.getType();
MyObject myObject = cacheManager.get("myKey", MyObject.class, myObjectType);
if ( myObject != null ) {
	// Object was found!
} else {
	// Object was not found, or was expired.
	// You should re-generate it and trigger a `.put()`.
}
```

If you're on Android, these operations can be run off the main thread;

``` java
cacheManager.putAsync("myKeyExpiry", myExpiryObject, CacheManager.ExpiryTimes.ONE_WEEK.asSeconds(), new PutCallback() {
    @Override
    public void onSuccess () {

    }

    @Override
    public void onFailure ( Exception e ) {

    }
});

cacheManager.getAsync("myKeyExpiry", myExpiryObject.class, myExpiryObjectType, new GetCallback() {
    @Override
    public void onSuccess ( ExpiryObject myObject ) {
		if ( myObject != null ) {
        	// Object was found!
        } else {
        	// Object was not found, or was expired.
        	// You should re-generate it and trigger a `.put()`.
        }
    }

    @Override
    public void onFailure ( Exception e ) {

    }
});
```

If you want to clear the cache manually, you can use;

``` java
diskCache.clearCache();
```

## Contact

Love it? Hate it? Want to make changes to it? Contact me at [@iainconnor](http://www.twitter.com/iainconnor) or [iainconnor@gmail.com](mailto:iainconnor@gmail.com).