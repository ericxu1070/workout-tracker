QT += core gui testlib
CONFIG += c++17 testcase
TARGET = gzclp-desk_tests
TEMPLATE = app

INCLUDEPATH += ../src
HEADERS += ../src/backend.h ../src/log.h ../src/theme.h
SOURCES += gzclp-desk_tests.cpp ../src/backend.cpp ../src/log.cpp ../src/metrics.cpp ../src/theme.cpp
