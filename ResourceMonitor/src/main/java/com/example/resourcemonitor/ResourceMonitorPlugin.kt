package com.example.resourcemonitor

import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import com.example.resourcemonitor.model.Extension
import com.example.resourcemonitor.tasks.ResourcesOptTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class ResourceMonitorPlugin : Plugin<Project> {

    companion object {
        const val EXTENSION_NAME = "resourceOpt"
    }

    override fun apply(project: Project) {
        val appExtension = project.extensions.getByType(AppExtension::class.java)
            ?: throw IllegalStateException("Not an Android project!")
        val extension = project.extensions.create(EXTENSION_NAME, Extension::class.java)
        appExtension.buildOutputs.all {
            val packageAndroidArtifact = (it as ApkVariantOutputImpl).packageApplication
            // 注册任务
            val resourcesOptTaskTaskProvider =
                project.tasks.register("${it.name}ResourcesOptTask", ResourcesOptTask::class.java)
            // 配置任务
            resourcesOptTaskTaskProvider.configure { task ->
                task.variantName.set(packageAndroidArtifact.variantName)
                task.config.set(extension.toConfig())
                task.resourceFilesInput.set(packageAndroidArtifact.resourceFiles)
            }
            // 设置任务依赖
            packageAndroidArtifact.dependsOn(resourcesOptTaskTaskProvider)
        }

    }
}