QT += core gui qml quick quickcontrols2 testlib
CONFIG += c++17
TARGET = gzclp-desk-shots
TEMPLATE = app

INCLUDEPATH += ../../src
HEADERS += ../../src/backend.h ../../src/log.h ../../src/theme.h ../../src/palette.h
SOURCES += shots.cpp ../../src/backend.cpp ../../src/log.cpp ../../src/metrics.cpp ../../src/theme.cpp ../../src/palette.cpp
RESOURCES += ../../src/resources.qrc
