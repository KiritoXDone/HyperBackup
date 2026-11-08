package io.github.kiritoxdone.hyperbackup.domain

object ScriptSafetyCopy {
    fun specification(source: ScriptSpec, installedPackages: List<String>): ScriptSpec? {
        require(source.command.restore)
        val command = when (source.command) {
            ScriptCommand.RESTORE_WIFI -> ScriptCommand.BACKUP_WIFI
            ScriptCommand.RESTORE_FOLDERS -> error("FOLDER_SAFETY_SCOPE_NOT_FROZEN")
            else -> if (installedPackages.isEmpty()) return null else ScriptCommand.BACKUP_APPS
        }
        return source.copy(command = command, workingDirectory = "", setMode = BackupSetMode.NEW, existingSet = "",
            selectedPackages = if (command == ScriptCommand.BACKUP_APPS) installedPackages else emptyList(),
            selectedApps = emptyList(), associatedWifi = false, config = mapOf(
                "Backup_Mode" to "1", "Backup_user_data" to "1", "Backup_obb_data" to "1", "backup_media" to "0",
                "backup_messages" to "0", "backup_calllogs" to "0", "Custom_path" to source.config["Custom_path"].orEmpty(),
                "Backup_suffix" to "_PRE"))
    }
}
