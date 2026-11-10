// The dashboard's numbers, computed per docs/log-format.md, "Computing the desktop metrics".
#include "log.h"

#include <QMap>
#include <QVariantList>
#include <algorithm>

namespace {

const QStringList kMainLifts{"squat", "bench", "deadlift", "ohp"};

double epley(int weight, int reps) { return weight * (1 + reps / 30.0); }

QDate monday(const QDate &d) { return d.addDays(1 - d.dayOfWeek()); }

double msecs(const QDate &d) { return double(QDateTime(d, QTime(12, 0)).toMSecsSinceEpoch()); }

double median(QVector<int> v) {
    std::sort(v.begin(), v.end());
    const qsizetype n = v.size();
    return n % 2 ? v[n / 2] : (v[n / 2 - 1] + v[n / 2]) / 2.0;
}

// Body weight in effect on a day: the latest day value on or before it, or 0 before the first weigh-in.
double bodyWeightOn(const Log &log, const QDate &day) {
    double weight = 0;
    for (const DayWeight &w : log.bodyWeight)
        if (w.day <= day)
            weight = w.weight;
    return weight;
}

QVariantMap strength(const Log &log) {
    QHash<QString, QDateTime> sessionStart;
    for (const Session &s : log.sessions)
        sessionStart.insert(s.id, s.start);

    // Best estimate per (lift, session): GZCLP counts its T1 AMRAP sets, cycle programs any set with reps.
    QMap<QString, QMap<QDateTime, double>> best;
    bool gzclp = false, cycle = false;
    for (const LoggedSet &s : log.sets) {
        const bool amrap = s.tier == "t1" && s.amrap, cycleSet = s.tier == "cycle" && s.reps > 0;
        if (!kMainLifts.contains(s.exercise) || !(amrap || cycleSet) || s.reps <= 0)
            continue;
        gzclp |= amrap;
        cycle |= cycleSet;
        double &value = best[s.exercise][sessionStart.value(s.session)];
        value = std::max(value, epley(s.weight, s.reps));
    }

    QVariantList lifts;
    for (const QString &lift : kMainLifts) {
        if (!best.contains(lift))
            continue;
        QVariantList points;
        for (auto it = best[lift].cbegin(); it != best[lift].cend(); ++it)
            points.append(QVariantMap{{"t", double(it.key().toMSecsSinceEpoch())}, {"v", it.value()}});
        const auto last = best[lift].lastKey();
        const double latest = best[lift].last(), weight = bodyWeightOn(log, last.date());
        lifts.append(QVariantMap{{"id", lift},
                                 {"name", log.name(lift)},
                                 {"points", points},
                                 {"latest", latest},
                                 {"first", best[lift].first()},
                                 {"firstT", double(best[lift].firstKey().toMSecsSinceEpoch())},
                                 {"ratio", weight > 0 ? latest / weight : 0.0}});
    }
    return {{"lifts", lifts}, {"gzclp", gzclp}, {"cycle", cycle}};
}

}  // namespace

