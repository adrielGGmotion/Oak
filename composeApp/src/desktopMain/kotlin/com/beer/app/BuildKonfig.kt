package com.beer.app

// For desktop, a common way is to use a system property.
// This can be set, for example, in the JVM arguments when running in debug mode: -Dbeer.debug=true
actual val isDebugBuild: Boolean = System.getProperty("beer.debug", "false").toBoolean()
