# Room entities and DAOs are kept by Room's own consumer rules.
# Add rules here for networking/serialization libraries in later phases.

# The app uses kotlinx-serialization only through JsonElement (no @Serializable classes); Room (KSP) and
# OkHttp ship their own consumer rules.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
# Action history stores javaClass.simpleName ("Call", "SetTimer", ...): keep those names readable in release.
-keepnames class com.raju.aiassistant.domain.assistant.AssistantAction$*

# PDFBox (Knowledge Base PDF import) loads fonts/resources by name and has optional JPEG2000 support.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.**
