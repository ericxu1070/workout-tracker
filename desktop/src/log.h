#pragma once

#include <QByteArray>
#include <QDateTime>
#include <QHash>
#include <QString>
#include <QVariantMap>
#include <QVector>

// The phone's log (docs/log-format.md, v2) resolved to the facts the
// dashboard reads: corrections applied, cleared entries dropped, sets of
// unfinished sessions removed.
struct LoggedSet {
    QString session, exercise, tier;
    int index = 0, reps = 0, weight = 0;
    bool amrap = false;
    int rest = -1, restTarget = -1;  // -1 when absent (first set of a session)
    QDateTime ts;                    // when the set was first logged
    qint64 order = 0;                // seq of its first line, for the previous-set lookup
};

struct Session {
    QString id, program;
    QDateTime start;
    int durationSec = 0;
    int sets = 0;  // resolved sets; 0 means an accidental start, not a workout
};

struct ProgressionEvent {
    QString exercise, tier, kind, fromStage, toStage;
    int fromWeight = 0, toWeight = 0;
    QDateTime ts;
};

struct DayWeight {
    QDate day;
    double weight = 0;
};

struct Log {
    QHash<QString, QString> names;  // exercise id -> display name
    QVector<LoggedSet> sets;        // ordered by first log time
    QVector<Session> sessions;      // ended sessions, by start
    QVector<ProgressionEvent> events;
    QVector<DayWeight> bodyWeight;  // one per day, by day

    QString name(const QString &exercise) const;
};

Log parseLog(const QByteArray &bytes);

// The view model the QML dashboard draws, one key per module.
QVariantMap dashboard(const Log &log);
