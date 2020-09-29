/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package io.bdeploy.gradle;

import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.PluginApplicationException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A simple unit test for the 'io.bdeploy.gradle.greeting' plugin.
 */
public class BDeployGradlePluginTest {
    
    @Test public void pluginApplication() {
    	Project root = ProjectBuilder.builder().build();
    	
    	root.getPlugins().apply(BDeployGradlePlugin.PLUGIN_ID);
    }
    
}
