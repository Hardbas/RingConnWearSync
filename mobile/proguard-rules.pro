# Project-specific R8 rules for the phone bridge.
#
# The phone release is not currently minified, so R8 never reads this file. It exists because
# mobile/build.gradle.kts names it, and a build that references a file which is not in the
# repository is a break waiting for the day someone turns minification on.
#
# When the phone module is minified, the components that must survive are the ones declared in
# AndroidManifest.xml (the accessibility service, the foreground service and the
# WearableListenerService): the Android Gradle plugin derives keep rules for those from the
# manifest. WorkManager resolves its worker by class name, and RingConnSyncWorker is referenced
# directly in code, so it is kept by reachability rather than by a rule.
#
# Add rules here only with a comment that says which library forces them and which runtime
# failure was observed.
