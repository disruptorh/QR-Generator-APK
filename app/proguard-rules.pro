# The JNI entry points are declared `external` in Kotlin, so R8 keeps them. The
# core itself is reached only through QrCore, so there is nothing else to keep.
-dontwarn org.jetbrains.annotations.**
