package com.resourcesopt.plugin.tasks

import com.resourcesopt.plugin.model.Config
import com.resourcesopt.plugin.util.FileOperation
import com.resourcesopt.plugin.util.Md5Util
import com.resourcesopt.plugin.util.TypedValue
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import pink.madis.apk.arsc.ResourceFile
import pink.madis.apk.arsc.ResourceTableChunk
import java.io.File
import java.io.FileOutputStream

abstract class ResourcesOptTask : DefaultTask() {

    @get:Input
    abstract val variantName: Property<String>

    // 输入 配置
    @get:Input
    abstract val config: Property<Config>

    @get:InputDirectory
    abstract val resourceFilesInput: DirectoryProperty

    @get:Internal
    abstract val buildOutputDir: DirectoryProperty


    @TaskAction
    fun doAction() {
        var delSize = 0L
        val start = System.currentTimeMillis()
        // 输入目录
        val inputDir = resourceFilesInput.get().asFile
        // 缓存输出目录
        val outputDir = buildOutputDir.get().asFile
        // 被删除的资源后缓存到该目录下，方便查看那些资源被优化了
        val outputResDir = outputDir.resolve("res")
        // 编译后的APK
        val originApk = inputDir.listFiles().find { it.extension == "ap_" }
        if (originApk?.exists() != true) return
        // 解压根目录
        var unZipDir = inputDir.resolve(TypedValue.UNZIP_DIR)
        // 重打包APK
        var repackageApk = inputDir.resolve("temp-${originApk.name}")
        // 优化日志文件
        var optResultFile = outputDir.resolve("opt-result.txt")
        val config = config.get()
        try {
            // 清理资源目录
            unZipDir.deleteRecursively()
            repackageApk.delete()
            // 创建资源目录
            if (!outputResDir.exists()) {
                outputResDir.mkdirs()
            }
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
            val resourcesFile = unZipDir.listFilesFirst { it.name == "resources.arsc" }!!
            val resFile = unZipDir.listFilesFirst { it.name == "res" }!!
            // 获取解压后的 resources.arsc 文件
            val resourceFile = resourcesFile.inputStream().use {
                ResourceFile.fromInputStream(it)
            }
            val regexes = config.resNameOptWhiteRegexList.map {
                it.toRegex()
            }

            val resNameOptPlaceholder = config.resNameOptPlaceholder
            resourceFile.chunks.filterIsInstance<ResourceTableChunk>()
                .forEach { resourceTableChunk ->
                    if (config.repeatResOptEnable) {
                        // 找出res目录下重复的资源
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
                            optResultFile.appendText("res 资源保留: $retainPath\n res 资源删除: \n")
                            it.second.forEach {
                                // 待删除资源
                                val delPath =
                                    unZipDir.toURI().relativize(it.toURI()).path
                                // 常量字符串池中去查找
                                val stringIndex = stringPool.indexOf(delPath)
                                stringPool.setString(stringIndex, retainPath)
                                optResultFile.appendText("---path: $delPath size: ${it.length()}\n")
                                // 优化文件复制到缓存目录
                                it.copyTo(outputResDir.resolve("${it.name}"))
                                delSize += it.length()
                                it.delete()
                            }
                        }
                    }

                    if (config.resNameOptEnable) {
                        // 优化key常量池
                        resourceTableChunk.packages.forEach {
                            val keyStringPool = it.keyStringPool
                            val keyStringCount = keyStringPool.stringCount
                            for (index in 0 until keyStringCount) {
                                val key = keyStringPool.getString(index)
                                // 在白名单内无需修改
                                val regex = regexes.find { regex -> regex.matches(key) }
                                if (regex != null) {
                                    optResultFile.appendText("$key 被白名单 $regex 匹配\n")
                                    continue
                                }
                                keyStringPool.setString(index, resNameOptPlaceholder)
                            }
                        }
                    }
                }
            if (config.resNameOptEnable || config.repeatResOptEnable) {
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // 耗时
            val costText = "重复资源优化插件耗时: ${System.currentTimeMillis() - start} ms\n"
            val optSizeText = "重复资源优化插件优化总大小: ${delSize / 1024} KB\n"
            println(costText)
            println(optSizeText)
            optResultFile.appendText(optSizeText)
            optResultFile.appendText(costText)
        }
    }


    private fun File?.listFilesFirst(predicate: (File) -> Boolean): File? {
        return this?.listFiles()?.first(predicate) ?: null
    }

}