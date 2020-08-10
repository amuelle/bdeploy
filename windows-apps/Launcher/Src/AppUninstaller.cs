﻿using Bdeploy.Launcher.Models;
using Bdeploy.Shared;
using Serilog;
using System.IO;
using System.Text;

namespace Bdeploy.Installer.Models {

    /// <summary>
    /// Removes the application and all created registry entries and shortcuts
    /// </summary>
    public class AppUninstaller : BaseLauncher {

        /// <summary>
        /// Creates a new uninstaller instance.
        /// </summary>
        /// <param name="clickAndStartFile">The .bdeploy file to pass to the companion script</param>
        public AppUninstaller(string clickAndStartFile) : base(clickAndStartFile) {
        }

        /// <summary>
        /// Starts the LauncherCli to remove the application  described by the ClickAndStart file.
        /// </summary>
        public int Start() {
            // Descriptor must be existing and valid
            if (!ValidateDescriptor()) {
                return -1;
            }

            // The embedded JRE must be valid
            if (!ValidateEmbeddedJre()) {
                return -2;
            }
            Log.Information("Requesting to uninstall application {0} of instance {1}/{2}", descriptor.ApplicationId, descriptor.GroupId, descriptor.InstanceId);

            // Build arguments to pass to the application
            StringBuilder builder = new StringBuilder();
            AppendCustomJvmArguments(builder);

            // Append classpath and mandatory arguments of application
            builder.AppendFormat("-cp \"{0}\" ", Path.Combine(LIB, "*"));
            builder.AppendFormat("{0} ", MAIN_CLASS);
            builder.AppendFormat("uninstaller ");
            builder.AppendFormat("\"--app={0}\" ", descriptor.ApplicationId);

            // Abort if uninstallation was not OK
            int returnCode = StartLauncher(builder.ToString());
            if (returnCode != 0) {
                return returnCode;
            }

            // Find and delete the registry entry
            SoftwareEntryData entry = SoftwareEntry.Read(descriptor.ApplicationId);
            if (entry != null) {
                SoftwareEntry.Remove(descriptor.ApplicationId);

                // Delete desktop shortcut
                if (entry.DesktopShortcut != null) {
                    FileHelper.DeleteFile(entry.DesktopShortcut);
                }

                // Delete start menu shortcut and directories if empty
                // Shortcut is stored in <vendor> / <instance group> / <instance> / <link>
                if (entry.StartMenuShortcut != null) {
                    FileHelper.DeleteFile(entry.StartMenuShortcut);
                    FileInfo appLink = new FileInfo(entry.StartMenuShortcut);

                    // Delete instance directory if empty
                    DirectoryInfo instanceDir = appLink.Directory;
                    FileHelper.DeleteDirIfEmpty(instanceDir.FullName);

                    // Delete instance group directory if empty
                    DirectoryInfo instanceGroupDir = instanceDir.Parent;
                    FileHelper.DeleteDirIfEmpty(instanceGroupDir.FullName);

                    // Delete instance group directory if empty
                    DirectoryInfo vendorDir = instanceGroupDir.Parent;
                    FileHelper.DeleteDirIfEmpty(vendorDir.FullName);
                }
            }
            // All OK
            Log.Information("Uninstallation successfully completed.");
            return 0;
        }
    }
}