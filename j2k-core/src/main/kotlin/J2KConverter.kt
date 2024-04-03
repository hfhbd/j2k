import com.intellij.core.*
import com.intellij.ide.highlighter.*
import com.intellij.ide.plugins.*
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.*
import com.intellij.lang.java.*
import com.intellij.lang.java.parser.*
import com.intellij.mock.MockEditorFactory
import com.intellij.mock.MockModule
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.*
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.*
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.project.*
import com.intellij.openapi.project.impl.*
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.CustomEntityProjectModelInfoProvider
import com.intellij.openapi.roots.impl.ProjectFileIndexImpl
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.*
import com.intellij.openapi.vfs.newvfs.persistent.*
import com.intellij.psi.*
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.stubs.*
import com.intellij.psi.util.PsiUtil.FILE_LANGUAGE_LEVEL_KEY
import com.intellij.testFramework.*
import com.intellij.util.indexing.*
import com.intellij.util.indexing.events.*
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.core.fileIndex.impl.*
import org.jetbrains.kotlin.caches.resolve.*
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.*
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.*
import org.jetbrains.kotlin.idea.caches.KotlinShortNamesCache
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.idea.caches.resolve.KotlinCacheServiceImpl
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.configuration.listener.ScriptChangeListener
import org.jetbrains.kotlin.idea.facet.KotlinFacetSettingsProviderImpl
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.EmptyJavaToKotlinServices
import org.jetbrains.kotlin.j2k.JKMultipleFilesPostProcessingTarget
import org.jetbrains.kotlin.j2k.WithProgressProcessor
import org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverter
import org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverter.Companion.addImports
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Files
import kotlin.io.path.absolutePathString

public class J2KConverter : AutoCloseable {
    init {
        val tmp = Files.createTempDirectory("ideaHack")
        System.setProperty("idea.home.path", tmp.absolutePathString())
        System.setProperty("java.awt.headless", "true")
        System.setProperty("psi.sleep.in.validity.check", "1")
        System.setProperty("idea.use.in.memory.file.based.index", "true")
        //System.setProperty("kotlin.scripting.fs.roots.storage.enabled", "false")
    }

    private val disposer = Disposer.newDisposable()

