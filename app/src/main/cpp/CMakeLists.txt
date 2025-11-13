cmake_minimum_required(VERSION 3.6)
project(native-lib)

add_library( native-lib SHARED native-lib.cpp )

find_library( log-lib log )

# NOTE: You must provide OpenCV Android SDK separately and set OpenCV_DIR or include paths.
# This CMakeLists links only to log by default.
target_link_libraries( native-lib ${log-lib} )
