# Readium uses reflection/serialization in a few places; keep its public surface.
-keep class org.readium.** { *; }
-dontwarn org.readium.**

# Room generated code is handled by Room's own consumer rules.
