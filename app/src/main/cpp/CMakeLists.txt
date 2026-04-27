cmake_minimum_required(VERSION 3.22.1)
project("synthpiano")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Oboe arrives via the Maven artifact com.google.oboe:oboe (Prefab).
find_package(oboe REQUIRED CONFIG)

add_library(synthpiano SHARED
    oscillator.cpp
    envelope.cpp
    voice.cpp
    synth_engine.cpp
    jni_bridge.cpp
)

target_include_directories(synthpiano PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_compile_options(synthpiano PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -ffast-math
    -fno-rtti
    -fno-exceptions
)

target_link_libraries(synthpiano
    oboe::oboe
    android
    log
)
