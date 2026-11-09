# Official ARM64 ATD 4 KB preparation

Status: helper unit checks run in the existing Check workflow; the ARM64 ATD/KernelSU app-functional job is not yet integrated. The pinned image is Google's Android 36 `aosp_atd` ARM64 revision 1. The official kernel is 4 KB `android15-6.6`; KernelSU v3.3.1 provides the corresponding AArch64 module.

`prepare_official_atd_ksu.py` verifies the pinned kernel, original ramdisk, source metadata, host tool, and KernelSU module. In CI it additionally requires the complete Google ZIP, verifies the publisher's indexed SHA-1 and size, and compares every installed image file with the archive. It produces a separate patched ramdisk; it never replaces the SDK source image. `--research-components-only` is local-only and does not establish full archive provenance.

```sh
python3 prepare_official_atd_ksu.py \
  --image-dir "$ANDROID_HOME/system-images/android-36/aosp_atd/arm64-v8a" \
  --archive "$RUNNER_TEMP/arm64-v8a-36_r01.zip" \
  --ksud "$RUNNER_TEMP/ksud" \
  --module "$RUNNER_TEMP/kernelsu.ko" \
  --output "$RUNNER_TEMP/atd-ksu-ramdisk.img" \
  > "$RUNNER_TEMP/atd-prepare.json"
```

`verify_running_atd.py` is intended for an isolated, explicitly identified emulator after its private SDK copy has been patched, booted, and the pinned manager installed. It checks the AVD config and source, ABI, runtime page size, kernel release, SELinux, KernelSU version, shell Root, and manager APK hash. It refuses non-emulator serials. Shell Root is not app Root; `check_root_fixture.py` independently rejects a skipped or failed app backup/restore fixture.

`offline-verification.json` records the actual local component-level patch and CPIO inspection. It explicitly records that the full archive, boot, and app Root have not been verified. The automated unit tests use disposable local fixtures and do not start an emulator or perform a backup.
