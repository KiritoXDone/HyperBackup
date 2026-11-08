package io.github.kiritoxdone.hyperbackup.engine;
import android.os.ParcelFileDescriptor;
interface IRootBridge {
    ParcelFileDescriptor call(String command, in ParcelFileDescriptor payload);
}
