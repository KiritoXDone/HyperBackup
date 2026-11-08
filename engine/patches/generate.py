#!/usr/bin/env python3
"""Apply anchored host hooks to one audited tools.sh; never rewrite business functions.

The pristine submodule remains untouched. A changed anchor or upstream hash fails the build.
"""
import hashlib
from pathlib import Path
import re
import sys
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'engine/upstream/backup_script/tools/tools.sh'

def replace_once(text, old, new):
    assert text.count(old) == 1, f'Upstream anchor drift: {old[:90]}'
    return text.replace(old, new, 1)

def generate(source):
    source = replace_once(source, 'if [[ -z ${SPEEDBACKUP_CONSOLE_DIR:-} && -n ${_sb_root:-}',
        'if [[ ${HYPERBACKUP_BRIDGE:-0} != 1 && -z ${SPEEDBACKUP_CONSOLE_DIR:-} && -n ${_sb_root:-}')
    source = replace_once(source, '# Keep the one-entry UI request private;',
        'if [[ ${HYPERBACKUP_BRIDGE:-0} = 1 ]]; then\n    . "$HYPERBACKUP_BRIDGE_LIBRARY" || exit 65\nfi\n# Keep the one-entry UI request private;')
    source = replace_once(source, '# A run-owned pointer, never an inherited/exported cleanup path.',
        '[[ ${HYPERBACKUP_BRIDGE:-0} != 1 ]] || _SPEEDBACKUP_WEBMENU_REQUEST=0\n# A run-owned pointer, never an inherited/exported cleanup path.')
    source = replace_once(source, 'tools_path="$MODDIR/tools"',
        'tools_path="${HYPERBACKUP_ENGINE_TOOLS:-$MODDIR/tools}"')
    source = replace_once(source, '        bin.mt.plus|bin.mt.plus.canary) return 0 ;;',
        '        io.github.kiritoxdone.hyperbackup|com.topjohnwu.magisk|me.weishu.kernelsu|com.rifsxd.ksunext|com.sukisu.ultra|me.bmax.apatch) return 0 ;;\n        bin.mt.plus|bin.mt.plus.canary) return 0 ;;')
    # Do not let a bundled engine self-update, import an arbitrary Download zip, or rewrite its language.
    start = source.index('\nupdate_script\n# 掃 Download')
    end = source.index('\nupdate_script\n# 給定路徑', start) + len('\nupdate_script')
    original = source[start:end]
    source = source[:start] + '\nif [[ ${HYPERBACKUP_BRIDGE:-0} != 1 ]]; then' + original + '\nelse\n    update=false\n    _download_txn_recover_startup || exit 1\nfi' + source[end:]
    # Only replace the list binding, never the restore loop or its policy.
    restore_start = source.index('\nRestore() {')
    before, restore = source[:restore_start], source[restore_start:]
    restore = replace_once(restore, '\t\ttxt="$MODDIR/appList.txt"', '\t\ttxt="${HYPERBACKUP_APPLIST:-$MODDIR/appList.txt}"')
    source = before + restore
    source = source.replace('selection-web --ssaid-filter "$MODDIR/appList.txt"', 'selection-web --ssaid-filter "${HYPERBACKUP_APPLIST:-$MODDIR/appList.txt}"')
    source = source.replace('selection-web --ssaid-rows "$MODDIR/appList.txt"', 'selection-web --ssaid-rows "${HYPERBACKUP_APPLIST:-$MODDIR/appList.txt}"')
    preparation_start = source.index('\n_selection_prepare() {')
    preparation_end = source.index('\nEdit_remote_restore_list()', preparation_start)
    preparation = source[preparation_start:preparation_end].replace('"$MODDIR/appList.txt"', '"${HYPERBACKUP_APPLIST:-$MODDIR/appList.txt}"')
    source = source[:preparation_start] + preparation + source[preparation_end:]
    source = replace_once(source, '_speedbackup_web_select() {\n',
        '_speedbackup_web_select() {\n    if [[ ${HYPERBACKUP_BRIDGE:-0} = 1 ]]; then _hb_prompt "$@"; return $?; fi\n')
    source = replace_once(source, '_speedbackup_progress_step() {\n',
        '_speedbackup_progress_step() {\n    [[ ${HYPERBACKUP_BRIDGE:-0} != 1 ]] || _hb_event progress "${name2:-}" "${4:-progress}" "${1:-0}" "${2:-0}"\n')
    source = replace_once(source, '\t[[ -n $_echo_forced ]] && _echo_last="$_echo_forced"\n',
        '\t[[ -n $_echo_forced ]] && _echo_last="$_echo_forced"\n    [[ ${HYPERBACKUP_BRIDGE:-0} != 1 ]] || _hb_event result "${name2:-}" "${var:-${Action:-unknown}}" "$_echo_last" "$1"\n')
    source = replace_once(source, '_backup_entry_event() {\n',
        '_backup_entry_event() {\n    [[ ${HYPERBACKUP_BRIDGE:-0} != 1 ]] || _hb_event archive "${name2:-}" "${_SBB_KEY:-}" "${4:--}" "$1"\n')
    source = replace_once(source, '_speed_debug_normal_finish_pack() {\n',
        '_speed_debug_normal_finish_pack() {\n    [[ ${HYPERBACKUP_BRIDGE:-0} != 1 ]] || _hb_snapshot "${1:-0}"\n')
    source = replace_once(source, '\t_power_complete_offer "${1:-1}"\n}',
        '''\t_power_complete_offer "${1:-1}"
    [[ ${HYPERBACKUP_BRIDGE:-0} != 1 ]] || printf '%s\\n' "${_SPEEDBACKUP_NORMAL_FINISH_EXIT_FAST:-0}" > "$HYPERBACKUP_JOB/cleanup"
}''')
    for function in ['_selection_prepare_page_start', '_selection_prepare_page_end']:
        source = replace_once(source, function + '() {\n', function + '() {\n    [[ ${HYPERBACKUP_BRIDGE:-0} != 1 ]] || return 0\n')
    for function in ['installapk', '_restore_app_guard_start', '_backup_entry_begin']:
        source = replace_once(source, function + '() {\n', function + '() {\n    [[ ${HYPERBACKUP_BRIDGE:-0} != 1 ]] || _hb_checkpoint "' + function + '"\n')
    source = replace_once(source, '\tresult=75\n\treturn 1\n}\n\n_restore_install_retry_allowed()',
        '\tresult=75\n    [[ ${HYPERBACKUP_BRIDGE:-0} != 1 ]] || { _hb_event unknown "$1" install 75 commit_unknown; exit 75; }\n\treturn 1\n}\n\n_restore_install_retry_allowed()')
    source = replace_once(source, '# 446 r2: 入口分流優先使用 wrapper 明確傳入的 SPEEDBACKUP_ENTRY_MODE。',
        'if [[ ${HYPERBACKUP_BRIDGE:-0} = 1 ]]; then\n    _hb_dispatch "$HYPERBACKUP_COMMAND"\n    _hb_rc=$?\n    _hb_snapshot "$_hb_rc"\n    _speed_debug_normal_finish_pack "$_hb_rc"\n    exit "$_hb_rc"\nfi\n# 446 r2: 入口分流優先使用 wrapper 明確傳入的 SPEEDBACKUP_ENTRY_MODE。')
    return source

