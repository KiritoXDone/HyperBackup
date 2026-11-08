package io.github.kiritoxdone.hyperbackup

import android.app.Application
import io.github.kiritoxdone.hyperbackup.data.*
import io.github.kiritoxdone.hyperbackup.engine.EngineGateway

class HyperBackupApp : Application() {
    val preferences by lazy { Preferences(this) }
    val session by lazy { BackupSession.create(this, EngineGateway(this)) }
}
