#!/system/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
# Arguments are host-produced, validated paths. No user-supplied Shell is evaluated.
set -u
[[ $# = 5 ]] || exit 65
HYPERBACKUP_ENGINE_TOOLS=$1
HYPERBACKUP_JOB=$2
MODDIR=$3
conf_path=$4
HYPERBACKUP_COMMAND=$5
IFS= read -r _hb_stat < /proc/self/stat || exit 65
printf '%s\n' "${_hb_stat%% *}" > "$HYPERBACKUP_JOB/process-pid"
_hb_fields=${_hb_stat##*) }
set -- $_hb_fields
shift 19
printf '%s\n' "$1" > "$HYPERBACKUP_JOB/process-start"
cat /proc/sys/kernel/random/boot_id > "$HYPERBACKUP_JOB/process-boot"
for _path in "$HYPERBACKUP_ENGINE_TOOLS" "$HYPERBACKUP_JOB" "$MODDIR" "${conf_path%/*}"; do
    [[ $_path = /* && -d $_path && ! -L $_path ]] || exit 65
done
[[ -f $conf_path && ! -L $conf_path ]] || exit 65
HYPERBACKUP_BRIDGE=1
HYPERBACKUP_BRIDGE_LIBRARY="$HYPERBACKUP_ENGINE_TOOLS/bridge.sh"
export HYPERBACKUP_BRIDGE HYPERBACKUP_ENGINE_TOOLS HYPERBACKUP_JOB HYPERBACKUP_COMMAND HYPERBACKUP_BRIDGE_LIBRARY
SPEEDBACKUP_ENTRY_MODE=6
SPEEDBACKUP_ENTRY_SCRIPT="$MODDIR/start.sh"
SPEEDBACKUP_ENTRY_QUIET_TRAP=1
export SPEEDBACKUP_ENTRY_MODE SPEEDBACKUP_ENTRY_SCRIPT SPEEDBACKUP_ENTRY_QUIET_TRAP
unset SPEEDBACKUP_CONSOLE_DIR SPEEDBACKUP_WEB_UI_ONLY SPEEDBACKUP_WEBUI_ONLY
if [[ -n ${HYPERBACKUP_APPLIST:-} ]]; then
    _SELECTION_CONFIRMED=1
    _SELECTION_CONFIRMED_KIND=restore
fi
set +u
. "$HYPERBACKUP_ENGINE_TOOLS/tools.sh"
exit $?
