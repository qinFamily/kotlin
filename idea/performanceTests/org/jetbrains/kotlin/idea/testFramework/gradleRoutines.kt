/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.testFramework

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.plugins.gradle.service.project.open.attachGradleProjectAndRefresh
import org.jetbrains.plugins.gradle.service.project.open.importProject
import org.jetbrains.plugins.gradle.service.project.open.setupGradleSettings
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleLog

fun refreshGradleProject(projectPath: String, project: Project) {
    GradleLog.LOG.info("Import project at $projectPath")
    val projectSdk = ProjectRootManager.getInstance(project).projectSdk
    val gradleProjectSettings = GradleProjectSettings()
    setupGradleSettings(gradleProjectSettings, projectPath, project, projectSdk)
    attachGradleProjectAndRefresh(gradleProjectSettings, project)
    ProjectUtil.updateLastProjectLocation(projectPath)

    val gradleArguments = System.getProperty("kotlin.test.gradle.import.arguments")
    ExternalSystemUtil.refreshProjects(
        ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
            .forceWhenUptodate()
            .useDefaultCallback()
            .use(ProgressExecutionMode.MODAL_SYNC)
            .also {
                gradleArguments?.run(it::withArguments)
            }
    )

    dispatchAllInvocationEvents()
}
