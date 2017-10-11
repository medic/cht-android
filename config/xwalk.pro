# ProGuard config for Crosswalk

-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.Nonnull
-dontwarn javax.annotation.concurrent.NotThreadSafe
-dontwarn javax.annotation.concurrent.ThreadSafe

-keep class org.xwalk.core.** {
	*;
}
-keep class org.chromium.** {
	*;
}
-keepattributes **

-optimizations !code/allocation/variable