def main(output):
    output = Path(output); output.mkdir(parents=True, exist_ok=True)
    pristine = SOURCE.read_text()
    generated = generate(pristine)
    native = ROOT / 'engine/generated/speednative'
    if native.is_file():
        original_hash = hashlib.sha256((SOURCE.parent / 'speednative').read_bytes()).hexdigest()
        native_hash = hashlib.sha256(native.read_bytes()).hexdigest()
        generated = replace_once(generated, 'speednative ' + original_hash, 'speednative ' + native_hash)
    (output / 'tools.sh').write_text(generated)
    keys = re.search(r'const KNOWN:&str="([^"]+)"', (SOURCE.parents[1] / 'rust/src/runtime_conf.rs').read_text()).group(1).split('|')
    (output / 'config-keys.txt').write_text('\n'.join(keys) + '\n')
    defaults = {}
    for kind, function in [('backup', 'update_backup_settings_conf'), ('restore', 'update_Restore_settings_conf')]:
        start = pristine.index('\n' + function + '() {') + 1
        end = pristine.index('\n}\n', start) + 2
        helpers = '_conf_print_raw() { :; }; _conf_write() { printf "%s\\0%s\\0" "$1" "$2"; };\n'
        result = subprocess.run(['/bin/bash', '-c', helpers + pristine[start:end] + '\n' + function], env={'PATH': '/usr/bin:/bin'}, check=True, capture_output=True)
        tokens = result.stdout.decode().split('\0')[:-1]
        assert len(tokens) % 2 == 0
        defaults[kind] = dict(zip(tokens[::2], tokens[1::2]))
    (output / 'config-defaults.json').write_text(json.dumps(defaults, ensure_ascii=False, indent=2) + '\n')
    (output / 'source.sha256').write_text(hashlib.sha256(SOURCE.read_bytes()).hexdigest() + '\n')

if __name__ == '__main__':
    main(sys.argv[1])
