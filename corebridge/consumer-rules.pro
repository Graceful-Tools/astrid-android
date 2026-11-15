# JNA and the UniFFI bindings are reached by reflection and by name from native code.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class uniffi.** { *; }
-dontwarn java.awt.**
# The certificate verifier's Kotlin half is reached only from native code (AATD-100).
-keep, includedescriptorclasses class org.rustls.platformverifier.** { *; }
