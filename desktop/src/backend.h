#pragma once

#include <QDateTime>
#include <QFileSystemWatcher>
#include <QObject>
#include <QTimer>
#include <QVariantMap>

// Reads the synced log and redraws whenever Syncthing replaces it. Read-only:
// the phone owns the file.
class Backend : public QObject {
    Q_OBJECT
    Q_PROPERTY(QString path READ displayPath CONSTANT)
    Q_PROPERTY(QString status READ status NOTIFY changed)  // "missing", "empty", or "ready"
    Q_PROPERTY(QVariantMap data READ data NOTIFY changed)
    Q_PROPERTY(QDateTime syncedAt READ syncedAt NOTIFY changed)

public:
    explicit Backend(const QString &path, QObject *parent = nullptr);

    QString displayPath() const;
    QString status() const { return m_status; }
    QVariantMap data() const { return m_data; }
    QDateTime syncedAt() const { return m_syncedAt; }

signals:
    void changed();

private:
    void reload();

    QString m_path;
    QString m_status = "missing";
    QVariantMap m_data;
    QDateTime m_syncedAt;
    QFileSystemWatcher m_watcher;
    QTimer m_debounce;
};