    private val kotlinProjectEnv = KotlinCoreProjectEnvironment(
        disposable = disposer,
        applicationEnvironment = KotlinCoreApplicationEnvironment.create(
            parentDisposable = disposer,
            unitTestMode = true,
        ).apply {
            registerApplicationService(PluginUtil::class.java, PluginUtilImpl())
            registerApplicationService(AsyncExecutionService::class.java, AsyncExecutionServiceImpl())
            registerApplicationService(TransactionGuard::class.java, TransactionGuardImpl())
            registerApplicationService(ProjectManager::class.java, ProjectManagerImpl())


            CoreApplicationEnvironment.registerApplicationExtensionPoint(
                FileBasedIndexExtension.EXTENSION_POINT_NAME, FileBasedIndexExtension::class.java
            )

            CoreApplicationEnvironment.registerApplicationExtensionPoint(
                StubElementTypeHolderEP.EP_NAME, StubElementTypeHolderEP::class.java
            )
            registerApplicationService(SerializationManagerEx::class.java, SerializationManagerImpl())

            registerApplicationService(StubUpdatableIndexFactory::class.java, StubUpdatableIndexFactoryImpl())

            CoreApplicationEnvironment.registerApplicationExtensionPoint(
                GlobalIndexFilter.EP_NAME, GlobalIndexFilter::class.java
            )

            CoreApplicationEnvironment.registerApplicationExtensionPoint(
                StubIndexExtension.EP_NAME, StubIndexExtension::class.java
            )

            application.extensionArea.getExtensionPoint<StubIndexExtension<*, *>>(
                StubIndexExtension.EP_NAME
            ).apply {
                registerExtension(KotlinAnnotationsIndex, parentDisposable)
                registerExtension(KotlinClassShortNameIndex, parentDisposable)
                registerExtension(KotlinExactPackagesIndex, parentDisposable)
                registerExtension(KotlinFileFacadeClassByPackageIndex, parentDisposable)
                registerExtension(KotlinFileFacadeFqNameIndex, parentDisposable)
                registerExtension(KotlinFileFacadeShortNameIndex, parentDisposable)
                registerExtension(KotlinFullClassNameIndex, parentDisposable)
                registerExtension(KotlinTopLevelTypeAliasFqNameIndex, parentDisposable)
                registerExtension(JavaFullClassNameIndex(), parentDisposable)
                registerExtension(KotlinTopLevelClassByPackageIndex, parentDisposable)
            }
            registerApplicationService(StubIndex::class.java, StubIndexImpl())

            registerApplicationService(FileBasedIndex::class.java, FileBasedIndexImpl())
            application.extensionArea.getExtensionPoint(
                FileBasedIndexExtension.EXTENSION_POINT_NAME
            ).registerExtension(StubUpdatingIndex(), parentDisposable)
            CoreApplicationEnvironment.registerApplicationExtensionPoint(
                AsyncEventSupport.EP_NAME, AsyncFileListener::class.java
            )
            application.extensionArea.getExtensionPoint(AsyncEventSupport.EP_NAME)
                .registerExtension(ChangedFilesCollector(), parentDisposable)
            registerApplicationService(AsyncFileListener::class.java, ChangedFilesCollector())

            CoreApplicationEnvironment.registerApplicationExtensionPoint(
                PersistentFsConnectionListener.EP_NAME, PersistentFsConnectionListener::class.java
            )
            registerApplicationService(ManagingFS::class.java, PersistentFSImpl())

            registerApplicationService(EditorFactory::class.java, MockEditorFactory())

            CoreApplicationEnvironment.registerApplicationExtensionPoint(
                FileBasedIndexInfrastructureExtension.EP_NAME, FileBasedIndexInfrastructureExtension::class.java
            )

            System.setProperty("indexing.separate.applying.values.from.counting", "false")
            System.setProperty(
                "indexing.separate.applying.values.from.counting.for.content.independent.indexes",
                "false"
            )
            Registry.get("kotlin.scripting.fs.roots.storage.enabled").setValue(false)

            CoreApplicationEnvironment.registerApplicationExtensionPoint(
                WorkspaceFileIndexImpl.EP_NAME,
                WorkspaceFileIndexContributor::class.java
            )
            CoreApplicationEnvironment.registerApplicationExtensionPoint(
                CustomEntityProjectModelInfoProvider.EP,
                CustomEntityProjectModelInfoProvider::class.java
            )

            ApplicationManager.setApplication(application, {
                CoreFileTypeRegistry().apply {
                    registerFileType(JavaFileType.INSTANCE, "java")
                    registerFileType(KotlinFileType.INSTANCE, "kt")
                }
            }, parentDisposable)
        },
    )

    private val converter: NewJavaToKotlinConverter

