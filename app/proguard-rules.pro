# R8 keep rules for the release build.
#
# Most of what this app depends on ships its own consumer rules (Compose,
# WorkManager, DataStore, kotlinx-serialization). The rules below cover the
# two places where *this* project's own code is reached reflectively or by
# generated code, which consumer rules cannot know about.

# --- kotlinx.serialization -------------------------------------------------
# @Serializable classes in :data (ShadeMetadata, ShadeAction, Command,
# ActionIcon) are constructed through generated $$serializer classes that
# nothing references statically. Without these, persisted JSON fails to decode
# in a minified build — and ShadeStore/ActionStore treat a decode failure as
# "no data", so the symptom is silently empty shade and action lists.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.scrivtech.powerview.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.scrivtech.powerview.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.scrivtech.powerview.data.**$$serializer { *; }

# --- WorkManager -----------------------------------------------------------
# CommandWorker is matched by class *name* in CommandWorker.Factory
# (workerClassName != CommandWorker::class.java.name), so obfuscating it
# silently stops every widget/tile command from running.
-keep class com.scrivtech.powerview.widget.CommandWorker { *; }

# --- Glance widget ---------------------------------------------------------
# Both of these are reached by class name rather than by a static reference:
# the receiver from the merged manifest, and RunActionCallback from the
# action parameters Glance serialises into the RemoteViews click intent.
# Glance ships consumer rules covering its own types, but these are ours, and
# the failure mode if they are stripped is a home-screen widget whose buttons
# do nothing in release builds only — the worst kind of bug to find late.
-keep class com.scrivtech.powerview.widget.ShadeActionWidgetReceiver { *; }
-keep class com.scrivtech.powerview.widget.RunActionCallback { *; }

# --- Tink (via androidx.security-crypto) -----------------------------------
# EncryptedSharedPreferences pulls in Google Tink, which is compiled against
# compile-only annotations -- ErrorProne's and JSR-305's -- that are absent from
# the runtime classpath by design. R8 sees the dangling references, reports them
# as missing classes and fails the build. Nothing dereferences an annotation at
# runtime, so warning about them is the only thing to suppress here; this is not
# a `-keep` and it does not weaken shrinking.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
