cmake_minimum_required(VERSION 3.22.1)

project("accelerometer")

add_library(${CMAKE_PROJECT_NAME} SHARED
        cadence.cpp
        gmw.cpp
        fcwt/src/fcwt/fcwt.cpp
)

set(FFTW_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/fftw)

add_library(fftw3 STATIC IMPORTED)
set_target_properties(fftw3 PROPERTIES
        IMPORTED_LOCATION
        ${FFTW_ROOT}/lib/${ANDROID_ABI}/libfftw3.a
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${FFTW_ROOT}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/fcwt/src/fcwt
)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE SINGLE_THREAD)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        m
        fftw3
)
