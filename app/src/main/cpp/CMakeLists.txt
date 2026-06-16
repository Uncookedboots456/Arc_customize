cmake_minimum_required(VERSION 3.22.1)

project(arcdarkhook)

find_package(xhook REQUIRED CONFIG)

add_library(arcdarkhook SHARED arcdarkhook.cpp)

target_compile_features(arcdarkhook PRIVATE cxx_std_17)
target_compile_options(arcdarkhook PRIVATE -Wall -Wextra -Werror)
target_link_libraries(arcdarkhook PRIVATE xhook::xhook log)
