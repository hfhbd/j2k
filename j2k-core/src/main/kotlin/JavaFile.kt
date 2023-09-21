import com.intellij.pom.java.LanguageLevel
import org.intellij.lang.annotations.Language

public class JavaFile(
    name: String,
    packageName: String,
    public val languageLevel: LanguageLevel,
    @Language("java") public val content: CharSequence
) {
    public val fileName: String = packageName.replace(".", "/") + "/$name.java"

    public var result: String? = null
        internal set
}