    init {
        kotlinProjectEnv.project.extensionArea.registerExtensionPoint(
            PsiElementFinder.EP.name,
            PsiElementFinder::class.qualifiedName!!,
            ExtensionPoint.Kind.INTERFACE,
        )
        kotlinProjectEnv.environment.registerParserDefinition(JavaParserDefinition())
        kotlinProjectEnv.environment.registerParserDefinition(KotlinParserDefinition())

        kotlinProjectEnv.project.registerService(KotlinPluginDisposable::class.java, KotlinPluginDisposable())

        kotlinProjectEnv.project.extensionArea.registerExtensionPoint(
            ScriptChangeListener.LISTENER.name, ScriptChangeListener::class.qualifiedName!!,
            ExtensionPoint.Kind.BEAN_CLASS
        )
        kotlinProjectEnv.project.registerExtension(
            ScriptChangeListener.LISTENER,
            FakeScriptChangeListener(kotlinProjectEnv.project),
            disposer
        )
        kotlinProjectEnv.project.registerService(
            ScriptConfigurationManager::class.java,
            FakeScriptConfigurationManager()
        )
        kotlinProjectEnv.project.registerService(PropertiesComponent::class.java, FakePropertiesComponent())
        KotlinMultiplatformAnalysisModeComponent.setMode(
            kotlinProjectEnv.project,
            KotlinMultiplatformAnalysisModeComponent.Mode.COMPOSITE
        )
        kotlinProjectEnv.project.registerService(
            WorkspaceFileIndex::class.java,
            WorkspaceFileIndexImpl(kotlinProjectEnv.project)
        )
        kotlinProjectEnv.project.registerService(
            ProjectFileIndex::class.java,
            ProjectFileIndexImpl(kotlinProjectEnv.project)
        )
        kotlinProjectEnv.project.registerService(
            LibraryModificationTracker::class.java,
            LibraryModificationTracker(kotlinProjectEnv.project)
        )
        kotlinProjectEnv.project.registerService(
            ScriptDependenciesModificationTracker::class.java,
            ScriptDependenciesModificationTracker()
        )
        kotlinProjectEnv.project.registerService(
            KotlinCacheService::class.java,
            KotlinCacheServiceImpl(kotlinProjectEnv.project)
        )

        kotlinProjectEnv.project.registerService(
            KotlinFacetSettingsProvider::class.java,
            KotlinFacetSettingsProviderImpl(kotlinProjectEnv.project)
        )
        kotlinProjectEnv.project.registerService(
            KotlinCommonCompilerArgumentsHolder::class.java,
            KotlinCommonCompilerArgumentsHolder(kotlinProjectEnv.project)
        )
        kotlinProjectEnv.project.registerService(
            LanguageVersionSettingsProvider::class.java,
            LanguageVersionSettingsProvider(kotlinProjectEnv.project)
        )
        kotlinProjectEnv.project.registerService(PathMacroManager::class.java, PathMacroManager(null))

        kotlinProjectEnv.project.registerService(
            PsiShortNamesCache::class.java,
            KotlinShortNamesCache(kotlinProjectEnv.project)
        )

        converter = NewJavaToKotlinConverter(
            project = kotlinProjectEnv.project,
            targetModule = MockModule(kotlinProjectEnv.project, disposer),
            settings = ConverterSettings.defaultSettings,
            oldConverterServices = EmptyJavaToKotlinServices
        )
    }

    public fun convert(
        files: Iterable<JavaFile>,
        apiVersion: ApiVersion,
        languageVersion: LanguageVersion,
    ): List<KotlinFile> = converter.targetModule!!.withLanguageVersionSettings(object : LanguageVersionSettings {
        override val apiVersion = apiVersion
        override val languageVersion = languageVersion
        override fun getFeatureSupport(feature: LanguageFeature) = LanguageFeature.State.DISABLED
        override fun <T> getFlag(flag: AnalysisFlag<T>) = flag.defaultValue
        override fun isPreRelease() = false
    }) {
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
        RebuildStatus.registerIndex(StubUpdatingIndex.INDEX_ID)
        initializeStubIndexes()
        index.waitUntilIndicesAreInitialized()

        val results = converter.elementsToKotlin(
            fileList.map { it.second }, EmptyPostProcessor, null
        )

        val ktFiles = results.results.mapIndexedNotNull { index, result ->
            if (result != null) {
                val (_, psi) = fileList[index]
                val ktFile = psiFileFactory.createFileFromText(
                    psi.name,
                    KotlinFileType.INSTANCE,
                    result.text,
                ) as KtFile
                ktFile.addImports(result.importsToAdd)
                ktFile
            } else null
        }

        val files = ktFiles.map { ktFile ->
            KotlinFile(ktFile.name.dropLast(5).substringAfterLast("/"), ktFile.packageFqName.asString(), ktFile.text.replace(Regex("/\\*@@.*@@\\*/"), ""))
        }
        files
    }

    override fun close() {
        Extensions.setRootArea(Extensions.getRootArea() as ExtensionsAreaImpl, disposer)
        Disposer.dispose(disposer)
    }
}

private class FakeScriptConfigurationManager : ScriptConfigurationManager {
    override fun getAllScriptDependenciesSources(): Collection<VirtualFile> {
        TODO("Not yet implemented")
    }

