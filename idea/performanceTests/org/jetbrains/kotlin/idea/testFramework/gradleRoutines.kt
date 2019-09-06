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
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

fun openProject(name: String, projectPath: String) = ProjectUtil.openOrImport(File(projectPath).canonicalPath, null, false)

fun refreshGradleProject(projectPath: String, project: Project) {
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
