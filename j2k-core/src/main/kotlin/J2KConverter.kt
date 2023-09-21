import com.intellij.core.*
import com.intellij.ide.highlighter.*
import com.intellij.ide.plugins.*
import com.intellij.lang.*
import com.intellij.lang.java.*
import com.intellij.lang.java.parser.*
import com.intellij.openapi.application.*
import com.intellij.openapi.extensions.*
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.util.*
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.*
import com.intellij.openapi.vfs.newvfs.persistent.*
import com.intellij.psi.*
import com.intellij.psi.search.*
import com.intellij.psi.stubs.*
import com.intellij.psi.util.PsiUtil.*
import com.intellij.util.indexing.*
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.idea.*
import org.jetbrains.kotlin.idea.core.script.*
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverter.Companion.addImports
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.scripting.resolve.*

private class ApplicationEnvironment {
    val disposer = Disposer.newDisposable()

    val kotlinCoreApplicationEnvironment = KotlinCoreApplicationEnvironment.create(
        parentDisposable = disposer, unitTestMode = false
    ).apply {
        registerApplicationService(PluginUtil::class.java, PluginUtilImpl())
        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            FileBasedIndexExtension.EXTENSION_POINT_NAME, FileBasedIndexExtension::class.java
        )

        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            StubIndexExtension.EP_NAME, StubIndexExtension::class.java
        )

        application.extensionArea.getExtensionPoint(
            StubIndexExtension.EP_NAME
        ).registerExtension(
            KotlinFullClassNameIndex, parentDisposable
        )
        registerApplicationService(StubIndex::class.java, StubIndexImpl())

        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            PersistentFsConnectionListener.EP_NAME, PersistentFsConnectionListener::class.java
        )
        registerApplicationService(ManagingFS::class.java, PersistentFSImpl())

        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            FileBasedIndexInfrastructureExtension.EP_NAME, FileBasedIndexInfrastructureExtension::class.java
        )
        registerApplicationService(FileBasedIndex::class.java, FileBasedIndexImpl())
        System.setProperty("indexing.separate.applying.values.from.counting", "false")
        System.setProperty("indexing.separate.applying.values.from.counting.for.content.independent.indexes", "false")

        ApplicationManager.setApplication(application, {
            CoreFileTypeRegistry().apply { registerFileType(JavaFileType.INSTANCE, "java") }
        }, parentDisposable)/*
        //
*//*
        registerApplicationService(ProjectManager::class.java, ProjectManagerExImpl())

        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            MetaLanguage.EP_NAME,
            MetaLanguage::class.java,
        )
        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            SmartPointerAnchorProvider.EP_NAME,
            SmartPointerAnchorProvider::class.java,
        )
        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            AdditionalLibraryRootsProvider.EP_NAME,
            AdditionalLibraryRootsProvider::class.java
        )
        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            KotlinBinaryExtension.EP_NAME, KotlinBinaryExtension::class.java
        )

        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            PsiAugmentProvider.EP_NAME, PsiAugmentProvider::class.java,
        )
        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            SuperMethodsSearch.EP_NAME,
            com.intellij.util.QueryExecutor::class.java,
        )

        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            StubIndexExtension.EP_NAME,
            StubIndexExtension::class.java
        )
        registerApplicationService(StubIndex::class.java, StubIndexImpl())

        CoreApplicationEnvironment.registerApplicationDynamicExtensionPoint(
            KotlinFullClassNameIndex.KEY.name,
            KotlinFullClassNameIndex::class.java
        )
        addExtension(
            ExtensionPointName(KotlinFullClassNameIndex.KEY.name),
            KotlinFullClassNameIndex
        )

        registerApplicationService(
            com.intellij.openapi.editor.EditorFactory::class.java, MockEditorFactory()
        )
        addExplicitExtension(LanguageASTFactory.INSTANCE, JavaLanguage.INSTANCE, JavaASTFactory())

         */
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

