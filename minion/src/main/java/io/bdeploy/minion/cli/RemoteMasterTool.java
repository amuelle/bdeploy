package io.bdeploy.minion.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.stream.Collectors;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.cli.RemoteMasterTool.RemoteMasterConfig;

@Help("Investigate a remote master minion")
@CliName("remote-master")
public class RemoteMasterTool extends RemoteServiceTool<RemoteMasterConfig> {

    public @interface RemoteMasterConfig {

        @Help(value = "List available minions", arg = false)
        boolean minions() default false;

        @Help("Path to an updated distribution (ZIP) which will be pushed to the master for update")
        String update();

        @Help("Path to a launcher distribution (ZIP) which will be pushed to the master")
        String launcher();

        @Help("OS of the remote master. If not specified it is assumed that the local and the remote OS are the same. ")
        String masterOs();

        @Help(value = "Don't ask for confirmation before initiating the update process on the remote", arg = false)
        boolean yes() default false;
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

    public RemoteMasterTool() {
        super(RemoteMasterConfig.class);
    }

    @Override
    protected void run(RemoteMasterConfig config, RemoteService svc) {
        MasterRootResource client = ResourceProvider.getResource(svc, MasterRootResource.class, null);

        if (config.minions()) {
            listMinions(client);
        } else if (config.update() != null) {
            Path zip = Paths.get(config.update());
            if (!Files.isRegularFile(zip)) {
                out().println(zip + " does not seem to be an update package");
            }

            performUpdate(config, svc, zip);
        } else if (config.launcher() != null) {
            Path zip = Paths.get(config.launcher());
            if (!Files.isReadable(zip)) {
                out().println(zip + " does not seem to be a launcher package");
            }

            pushLauncher(svc, zip);
        }
    }

    private void listMinions(MasterRootResource client) {
        SortedMap<String, MinionStatusDto> minions = client.getMinions();
        String formatString = "%1$-20s %2$-8s %3$25s %4$-10s %5$-15s";
        out().println(String.format(formatString, "NAME", "STATUS", "START", "OS", "VERSION"));
        for (Map.Entry<String, MinionStatusDto> e : minions.entrySet()) {
            MinionStatusDto s = e.getValue();
            String startTime = s.startup != null ? FORMATTER.format(s.startup) : "-";
            String status = s.offline ? "OFFLINE" : "ONLINE";
            MinionDto config = s.config;
            out().println(String.format(formatString, e.getKey(), status, startTime, config.os, config.version));
        }
    }

    private void performUpdate(RemoteMasterConfig config, RemoteService svc, Path zip) {
        try {
            Collection<Manifest.Key> keys = importAndPushUpdate(svc, zip, getActivityReporter());

            if (!config.yes()) {
                System.out.println("Pushing of update package successful, press any key to continue updating or CTRL+C to abort");
                System.in.read();
            }

            UpdateHelper.update(svc, keys, true);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to process update", e);
        }
    }

    private void pushLauncher(RemoteService svc, Path zip) {
        try {
            importAndPushUpdate(svc, zip, getActivityReporter());
            out().println("Pushed launcher update");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to process launcher", e);
        }
    }

    /**
     * Import an update ZIP. BDeploy update ZIPs may carry nested launcher updates, which are imported as well.
     * <p>
     * The key of the BDeploy update is returned for further update purposes.
     */
    public static Collection<Manifest.Key> importAndPushUpdate(RemoteService remote, Path updateZipFile,
            ActivityReporter reporter) throws IOException {
        Path tmpDir = Files.createTempDirectory("update-");
        try {
            Path hive = tmpDir.resolve("hive");
            try (BHive tmpHive = new BHive(hive.toUri(), reporter)) {
                List<Manifest.Key> keys = UpdateHelper.importUpdate(updateZipFile, tmpDir.resolve("import"), tmpHive);
                PushOperation pushOp = new PushOperation().setRemote(remote);
                keys.forEach(pushOp::addManifest);
                tmpHive.execute(pushOp);

                return keys.stream().filter(UpdateHelper::isBDeployServerKey).collect(Collectors.toList());
            }
        } finally {
            PathHelper.deleteRecursive(tmpDir);
        }
    }

}
