cmake_minimum_required(VERSION 3.24)
project(zayit_ble_peripheral_macos LANGUAGES OBJCXX)

find_package(JNI REQUIRED)

add_library(zayit-ble-peripheral SHARED src/BlePeripheral.mm)
target_compile_features(zayit-ble-peripheral PRIVATE cxx_std_20)
target_compile_options(zayit-ble-peripheral PRIVATE -fobjc-arc)
target_include_directories(zayit-ble-peripheral PRIVATE ${JNI_INCLUDE_DIRS})
target_link_libraries(
    zayit-ble-peripheral
    PRIVATE
        "-framework Foundation"
        "-framework CoreBluetooth"
)

set_target_properties(
    zayit-ble-peripheral
    PROPERTIES
        OUTPUT_NAME "zayit-ble-peripheral"
        MACOSX_RPATH ON
)

install(
    TARGETS zayit-ble-peripheral
    LIBRARY DESTINATION "native/${ZAYIT_NATIVE_PLATFORM}"
)
