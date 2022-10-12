package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class TemplateApplication {

    @JsonPropertyDescription("The ID of the application to use, as given in the product-info.yaml file of the product.")
    public String application;

    @JsonPropertyDescription("ID of another application template, which should be the basis of this template (inheritance).")
    public String template;

    @JsonPropertyDescription("The name of the resulting process configuration.")
    public String name;

    @JsonPropertyDescription("A description of the process created by this template.")
    public String description;

    @JsonPropertyDescription("The name of a potentially existing process control group. If a group with this name exists, the application is put into that group. Otherwise it is appended to the last existing group on the node (the default for adding applications).")
    public String preferredProcessControlGroup;

    @JsonPropertyDescription("One or more processControl properties as defined in 'app-info.yaml' syntax.")
    public Map<String, Object> processControl = new TreeMap<>();

    @JsonPropertyDescription("A set of parameters to configure on the process resulting from this template.")
    public List<TemplateParameter> startParameters = new ArrayList<>();

    public List<TemplateVariableFixedValueOverride> fixedVariables = new ArrayList<>();

    public TemplateApplication() {
        // intentionally left blank
    }

    public TemplateApplication(TemplateApplication original) {
        this.application = original.application;
        this.template = original.template;
        this.name = original.name;
        this.description = original.description;
        this.preferredProcessControlGroup = original.preferredProcessControlGroup;
        this.processControl = original.processControl; // immutable, shallow OK
        this.fixedVariables = original.fixedVariables;

        // this one is the tricky one, as fixedVariables can transitively modify parameters.
        for (var p : original.startParameters) {
            var np = new TemplateParameter();
            np.id = p.id;
            np.value = p.value;
            this.startParameters.add(np);
        }
    }

    public TemplateApplication copy() {
        return new TemplateApplication(this);
    }

}
