#include "backend.h"

#include <QDir>
#include <QFile>
#include <QFileInfo>

#include "log.h"

Backend::Backend(const QString &path, QObject *parent) : QObject(parent), m_path(path) {
    // Syncthing writes a temp file and renames it over the log, so a burst of
    // events settles into one reload.
    m_debounce.setSingleShot(true);
    m_debounce.setInterval(250);
    connect(&m_watcher, &QFileSystemWatcher::directoryChanged, this, [this] { m_debounce.start(); });
    connect(&m_watcher, &QFileSystemWatcher::fileChanged, this, [this] { m_debounce.start(); });
    connect(&m_debounce, &QTimer::timeout, this, &Backend::reload);
    reload();
}

QString Backend::displayPath() const {
    const QString home = QDir::homePath();
    return m_path.startsWith(home + "/") ? "~" + m_path.mid(home.size()) : m_path;
}

void Backend::reload() {
    // A rename drops the file from the watch, so re-arm on the file and the
    // nearest existing folder (the Syncthing folder may not exist yet).
    const auto watched = m_watcher.files() + m_watcher.directories();
    if (!watched.isEmpty())
        m_watcher.removePaths(watched);
    QString folder = QFileInfo(m_path).absolutePath();
    while (!QFileInfo::exists(folder) && folder != "/")
        folder = QFileInfo(folder).absolutePath();
    m_watcher.addPath(folder);

    QFile file(m_path);
    if (!file.open(QIODevice::ReadOnly)) {
        m_status = "missing";
        m_data.clear();
        m_syncedAt = {};
    } else {
        m_watcher.addPath(m_path);
        const Log log = parseLog(file.readAll());
        m_status = log.sessions.isEmpty() && log.bodyWeight.isEmpty() && log.events.isEmpty() ? "empty" : "ready";
        m_data = dashboard(log);
        m_syncedAt = QFileInfo(file).lastModified();
    }
    emit changed();
}
