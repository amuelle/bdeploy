package io.bdeploy.interfaces.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

import io.bdeploy.api.product.v1.ApplicationDescriptorApi;
import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.product.v1.ProductManifestBuilder;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.meta.PersistentManifestClassification;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestRefScanOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.bhive.op.TreeLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.interfaces.configuration.TemplateableVariableConfiguration;
import io.bdeploy.interfaces.configuration.TemplateableVariableDefaultConfiguration;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceVariableTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.ParameterTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.TemplateApplication;
import io.bdeploy.interfaces.descriptor.template.TemplateVariable;
import io.bdeploy.interfaces.descriptor.template.TemplateVariableFixedValueOverride;
import io.bdeploy.interfaces.manifest.product.ProductManifestStaticCache;
import io.bdeploy.interfaces.manifest.product.ProductManifestStaticCacheRecord;
import io.bdeploy.interfaces.variables.TemplateOverrideResolver;

/**
 * A special manifestation of a {@link Manifest} which must follow a certain layout and groups multiple applications together
 * which are deployed via an 'instance' to for a version-consistent bundle.
 */
public class ProductManifest {

    private static final Logger log = LoggerFactory.getLogger(ProductManifest.class);

    private final SortedSet<Manifest.Key> applications;
    private final SortedSet<Manifest.Key> references;
    private final String prodName;
    private final ProductDescriptor desc;
    private final Manifest manifest;
    private final ObjectId cfgTreeId;
    private final List<ObjectId> plugins;
    private final List<InstanceTemplateDescriptor> instanceTemplates;
    private final List<ApplicationTemplateDescriptor> applicationTemplates;
    private final List<ParameterTemplateDescriptor> paramTemplates;

    private ProductManifest(String name, Manifest manifest, SortedSet<Manifest.Key> applications,
            SortedSet<Manifest.Key> references, ProductDescriptor desc, ObjectId cfgTreeId, List<ObjectId> plugins,
            List<InstanceTemplateDescriptor> instanceTemplates, List<ApplicationTemplateDescriptor> applicationTemplates,
            List<ParameterTemplateDescriptor> paramTemplates) {
        this.prodName = name;
        this.manifest = manifest;
        this.applications = applications;
        this.references = references;
        this.desc = desc;
        this.cfgTreeId = cfgTreeId;
        this.plugins = plugins;
        this.instanceTemplates = instanceTemplates;
        this.applicationTemplates = applicationTemplates;
        this.paramTemplates = paramTemplates;
    }

    /**
     * @param hive the {@link BHive} to read from
     * @param manifest the {@link Manifest} which represents the {@link ProductManifest}
     * @return a {@link ProductManifest} or <code>null</code> if the given {@link Manifest} is not a {@link ProductManifest}.
     */
    public static ProductManifest of(BHive hive, Manifest.Key manifest) {
        Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(manifest));
        String label = mf.getLabels().get(ProductManifestBuilder.PRODUCT_LABEL);
        if (label == null) {
            return null;
        }

        ProductManifestStaticCache cacheStorage = new ProductManifestStaticCache(manifest, hive);
        ProductManifestStaticCacheRecord cached = cacheStorage.read();

        if (cached != null) {
            return new ProductManifest(label, mf, cached.appRefs, cached.otherRefs, cached.desc, cached.cfgEntry, cached.plugins,
                    cached.templates, cached.applicationTemplates, cached.paramTemplates);
        }

        SortedSet<Key> allRefs = new TreeSet<>(
                hive.execute(new ManifestRefScanOperation().setManifest(manifest).setMaxDepth(2)).values());

        SortedSet<Key> appRefs = new TreeSet<>();
        SortedSet<Key> otherRefs = new TreeSet<>();

        for (Manifest.Key ref : allRefs) {
            TreeView tv = hive.execute(new ScanOperation().setMaxDepth(1).setFollowReferences(false).setManifest(ref));
            if (tv.getChildren().containsKey(ApplicationDescriptorApi.FILE_NAME)) {
                appRefs.add(ref);
            } else {
                // not an application
                otherRefs.add(ref);
            }
        }