public class J2KConverter : AutoCloseable {
    private val env = ApplicationEnvironment()
    private val kotlinProjectEnv = KotlinCoreProjectEnvironment(
        env.kotlinCoreApplicationEnvironment.parentDisposable,
        env.kotlinCoreApplicationEnvironment,
    )

    private val converter: NewJavaToKotlinConverter

    init {
        kotlinProjectEnv.project.extensionArea.registerExtensionPoint(
            PsiElementFinder.EP.name,
            PsiElementFinder::class.qualifiedName!!,
            ExtensionPoint.Kind.INTERFACE,
        )
        kotlinProjectEnv.environment.registerParserDefinition(JavaParserDefinition())
        // LanguageParserDefinitions.INSTANCE.addExplicitExtension(KotlinLanguage.INSTANCE, KotlinParserDefinition())

        /*
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

        projectEnvironment.project.registerService(
            JavaFileManager::class.java,
            CoreJavaFileManager(projectEnvironment.project.service())
        )


        projectEnvironment.project.extensionArea.registerExtensionPoint(
            TreeGenerator.EP_NAME.name,
            TreeGenerator::class.qualifiedName!!,
            ExtensionPoint.Kind.INTERFACE
        )
        projectEnvironment.project.registerExtension(
            TreeGenerator.EP_NAME,
            JavaTreeGenerator(),
            projectEnvironment.parentDisposable
        )

        projectEnvironment.project.extensionArea.registerExtensionPoint(
            DirectoryIndexExcludePolicy.EP_NAME.name,
            DirectoryIndexExcludePolicy::class.qualifiedName!!,
            ExtensionPoint.Kind.INTERFACE
        )
        projectEnvironment.project.registerExtension(
            DirectoryIndexExcludePolicy.EP_NAME,
            object : DirectoryIndexExcludePolicy {},
            projectEnvironment.parentDisposable
        )

        projectEnvironment.project.registerService(
            PsiElementFactory::class.java,
            PsiElementFactoryImpl(projectEnvironment.project)
        )
        projectEnvironment.project.registerService(
            com.intellij.psi.JvmPsiConversionHelper::class.java,
            JvmPsiConversionHelperImpl()
        )
        projectEnvironment.project.registerService(
            com.intellij.psi.JavaPsiFacade::class.java,
            JavaPsiFacadeImpl(projectEnvironment.project)
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
        projectEnvironment.project.registerExtension(
            ScriptChangeListener.LISTENER,
            DefaultScriptChangeListener(projectEnvironment.project),
            projectEnvironment.parentDisposable
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

        projectEnvironment.project.registerService(
            com.intellij.psi.PsiResolveHelper::class.java,
            PsiResolveHelperImpl(projectEnvironment.project)
        )
        projectEnvironment.project.registerService(
            JavaResolveCache::class.java,
            JavaResolveCache(projectEnvironment.project)
        )
*/
        converter = NewJavaToKotlinConverter(
            project = kotlinProjectEnv.project,
            targetModule = null,
            settings = ConverterSettings.defaultSettings,
            oldConverterServices = EmptyJavaToKotlinServices
        )
    }

    public fun convert(
        files: Iterable<JavaFile>,
    ) {
        val psiFileFactory = PsiFileFactory.getInstance(kotlinProjectEnv.project)
        val eventSystemEnabled = false
        val markAsCopy = false

        val fileList = files.map {
            val javaFile = psiFileFactory.createFileFromText(
                it.fileName, JavaLanguage.INSTANCE, it.content, eventSystemEnabled, markAsCopy
            )

            javaFile.putUserData(FILE_LANGUAGE_LEVEL_KEY, it.languageLevel)

            val builder: PsiBuilder = PsiBuilderFactory.getInstance().createBuilder(
                kotlinProjectEnv.project, JavaParserDefinition.createLexer(it.languageLevel), javaFile.node
            )
            JavaParserUtil.setLanguageLevel(builder, it.languageLevel)

            it to javaFile as PsiJavaFile
        }

        val index = FileBasedIndex.getInstance() as FileBasedIndexEx
        index.loadIndexes()

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

    override fun close() {
        Disposer.dispose(env.disposer)
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
