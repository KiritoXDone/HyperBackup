# libsu instantiates the service by its manifest component name in another process.
-keep class io.github.kiritoxdone.hyperbackup.engine.root.HyperBackupRootService {
    public <init>();
    public void onCreate();
    public android.os.IBinder onBind(android.content.Intent);
    public boolean onUnbind(android.content.Intent);
}

# Room and kotlinx.serialization supply their own consumer rules.
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable

# SMBJ 0.14 skips SPNEGO on Android (SmbConfig.getDefaultAuthenticators).
# Its MBassador bus uses annotation handlers, with no EL expression filters.
-dontwarn org.ietf.jgss.**
-dontwarn javax.el.**
-keepclassmembers class * {
    @net.engio.mbassy.listener.Handler <methods>;
}