QVariantMap dashboard(const Log &log) {
    QVector<Session> workouts;
    for (const Session &s : log.sessions)
        if (s.sets > 0)
            workouts.append(s);

    // One run of ISO weeks, Monday first, from the first workout to the last, shared by every weekly chart.
    QDate first, last;
    auto extend = [&](const QDate &d) {
        if (!first.isValid() || d < first) first = d;
        if (!last.isValid() || d > last) last = d;
    };
    for (const Session &s : workouts) extend(s.start.date());
    for (const LoggedSet &s : log.sets) extend(s.ts.date());
    QVariantList weeks;
    QHash<QDate, int> weekIndex;
    if (first.isValid())
        for (QDate m = monday(first); m <= last; m = m.addDays(7)) {
            weekIndex.insert(m, int(weeks.size()));
            weeks.append(QVariantMap{{"t", msecs(m)}, {"label", m.toString("MMM d")}});
        }

    struct Bucket { int reps = 0; qint64 tonnage = 0; };
    QMap<std::tuple<int, QString, QString>, Bucket> buckets;
    for (const LoggedSet &s : log.sets) {
        Bucket &b = buckets[{weekIndex.value(monday(s.ts.date())), s.exercise, s.tier}];
        b.reps += s.reps;
        b.tonnage += qint64(s.weight) * s.reps;
    }
    QVariantList volume;
    for (auto it = buckets.cbegin(); it != buckets.cend(); ++it) {
        const QString exercise = std::get<1>(it.key());
        volume.append(QVariantMap{{"week", std::get<0>(it.key())},
                                  {"lift", kMainLifts.contains(exercise) ? exercise : QStringLiteral("other")},
                                  {"tier", std::get<2>(it.key())},
                                  {"reps", it->reps},
                                  {"tonnage", double(it->tonnage)}});
    }

    QVariantList sessions, perWeek;
    QVector<int> counts(weeks.size(), 0);
    for (const Session &s : workouts) {
        sessions.append(QVariantMap{{"t", double(s.start.toMSecsSinceEpoch())},
                                    {"v", s.durationSec / 60.0},
                                    {"program", s.program}});
        counts[weekIndex.value(monday(s.start.date()))]++;
    }
    for (int c : counts)
        perWeek.append(c);

    // Stalls: GZCLP failures (stage changes and resets) and cycles that had to repeat.
    QMap<QString, QVariantList> marks;
    for (const QString &lift : kMainLifts)
        marks[lift];
    double start = weeks.isEmpty() ? 0 : weeks.first().toMap()["t"].toDouble(), end = start;
    for (const Session &s : workouts) end = std::max(end, double(s.start.toMSecsSinceEpoch()));
    for (const ProgressionEvent &e : log.events) {
        if (e.kind != "stage_change" && e.kind != "reset" && e.kind != "cycle_repeat")
            continue;
        const double t = double(e.ts.toMSecsSinceEpoch());
        start = start == 0 ? t : std::min(start, t);
        end = std::max(end, t);
        marks[e.exercise].append(QVariantMap{{"t", t},
                                             {"kind", e.kind},
                                             {"tier", e.tier},
                                             {"fromStage", e.fromStage},
                                             {"toStage", e.toStage},
                                             {"fromWeight", e.fromWeight},
                                             {"toWeight", e.toWeight}});
    }
    QStringList order = kMainLifts;
    QStringList others = marks.keys();
    others.removeIf([](const QString &id) { return kMainLifts.contains(id); });
    std::sort(others.begin(), others.end(), [&](const QString &a, const QString &b) { return log.name(a) < log.name(b); });
    order += others;
    QVariantList rows;
    for (const QString &id : order)
        rows.append(QVariantMap{{"id", id}, {"name", log.name(id)}, {"events", marks[id]}});

    // Rest is grouped by the previous set's tier, the tier its timer target came from.
    QHash<QString, QString> previousTier;
    QMap<QString, QPair<QVector<int>, QVector<int>>> rest;
    for (const LoggedSet &s : log.sets) {
        if (s.rest >= 0 && s.restTarget > 0 && previousTier.contains(s.session)) {
            rest[previousTier[s.session]].first.append(s.rest);
            rest[previousTier[s.session]].second.append(s.restTarget);
        }
        previousTier[s.session] = s.tier;
    }
    QVariantList restRows;
    for (const QString &tier : {QStringLiteral("t1"), QStringLiteral("t2"), QStringLiteral("t3"), QStringLiteral("cycle")})
        if (rest.contains(tier))
            restRows.append(QVariantMap{{"tier", tier},
                                        {"n", int(rest[tier].first.size())},
                                        {"actual", median(rest[tier].first)},
                                        {"target", median(rest[tier].second)}});

    QVariantList body;
    for (const DayWeight &w : log.bodyWeight) {
        double sum = 0;
        int n = 0;
        for (const DayWeight &o : log.bodyWeight)
            if (o.day <= w.day && o.day > w.day.addDays(-7)) {
                sum += o.weight;
                ++n;
            }
        body.append(QVariantMap{{"t", msecs(w.day)}, {"v", w.weight}, {"avg", sum / n}});
    }

    return {{"workouts", int(workouts.size())},
            {"weeks", weeks},
            {"strength", strength(log)},
            {"volume", volume},
            {"sessions", sessions},
            {"perWeek", perWeek},
            {"timeline", QVariantMap{{"rows", rows}, {"start", start}, {"end", end}}},
            {"rest", restRows},
            {"bodyWeight", body}};
}
