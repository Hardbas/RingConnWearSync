# Project-specific R8 rules for the watch app.
#
# There is no entry here yet, and that is deliberate: everything this module needs to keep
# already ships with the libraries it depends on. Compose, ProtoLayout, the Tiles runtime,
# play-services-wearable and the complication data sources all carry consumer rules, and every
# component R8 must not rename (TileService, ComplicationDataSourceService,
# WearableListenerService) is declared in AndroidManifest.xml, which the Android Gradle plugin
# turns into keep rules on its own.
#
# This file exists because wear/build.gradle.kts names it. It used to not exist at all, which was
# harmless only while isMinifyEnabled was false: a minified build would have failed on a missing
# rules file before compiling a single class.
#
# Add rules here only with a comment that says which library forces them and which runtime
# failure was observed. A keep rule nobody can explain is a crash someone will re-introduce.
