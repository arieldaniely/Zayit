# macOS BLE peripheral adapter

The Objective-C++ JNI adapter uses CoreBluetooth so a Mac can advertise the shared-study GATT
service while its Blue Falcon engine scans as a central. Build and stage the library on the target
Mac architecture with:

```bash
cmake -S .. -B ../build \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_OSX_ARCHITECTURES="$(uname -m)" \
  -DZAYIT_NATIVE_PLATFORM="macos-$( [ "$(uname -m)" = arm64 ] && echo arm64 || echo x64 )"
cmake --build ../build --config Release --parallel
cmake --install ../build --config Release --prefix ../../src/jvmMain/resources
```

GitHub Actions performs this build for Apple Silicon and Intel and packages the resulting
`libzayit-ble-peripheral.dylib` inside the application. The macOS package also contains the
Bluetooth usage description required for the system permission prompt.
