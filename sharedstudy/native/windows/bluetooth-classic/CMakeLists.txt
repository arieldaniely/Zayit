cmake_minimum_required(VERSION 3.24)
project(zayit_bluetooth_classic LANGUAGES CXX)

find_package(JNI REQUIRED)

add_library(zayit-bluetooth SHARED src/BluetoothClassic.cpp)
target_compile_features(zayit-bluetooth PRIVATE cxx_std_20)
target_compile_definitions(zayit-bluetooth PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX)
target_include_directories(zayit-bluetooth PRIVATE ${JNI_INCLUDE_DIRS})
target_link_libraries(zayit-bluetooth PRIVATE windowsapp)

set_target_properties(
    zayit-bluetooth
    PROPERTIES
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>"
)

install(
    TARGETS zayit-bluetooth
    RUNTIME DESTINATION "native/${ZAYIT_NATIVE_PLATFORM}"
)
