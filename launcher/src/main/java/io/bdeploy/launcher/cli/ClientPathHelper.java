package io.bdeploy.launcher.cli;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.Version;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;

/**
 * Helper class providing access to common folders.
 */
public class ClientPathHelper {

    /**
     * Name of the directory containing the launcher
     */
    public static final String LAUNCHER_DIR = "launcher";

    /**
     * Name of the launcher.bat file
     */
    public static final String LAUNCHER_BAT = "launcher.bat";

    /**
     * Name of the launch files
     */
    public static final String LAUNCH_FILE_NAME = "launch.bdeploy";

    /**
     * Name of the launcher on Windows
     */
    public static final String WIN_LAUNCHER = "BDeploy.exe";

    /**
     * Name of the launcher on Linux
     */
    public static final String LINUX_LAUNCHER = "launcher";

    /**
     * Name of the file association utility on Windows
     */
    public static final String WIN_FILE_ASSOC = "FileAssoc.exe";

    /**
     * Name of the file association utility on Linux
     */
    public static final String LINUX_FILE_ASSOC = "file-assoc.sh";

    private ClientPathHelper() {
    }

    /**
     * Returns the are where the user is permitted to write files required for the application to run.
     */
    public static Path getUserAreaOrThrow() {
        // Check if a specific directory should be used
        String userAreaEnv = System.getenv("BDEPLOY_USER_AREA");
        Path userArea;
        if (userAreaEnv != null) {
            userArea = Path.of(userAreaEnv);
        } else if (OsHelper.getRunningOs() == OperatingSystem.WINDOWS) {
            // On Windows we default to the local application data folder
            userArea = Path.of(System.getenv("LOCALAPPDATA"), "BDeploy");
        } else {
            throw new IllegalStateException("No user area was found.");
        }

        if (PathHelper.isReadOnly(userArea)) {
            throw new IllegalStateException("The user area '" + userArea + "' cannot be modified.");
        }

        return userArea.normalize().toAbsolutePath();
    }

    /**
     * Returns the home directory for the given version. Each version will get its own directory where the launcher, the hive as
     * well as all apps are stored. Nothing is shared between different versions to prevent side-effects
     */
    public static Path getHome(Path root, Version version) {
        String name = "bdeploy-" + version.toString();
        return root.resolve(name);
    }

    /**
     * Returns a list of all hives in the given directory.
     */
    public static List<Path> getHives(Path rootDir) throws IOException {
        List<Path> hives = new ArrayList<>();
        hives.add(rootDir.resolve("bhive"));
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDir,
                p -> p.getFileName().toString().toLowerCase().startsWith("bdeploy-"))) {
            Iterator<Path> dirs = stream.iterator();
            while (dirs.hasNext()) {
                Path nestedRoot = dirs.next();
                Path nestedHive = nestedRoot.resolve("bhive");
                if (!nestedHive.toFile().isDirectory()) {
                    continue;
                }
                hives.add(nestedHive);
            }
        }
        return hives;
    }

    /**
     * Returns the native file association utility.
     */
    public static Path getNativeFileAssocTool(LauncherPathProvider lpp) {
        Path launcherDir = lpp.get(SpecialDirectory.LAUNCHER);
        Path result = OsHelper.getRunningOs() == OperatingSystem.WINDOWS//
                ? launcherDir.resolve(WIN_FILE_ASSOC)
                : launcherDir.resolve("bin").resolve(LINUX_FILE_ASSOC);
        return result.normalize().toAbsolutePath();
    }

    /**
     * Returns the native launcher used to start the application.
     */
    public static Path getNativeLauncher(LauncherPathProvider lpp) {
        Path launcherDir = lpp.get(SpecialDirectory.LAUNCHER);
        Path result = OsHelper.getRunningOs() == OperatingSystem.WINDOWS//
                ? launcherDir.resolve(WIN_LAUNCHER)
                : launcherDir.resolve("bin").resolve(LINUX_LAUNCHER);
        return result.normalize().toAbsolutePath();
    }

    /**
     * Returns the script launcher which can be used to launch with console being attached.
     */
    public static Path getScriptLauncher(LauncherPathProvider lpp) {
        Path launcherDir = lpp.get(SpecialDirectory.LAUNCHER);
        Path launcherBinDir = launcherDir.resolve("bin");
        Path result = OsHelper.getRunningOs() == OperatingSystem.WINDOWS//
                ? launcherBinDir.resolve(LAUNCHER_BAT)
                : launcherBinDir.resolve(LINUX_LAUNCHER);
        return result.normalize().toAbsolutePath();
    }

    /**
     * Returns the descriptor for the given application
     */
    public static Path getOrCreateClickAndStart(Path rootDir, ClickAndStartDescriptor clickAndStart) throws IOException {
        Path appDir = getAppHomeDir(rootDir, clickAndStart);
        Path launchFile = appDir.resolve(LAUNCH_FILE_NAME);

        // Create if not existing
        if (!launchFile.toFile().isFile()) {
            PathHelper.mkdirs(appDir);
            Files.write(launchFile, StorageHelper.toRawBytes(clickAndStart));
        }
        return launchFile;
    }

    /**
     * Returns the home directory for the given application
     */
    public static Path getAppHomeDir(Path rootDir, ClickAndStartDescriptor clickAndStart) {
        return rootDir.resolve("apps").resolve(clickAndStart.applicationId);
    }
}
