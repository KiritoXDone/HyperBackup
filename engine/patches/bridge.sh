#!/system/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
# Host-only control plane. Business decisions stay in the pinned tools.sh.

_hb_event() {
    local _kind="$1" _pkg="$2" _part="$3" _code="$4" _detail="$5"
    _pkg="${_pkg//$'\t'/ }"; _pkg="${_pkg//$'\n'/ }"
    _part="${_part//$'\t'/ }"; _part="${_part//$'\n'/ }"
    _detail="${_detail//$'\t'/ }"; _detail="${_detail//$'\n'/ }"
    printf 'HB1\t%s\t%s\t%s\t%s\t%s\n' "$_kind" "$_pkg" "$_part" "$_code" "$_detail" >> "$HYPERBACKUP_JOB/events.tsv"
}

_hb_checkpoint() {
    if [[ -e $HYPERBACKUP_JOB/stop ]]; then
        _hb_event stopped "${name2:-}" "$1" 130 requested
        exit 130
    fi
    # The host records this phase durably before granting permission to begin mutation.
    local _gate
    _gate="$(umask 077; mktemp -d "$HYPERBACKUP_JOB/gates/g.XXXXXXXX")" || exit 65
    printf '%s\n' "$1" > "$_gate/phase"
    printf '%s\n' "${name2:-}" > "$_gate/package"
    : > "$_gate/READY"
    while [[ ! -f $_gate/ALLOW ]]; do
        [[ ! -f $HYPERBACKUP_JOB/stop ]] || exit 130
        kill -0 "$HYPERBACKUP_OWNER_PID" 2>/dev/null || exit 75
        sleep 0.1
    done
    _hb_event begin "${name2:-}" "$1" - checkpoint
}

_hb_prompt() {
    local _dir _answer _count=0 _line _mode="${4:-0}"
    _dir="$(umask 077; mktemp -d "$HYPERBACKUP_JOB/prompts/p.XXXXXXXX")" || return 65
    printf '%s' "$1" > "$_dir/title"
    printf '%s' "$2" > "$_dir/options"
    printf '%s' "${3:-返回}" > "$_dir/cancel"
    printf '%s' "$_mode" > "$_dir/mode"
    : > "$_dir/READY"
    while [[ ! -f $_dir/answer ]]; do
        [[ ! -f $HYPERBACKUP_JOB/stop ]] || return 130
        kill -0 "$HYPERBACKUP_OWNER_PID" 2>/dev/null || return 75
        sleep 0.2
    done
    IFS= read -r _answer < "$_dir/answer" || return 65
    [[ ${#_answer} -le 8192 && $_answer != *$'\n'* && $_answer != *$'\r'* ]] || return 65
    while IFS= read -r _line || [[ -n $_line ]]; do _count=$((_count + 1)); done < "$_dir/options"
    case $_mode in
        input) ;;
        multi) case $_answer in ''|*[!0-9\ ]*) return 65 ;; esac
            for _line in $_answer; do [[ $_line -le $_count ]] || return 65; done ;;
        *) case $_answer in ''|*[!0-9]*) return 65 ;; esac; [[ $_answer -le $_count ]] || return 65 ;;
    esac
    _SPEEDBACKUP_WEB_CHOICE=$_answer
    : > "$_dir/CONSUMED"
    return 0
}

_hb_snapshot() {
    local _name
    for _name in .backup_run.plan .backup_run.events .restore_install_issues .restore_install_unknown; do
        [[ ! -f $TMPDIR/$_name ]] || cp "$TMPDIR/$_name" "$HYPERBACKUP_JOB/evidence/$_name"
    done
    printf '%s\n' "${Backup:-}" > "$HYPERBACKUP_JOB/backup-path"
    printf '%s\n' "${TMPDIR:-}" > "$HYPERBACKUP_JOB/runtime-path"
    printf '%s\n' "${SPEED_DEBUG_ARCHIVE:-}" > "$HYPERBACKUP_JOB/diagnostics-path"
    _hb_event finished "${name2:-}" "${_SPEEDBACKUP_OPERATION_KIND:-$HYPERBACKUP_COMMAND}" "$1" explicit_exit
}

_hb_dispatch() {
    case "$1" in
        backup_apps) _selection_prepare 'backup; exit' && backup ;;
        backup_updated) _selection_prepare 'backup_update_apk; exit $?' && backup_update_apk ;;
        backup_folders) backup_media ;;
        backup_wifi) wifi ;;
        restore_apps) _selection_prepare 'Restore; exit' && Restore ;;
        restore_ssaid_apps) _selection_prepare 'ssaid_mode=true && Restore; exit' && { ssaid_mode=true; Restore; } ;;
        restore_app_state) _selection_prepare 'ssaid_mode_1=true && Restore4' && { ssaid_mode_1=true; Restore4; } ;;
        restore_folders) _selection_prepare 'Restore3; exit' && Restore3 ;;
        restore_wifi) recover_wifi "$MODDIR/wifi" ;;
        remote_restore_staged) _selection_prepare 'remote_download_restore; exit $?' && remote_download_restore ;;
        remote_restore_stream) _selection_prepare 'remote_stream_restore; exit $?' && remote_stream_restore ;;
        upload_existing) upload_current_backup ;;
        remote_catalog) remote_list_backups ;;
        edit_remote_backups) Edit_remote_backups ;;
        remote_cleanup) delete_remote_uninstalled_apps ;;
        remote_health) remote_test_menu --run-choice 1 ;;
        smb_discover) remote_test_menu --run-choice 2 ;;
        smb_discover_apply) remote_test_menu --run-choice 3 ;;
        webdav_discover) remote_test_menu --run-choice 4 ;;
        webdav_discover_advanced) remote_test_menu --run-choice 5 ;;
        remote_speed) remote_test_menu --run-choice 6 ;;
        statistics) Stats_Dispatch ;;
        verify_archives) check_file ;;
        verify_metadata) Check_json ;;
        rebuild_metadata) Json_refresh_menu ;;
        convert_names) convert ;;
        inventory) Getlist ;;
        *) _hb_event rejected "" command 65 unknown_command; return 65 ;;
    esac
}
