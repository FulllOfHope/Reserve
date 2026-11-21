# WorkManager creates the daily backup worker by class name. Its own consumer
# rules keep ListenableWorker subclasses; this makes the requirement explicit.
-keep public class com.app.reserve.core.backup.DailyBackupWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
