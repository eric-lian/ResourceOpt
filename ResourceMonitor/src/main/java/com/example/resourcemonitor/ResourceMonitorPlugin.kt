package com.example.resourcemonitor

import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.example.resourcemonitor.util.FileOperation
import com.example.resourcemonitor.util.Md5Util
import com.example.resourcemonitor.util.TypedValue
import org.gradle.api.Plugin
import org.gradle.api.Project
import pink.madis.apk.arsc.ResourceFile
import pink.madis.apk.arsc.ResourceTableChunk
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

class ResourceMonitorPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.extensions.getByType(AppExtension::class.java) ?: throw IllegalStateException(
            "Not an Android project!"
        )
        printAllDrawableResByProcessResTask(project)
    }


    private fun printAllDrawableResByProcessResTask(project: Project) {
        project.afterEvaluate { it ->
            val appExtension = it.extensions.getByType(AppExtension::class.java)
            // 获取输出集合
            appExtension.buildOutputs.all { it ->
                if (it is ApkVariantOutputImpl) {
                    // 获取到 PackageAndroidArtifact Task 任务
                    it.packageApplication.doFirst { it ->
                        var delSize = 0L
                        val start = System.currentTimeMillis()
                        var unZipDir: File? = null
                        var repackageApk: File? = null
                        try {
                            val outputDir =
                                (it as PackageAndroidArtifact).resourceFiles.get().asFile
                            // 原始APK
                            val originApk = outputDir.listFiles().find { it.extension == "ap_" }
                                ?: throw FileNotFoundException("originApk not found")
                            // 重打包APK
                            repackageApk = File(outputDir, "temp-${originApk.name}")
                            // 解压根目录
                            unZipDir = File(outputDir, TypedValue.UNZIP_DIR)
                            // 解压原始APK key = 文件名 value = ZipEntry
                            val unZipEntryMap = FileOperation.unZipAPk(
                                originApk.absolutePath, unZipDir.absolutePath
                            )
                            // 该方案 debug 包可以 ，并且 resources.arsc 可以被压缩，并且体积能减少一半 ，但是release包会出现问题
                            // INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED , 在构建release包时，可能会对资源文件进行额外的压缩和打包。
                            // 这可能导致resources.arsc文件的压缩格式不正确
                            //
                            // 但是 Target Sdk 在30以上 arsc 压缩被禁了。压缩 resources.arsc 虽然能带来包体上的收益，但也有弊端，它将带来内存和运行速度上的劣势。不压缩的resources.arsc系统可以使用mmap来节约内存的使用（一个app的资源至少被3个进程所持有：自己, launcher, system），而压缩的resources.arsc会存在于每个进程中。
                            //    val patterns = listOf(
                            //        ".+\\.(png|jpg|jpeg|gif)$".toPattern(),
                            //        "resources.arsc".toPattern()
                            //    )
                            //    compressedData.forEach {
                            //        val name = it.key
                            //        if (patterns.any { p -> p.matcher(name).matches() }) {
                            //            compressedData[name] = TypedValue.ZIP_DEFLATED
                            //        }
                            //    }
                            val resourcesFile =
                                unZipDir.listFilesFirst { it.name == "resources.arsc" }
                                    ?: throw FileNotFoundException("resources.arsc not found")
                            val resFile = unZipDir.listFilesFirst { it.name == "res" }
                                ?: throw FileNotFoundException("res not found")
                            // 获取解压后的 resources.arsc 文件
                            val resourceFile =
                                resourcesFile.inputStream().use { ResourceFile.fromInputStream(it) }
                            resourceFile.chunks.filterIsInstance<ResourceTableChunk>()
                                .forEach { resourceTableChunk ->
                                    // 优化key常量池
                                    resourceTableChunk.packages.forEach {
                                        val keyStringPool = it.keyStringPool
                                        val keyStringCount = keyStringPool.stringCount
                                        for (index in 0 until keyStringCount) {
                                            keyStringPool.setString(index, "yd")
                                        }
                                    }

                                    val stringPool = resourceTableChunk.stringPool
                                    resFile.listFiles { file ->
                                        !file.isDirectory
                                    }.groupBy {
                                        val path = unZipDir.toURI().relativize(it.toURI()).path
                                        // 先通过 crc 去重
                                        unZipEntryMap[path]!!.crc
                                    }.filter {
                                        it.value.size > 1
                                    }.values.flatten().groupBy {
                                        // 再根据 md5 再次分组
                                        Md5Util.getMD5Str(it)
                                    }.filter {
                                        it.value.size > 1
                                    }.map {
                                        // 默认保留第一个文件
                                        Pair(it.value.first(), it.value.drop(1))
                                    }.forEach { it ->
                                        val retainPath =
                                            unZipDir.toURI().relativize(it.first.toURI()).path
                                        println("res 资源保留: $retainPath")
                                        println("res 资源删除:")
                                        it.second.forEach {
                                            // 待删除资源
                                            val delPath =
                                                unZipDir.toURI().relativize(it.toURI()).path
                                            // 常量字符串池中去查找
                                            val stringIndex = stringPool.indexOf(delPath)
                                            stringPool.setString(stringIndex, retainPath)
                                            println("---path: $delPath size: ${it.length()}")
                                            delSize += it.length()
                                            it.delete()
                                        }
                                    }
                                }
                            // 重新生成 resources.arsc
                            FileOutputStream(resourcesFile).use {
                                it.write(resourceFile.toByteArray())
                            }
                            // 压缩修改后的资源为新的APK
                            FileOperation.zipFiles(unZipDir.listFiles().asList(),
                                unZipDir,
                                repackageApk,
                                unZipEntryMap.mapValues { it.value.method })
                            // 替换原始APK
                            repackageApk.renameTo(originApk)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            // 清理资源目录
                            unZipDir?.deleteRecursively()
                            repackageApk?.delete()
                            // 耗时
                            val end = System.currentTimeMillis()
                            println("重复资源优化插件优化总大小: ${delSize / 1024} KB")
                            println("重复资源优化插件耗时: ${end - start} ms")
                        }
                    }
                }
            }
        }
    }
}

private fun File?.listFilesFirst(predicate: (File) -> Boolean): File? {
    return this?.listFiles()?.first(predicate) ?: null
}