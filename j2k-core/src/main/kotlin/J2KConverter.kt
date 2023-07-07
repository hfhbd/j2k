import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.CoreProjectEnvironment
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.MetaLanguage
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.java.JavaParserDefinition
import com.intellij.mock.MockEditorFactory
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.module.EmptyModuleManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectManagerExImpl
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.psi.impl.smartPointers.SmartPointerAnchorProvider
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtil.FILE_LANGUAGE_LEVEL_KEY
import com.intellij.testFramework.registerExtension
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.compiler.IDELanguageSettingsProviderHelper
import org.jetbrains.kotlin.idea.compiler.configuration.*
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.listener.DefaultScriptChangeListener
import org.jetbrains.kotlin.idea.core.script.configuration.listener.ScriptChangeListener
import org.jetbrains.kotlin.idea.util.JavaClassBinary
import org.jetbrains.kotlin.idea.util.KotlinBinaryExtension
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverter
import org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverter.Companion.addImports
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper

private object ApplicationEnvironment {
    private val logger = object : DefaultLogger("") {
        override fun warn(message: String?, t: Throwable?) = Unit
        override fun error(message: Any?) = Unit
    }

    val coreApplicationEnvironment: CoreApplicationEnvironment by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        CoreApplicationEnvironment(Disposer.newDisposable()).apply {
            Logger.setFactory { logger }

            registerApplicationService(ProjectManager::class.java, ProjectManagerExImpl())

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
            CoreApplicationEnvironment.registerExtensionPoint(
                Extensions.getRootArea(),
                AdditionalLibraryRootsProvider.EP_NAME,
                AdditionalLibraryRootsProvider::class.java
            )
            CoreApplicationEnvironment.registerExtensionPoint(
                Extensions.getRootArea(),
                DirectoryIndexExcludePolicy.EP_NAME.name,
                DirectoryIndexExcludePolicy::class.java
            )
            CoreApplicationEnvironment.registerExtensionPoint(
                Extensions.getRootArea(), KotlinBinaryExtension.EP_NAME, KotlinBinaryExtension::class.java
            )

            CoreApplicationEnvironment.registerExtensionPoint(
                Extensions.getRootArea(), ScriptChangeListener.LISTENER.name, ScriptChangeListener::class.java
            )

            registerApplicationService(
                com.intellij.openapi.editor.EditorFactory::class.java, MockEditorFactory()
            )
        }
    }
}

private object FakeScriptConfigurationManager : ScriptConfigurationManager {
    override fun getAllScriptDependenciesSources(): Collection<VirtualFile> = emptyList()

    override fun getAllScriptDependenciesSourcesScope(): GlobalSearchScope = GlobalSearchScope.EMPTY_SCOPE

    override fun getAllScriptsDependenciesClassFiles(): Collection<VirtualFile> = emptyList()

    override fun getAllScriptsDependenciesClassFilesScope(): GlobalSearchScope {
        TODO("Not yet implemented")
    }

    override fun getConfiguration(file: KtFile): ScriptCompilationConfigurationWrapper? = null

    override fun getFirstScriptsSdk(): Sdk? = null

    @Deprecated("Use getScriptClasspath(KtFile) instead", ReplaceWith("emptyList()"))
    override fun getScriptClasspath(file: VirtualFile): List<VirtualFile> = emptyList()

    override fun getScriptClasspath(file: KtFile): List<VirtualFile> = emptyList()

    override fun getScriptDependenciesClassFilesScope(file: VirtualFile): GlobalSearchScope {
        TODO("Not yet implemented")
    }

    override fun getScriptSdk(file: VirtualFile): Sdk? = null

    override fun hasConfiguration(file: KtFile): Boolean = false

    override fun isConfigurationLoadingInProgress(file: KtFile): Boolean = false

    override fun loadPlugins() {}

    override fun updateScriptDefinitionReferences() {}
}

public class J2KConverter {
    private val projectEnvironment = CoreProjectEnvironment(
        ApplicationEnvironment.coreApplicationEnvironment.parentDisposable,
        ApplicationEnvironment.coreApplicationEnvironment,
    )

    private val converter: NewJavaToKotlinConverter

