package io.bdeploy.minion.remote.jersey;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.LocalDependencyFetcher;
import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestRefScanOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.dcu.InstanceNodeController;
import io.bdeploy.interfaces.cleanup.CleanupAction;
import io.bdeploy.interfaces.cleanup.CleanupAction.CleanupType;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.remote.NodeCleanupResource;
import io.bdeploy.minion.MinionRoot;
import jakarta.inject.Inject;

public class NodeCleanupResourceImpl implements NodeCleanupResource {

    private static final Logger log = LoggerFactory.getLogger(NodeCleanupResourceImpl.class);

    @Inject
    private MinionRoot root;

    @Override
    public List<CleanupAction> cleanup(SortedSet<Key> toKeep) {
        List<CleanupAction> notExecuted = new ArrayList<>();

        BHive hive = root.getHive();

        Set<String> newestLauncherTags = hive.execute(new ManifestListOperation().setManifestName("meta/launcher")).stream() //
                .map(Key::getTag) //
                .collect(Collectors.toCollection(() -> new TreeSet<>((a, b) -> VersionHelper.compare(b, a)))) // reverse order
                .stream().limit(2).collect(Collectors.toSet());

        Set<Key> allMfs = hive.execute(new ManifestListOperation()); // list ALL

        // filter some well-known things.
        SortedSet<Key> toClean = allMfs.stream() //
                .filter(this::isNotWellKnown) //
                .filter(key -> !newestLauncherTags.contains(key.getTag())) //
                .collect(Collectors.toCollection(TreeSet::new));

        SortedSet<Key> allRefs = new TreeSet<>();
        for (Key keep : toKeep) {
            // toKepp may contain way more manifests than actually exist in our hive, since it's the list of all manifests in all groups
            if (!allMfs.contains(keep)) {
                continue;
            }
            SortedMap<String, Key> refs = hive.execute(new ManifestRefScanOperation().setManifest(keep));
            allRefs.add(keep);
            allRefs.addAll(refs.values());

            if (log.isDebugEnabled()) {
                log.debug("Keeping {} alive, refs implicitly kept alive: {}", keep, refs);
            }

            // if the manifest is an InstanceNodeManifest, check all attached (indirectly referenced applications).
            if (hive.execute(new ManifestLoadOperation().setManifest(keep)).getLabels()
                    .containsKey(InstanceNodeManifest.INSTANCE_NODE_LABEL)) {
                InstanceNodeManifest inm = InstanceNodeManifest.of(hive, keep);
                LocalDependencyFetcher localDeps = new LocalDependencyFetcher();
                for (ApplicationConfiguration app : inm.getConfiguration().applications) {
                    try {
                        // Try to load. This might fail if the application is no longer present - for whatever reason
                        ApplicationManifest amf = ApplicationManifest.of(hive, app.application, null);

                        // Add the application if it could be loaded
                        allRefs.add(app.application);

                        // Applications /must/ follow the ScopedManifestKey rules.
                        ScopedManifestKey smk = ScopedManifestKey.parse(app.application);

                        // The dependencies must be local. They have been pushed here together with the application.
                        var deps = localDeps.fetch(hive, amf.getDescriptor().runtimeDependencies, smk.getOperatingSystem());
                        allRefs.addAll(deps);

                        if (log.isTraceEnabled()) {
                            log.trace(" - Additionally keeping application {} alive with dependencies: {}", app.application,
                                    deps);
                        }
                    } catch (Exception e) {
                        log.warn("Cannot read application {} while protecting {}", app.application, keep, e);
                    }
                }
            }
        }

        toClean.removeAll(allRefs);
        for (Key clean : toClean) {
            if (MetaManifest.isMetaManifest(clean) && MetaManifest.isParentAlive(clean, hive, toClean)) {
                continue;
            }
            notExecuted.add(new CleanupAction(CleanupType.DELETE_MANIFEST, clean.toString(), "Delete manifest " + clean));

            if (log.isTraceEnabled()) {
                log.trace("Cleaning manifest {}, not kept alive through anchors", clean);
            }
        }

        // after manifests, cleanup dist (deployment dir, temp, download, ...).
        notExecuted.addAll(InstanceNodeController.cleanup(hive, root.getDeploymentDir(), toClean));

        return notExecuted;
    }

    private boolean isNotWellKnown(Key key) {
        return !((key.getName().startsWith("meta/") && !key.getName().startsWith("meta/launcher"))
                || key.getName().startsWith("users/"));
    }

    @Override
    public void perform(List<CleanupAction> actions) {
        boolean needPrune = false;
        for (CleanupAction action : actions) {
            switch (action.type) {
                case DELETE_FOLDER:
                    doDeleteFolder(Paths.get(action.what));
                    break;
                case DELETE_MANIFEST:
                    needPrune = true;
                    doDeleteManifest(root.getHive(), Key.parse(action.what));
                    break;
                case TRUNCATE_META_MANIFEST:
                    log.warn("Truncating history not supported on node");
                    break;
                default:
                    throw new IllegalStateException("CleanupType " + action.type + " not supported here");
            }
        }

        if (needPrune) {
            root.getHive().execute(new PruneOperation());
        }
    }

    private void doDeleteManifest(BHive hive, Key key) {
        hive.execute(new ManifestDeleteOperation().setToDelete(key));
    }

    private void doDeleteFolder(Path path) {
        // check folder location and permission
        if (!root.isManagedPath(path)) {
            throw new IllegalStateException("Path to delete is not under minion's control: " + path);
        }

        PathHelper.deleteRecursiveRetry(path);
    }

}
