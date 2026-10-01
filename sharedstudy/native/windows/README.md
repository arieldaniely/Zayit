# Windows Bluetooth native adapters

The Windows adapters are C++20/C++/WinRT JNI libraries. Build and stage both of them from a
Visual Studio Developer PowerShell with a Windows 10/11 SDK and CMake available:

```powershell
cmake -S .. -B ../build -A x64 -DZAYIT_NATIVE_PLATFORM=windows-x64
cmake --build ../build --config Release --parallel
cmake --install ../build --config Release --prefix ../../src/jvmMain/resources
```

The install command copies `zayit-ble-peripheral.dll` and `zayit-bluetooth.dll` to the correct
resource directory. GitHub Actions runs the same build automatically before packaging test and
release distributions. `BundledNativeLibrary` extracts each packaged library to a private
temporary directory and loads it.

The Java process must be packaged with the Windows Bluetooth capability available to desktop
applications. Runtime capability detection remains authoritative: machines without peripheral-role
support continue as BLE centrals and use LAN, paired RFCOMM, or the guided hotspot fallback.
