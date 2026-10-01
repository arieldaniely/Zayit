cmake_minimum_required(VERSION 3.24)
project(zayit_ble_peripheral LANGUAGES CXX)

find_package(JNI REQUIRED)

add_library(zayit-ble-peripheral SHARED src/BlePeripheral.cpp)
target_compile_features(zayit-ble-peripheral PRIVATE cxx_std_20)
target_compile_definitions(zayit-ble-peripheral PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX)
target_include_directories(zayit-ble-peripheral PRIVATE ${JNI_INCLUDE_DIRS})
target_link_libraries(zayit-ble-peripheral PRIVATE windowsapp)

set_target_properties(
    zayit-ble-peripheral
    PROPERTIES
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>"
)

install(
    TARGETS zayit-ble-peripheral
    RUNTIME DESTINATION "native/${ZAYIT_NATIVE_PLATFORM}"
)
