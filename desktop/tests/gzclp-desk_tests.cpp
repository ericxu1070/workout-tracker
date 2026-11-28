#include <QFile>
#include <QRegularExpression>
#include <QTemporaryDir>
#include <QtTest>

#include "backend.h"
#include "log.h"
#include "theme.h"
#include "palette.h"

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
        QCOMPARE(log.sets.size(), 6);  // the A1 workout's three, a top-sets line and a quick log's two
        QCOMPARE(log.sets[0].rpe, 0.0);  // absent
        QCOMPARE(log.sets[1].rpe, 8.5);
        QCOMPARE(log.name("squat"), QString("Squat"));
        QCOMPARE(log.name("leg_curl"), QString("Leg curl"));
        QCOMPARE(log.bodyWeight.size(), 1);  // the Nov 20 weigh-in was cleared
        QCOMPARE(log.bodyWeight[0].day, QDate(2026, 11, 21));
        QCOMPARE(log.bodyWeight[0].weight, 181.4);  // the edit wins

        const QVariantMap d = dashboard(log);
        QCOMPARE(d["workouts"].toInt(), 2);  // A1 and the top-sets day; the quick log is not a workout
        // No AMRAP or cycle sets; the quick log's handstand presses are a bodyweight move, so they count.
        const QVariantList lifts = d["strength"].toMap()["lifts"].toList();
        QCOMPARE(lifts.size(), 1);
        QCOMPARE(lifts[0].toMap()["id"].toString(), QString("handstand_press"));
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
        QCOMPARE(d["sessions"].toList().value(0).toMap()["v"].toDouble(), 3120 / 60.0);  // the session_edit wins

        // The quick log's sets count, but it is no workout: no duration bar.
        int quickSets = 0;
        for (const LoggedSet &s : log.sets)
            quickSets += s.session == "s1796484600";
        QCOMPARE(quickSets, 2);
        const qint64 quickStart = QDateTime::fromString("2026-12-05T07:25:00-08:00", Qt::ISODate).toMSecsSinceEpoch();
        for (const QVariant &v : d["sessions"].toList())
            QVERIFY(qint64(v.toMap()["t"].toDouble()) != quickStart);

        // Cardio: the run, the basketball edit (4500 -> 3900 s), and no discarded ride.
        QCOMPARE(log.cardio.size(), 2);
        QCOMPARE(log.cardio[0].activity, QString("basketball"));
        QCOMPARE(log.cardio[0].durationSec, 3900);
        QCOMPARE(log.cardio[1].distanceM, 8047);
        const QVariantMap cardio = d["cardio"].toMap();
        QCOMPARE(d["weeks"].toList().size(), 5);  // the December top-sets day and quick log extend it to the week of Nov 30
        QCOMPARE(cardio["minutes"].toList(), (QVariantList{0.0, 0.0, (2490 + 3900) / 60.0, 0.0, 0.0}));
        QCOMPARE(cardio["activities"].toList().value(0).toMap()["name"].toString(), QString("Basketball"));

        const QVariantList rows = d["timeline"].toMap()["rows"].toList();
        QCOMPARE(find(rows, "id", "squat")["events"].toList().size(), 0);  // cycle_up is not a stall
        QCOMPARE(find(rows, "id", "leg_curl")["events"].toList().value(0).toMap()["kind"].toString(),
                 QString("cycle_repeat"));
        // The Dec 3 workout was deleted from History: no workout, and the stall it caused is taken back.
        QVERIFY(find(rows, "id", "ohp")["events"].toList().isEmpty());

        // Cleared water and one cleared stretch; the custom routine's stretch counts after its routine is deleted.
        QCOMPARE(log.water.size(), 0);
        QCOMPARE(log.stretches.size(), 1);
        QCOMPARE(log.stretches[0].routine, QString("evening"));
        QVERIFY(d["water"].toList().isEmpty());
        QCOMPARE(d["stretches"].toMap()["total"].toInt(), 1);
        // The custom routine keeps the name its define gave it after it is removed.
        const QVariantMap evening = d["stretches"].toMap()["routines"].toList().value(0).toMap();
        QCOMPARE(evening["name"].toString(), QString("Evening"));
        QCOMPARE(evening["minutes"].toDouble(), 115 / 60.0);
    }

    void everyDocumentedTypeIsReadOrSkipped() {
        const QString doc = QString::fromUtf8(readFile("GZCLP_LOG_DOC"));
        auto it = QRegularExpression("^### `([a-z_]+)`$", QRegularExpression::MultilineOption).globalMatch(doc);
        int types = 0;
        while (it.hasNext()) {
            const QString type = it.next().captured(1);
            QVERIFY2(kReadTypes.contains(type) != kSkippedTypes.contains(type), qPrintable(type));
            ++types;
        }
        QCOMPARE(types, int(kReadTypes.size() + kSkippedTypes.size()));
    }

    void stretchRoutinesAreNamed() {
        const QByteArray lines =
            "{\"v\":2,\"seq\":1,\"ts\":\"2026-11-16T08:00:00-08:00\",\"type\":\"stretch\",\"id\":\"st1\",\"at\":\"2026-11-16T08:00:00-08:00\",\"routine\":\"limber-11\",\"duration_sec\":300,\"moves\":[]}\n"
            "{\"v\":2,\"seq\":2,\"ts\":\"2026-11-17T08:00:00-08:00\",\"type\":\"stretch\",\"id\":\"st2\",\"at\":\"2026-11-17T08:00:00-08:00\",\"routine\":\"limber-11\",\"duration_sec\":30,\"moves\":[]}\n"
            "{\"v\":2,\"seq\":3,\"ts\":\"2026-11-17T09:00:00-08:00\",\"type\":\"stretch_routine_define\",\"routine\":\"evening\",\"name\":\"Evening\",\"moves\":[]}\n"
            "{\"v\":2,\"seq\":4,\"ts\":\"2026-11-17T09:01:00-08:00\",\"type\":\"stretch_routine_define\",\"routine\":\"evening\",\"name\":\"Night\",\"moves\":[]}\n"
            "{\"v\":2,\"seq\":5,\"ts\":\"2026-11-18T20:00:00-08:00\",\"type\":\"stretch\",\"id\":\"st3\",\"at\":\"2026-11-18T20:00:00-08:00\",\"routine\":\"evening\",\"duration_sec\":600,\"moves\":[]}\n"
            "{\"v\":2,\"seq\":6,\"ts\":\"2026-11-19T08:00:00-08:00\",\"type\":\"stretch_routine_remove\",\"routine\":\"evening\"}\n";
        const QVariantList routines = dashboard(parseLog(lines))["stretches"].toMap()["routines"].toList();
        QCOMPARE(routines.size(), 2);
        QCOMPARE(routines[0].toMap()["name"].toString(), QString("Limber 11"));  // most sessions first
        QCOMPARE(routines[0].toMap()["sessions"].toInt(), 2);
        QCOMPARE(routines[1].toMap()["name"].toString(), QString("Night"));  // the newest define wins
    }

    void onlyWaterIsNotAnEmptyLog() {
        QTemporaryDir dir;
        QFile file(dir.filePath("log.jsonl"));
        QVERIFY(file.open(QIODevice::WriteOnly));
        file.write("{\"v\":2,\"seq\":1,\"ts\":\"2026-11-20T08:00:00-08:00\",\"type\":\"water\",\"id\":\"wt1\",\"at\":\"2026-11-20T08:00:00-08:00\",\"ml\":500}\n");
        file.close();
        QCOMPARE(Backend(file.fileName()).status(), QString("ready"));
    }

    void partialLastLineIsSkipped() {
        const QByteArray line =
            R"({"v":2,"seq":1,"ts":"2026-11-21T07:02:00-08:00","type":"body_weight","entry_id":"w1","at":"2026-11-21T07:02:00-08:00","weight":180.0})";
        QCOMPARE(parseLog(line).bodyWeight.size(), 0);
        QCOMPARE(parseLog(line + "\n").bodyWeight.size(), 1);
        QCOMPARE(parseLog(QByteArray(line).replace("\"v\":2", "\"v\":1") + "\n").bodyWeight.size(), 0);
    }

    void openCardioIsNotCounted() {
        const QByteArray start =
            R"({"v":2,"seq":1,"ts":"2026-11-21T07:00:00-08:00","type":"cardio_start","entry_id":"c1","activity":"running","name":"Running"})";
        QVERIFY(parseLog(start + "\n").cardio.isEmpty());
    }

    void fixtureMetrics() {
        const Log log = parseLog(readFile("GZCLP_FIXTURE"));
        const QVariantMap d = dashboard(log);
        // 18 GZCLP, 6 cycle, and 3 custom workouts; the accidental start and the open session do not count.
        QCOMPARE(d["workouts"].toInt(), 27);
        QCOMPARE(d["perWeek"].toList().size(), 8);
        QCOMPARE(d["perWeek"].toList().first().toInt(), 3);
        QCOMPARE(log.sessions.size(), 28);

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

        // Weekly cardio: the Tuesday run (30 + k min) plus basketball, the last game edited to 75 min.
        const QVariantMap cardio = d["cardio"].toMap();
        QCOMPARE(cardio["minutes"].toList(), (QVariantList{30.0, 31.0, 32.0, 33.0, 34.0, 95.0, 96.0, 112.0}));
        const QVariantList activities = cardio["activities"].toList();
        QCOMPARE(activities.size(), 2);  // the discarded ride is dropped
        const QVariantMap running = activities.value(0).toMap();
        QCOMPARE(running["name"].toString(), QString("Running"));
        QCOMPARE(running["sessions"].toInt(), 8);
        QCOMPARE(running["minutes"].toDouble(), 268.0);
        QCOMPARE(running["distanceM"].toInt(), 8 * 4828);
        QCOMPARE(activities.value(1).toMap()["minutes"].toDouble(), 195.0);

        const QVariantList rest = d["rest"].toList();
        QCOMPARE(rest.size(), 5);
        QCOMPARE(find(rest, "tier", "t1")["target"].toDouble(), 180.0);
        QVERIFY(find(rest, "tier", "t1")["actual"].toDouble() > 180.0);

        // The custom split: bench at 5x8,6,5, deleted after one repeated cycle. Its sets count like cycle sets.
        QVERIFY(strength["custom"].toBool());
        const QVariantMap bench = find(strength["lifts"].toList(), "id", "bench");
        QCOMPARE(bench["latest"].toDouble(), 165 * (1 + 5 / 30.0));  // week 3: best set of 5x5 at 165, last one short
        qint64 customReps = 0;
        for (const QVariant &v : d["volume"].toList())
            if (v.toMap()["tier"] == "custom")
                customReps += v.toMap()["reps"].toInt();
        QCOMPARE(customReps, 5 * 8 + 5 * 6 + 4 * 5 + 4);
        QCOMPARE(find(rest, "tier", "custom")["target"].toDouble(), 120.0);
        const QVariantMap repeat = find(rows, "id", "bench")["events"].toList().last().toMap();
        QCOMPARE(repeat["kind"].toString(), QString("cycle_repeat"));
        QCOMPARE(repeat["tier"].toString(), QString("custom"));
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
        QTRY_COMPARE(backend.data()["workouts"].toInt(), 27);
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

    // The phone's Omarchy themes (Palettes.omarchy in Theme.kt): every text token reads on every surface, flat and in glass.
    void paletteTextReadsInEveryTheme_data() {
        QTest::addColumn<QVariantMap>("colors");
        const QList<QStringList> themes{
            {"catppuccin-latte", "light", "#eff1f5", "#dce0e8", "#4c4f69", "#9ca0b0", "#1e66f5", "#d20f39"},
            {"catppuccin", "dark", "#1e1e2e", "#313244", "#cdd6f4", "#6c7086", "#89b4fa", "#f38ba8"},
            {"city-783", "dark", "#181a1f", "#20232a", "#b9bec6", "#6a707a", "#ff5c5c", "#e53939"},
            {"deep-ocean", "dark", "#0a1a1f", "#12282f", "#c8e6e3", "#6a8f93", "#3ee6d6", "#ff6b6b"},
            {"desert-dusk", "dark", "#1f1a24", "#2c2532", "#e8d9c8", "#8a7a80", "#e8925a", "#e06c6c"},
            {"ethereal", "dark", "#060b1e", "#131a3a", "#ffcead", "#6d7db6", "#7d82d9", "#ed5b5a"},
            {"everforest", "dark", "#2d353b", "#343f44", "#d3c6aa", "#4f585e", "#7fbbb3", "#e67e80"},
            {"flexoki-light", "light", "#fffcf0", "#e6e4d9", "#100f0f", "#878580", "#205ea6", "#d14d41"},
            {"gruvbox", "dark", "#282828", "#3c3836", "#d4be98", "#7c6f64", "#7daea3", "#ea6962"},
            {"hackerman", "dark", "#0b0c16", "#151828", "#ddf7ff", "#6a6e95", "#82fb9c", "#50f872"},
            {"kanagawa", "dark", "#1f1f28", "#223249", "#dcd7ba", "#727169", "#dcd7ba", "#c34043"},
            {"last-horizon", "dark", "#0c0b0c", "", "#fafcfb", "#584e51", "#b59790", "#c38b7b"},
            {"lumon", "dark", "#16242d", "#1b2d40", "#d6e2ee", "#4d86b0", "#8bc9eb", "#4d86b0"},
            {"lupine", "light", "#fafafa", "#f5f5f5", "#212121", "#757575", "#3264eb", "#c900c4"},
            {"matte-black", "dark", "#121212", "#1e1e1e", "#bebebe", "#555555", "#e68e0d", "#d35f5f"},
            {"miasma", "dark", "#222222", "#2c2c2c", "#c2c2b0", "#555555", "#78824b", "#685742"},
            {"nord", "dark", "#2e3440", "#3b4252", "#d8dee9", "#667080", "#81a1c1", "#bf616a"},
            {"retro-82", "dark", "#05182e", "#0a2540", "#f6dcac", "#3f8f8a", "#faa968", "#f85525"},
            {"ristretto", "dark", "#2c2525", "#3d2f2a", "#e6d9db", "#72696a", "#f38d70", "#fd6883"},
            {"rose-pine", "light", "#faf4ed", "#f2e9e1", "#575279", "#9893a5", "#56949f", "#b4637a"},
            {"solitude", "dark", "#101315", "", "#cacccc", "#4b4e55", "#798186", "#565d60"},
            {"starship", "dark", "#1c1c1e", "#2c2c2e", "#f5f5f7", "#98989d", "#ff9f0a", "#ff5f4a"},
            {"vantablack", "dark", "#000000", "#1a1a1a", "#ffffff", "#505050", "#8d8d8d", "#a4a4a4"},
            {"white", "light", "#ffffff", "#c0c0c0", "#000000", "#c0c0c0", "#6e6e6e", "#2a2a2a"},
            {"tokyo-night", "", "#1a1b26", "#24283b", "#c0caf5", "#a9b1d6", "#7aa2f7", "#f7768e"}};
        for (const QStringList &t : themes) {
            QVariantMap colors{{"background", t[2]}, {"foreground", t[4]}, {"dark_foreground", t[5]}, {"accent", t[6]}, {"red", t[7]}};
            if (!t[1].isEmpty()) colors["mode"] = t[1];
            if (!t[3].isEmpty()) colors["lighter_background"] = t[3];
            QTest::newRow(qPrintable(t[0])) << colors;
        }
    }
    void paletteTextReadsInEveryTheme() {
        QFETCH(QVariantMap, colors);
        const Palette flat = Palette::parse(colors);
        QVERIFY(flat.bg.isValid() && !flat.glass());
        QVERIFY(flat.textReads());
        const Palette glass = flat.glassed();
        QVERIFY(glass.glass());
        QVERIFY(glass.textReads());
        QVERIFY(glass.bar().alphaF() >= 0.3f - 1e-3);
        QVERIFY(glass.chromeAlpha >= 0.82 - 1e-9);
        QCOMPARE(flat.dark, colors.value("mode") != "light");
    }

    void paletteMatchesThePhone() {
        // Tokyo Night as the phone's Laptop theme reads it: its own colors already read, so they pass through.
        const Palette p = Palette::parse({{"background", "#1a1b26"}, {"lighter_background", "#24283b"}, {"foreground", "#c0caf5"},
                                          {"dark_foreground", "#a9b1d6"}, {"accent", "#7aa2f7"}, {"red", "#f7768e"}});
        QCOMPARE(p.accent.name(), QString("#7aa2f7"));
        QCOMPARE(p.fg2.name(), QString("#a9b1d6"));
        QCOMPARE(p.onAccent.name(), QString("#000000"));
        QCOMPARE(p.separator().alpha(), 26);  // fg at 10%
        // A dim secondary is lifted toward fg until it reads; a missing lighter background is a step toward fg.
        const Palette e = Palette::parse({{"background", "#2d353b"}, {"foreground", "#d3c6aa"}, {"dark_foreground", "#4f585e"},
                                          {"accent", "#7fbbb3"}});
        QVERIFY(contrast(e.fg2, e.elevated) >= 4.5 && e.fg2 != QColor("#4f585e"));
        QCOMPARE(e.elevated.name(), QString("#3a4144"));
        QVERIFY(contrast(e.red, e.elevated) >= 4.5 && e.red.red() > e.red.green());  // no red key: the phone's danger red, lifted until it reads
        QCOMPARE(Palette::parse({{"background", "#000000"}}).bg.isValid(), false);
    }

    void themeFallsBack() {
        QTemporaryDir dir;
        Theme theme(dir.path());
        QCOMPARE(theme.accent(), QString("#FFD60A"));
        QVERIFY(theme.colors().isEmpty());
        QVERIFY(!theme.light());
    }

    void calisthenicsEffectiveLoadInStrengthChart() {
        const QByteArray lines =
            "{\"v\":2,\"seq\":1,\"ts\":\"2026-11-20T07:00:00-08:00\",\"type\":\"body_weight\",\"entry_id\":\"w1\",\"at\":\"2026-11-20T07:00:00-08:00\",\"weight\":180.0}\n"
            "{\"v\":2,\"seq\":2,\"ts\":\"2026-11-20T17:00:00-08:00\",\"type\":\"exercise_meta\",\"exercise\":\"pull_up\",\"bodyweight_factor\":1.0}\n"
            "{\"v\":2,\"seq\":3,\"ts\":\"2026-11-20T17:01:00-08:00\",\"type\":\"session_start\",\"session_id\":\"s1\",\"program\":\"gzclp\",\"day\":\"A1\"}\n"
            "{\"v\":2,\"seq\":4,\"ts\":\"2026-11-20T17:10:00-08:00\",\"type\":\"set\",\"session_id\":\"s1\",\"exercise\":\"pull_up\",\"tier\":\"t3\",\"stage\":\"3x12\",\"set_index\":1,\"target_reps\":12,\"actual_reps\":10,\"weight\":0,\"added_lb\":20.0,\"bodyweight_lb\":180.0}\n"
            "{\"v\":2,\"seq\":5,\"ts\":\"2026-11-20T17:20:00-08:00\",\"type\":\"set\",\"session_id\":\"s1\",\"exercise\":\"dip\",\"tier\":\"t3\",\"stage\":\"3x12\",\"set_index\":1,\"target_reps\":12,\"actual_reps\":10,\"weight\":0,\"added_lb\":0.0}\n"
            "{\"v\":2,\"seq\":6,\"ts\":\"2026-11-20T17:30:00-08:00\",\"type\":\"session_end\",\"session_id\":\"s1\",\"program\":\"gzclp\",\"day\":\"A1\",\"started_at\":\"2026-11-20T17:01:00-08:00\",\"duration_sec\":1800}\n";

        const Log log = parseLog(lines);
        QCOMPARE(log.sets.size(), 2);
        QVERIFY(log.meta.contains("pull_up"));
        QCOMPARE(log.meta["pull_up"].bodyweightFactor, 1.0);

        const QVariantMap d = dashboard(log);
        const QVariantList lifts = d["strength"].toMap()["lifts"].toList();
        const QVariantMap pullUp = find(lifts, "id", "pull_up");
        QVERIFY(!pullUp.isEmpty());
        // Load = 1.0 * 180 + 20 = 200. Epley: 200 * (1 + 10 / 30.0) = 266.666...
        const double expectedPullUp = 200.0 * (1.0 + 10.0 / 30.0);
        QCOMPARE(pullUp["latest"].toDouble(), expectedPullUp);
        QCOMPARE(pullUp["ratio"].toDouble(), expectedPullUp / 180.0);

        const QVariantMap dip = find(lifts, "id", "dip");
        QVERIFY(!dip.isEmpty());
        // Default factor for dip: 0.95. BW from bodyWeightOn = 180.0. Added = 0. Load = 0.95 * 180 = 171.0.
        // Epley: 171.0 * (1 + 10 / 30.0) = 228.0
        const double expectedDip = 171.0 * (1.0 + 10.0 / 30.0);
        QCOMPARE(dip["latest"].toDouble(), expectedDip);
        QCOMPARE(dip["ratio"].toDouble(), expectedDip / 180.0);
    }

    void remapMergesEarlierLinesIntoTheBuiltIn() {
        const QByteArray lines =
            "{\"v\":2,\"seq\":1,\"ts\":\"2026-11-20T07:00:00-08:00\",\"type\":\"body_weight\",\"entry_id\":\"w1\",\"at\":\"2026-11-20T07:00:00-08:00\",\"weight\":180.0}\n"
            "{\"v\":2,\"seq\":2,\"ts\":\"2026-11-20T17:00:00-08:00\",\"type\":\"exercise_define\",\"exercise\":\"pull_ups\",\"name\":\"Pull ups\",\"lower\":false}\n"
            "{\"v\":2,\"seq\":3,\"ts\":\"2026-11-20T17:01:00-08:00\",\"type\":\"set\",\"session_id\":\"s1\",\"exercise\":\"pull_ups\",\"tier\":\"t3\",\"stage\":\"3x15+\",\"set_index\":1,\"target_reps\":15,\"actual_reps\":10,\"target_weight\":0,\"weight\":180,\"amrap\":false}\n"
            "{\"v\":2,\"seq\":4,\"ts\":\"2026-11-20T17:30:00-08:00\",\"type\":\"session_end\",\"session_id\":\"s1\",\"program\":\"gzclp\",\"day\":\"A1\",\"started_at\":\"2026-11-20T17:00:00-08:00\",\"duration_sec\":1800}\n"
            "{\"v\":2,\"seq\":5,\"ts\":\"2026-11-24T09:00:00-08:00\",\"type\":\"exercise_remap\",\"from\":\"pull_ups\",\"to\":\"pull_up\",\"bodyweight\":true}\n"
            "{\"v\":2,\"seq\":6,\"ts\":\"2026-11-24T09:01:00-08:00\",\"type\":\"exercise_define\",\"exercise\":\"pull_ups\",\"name\":\"Pull ups\",\"lower\":false}\n";

        const Log log = parseLog(lines);
        QCOMPARE(log.sets.size(), 1);
        QCOMPARE(log.sets[0].exercise, QString("pull_up"));
        QCOMPARE(log.sets[0].weight, 0);
        QCOMPARE(log.sets[0].bodyweightLb, 180.0);
        QVERIFY(log.sets[0].hasAddedLb);
        QCOMPARE(log.name("pull_ups"), QString("Pull ups"));  // defined again after the remap

        // Load is the body weight once, not body weight plus the 180 typed as the weight.
        const QVariantMap pullUp = find(dashboard(log)["strength"].toMap()["lifts"].toList(), "id", "pull_up");
        QCOMPARE(pullUp["latest"].toDouble(), 180.0 * (1.0 + 10.0 / 30.0));
    }

    void waterPerDayAndCleared() {
        const QByteArray lines =
            "{\"v\":2,\"seq\":1,\"ts\":\"2026-11-20T08:00:00-08:00\",\"type\":\"water\",\"id\":\"wt1\",\"at\":\"2026-11-20T08:00:00-08:00\",\"ml\":500}\n"
            "{\"v\":2,\"seq\":2,\"ts\":\"2026-11-20T12:00:00-08:00\",\"type\":\"water\",\"id\":\"wt2\",\"at\":\"2026-11-20T12:00:00-08:00\",\"ml\":750}\n"
            "{\"v\":2,\"seq\":3,\"ts\":\"2026-11-20T14:00:00-08:00\",\"type\":\"water\",\"id\":\"wt3\",\"at\":\"2026-11-20T14:00:00-08:00\",\"ml\":250}\n"
            "{\"v\":2,\"seq\":4,\"ts\":\"2026-11-20T14:05:00-08:00\",\"type\":\"water_clear\",\"id\":\"wt3\"}\n"
            "{\"v\":2,\"seq\":5,\"ts\":\"2026-11-21T09:00:00-08:00\",\"type\":\"water\",\"id\":\"wt4\",\"at\":\"2026-11-21T09:00:00-08:00\",\"ml\":1000}\n";

        const Log log = parseLog(lines);
        QCOMPARE(log.water.size(), 3);  // wt3 was cleared

        const QVariantMap d = dashboard(log);
        const QVariantList water = d["water"].toList();
        QCOMPARE(water.size(), 2);
        // Day 1: 500 + 750 = 1250 ml
        QCOMPARE(water[0].toMap()["v"].toDouble(), 1250.0);
        // Day 2: 1000 ml
        QCOMPARE(water[1].toMap()["v"].toDouble(), 1000.0);
    }

    void stretchingSessionsPerWeekAndCleared() {
        const QByteArray lines =
            "{\"v\":2,\"seq\":1,\"ts\":\"2026-11-16T08:00:00-08:00\",\"type\":\"stretch\",\"id\":\"st1\",\"at\":\"2026-11-16T08:00:00-08:00\",\"routine\":\"morning\",\"duration_sec\":300,\"moves\":[\"neck\",\"chest\"]}\n"
            "{\"v\":2,\"seq\":2,\"ts\":\"2026-11-18T20:00:00-08:00\",\"type\":\"stretch\",\"id\":\"st2\",\"at\":\"2026-11-18T20:00:00-08:00\",\"routine\":\"evening\",\"duration_sec\":600,\"moves\":[\"hamstring\",\"quad\"]}\n"
            "{\"v\":2,\"seq\":3,\"ts\":\"2026-11-19T20:00:00-08:00\",\"type\":\"stretch\",\"id\":\"st3\",\"at\":\"2026-11-19T20:00:00-08:00\",\"routine\":\"discarded\",\"duration_sec\":600,\"moves\":[]}\n"
            "{\"v\":2,\"seq\":4,\"ts\":\"2026-11-19T20:05:00-08:00\",\"type\":\"stretch_clear\",\"id\":\"st3\"}\n";

        const Log log = parseLog(lines);
        QCOMPARE(log.stretches.size(), 2);  // st3 was cleared
        QCOMPARE(log.stretches[0].routine, QString("morning"));

        const QVariantMap d = dashboard(log);
        const QVariantMap stretches = d["stretches"].toMap();
        QCOMPARE(stretches["total"].toInt(), 2);
        const QVariantList perWeek = stretches["perWeek"].toList();
        QVERIFY(!perWeek.isEmpty());
        // Week of Nov 16 has 2 stretching sessions
        QCOMPARE(perWeek.last().toInt(), 2);
    }
};

QTEST_GUILESS_MAIN(Tests)
#include "gzclp-desk_tests.moc"
