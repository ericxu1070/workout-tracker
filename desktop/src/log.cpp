#include "log.h"

#include <QJsonDocument>
#include <QJsonObject>
#include <QSet>
#include <algorithm>

namespace {

QDateTime parseTime(const QJsonValue &value) {
    return QDateTime::fromString(value.toString(), Qt::ISODate);
}

QString setKey(const QJsonObject &o) {
    return o["session_id"].toString() + '\x1f' + o["exercise"].toString() + '\x1f' + o["tier"].toString() + '\x1f'
           + QString::number(o["set_index"].toInt());
}

}  // namespace

QString Log::name(const QString &exercise) const {
    if (names.contains(exercise))
        return names.value(exercise);
    QString pretty = exercise;
    pretty.replace('_', ' ');
    if (!pretty.isEmpty())
        pretty[0] = pretty[0].toUpper();
    return pretty;
}

Log parseLog(const QByteArray &bytes) {
    static const QSet<QString> known{"exercise_define", "session_start", "session_end", "set", "set_clear",
                                     "progression", "body_weight", "body_weight_clear"};

    QList<QByteArray> lines = bytes.split('\n');
    // The last piece is either empty (the file ends with \n) or an append in progress.
    lines.removeLast();

    QVector<QJsonObject> objects;
    for (const QByteArray &line : lines) {
        if (line.trimmed().isEmpty())
            continue;
        const QJsonObject o = QJsonDocument::fromJson(line).object();
        if (o["v"].toInt() != 2 || !known.contains(o["type"].toString()))
            continue;
        objects.append(o);
    }
    std::stable_sort(objects.begin(), objects.end(),
                     [](const QJsonObject &a, const QJsonObject &b) { return a["seq"].toInteger() < b["seq"].toInteger(); });

    Log log;
    QHash<QString, LoggedSet> sets;
    QHash<QString, QJsonObject> weighIns;
    QHash<QString, Session> ended;

    for (const QJsonObject &o : objects) {
        const QString type = o["type"].toString();
        if (type == "exercise_define") {
            log.names.insert(o["exercise"].toString(), o["name"].toString());
        } else if (type == "set") {
            const QString key = setKey(o);
            LoggedSet s;
            s.session = o["session_id"].toString();
            s.exercise = o["exercise"].toString();
            s.tier = o["tier"].toString();
            s.index = o["set_index"].toInt();
            s.reps = o["actual_reps"].toInt();
            s.weight = o["weight"].toInt();
            s.amrap = o["amrap"].toBool();
            s.rest = o["rest_sec"].toInt(-1);
            s.restTarget = o["rest_target_sec"].toInt(-1);
            s.rpe = o["rpe"].toDouble();
            // A correction replaces the values but the set keeps its place in the session.
            const auto previous = sets.constFind(key);
            s.ts = previous != sets.cend() ? previous->ts : parseTime(o["ts"]);
            s.order = previous != sets.cend() ? previous->order : o["seq"].toInteger();
            sets.insert(key, s);
        } else if (type == "set_clear") {
            sets.remove(setKey(o));
        } else if (type == "session_end") {
            Session s;
            s.id = o["session_id"].toString();
            s.program = o["program"].toString();
            s.start = parseTime(o["started_at"]);
            s.durationSec = o["duration_sec"].toInt();
            ended.insert(s.id, s);
        } else if (type == "progression") {
            ProgressionEvent e;
            e.exercise = o["exercise"].toString();
            e.tier = o["tier"].toString();
            e.kind = o["kind"].toString();
            e.fromStage = o["from_stage"].toString();
            e.toStage = o["to_stage"].toString();
            e.fromWeight = o["from_weight"].toInt();
            e.toWeight = o["to_weight"].toInt();
            e.ts = parseTime(o["ts"]);
            log.events.append(e);
        } else if (type == "body_weight") {
            weighIns.insert(o["entry_id"].toString(), o);
        } else if (type == "body_weight_clear") {
            weighIns.remove(o["entry_id"].toString());
        }
    }

    for (const LoggedSet &s : std::as_const(sets)) {
        if (!ended.contains(s.session))
            continue;
        log.sets.append(s);
        ended[s.session].sets++;
    }
    std::sort(log.sets.begin(), log.sets.end(), [](const LoggedSet &a, const LoggedSet &b) { return a.order < b.order; });

    log.sessions = ended.values().toVector();
    std::sort(log.sessions.begin(), log.sessions.end(),
              [](const Session &a, const Session &b) { return a.start < b.start; });

    // A day's body weight is its earliest weigh-in, on the clock it was written with.
    QHash<QDate, QDateTime> earliest;
    QHash<QDate, double> dayWeight;
    for (const QJsonObject &o : std::as_const(weighIns)) {
        const QDateTime at = parseTime(o["at"]);
        const QDate day = at.date();
        if (!earliest.contains(day) || at < earliest[day]) {
            earliest[day] = at;
            dayWeight[day] = o["weight"].toDouble();
        }
    }
    for (auto it = dayWeight.cbegin(); it != dayWeight.cend(); ++it)
        log.bodyWeight.append({it.key(), it.value()});
    std::sort(log.bodyWeight.begin(), log.bodyWeight.end(),
              [](const DayWeight &a, const DayWeight &b) { return a.day < b.day; });
    return log;
}
