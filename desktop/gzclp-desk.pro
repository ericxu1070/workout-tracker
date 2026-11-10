QT += core gui qml quick quickcontrols2

CONFIG += c++17 release
TARGET = gzclp-desk
TEMPLATE = app

HEADERS += src/backend.h src/log.h src/theme.h
SOURCES += src/main.cpp src/backend.cpp src/log.cpp src/metrics.cpp src/theme.cpp
RESOURCES += src/resources.qrc