    override fun getAllScriptDependenciesSourcesScope(): GlobalSearchScope {
        TODO("Not yet implemented")
    }

    override fun getAllScriptSdkDependenciesSources(): Collection<VirtualFile> {
        TODO("Not yet implemented")
    }

    override fun getAllScriptsDependenciesClassFiles(): Collection<VirtualFile> {
        TODO("Not yet implemented")
    }

    override fun getAllScriptsDependenciesClassFilesScope(): GlobalSearchScope {
        TODO("Not yet implemented")
    }

    override fun getAllScriptsSdkDependenciesClassFiles(): Collection<VirtualFile> {
        TODO("Not yet implemented")
    }

    override fun getConfiguration(file: KtFile): Nothing {
        TODO("Not yet implemented")
    }

    override fun getFirstScriptsSdk(): Nothing? = null

    override fun getScriptClasspath(file: VirtualFile): List<VirtualFile> {
        TODO("Not yet implemented")
    }

    override fun getScriptClasspath(file: KtFile): List<VirtualFile> {
        TODO("Not yet implemented")
    }

    override fun getScriptDependenciesClassFiles(file: VirtualFile): Collection<VirtualFile> {
        TODO("Not yet implemented")
    }

    override fun getScriptDependenciesClassFilesScope(file: VirtualFile): GlobalSearchScope {
        TODO("Not yet implemented")
    }

    override fun getScriptDependenciesSourceFiles(file: VirtualFile): Collection<VirtualFile> {
        TODO("Not yet implemented")
    }

    override fun getScriptSdk(file: VirtualFile): Sdk? {
        TODO("Not yet implemented")
    }

    override fun getScriptSdkDependenciesClassFiles(file: VirtualFile): Collection<VirtualFile> {
        TODO("Not yet implemented")
    }

    override fun getScriptSdkDependenciesSourceFiles(file: VirtualFile): Collection<VirtualFile> {
        TODO("Not yet implemented")
    }

    override fun hasConfiguration(file: KtFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun isConfigurationLoadingInProgress(file: KtFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun loadPlugins() {
        TODO("Not yet implemented")
    }

    override fun updateScriptDefinitionReferences() {
        TODO("Not yet implemented")
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

private class FakeScriptChangeListener(project: Project) : ScriptChangeListener(project) {
    override fun editorActivated(vFile: VirtualFile) {}
    override fun documentChanged(vFile: VirtualFile) {}

    override fun isApplicable(vFile: VirtualFile): Boolean = false
}

private class FakePropertiesComponent : PropertiesComponent() {
    val props = mutableMapOf<String, Any?>()
    override fun unsetValue(name: String) {
        props.remove(name)
    }

    override fun isValueSet(name: String): Boolean = props.containsKey(name)

    override fun getValue(name: String): String? = props[name] as String?

    override fun setValue(name: String, value: String?) {
        props[name] = value
    }

    override fun setValue(name: String, value: String?, defaultValue: String?) {
        if (props[name] == defaultValue) {
            props.remove(name)
        } else {
            props[name] = value
        }
    }

    override fun setValue(name: String, value: Float, defaultValue: Float) {
        if (props[name] == defaultValue) {
            props.remove(name)
        } else {
            props[name] = value
        }
    }

    override fun setValue(name: String, value: Int, defaultValue: Int) {
        if (props[name] == defaultValue) {
            props.remove(name)
        } else {
            props[name] = value
        }
    }

    override fun setValue(name: String, value: Boolean, defaultValue: Boolean) {
        if (props[name] == defaultValue) {
            props.remove(name)
        } else {
            props[name] = value
        }
    }

    override fun getValues(name: String): Array<String> {
        return emptyArray()
    }

    override fun setValues(name: String, values: Array<out String>?) {
        TODO("Not yet implemented")
    }

    override fun getList(name: String): MutableList<String>? {
        TODO("Not yet implemented")
    }

    override fun setList(name: String, values: MutableCollection<String>?) {
        TODO("Not yet implemented")
    }
}
