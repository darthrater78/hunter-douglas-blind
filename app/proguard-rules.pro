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
