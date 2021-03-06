/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.sdkcompat.java.JavaConfigurationProducerList;
import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Suppresses certain non-Blaze configuration producers in Blaze projects. */
public class NonBlazeProducerSuppressor extends AbstractProjectComponent {

  private static final String KOTLIN_PLUGIN_ID = "org.jetbrains.kotlin";
  private static final String ANDROID_PLUGIN_ID = "org.jetbrains.android";

  private static final ImmutableList<String> KOTLIN_PRODUCERS =
      ImmutableList.of(
          "org.jetbrains.kotlin.idea.run.KotlinJUnitRunConfigurationProducer",
          "org.jetbrains.kotlin.idea.run.KotlinPatternConfigurationProducer",
          "org.jetbrains.kotlin.idea.run.KotlinRunConfigurationProducer");

  private static final ImmutableList<String> ANDROID_PRODUCERS =
      ImmutableList.of(
          "com.android.tools.idea.run.AndroidConfigurationProducer",
          "com.android.tools.idea.testartifacts.instrumented.AndroidTestConfigurationProducer",
          "com.android.tools.idea.testartifacts.junit.TestClassAndroidConfigurationProducer",
          "com.android.tools.idea.testartifacts.junit.TestDirectoryAndroidConfigurationProducer",
          "com.android.tools.idea.testartifacts.junit.TestMethodAndroidConfigurationProducer",
          "com.android.tools.idea.testartifacts.junit.TestPackageAndroidConfigurationProducer",
          "com.android.tools.idea.testartifacts.junit.TestPatternConfigurationProducer");

  private static Collection<Class<? extends RunConfigurationProducer<?>>> getProducers(
      String pluginId, Collection<String> qualifiedClassNames) {
    // rather than compiling against additional plugins, and including a switch in the our
    // plugin.xml, just get the classes manually via the plugin class loader.
    IdeaPluginDescriptor plugin = PluginManager.getPlugin(PluginId.getId(pluginId));
    if (plugin == null || !plugin.isEnabled()) {
      return ImmutableList.of();
    }
    ClassLoader loader = plugin.getPluginClassLoader();
    return qualifiedClassNames
        .stream()
        .map((qualifiedName) -> loadClass(loader, qualifiedName))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Nullable
  private static Class<RunConfigurationProducer<?>> loadClass(
      ClassLoader loader, String qualifiedName) {
    try {
      Class<?> clazz = loader.loadClass(qualifiedName);
      if (RunConfigurationProducer.class.isAssignableFrom(clazz)) {
        return (Class<RunConfigurationProducer<?>>) clazz;
      }
      return null;
    } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
      return null;
    }
  }

  public NonBlazeProducerSuppressor(Project project) {
    super(project);
  }

  @Override
  public void projectOpened() {
    if (Blaze.isBlazeProject(myProject)) {
      suppressProducers(myProject);
    }
  }

  private static void suppressProducers(Project project) {
    RunConfigurationProducerService producerService =
        RunConfigurationProducerService.getInstance(project);
    ImmutableList.<Class<? extends RunConfigurationProducer<?>>>builder()
        .addAll(JavaConfigurationProducerList.PRODUCERS_TO_SUPPRESS)
        .addAll(getProducers(KOTLIN_PLUGIN_ID, KOTLIN_PRODUCERS))
        .addAll(getProducers(ANDROID_PLUGIN_ID, ANDROID_PRODUCERS))
        .build()
        .forEach(producerService::addIgnoredProducer);
  }
}
