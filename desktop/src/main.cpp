// gzclp-desk: Repsilon's desktop dashboard for the phone app's synced log.

#include <QDir>
#include <QFileInfo>
#include <QGuiApplication>
#include <QIcon>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQuickStyle>
#include <QUrl>

#include "backend.h"
#include "theme.h"

int main(int argc, char *argv[]) {
    QGuiApplication app(argc, argv);
    app.setOrganizationName("Repsilon");  // where the window keeps its tab and Liquid Glass choice
    app.setApplicationName("gzclp-desk");
    app.setApplicationVersion("0.2.0");
    // Matches the window to gzclp-desk.desktop for the compositor and the launcher.
    app.setDesktopFileName("gzclp-desk");
    app.setWindowIcon(QIcon::fromTheme("gzclp-desk"));
    QQuickStyle::setStyle("Material");

    const QStringList args = app.arguments();
    Theme theme;
    Backend backend(args.size() > 1 ? QFileInfo(args[1]).absoluteFilePath()
                                    : QDir::homePath() + "/GZCLP/gzclp-log.jsonl");

    QQmlApplicationEngine engine;
    engine.rootContext()->setContextProperty("theme", &theme);
    engine.rootContext()->setContextProperty("backend", &backend);
    engine.load(QUrl("qrc:/Main.qml"));
    if (engine.rootObjects().isEmpty())
        return 1;
    return app.exec();
}
