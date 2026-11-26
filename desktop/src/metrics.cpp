// The dashboard's numbers, computed per docs/log-format.md, "Computing the desktop metrics".
#include "log.h"

#include <QMap>
#include <QVariantList>
#include <algorithm>

namespace {

const QStringList kMainLifts{"squat", "bench", "deadlift", "ohp"};

double epley(double weight, int reps) { return weight * (1.0 + reps / 30.0); }

double bodyweightFactorFor(const QString &exercise, const QHash<QString, ExerciseMeta> &meta) {
    if (meta.contains(exercise) && meta[exercise].bodyweightFactor >= 0)
        return meta[exercise].bodyweightFactor;
    if (meta.contains(exercise) && !meta[exercise].parent.isEmpty()) {
        const QString parent = meta[exercise].parent;
        if (meta.contains(parent) && meta[parent].bodyweightFactor >= 0)
            return meta[parent].bodyweightFactor;
    }
    static const QHash<QString, double> kDefaults{
        {"pull_up", 1.0}, {"chin_up", 1.0}, {"muscle_up", 1.0},
        {"dip", 0.95}, {"push_up", 0.65}, {"inverted_row", 0.5}, {"pistol_squat", 0.8}
    };
    QString clean = exercise.toLower().trimmed();
    clean.replace('-', '_');
    if (kDefaults.contains(clean))
        return kDefaults.value(clean);
    if (clean.startsWith("weighted_"))
        return kDefaults.value(clean.mid(9), -1.0);
    if (clean.startsWith("assisted_"))
        return kDefaults.value(clean.mid(9), -1.0);
    return -1.0;
}

// The phone's built-in stretch routines (stretch/StretchRoutine.kt); a custom one is named by its stretch_routine_define.
QString routineName(const Log &log, const QString &id) {
    static const QHash<QString, QString> kBuiltIn{
        {"after_workout", "After workout"}, {"flexibility", "Flexibility"}, {"dance", "Dance mobility"},
        {"athletics", "Athletics static"}, {"athletics_dynamic", "Athletics dynamic"}, {"desk-reset", "Desk reset"},
        {"runners-cooldown", "Runner's cooldown"}, {"morning-flow", "Morning flow"}, {"bedtime-wind-down", "Bedtime wind-down"},
        {"low-back-relief", "Low back relief"}, {"hip-flexor-reset", "Hip flexor reset"}, {"front-splits", "Front splits"},
        {"overhead-mobility", "Overhead mobility"}, {"deep-squat-prep", "Deep squat prep"}, {"calves-and-ankles", "Calves and ankles"},
        {"cyclists-cooldown", "Cyclist's cooldown"}, {"throwers-shoulder", "Thrower's shoulder"}, {"golf-warm-up", "Golf warm-up"},
        {"starting-to-stretch", "Starting to Stretch"}, {"sun-salutation-a", "Sun salutation A"}, {"simple-6", "Simple 6"},
        {"limber-11", "Limber 11"}};
    return log.routineNames.value(id, kBuiltIn.value(id, log.name(id)));
}

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

    // Best estimate per (lift, session): GZCLP counts its T1 AMRAP sets, cycle programs and custom splits any set with reps,
    // and calisthenics exercises count any set with reps using effective load.
    QMap<QString, QMap<QDateTime, double>> best;
    bool gzclp = false, cycle = false, custom = false;
    for (const LoggedSet &s : log.sets) {
        if (s.reps <= 0)
            continue;
        const double factor = bodyweightFactorFor(s.exercise, log.meta);
        const bool isCalisthenics = factor >= 0.0;
        const bool amrap = s.tier == "t1" && s.amrap, cycleSet = s.tier == "cycle", customSet = s.tier == "custom";

        if (!isCalisthenics) {
            if (!kMainLifts.contains(s.exercise) || !(amrap || cycleSet || customSet))
                continue;
            gzclp |= amrap;
            cycle |= cycleSet;
            custom |= customSet;
            double &value = best[s.exercise][sessionStart.value(s.session)];
            value = std::max(value, epley(s.weight, s.reps));
        } else {
            const double bw = s.hasBodyweightLb ? s.bodyweightLb : bodyWeightOn(log, sessionStart.value(s.session).date());
            const double added = s.hasAddedLb ? s.addedLb : s.weight;
            const double load = bw > 0 ? (factor * bw + added) : added;
            double &value = best[s.exercise][sessionStart.value(s.session)];
            value = std::max(value, epley(load, s.reps));
        }
    }

    QStringList liftOrder = kMainLifts;
    QStringList calisthenicsLifts;
    for (auto it = best.cbegin(); it != best.cend(); ++it) {
        if (!liftOrder.contains(it.key()))
            calisthenicsLifts.append(it.key());
    }
    std::sort(calisthenicsLifts.begin(), calisthenicsLifts.end());
    liftOrder += calisthenicsLifts;

    QVariantList lifts;
    for (const QString &lift : liftOrder) {
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
    return {{"lifts", lifts}, {"gzclp", gzclp}, {"cycle", cycle}, {"custom", custom}};
}

}  // namespace

