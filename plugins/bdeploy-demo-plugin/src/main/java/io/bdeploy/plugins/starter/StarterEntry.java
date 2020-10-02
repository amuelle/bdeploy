/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package io.bdeploy.plugins.starter;

import java.util.Collection;
import java.util.Collections;

import io.bdeploy.api.plugin.v1.CustomEditor;
import io.bdeploy.api.plugin.v1.CustomProductVersionSorter;
import io.bdeploy.api.plugin.v1.Plugin;
import io.bdeploy.api.plugin.v1.PluginAssets;
import io.bdeploy.common.Version;
import io.bdeploy.common.util.VersionHelper;

public class StarterEntry extends Plugin {
	
	@Override
	public Collection<Class<?>> getComponentClasses() {
		// All component classes are registered here. Component classes are JAX-RS resource implementations which
		// will be hosted by BDeploy in a dedicated per-plugin namespace on the server. Any frontend module which
		// is part of the plugin will have access to the APIs registered here.
		return Collections.singleton(StarterResource.class);
	}
	
	@Override
	public Collection<PluginAssets> getAssets() {
		return Collections.singletonList(new PluginAssets("/js", "/js"));
	}
	
	@Override
	public Collection<CustomEditor> getCustomEditors() {
		return Collections.singletonList(new CustomEditor("/js/encoding-editor.js", "BASE64_EDITOR", false));
	}
	
	@Override
	public CustomProductVersionSorter getCustomSorter() {
		return new CustomProductVersionSorter((a, b) -> {
			Version va = VersionHelper.tryParse(a);
			Version vb = VersionHelper.tryParse(b);
			
			// ignore leading characters in qualifiers.
			while(!Character.isDigit(va.getQualifier().charAt(0))) {
				va = new Version(va.getMajor(), va.getMinor(), va.getMicro(), va.getQualifier().substring(1));
			}
			while(!Character.isDigit(vb.getQualifier().charAt(0))) {
				vb = new Version(vb.getMajor(), vb.getMinor(), vb.getMicro(), vb.getQualifier().substring(1));
			}
			
			return VersionHelper.compare(va, vb);
		});
	}
	
}