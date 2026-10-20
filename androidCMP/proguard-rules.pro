# The libraries ship their own consumer rules (kotlinx.serialization, Ktor, OkHttp, Koin, Room,
# Compose). Add a rule here only for a class R8 breaks, with the reason next to it.

# ML Kit (QR pairing) finds its registrars by name and builds them through the no-argument
# constructor; full-mode R8 drops it ("NoSuchMethodException ... Registrar.<init>").
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