QVariantMap dashboard(const Log &log) {
    QVector<Session> workouts;
    for (const Session &s : log.sessions)
        if (s.sets > 0)
            workouts.append(s);

    // One run of ISO weeks, Monday first, from the first workout or cardio session to the last, shared by every weekly chart.
    QDate first, last;
    auto extend = [&](const QDate &d) {
        if (!first.isValid() || d < first) first = d;
        if (!last.isValid() || d > last) last = d;
    };
    for (const Session &s : workouts) extend(s.start.date());
    for (const LoggedSet &s : log.sets) extend(s.ts.date());
    for (const Cardio &c : log.cardio) extend(c.start.date());
    for (const WaterEntry &w : log.water) extend(w.at.date());
    for (const StretchEntry &st : log.stretches) extend(st.at.date());
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
    for (const QString &tier : {QStringLiteral("t1"), QStringLiteral("t2"), QStringLiteral("t3"), QStringLiteral("cycle"), QStringLiteral("custom")})
        if (rest.contains(tier))
            restRows.append(QVariantMap{{"tier", tier},
                                        {"n", int(rest[tier].first.size())},
                                        {"actual", median(rest[tier].first)},
                                        {"target", median(rest[tier].second)}});

    // Cardio minutes per week, and per-activity totals over the last 8 weeks.
    QVariantList cardioMinutes;
    QVector<double> minutes(weeks.size(), 0);
    struct Totals { QString name; int sessions = 0; double minutes = 0; int distanceM = 0; };
    QMap<QString, Totals> totals;
    for (const Cardio &c : log.cardio) {
        const int week = weekIndex.value(monday(c.start.date()));
        minutes[week] += c.durationSec / 60.0;
        if (week < weeks.size() - 8)
            continue;
        Totals &t = totals[c.activity];
        t.name = c.name;  // the newest name, as cardio is by start
        t.sessions++;
        t.minutes += c.durationSec / 60.0;
        t.distanceM += c.distanceM;
    }
    for (double m : minutes)
        cardioMinutes.append(m);
    QVariantList activities;
    for (auto it = totals.cbegin(); it != totals.cend(); ++it)
        activities.append(QVariantMap{{"id", it.key()},
                                      {"name", it->name},
                                      {"sessions", it->sessions},
                                      {"minutes", it->minutes},
                                      {"distanceM", it->distanceM}});
    std::stable_sort(activities.begin(), activities.end(), [](const QVariant &a, const QVariant &b) {
        return a.toMap()["minutes"].toDouble() > b.toMap()["minutes"].toDouble();
    });

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

    // Water per day
    QMap<QDate, int> dailyWater;
    for (const WaterEntry &w : log.water)
        dailyWater[w.at.date()] += w.ml;
    QVariantList water;
    for (auto it = dailyWater.cbegin(); it != dailyWater.cend(); ++it) {
        double sum = 0;
        int n = 0;
        for (auto o = dailyWater.cbegin(); o != dailyWater.cend(); ++o) {
            if (o.key() <= it.key() && o.key() > it.key().addDays(-7)) {
                sum += o.value();
                ++n;
            }
        }
        water.append(QVariantMap{{"t", msecs(it.key())}, {"v", double(it.value())}, {"avg", sum / n}});
    }

    // Stretching sessions per week, and per-routine totals over the last 8 weeks.
    QVariantList stretchPerWeek;
    QVector<int> stretchCounts(weeks.size(), 0);
    QMap<QString, Totals> routineTotals;
    for (const StretchEntry &st : log.stretches) {
        const int week = weekIndex.value(monday(st.at.date()));
        stretchCounts[week]++;
        if (week < weeks.size() - 8)
            continue;
        Totals &t = routineTotals[st.routine];
        t.name = routineName(log, st.routine);
        t.sessions++;
        t.minutes += st.durationSec / 60.0;
    }
    for (int c : stretchCounts)
        stretchPerWeek.append(c);
    QVariantList routines;
    for (auto it = routineTotals.cbegin(); it != routineTotals.cend(); ++it)
        routines.append(QVariantMap{{"id", it.key()}, {"name", it->name}, {"sessions", it->sessions}, {"minutes", it->minutes}});
    std::stable_sort(routines.begin(), routines.end(), [](const QVariant &a, const QVariant &b) {
        return a.toMap()["sessions"].toInt() > b.toMap()["sessions"].toInt();
    });

    return {{"workouts", int(workouts.size())},
            {"weeks", weeks},
            {"strength", strength(log)},
            {"volume", volume},
            {"sessions", sessions},
            {"perWeek", perWeek},
            {"timeline", QVariantMap{{"rows", rows}, {"start", start}, {"end", end}}},
            {"rest", restRows},
            {"bodyWeight", body},
            {"cardio", QVariantMap{{"minutes", cardioMinutes}, {"activities", activities}}},
            {"water", water},
            {"stretches", QVariantMap{{"perWeek", stretchPerWeek}, {"total", int(log.stretches.size())}, {"routines", routines}}}};
}
