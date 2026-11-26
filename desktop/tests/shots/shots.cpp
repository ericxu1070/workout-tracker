// Renders the dashboard offscreen to a PNG, for review and PR screenshots from the fixture, never the real log.
// Usage: gzclp-desk-shots LOG THEME_DIR OUT.png WIDTH HEIGHT [prop=value | hover=x,y | click=x,y | press=x,y | drag=x,y | release=x,y | key=K | scroll=y]...
// THEME_DIR holds theme/colors.toml, as ~/.local/state/omarchy/current does. Steps run in order, 300 ms apart.
#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQuickItem>
#include <QQuickStyle>
#include <QQuickWindow>
#include <QTemporaryDir>
#include <QTimer>
#include <QtTest>

#include "backend.h"
#include "theme.h"

int main(int argc, char *argv[]) {
    qputenv("QT_QPA_PLATFORM", "offscreen");
    qputenv("QT_QUICK_BACKEND", "rhi");
    qputenv("QSG_RHI_BACKEND", "opengl");
    // The window's saved tab and glass choice start fresh each render and never touch the user's own.
    QTemporaryDir config;
    qputenv("XDG_CONFIG_HOME", config.path().toUtf8());
    QGuiApplication app(argc, argv);
    app.setOrganizationName("Repsilon");
    app.setApplicationName("gzclp-desk");
    QQuickStyle::setStyle("Material");
    const QStringList args = app.arguments();
    if (args.size() < 6)
        qFatal("usage: gzclp-desk-shots LOG THEME_DIR OUT.png WIDTH HEIGHT [steps...]");

    Theme theme(args[2]);
    Backend backend(QFileInfo(args[1]).absoluteFilePath());
    QQmlApplicationEngine engine;
    engine.rootContext()->setContextProperty("theme", &theme);
    engine.rootContext()->setContextProperty("backend", &backend);
    engine.load(QUrl("qrc:/Main.qml"));
    if (engine.rootObjects().isEmpty())
        return 1;
    auto *win = qobject_cast<QQuickWindow *>(engine.rootObjects().first());
    win->resize(args[4].toInt(), args[5].toInt());

    QStringList steps = args.mid(6);
    std::function<void()> next = [&] {
        QTest::qWait(300);
        if (steps.isEmpty()) {
            win->grabWindow().save(args[3]);
            app.quit();
            return;
        }
        const QString step = steps.takeFirst();
        const QString key = step.section('=', 0, 0), value = step.section('=', 1);
        const QPoint at(value.section(',', 0, 0).toInt(), value.section(',', 1, 1).toInt());
        if (key == "hover")
            QTest::mouseMove(win, at);
        else if (key == "click")
            QTest::mouseClick(win, Qt::LeftButton, {}, at);
        else if (key == "press")  // held down until a release step, or through the grab
            QTest::mousePress(win, Qt::LeftButton, {}, at);
        else if (key == "drag")  // a move with the button held
            QTest::mouseMove(win, at);
        else if (key == "release")
            QTest::mouseRelease(win, Qt::LeftButton, {}, at);
        else if (key == "key")
            QTest::keyClick(win, QKeySequence(value)[0].key());
        else if (key == "scroll")
            win->findChild<QQuickItem *>("page")->setProperty("contentY", value.toDouble());
        else
            win->setProperty(key.toUtf8(), value);
        QTimer::singleShot(0, next);
    };
    QTimer::singleShot(800, next);
    return app.exec();
}