        Tree tree = hive.execute(new TreeLoadOperation().setTree(mf.getRoot()));
        ObjectId djId = tree.getNamedEntry(ProductManifestBuilder.PRODUCT_DESC).getValue();

        ProductDescriptor desc;
        try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(djId))) {
            desc = StorageHelper.fromStream(is, ProductDescriptor.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load deployment manifest", e);
        }

        ObjectId cfgEntry = null;
        Map<Tree.Key, ObjectId> entries = tree.getChildren();
        Tree.Key configKey = new Tree.Key(ProductManifestBuilder.CONFIG_ENTRY, Tree.EntryType.TREE);
        if (entries.containsKey(configKey)) {
            cfgEntry = entries.get(configKey);
        }

        List<ObjectId> plugins = new ArrayList<>();
        Tree.Key pluginKey = new Tree.Key(ProductManifestBuilder.PLUGINS_ENTRY, Tree.EntryType.TREE);
        if (entries.containsKey(pluginKey)) {
            TreeView tv = hive.execute(new ScanOperation().setTree(entries.get(pluginKey)));
            tv.visit(new TreeVisitor.Builder().onBlob(b -> {
                if (b.getName().toLowerCase().endsWith(".jar")) {
                    plugins.add(b.getElementId());
                }
            }).build());
        }

        List<InstanceTemplateDescriptor> templates = new ArrayList<>();
        Tree.Key templateKey = new Tree.Key(ProductManifestBuilder.TEMPLATES_ENTRY, Tree.EntryType.TREE);
        if (entries.containsKey(templateKey)) {
            TreeView tv = hive.execute(new ScanOperation().setTree(entries.get(templateKey)));
            tv.visit(new TreeVisitor.Builder().onBlob(b -> {
                if (b.getName().toLowerCase().endsWith(".yaml")) {
                    try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(b.getElementId()))) {
                        templates.add(StorageHelper.fromYamlStream(is, InstanceTemplateDescriptor.class));
                    } catch (Exception e) {
                        log.warn("Cannot load instance template from {}, {}", manifest, b.getPathString(), e);
                    }
                }
            }).build());
        }
        templates.sort((a, b) -> a.name.compareTo(b.name));

        List<ApplicationTemplateDescriptor> applicationTemplates = new ArrayList<>();
        Tree.Key appTemplateKey = new Tree.Key(ProductManifestBuilder.APP_TEMPLATES_ENTRY, Tree.EntryType.TREE);
        if (entries.containsKey(appTemplateKey)) {
            TreeView tv = hive.execute(new ScanOperation().setTree(entries.get(appTemplateKey)));
            tv.visit(new TreeVisitor.Builder().onBlob(b -> {
                if (b.getName().toLowerCase().endsWith(".yaml")) {
                    try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(b.getElementId()))) {
                        applicationTemplates.add(StorageHelper.fromYamlStream(is, ApplicationTemplateDescriptor.class));
                    } catch (Exception e) {
                        log.warn("Cannot load application template from {}, {}", manifest, b.getPathString(), e);
                    }
                }
            }).build());
        }

        List<ParameterTemplateDescriptor> paramTemplates = new ArrayList<>();
        Tree.Key paramTemplateKey = new Tree.Key(ProductManifestBuilder.PARAM_TEMPLATES_ENTRY, Tree.EntryType.TREE);
        if (entries.containsKey(paramTemplateKey)) {
            TreeView tv = hive.execute(new ScanOperation().setTree(entries.get(paramTemplateKey)));
            tv.visit(new TreeVisitor.Builder().onBlob(b -> {
                if (b.getName().toLowerCase().endsWith(".yaml")) {
                    try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(b.getElementId()))) {
                        paramTemplates.add(StorageHelper.fromYamlStream(is, ParameterTemplateDescriptor.class));
                    } catch (Exception e) {
                        log.warn("Cannot load application template from {}, {}", manifest, b.getPathString(), e);
                    }
                }
            }).build());
        }

        List<InstanceVariableTemplateDescriptor> varTemplates = new ArrayList<>();
        Tree.Key varTemplateKey = new Tree.Key(ProductManifestBuilder.VARIABLE_TEMPLATES_ENTRY, Tree.EntryType.TREE);
        if (entries.containsKey(varTemplateKey)) {
            TreeView tv = hive.execute(new ScanOperation().setTree(entries.get(varTemplateKey)));
            tv.visit(new TreeVisitor.Builder().onBlob(b -> {
                if (b.getName().toLowerCase().endsWith(".yaml")) {
                    try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(b.getElementId()))) {
                        varTemplates.add(StorageHelper.fromYamlStream(is, InstanceVariableTemplateDescriptor.class));
                    } catch (Exception e) {
                        log.warn("Cannot load instance variable template from {}, {}", manifest, b.getPathString(), e);
                    }
                }
            }).build());
        }

        // lazy, DFS resolving of all templates.
        resolveTemplates(templates, applicationTemplates, varTemplates);
        applicationTemplates.sort((a, b) -> a.name.compareTo(b.name));

        // store persistent information.
        try {
            cacheStorage.store(appRefs, otherRefs, desc, cfgEntry, plugins, templates, applicationTemplates, paramTemplates);
        } catch (Exception e) {
            // there is a chance for a race condition here, which actually does not do any harm (except for a
            // tiny performance hit since two threads calculate this). in case two threads try to persist the
            // exact same thing, we simply ignore the failure.
            if (log.isDebugEnabled()) {
                log.debug("Cannot store persistent cache for {}: {}", manifest, e.toString());
            }
        }

        return new ProductManifest(label, mf, appRefs, otherRefs, desc, cfgEntry, plugins, templates, applicationTemplates,
                paramTemplates);
    }

    private static void resolveTemplates(List<InstanceTemplateDescriptor> instTemplates,
            List<ApplicationTemplateDescriptor> appTemplates, List<InstanceVariableTemplateDescriptor> varTemplates) {
        for (var itd : instTemplates) {
            // inline resolve all variable templates to their expanded values.
            resolveInstanceVariableTemplates(varTemplates, itd.instanceVariables, itd.instanceVariableDefaults);

            for (var group : itd.groups) {
                var appList = new ArrayList<TemplateApplication>();
                for (var app : group.applications) {
                    try {
                        var res = resolveAppTemplate(app, appTemplates, itd.templateVariables, new ArrayList<>(),
                                new ArrayList<>());
                        if (res != null) {
                            appList.add(res);
                        }
                    } catch (Exception e) {
                        log.error("Cannot resolve application template {} from instance template {}", app.name, itd.name, e);
                    }
                }

                group.applications = appList;
            }

            // remove group if no application resolved.
            itd.groups.removeIf(g -> g.applications.isEmpty());
        }

        // remove template if no application in any group resolved.
        instTemplates.removeIf(t -> t.groups.isEmpty());

        var resApps = new ArrayList<ApplicationTemplateDescriptor>();
        for (ApplicationTemplateDescriptor atd : appTemplates) {
            try {
                var res = resolveAppTemplate(atd, appTemplates, atd.templateVariables, new ArrayList<>(), new ArrayList<>());
                if (res != null) {
                    resApps.add(res);
                }
            } catch (Exception e) {
                log.error("Cannot resolve application template {}", atd.name, e);
            }
        }

        appTemplates.clear();
        appTemplates.addAll(resApps);
    }

    private static void resolveInstanceVariableTemplates(List<InstanceVariableTemplateDescriptor> templates,
            List<TemplateableVariableConfiguration> vars,
            List<TemplateableVariableDefaultConfiguration> instanceVariableDefaults) {
        TemplateableVariableConfiguration toReplace = null;
        do {
            toReplace = vars.stream().filter(v -> v.template != null).findFirst().orElse(null);
            if (toReplace != null) {
                // replace/expand it.
                int index = vars.indexOf(toReplace);
                vars.remove(index); // remove the original element.

                String templateId = toReplace.template;
                List<TemplateableVariableConfiguration> replacements = templates.stream().filter(t -> t.id.equals(templateId))
                        .map(t -> t.instanceVariables).findFirst().orElse(null);

                if (replacements == null) {
                    log.warn("No instance variable template found for {}", templateId);
                } else {
                    // only apply things which are not already there for *whatever* reason.
                    ImmutableList.copyOf(replacements).reverse().stream()
                            .filter(p -> vars.stream().filter(x -> x.id != null && x.id.equals(p.id)).findFirst().isEmpty())
                            .forEach(r -> vars.add(index, r));
                }
            }
        } while (toReplace != null);

        // now that all are resolved, fixup any default value overrides from the instance template.
        if (instanceVariableDefaults != null && !instanceVariableDefaults.isEmpty()) {
            for (var def : instanceVariableDefaults) {
                var variable = vars.stream().filter(x -> x.id.equals(def.id)).findFirst();
                if (variable.isEmpty()) {
                    log.warn("Variable not found while applying override: {}", def.id);
                } else {
                    variable.get().value = def.value;
                }
            }
        }
    }

    private static <T extends TemplateApplication> T resolveAppTemplate(T original,
            List<ApplicationTemplateDescriptor> appTemplates, List<TemplateVariable> varList,
            List<TemplateVariableFixedValueOverride> overrides, List<String> circleDetection) {

        // we always work on a copy of the configuration just in case it is referenced from more than one place.
        @SuppressWarnings("unchecked")
        T app = (T) original.copy();

        if (app instanceof ApplicationTemplateDescriptor desc) {
            // *must* have an ID.
            if (circleDetection.contains(desc.id)) {
                // we already resolved this through *some* path - this is not ok.
                throw new IllegalStateException("Circular definition found in application templates when processing: " + desc.id);
            }

            circleDetection.add(desc.id);
        }

        if (app.template == null && app.application == null) {
            // unfortunately no more information available...
            throw new IllegalArgumentException("Template without application and template reference found");
        }

        List<TemplateVariableFixedValueOverride> mergedOverrides = new ArrayList<>();
        if (overrides != null && !overrides.isEmpty()) {
            mergedOverrides.addAll(overrides);
        }

        if (app.fixedVariables != null && !app.fixedVariables.isEmpty()) {
            // add only if overrides don't already contain a value.
            for (var fixed : app.fixedVariables) {
                if (mergedOverrides.stream().filter(o -> o.id.equals(fixed.id)).findAny().isEmpty()) {
                    mergedOverrides.add(fixed);
                }
            }
        }

        // update variable values for all "our" parameters according to the present overrides.
        app.name = processTemplateOverride(app.name, mergedOverrides);
        for (var param : app.startParameters) {
            param.value = processTemplateOverride(param.value, mergedOverrides);
        }

        if (app.template != null) {
            var parent = appTemplates.stream().filter(t -> app.template.equals(t.id)).findFirst();
            if (!parent.isPresent()) {
                if (log.isDebugEnabled()) {
                    log.debug("Template error. Cannot find template {}", app.template);
                }
                return null;
            }
            var parentDesc = parent.get();
            var parentApp = resolveAppTemplate(parentDesc, appTemplates, parentDesc.templateVariables, mergedOverrides,
                    circleDetection);

            // add variables from parent template to next outer variables.
            for (var variable : parentDesc.templateVariables) {
                var existing = varList.stream().filter(v -> v.id.equals(variable.id)).findAny();
                var override = overrides.stream().filter(o -> o.id.equals(variable.id)).findAny();
                if (!existing.isPresent() && !override.isPresent()) {
                    varList.add(variable);
                }
            }

            // merge all kinds of attributes, so that 'app' contains a complete template in the end.
            mergeParentIntoTemplate(app, parentApp);
        }

        return app;
    }

    private static String processTemplateOverride(String value, List<TemplateVariableFixedValueOverride> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return value;
        }

        TemplateOverrideResolver res = new TemplateOverrideResolver(overrides);
        return TemplateHelper.process(value, res, res::canResolve);
    }

    private static void mergeParentIntoTemplate(TemplateApplication app, TemplateApplication parentApp) {
        // merge simple attributes
        app.application = resolveStringValue(app.application, parentApp.application);
        app.name = resolveStringValue(app.name, parentApp.name);
        app.description = resolveStringValue(app.description, parentApp.description);
        app.preferredProcessControlGroup = resolveStringValue(app.preferredProcessControlGroup,
                parentApp.preferredProcessControlGroup);

        // merge process control partial object as map
        for (var entry : parentApp.processControl.entrySet()) {
            if (!app.processControl.containsKey(entry.getKey())) {
                app.processControl.put(entry.getKey(), entry.getValue());
            }
        }

        if (app.startParameters == null) {
            app.startParameters = new ArrayList<>();
        }

        if (parentApp.startParameters != null) {
            // merge start parameters
            for (var param : parentApp.startParameters) {
                var existing = app.startParameters.stream().filter(p -> p.id.equals(param.id)).findAny();
                if (!existing.isPresent()) {
                    app.startParameters.add(param);
                }
            }
        }
    }

    private static String resolveStringValue(String ours, String theirs) {
        if (ours != null) {
            return ours;
        }
        return theirs;
    }

    /**
     * @return the tree ID of the config file template folder.
     */
    public ObjectId getConfigTemplateTreeId() {
        return cfgTreeId;
    }

    /**
     * @return a list of plugin files discovered in the products plugin folder.
     */
    public List<ObjectId> getPlugins() {
        return plugins;
    }

    /**
     * @return a list of instance templates which can be used to populate empty instances.
     */
    public List<InstanceTemplateDescriptor> getInstanceTemplates() {
        return instanceTemplates;
    }

    /**
     * @return a list of application templates which can be used when creating applications.
     */
    public List<ApplicationTemplateDescriptor> getApplicationTemplates() {
        return applicationTemplates;
    }

    /**
     * @return a list of templates which provide re-usable definitions of parameters.
     */
    public List<ParameterTemplateDescriptor> getParameterTemplates() {
        return paramTemplates;
    }

    /**
     * @return the name of the product.
     */
    public String getProduct() {
        return prodName;
    }

    /**
     * @return the product's descriptor.
     */
    public ProductDescriptor getProductDescriptor() {
        return desc;
    }

    /**
     * @return the product {@link Manifest} {@link Key}.
     */
    public Key getKey() {
        return manifest.getKey();
    }

    public Map<String, String> getLabels() {
        return manifest.getLabels();
    }

    /**
     * @return the applications grouped by this product
     */
    public SortedSet<Manifest.Key> getApplications() {
        return applications;
    }

    /**
     * @return additionally referenced non-application manifests (e.g. dependencies of applications).
     */
    public SortedSet<Manifest.Key> getReferences() {
        return references;
    }

    /**
     * @param hive the {@link BHive} to scan for available {@link ProductManifest}s.
     * @return a {@link SortedSet} with all available {@link ProductManifest}s.
     */
    public static SortedSet<Manifest.Key> scan(BHive hive) {
        SortedSet<Manifest.Key> result = new TreeSet<>();

        // filter out internal (meta, etc.) manifests right away so we don't waste time checking.
        Set<Manifest.Key> allKeys = hive.execute(new ManifestListOperation()).stream().filter(k -> !k.getName().startsWith("."))
                .collect(Collectors.toSet());

        PersistentManifestClassification<ProductClassification> pc = new PersistentManifestClassification<>(hive, "products",
                m -> new ProductClassification(m.getLabels().containsKey(ProductManifestBuilder.PRODUCT_LABEL)));

        pc.loadAndUpdate(allKeys);
        pc.getClassifications().entrySet().stream().filter(e -> e.getValue().isProduct).map(Entry::getKey).forEach(result::add);

        return result;
    }

    public static final class ProductClassification {

        public final boolean isProduct;

        @JsonCreator
        public ProductClassification(@JsonProperty("isProduct") boolean prod) {
            isProduct = prod;
        }

    }

}
