#include <QFile>
#include <QRegularExpression>
#include <QTemporaryDir>
#include <QtTest>

#include "backend.h"
#include "log.h"
#include "theme.h"

namespace {

QByteArray readFile(const QByteArray &envName) {
    QFile file(qEnvironmentVariable(envName));
    if (!file.open(QIODevice::ReadOnly))
        qFatal("cannot read %s", envName.constData());
    return file.readAll();
}

// The jsonl example in docs/log-format.md, the contract both apps share.
QByteArray docExample() {
    const QString doc = QString::fromUtf8(readFile("GZCLP_LOG_DOC"));
    const auto m = QRegularExpression("```jsonl\n(.*?)```", QRegularExpression::DotMatchesEverythingOption).match(doc);
    return m.captured(1).toUtf8();
}

QVariantMap find(const QVariantList &list, const QString &key, const QVariant &value) {
    for (const QVariant &v : list)
        if (v.toMap().value(key) == value)
            return v.toMap();
    return {};
}

}  // namespace

class Tests : public QObject {
    Q_OBJECT

private slots:
    void docExampleResolves() {
        const Log log = parseLog(docExample());
        QCOMPARE(log.sets.size(), 3);
        QCOMPARE(log.sets[0].rpe, 0.0);  // absent
        QCOMPARE(log.sets[1].rpe, 8.5);
        QCOMPARE(log.name("squat"), QString("Squat"));
        QCOMPARE(log.name("leg_curl"), QString("Leg curl"));
        QCOMPARE(log.bodyWeight.size(), 1);  // the Nov 20 weigh-in was cleared
        QCOMPARE(log.bodyWeight[0].day, QDate(2026, 11, 21));
        QCOMPARE(log.bodyWeight[0].weight, 181.4);  // the edit wins

        const QVariantMap d = dashboard(log);
        QCOMPARE(d["workouts"].toInt(), 1);
        QVERIFY(d["strength"].toMap()["lifts"].toList().isEmpty());  // no AMRAP or cycle sets
        const QVariantMap volume = find(d["volume"].toList(), "lift", "squat");
        QCOMPARE(volume["reps"].toInt(), 6);
        QCOMPARE(volume["tonnage"].toDouble(), 210.0 * 3 + 215 * 3);  // each set's own weight
        // The exercise_swap line is skipped; the set counts for the substitute actually done, not lat_pulldown.
        QCOMPARE(log.sets[2].exercise, QString("seated_cable_row"));
        QCOMPARE(find(d["volume"].toList(), "lift", "other")["tonnage"].toDouble(), 100.0 * 15);
        QCOMPARE(d["weeks"].toList().value(0).toMap()["label"].toString(), QString("Nov 2"));
        const QVariantMap rest = d["rest"].toList().value(0).toMap();
        QCOMPARE(rest["tier"].toString(), QString("t1"));
        QCOMPARE(rest["actual"].toDouble(), 190.0);
        QCOMPARE(rest["target"].toDouble(), 180.0);
        QCOMPARE(d["sessions"].toList().value(0).toMap()["v"].toDouble(), 2975 / 60.0);

        const QVariantList rows = d["timeline"].toMap()["rows"].toList();
        QCOMPARE(find(rows, "id", "squat")["events"].toList().size(), 0);  // cycle_up is not a stall
        QCOMPARE(find(rows, "id", "leg_curl")["events"].toList().value(0).toMap()["kind"].toString(),
                 QString("cycle_repeat"));
    }

    void partialLastLineIsSkipped() {
        const QByteArray line =
            R"({"v":2,"seq":1,"ts":"2026-11-21T07:02:00-08:00","type":"body_weight","entry_id":"w1","at":"2026-11-21T07:02:00-08:00","weight":180.0})";
        QCOMPARE(parseLog(line).bodyWeight.size(), 0);
        QCOMPARE(parseLog(line + "\n").bodyWeight.size(), 1);
        QCOMPARE(parseLog(QByteArray(line).replace("\"v\":2", "\"v\":1") + "\n").bodyWeight.size(), 0);
    }

