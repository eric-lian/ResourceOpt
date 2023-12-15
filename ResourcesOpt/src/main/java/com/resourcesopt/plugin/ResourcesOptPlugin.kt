package com.resourcesopt.plugin

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.impl.ApplicationVariantBuilderImpl
import com.android.build.api.variant.impl.ApplicationVariantImpl
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.resourcesopt.plugin.model.Extension
import com.resourcesopt.plugin.tasks.ResourcesOptTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.time.format.DateTimeFormatter

class ResourcesOptPlugin : Plugin<Project> {

    companion object {
        const val EXTENSION_NAME = "resourcesOpt"
    }

    override fun apply(project: Project) {
        val appExtension = project.extensions.getByType(AppExtension::class.java)
        val extension = project.extensions.create(EXTENSION_NAME, Extension::class.java)
        appExtension.buildOutputs.all {
            // 获取是否开启了混淆
            val packageAndroidArtifact = (it as ApkVariantOutputImpl).packageApplication
            // 注册任务
            val resourcesOptTaskTaskProvider =
                project.tasks.register("${it.name}ResourcesOptTask", ResourcesOptTask::class.java)
            // 配置任务
            resourcesOptTaskTaskProvider.configure { task ->
                task.variantName.set(packageAndroidArtifact.variantName)
                task.config.set(extension.toConfig())
                task.resourceFilesInput.set(packageAndroidArtifact.resourceFiles)
                // 当前日期 yyyy-MM-dd HH:mm:ss
                val now = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(
                    java.time.LocalDateTime.now()
                )
                task.buildOutputDir.set(project.buildDir.resolve("$EXTENSION_NAME/${packageAndroidArtifact.variantName}/$now"))
            }
            // 设置任务依赖
            packageAndroidArtifact.dependsOn(resourcesOptTaskTaskProvider)
        }
    }
}