    init {
        projectEnvironment.registerProjectComponent(
            ProjectRootManager::class.java,
            ProjectRootManagerImpl(projectEnvironment.project),
        )

        projectEnvironment.project.registerService(
            ModuleManager::class.java, EmptyModuleManager(projectEnvironment.project)
        )
        projectEnvironment.project.registerService(
            ProjectRootManager::class.java, ProjectRootManagerImpl(projectEnvironment.project)
        )

        projectEnvironment.environment.registerFileType(JavaFileType.INSTANCE, "java")
        projectEnvironment.environment.registerParserDefinition(JavaParserDefinition())

        projectEnvironment.project.extensionArea.registerExtensionPoint(
            DirectoryIndexExcludePolicy.EP_NAME.name,
            DirectoryIndexExcludePolicy::class.qualifiedName!!,
            ExtensionPoint.Kind.INTERFACE
        )
        DirectoryIndexExcludePolicy.EP_NAME.getPoint(projectEnvironment.project).registerExtension(
            object : DirectoryIndexExcludePolicy {}, projectEnvironment.parentDisposable
        )

        projectEnvironment.project.extensionArea.registerExtensionPoint(
            KotlinBinaryExtension.EP_NAME.name,
            KotlinBinaryExtension::class.qualifiedName!!,
            ExtensionPoint.Kind.BEAN_CLASS
        )
        projectEnvironment.project.registerExtension(
            KotlinBinaryExtension.EP_NAME, JavaClassBinary(), projectEnvironment.parentDisposable
        )

        projectEnvironment.project.extensionArea.registerExtensionPoint(
            ScriptChangeListener.LISTENER.name,
            ScriptChangeListener::class.qualifiedName!!,
            ExtensionPoint.Kind.BEAN_CLASS
        )
        ScriptChangeListener.LISTENER.getPoint(projectEnvironment.project).registerExtension(
            DefaultScriptChangeListener(projectEnvironment.project), projectEnvironment.parentDisposable
        )

        projectEnvironment.project.registerService(KotlinPluginDisposable::class.java)

        projectEnvironment.project.registerService(
            ScriptConfigurationManager::class.java, FakeScriptConfigurationManager
        )

        projectEnvironment.project.registerService(
            IDELanguageSettingsProviderHelper::class.java, IDELanguageSettingsProviderHelper(projectEnvironment.project)
        )

        projectEnvironment.project.registerService(
            KotlinCommonCompilerArgumentsHolder::class.java,
            KotlinCommonCompilerArgumentsHolder(projectEnvironment.project)
        )

        converter = NewJavaToKotlinConverter(
            project = projectEnvironment.project,
            targetModule = null,
            settings = ConverterSettings.defaultSettings,
            oldConverterServices = EmptyJavaToKotlinServices
        )
    }

    public fun convert(
        files: Iterable<JavaFile>,
    ) {
        val psiFileFactory = PsiFileFactory.getInstance(projectEnvironment.project)
        val eventSystemEnabled = false
        val markAsCopy = false

        val fileList = files.map {
            val javaFile = psiFileFactory.createFileFromText(
                it.name, JavaLanguage.INSTANCE, it.content, eventSystemEnabled, markAsCopy
            )

            javaFile.putUserData(FILE_LANGUAGE_LEVEL_KEY, it.languageLevel)
            PsiFileFactoryImpl.markGenerated(javaFile)

            it to javaFile as PsiJavaFile
        }

        val results = converter.elementsToKotlin(
            fileList.map { it.second }, EmptyPostProcessor, null
        )

        for ((index, result) in results.results.withIndex()) {
            if (result != null) {
                val (javaFile, psi) = fileList[index]
                val ktFile = psiFileFactory.createFileFromText(
                    psi.name,
                    KotlinFileType.INSTANCE,
                    result.text,
                ) as KtFile
                ktFile.addImports(result.importsToAdd)
                javaFile.result = ktFile.text
            }
        }
    }
}

private object EmptyPostProcessor : WithProgressProcessor {
    override fun <T> process(action: () -> T): T = action()

    override fun <TInputItem, TOutputItem> processItems(
        fractionPortion: Double, inputItems: Iterable<TInputItem>, processItem: (TInputItem) -> TOutputItem
    ): List<TOutputItem> = inputItems.map(processItem)

    override fun updateState(phase: Int, subPhase: Int, subPhaseCount: Int, fileIndex: Int?, description: String) {}
    override fun updateState(fileIndex: Int?, phase: Int, description: String) {}
}