    void fixtureMetrics() {
        const Log log = parseLog(readFile("GZCLP_FIXTURE"));
        const QVariantMap d = dashboard(log);
        // 18 GZCLP and 6 cycle workouts; the accidental start and the open session do not count.
        QCOMPARE(d["workouts"].toInt(), 24);
        QCOMPARE(d["perWeek"].toList().size(), 8);
        QCOMPARE(d["perWeek"].toList().first().toInt(), 3);
        QCOMPARE(log.sessions.size(), 25);

        // 50 days, 7 skipped, one cleared; the morning weigh-in wins over the evening one.
        const QVariantList body = d["bodyWeight"].toList();
        QCOMPARE(body.size(), 42);
        QCOMPARE(body.first().toMap()["v"].toDouble(), 185.0);
        const double day10 = round((186.0 - 10 * 0.08 + ((10 * 37) % 11 - 5) * 0.15) * 10) / 10;
        QCOMPARE(find(body, "t", double(QDateTime(QDate(2026, 10, 8), QTime(12, 0)).toMSecsSinceEpoch()))["v"].toDouble(),
                 day10);

        const QVariantList rows = d["timeline"].toMap()["rows"].toList();
        const QVariantList ohp = find(rows, "id", "ohp")["events"].toList();
        QCOMPARE(ohp.size(), 3);
        QCOMPARE(ohp.last().toMap()["kind"].toString(), QString("reset"));
        QCOMPARE(find(rows, "id", "squat")["events"].toList().size(), 1);  // the T2 stall
        QCOMPARE(find(rows, "id", "deadlift")["events"].toList().size(), 0);
        QCOMPARE(find(rows, "id", "leg_curl")["events"].toList().size(), 1);

        // Squat has GZCLP AMRAP points and cycle points; its latest is the last cycle session.
        const QVariantMap strength = d["strength"].toMap();
        QVERIFY(strength["gzclp"].toBool() && strength["cycle"].toBool());
        const QVariantMap squat = find(strength["lifts"].toList(), "id", "squat");
        QCOMPARE(squat["points"].toList().size(), 5 + 3);
        QCOMPARE(squat["latest"].toDouble(), 180 * (1 + 8 / 30.0));  // week 3: 160 + 2 x 10 for 8 reps
        // The last weigh-in (Nov 16) is the body weight in effect on Nov 19.
        QCOMPARE(squat["ratio"].toDouble(), squat["latest"].toDouble() / body.last().toMap()["v"].toDouble());

        // The corrected first set of session 4 (reps 3 -> 2) and the cleared last set of session 5.
        qint64 reps = 0;
        for (const QVariant &v : d["volume"].toList())
            reps += v.toMap()["reps"].toInt();
        qint64 raw = 0;
        for (const LoggedSet &s : log.sets)
            raw += s.reps;
        QCOMPARE(reps, raw);
        QVERIFY(std::none_of(log.sets.cbegin(), log.sets.cend(),
                             [](const LoggedSet &s) { return s.ts.date() == QDate(2026, 11, 21); }));

        const QVariantList rest = d["rest"].toList();
        QCOMPARE(rest.size(), 4);
        QCOMPARE(find(rest, "tier", "t1")["target"].toDouble(), 180.0);
        QVERIFY(find(rest, "tier", "t1")["actual"].toDouble() > 180.0);
    }

    void backendFollowsRenames() {
        QTemporaryDir dir;
        const QString path = dir.filePath("sync/gzclp-log.jsonl");
        Backend backend(path);
        QCOMPARE(backend.status(), QString("missing"));
        QVERIFY(QDir(dir.path()).mkpath("sync"));  // the Syncthing folder appears later
        auto deliver = [&](const QByteArray &bytes) {
            // Syncthing writes a temp file beside the target and renames it over.
            QFile tmp(dir.filePath("sync/.syncthing.gzclp-log.jsonl.tmp"));
            QVERIFY(tmp.open(QIODevice::WriteOnly));
            tmp.write(bytes);
            tmp.close();
            QFile::remove(path);
            QVERIFY(QFile::rename(tmp.fileName(), path));
        };
        deliver("");
        QTRY_COMPARE(backend.status(), QString("empty"));
        deliver(docExample());
        QTRY_COMPARE(backend.data()["workouts"].toInt(), 1);
        deliver(readFile("GZCLP_FIXTURE"));
        QTRY_COMPARE(backend.data()["workouts"].toInt(), 24);
    }

    void themeReadsColorsAndMode() {
        QTemporaryDir dir;
        QDir(dir.path()).mkpath("theme");
        QFile colors(dir.path() + "/theme/colors.toml");
        QVERIFY(colors.open(QIODevice::WriteOnly));
        colors.write("mode = \"light\"\naccent = \"#112233\"\nbackground = \"#FFFCF0\"\n");
        colors.close();
        Theme theme(dir.path());
        QCOMPARE(theme.accent(), QString("#112233"));
        QCOMPARE(theme.colors()["background"].toString(), QString("#FFFCF0"));
        QVERIFY(theme.light());
        QCOMPARE(theme.accentForeground(), QString("white"));
    }

    void themeFallsBack() {
        QTemporaryDir dir;
        Theme theme(dir.path());
        QCOMPARE(theme.accent(), QString("#FFD60A"));
        QVERIFY(theme.colors().isEmpty());
        QVERIFY(!theme.light());
    }
};

QTEST_GUILESS_MAIN(Tests)
#include "gzclp-desk_tests.moc"
