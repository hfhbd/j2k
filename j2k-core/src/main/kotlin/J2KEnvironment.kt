import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.CoreProjectEnvironment
import com.intellij.lang.MetaLanguage
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.roots.impl.DirectoryIndexImpl
import com.intellij.openapi.roots.impl.ProjectFileIndexImpl
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.smartPointers.SmartPointerAnchorProvider
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.EmptyJavaToKotlinServices
import org.jetbrains.kotlin.j2k.FilesResult
import org.jetbrains.kotlin.j2k.PostProcessor
import org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverter
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import com.intellij.lang.ParserDefinition
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import java.io.File

private object ApplicationEnvironment {
    private val logger = object : DefaultLogger("") {
        override fun warn(message: String?, t: Throwable?) = Unit
        override fun error(message: Any?) = Unit
    }

    val coreApplicationEnvironment: CoreApplicationEnvironment by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        CoreApplicationEnvironment(Disposer.newDisposable()).apply {
            Logger.setFactory { logger }

            CoreApplicationEnvironment.registerExtensionPoint(
                Extensions.getRootArea(),
                MetaLanguage.EP_NAME,
                MetaLanguage::class.java,
            )
            CoreApplicationEnvironment.registerExtensionPoint(
                Extensions.getRootArea(),
                SmartPointerAnchorProvider.EP_NAME,
                SmartPointerAnchorProvider::class.java,
            )
        }
    }
}

public class J2KEnvironment(
    sourceFolders: List<File>,
    settings: ConverterSettings = ConverterSettings.defaultSettings
) {
    private val fileIndex: CoreFileIndex

    private val projectEnvironment = CoreProjectEnvironment(
        ApplicationEnvironment.coreApplicationEnvironment.parentDisposable,
        ApplicationEnvironment.coreApplicationEnvironment,
    )

    private val localFileSystem: VirtualFileSystem =
        VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)

    private val converter: NewJavaToKotlinConverter

    init {
        projectEnvironment.registerProjectComponent(
            ProjectRootManager::class.java,
            ProjectRootManagerImpl(projectEnvironment.project),
        )

        projectEnvironment.project.registerService(
            DirectoryIndex::class.java,
            DirectoryIndexImpl(projectEnvironment.project),
        )

        fileIndex = CoreFileIndex(sourceFolders, localFileSystem, projectEnvironment.project)
        projectEnvironment.project.registerService(ProjectFileIndex::class.java, fileIndex)

        projectEnvironment.environment.registerFileType(KotlinFileType.INSTANCE as LanguageFileType, KotlinFileType.EXTENSION)
        projectEnvironment.environment.registerParserDefinition(KotlinParserDefinition.instance as ParserDefinition)
        
        converter = NewJavaToKotlinConverter(
            project = projectEnvironment.project,
            targetModule = null,
            settings = settings,
            oldConverterServices = EmptyJavaToKotlinServices
        )
    }

    public fun convert(postProcessor: PostProcessor): FilesResult {
        val psiManager = PsiManager.getInstance(projectEnvironment.project)
        val fileList = buildList {
            fileIndex.iterateContent { file ->
                val psiFile = psiManager.findFile(file) ?: return@iterateContent true
                psiFile as com.intellij.psi.PsiJavaFile
                add(psiFile)
                return@iterateContent true
            }
        }
        return converter.filesToKotlin(
            fileList,
            postProcessor = postProcessor,
            progress = EmptyProgressIndicator()
        )
    }
}

private class CoreFileIndex(
    val sourceFolders: List<File>,
    private val localFileSystem: VirtualFileSystem,
    project: Project,
) : ProjectFileIndexImpl(project) {
    override fun iterateContent(iterator: ContentIterator): Boolean {
        return sourceFolders.all {
            val file = localFileSystem.findFileByPath(it.absolutePath)
                ?: throw NullPointerException("File ${it.absolutePath} not found")
            iterateContentUnderDirectory(file, iterator)
        }
    }

    override fun iterateContentUnderDirectory(file: VirtualFile, iterator: ContentIterator): Boolean {
        if (file.isDirectory) {
            file.children.forEach { if (!iterateContentUnderDirectory(it, iterator)) return false }
            return true
        }
        return iterator.processFile(file)
    }
}
