import com.intellij.pom.java.LanguageLevel

public class JavaFile(
    public val name: String,
    public val languageLevel: LanguageLevel,
    public val content: CharSequence
) {
    public lateinit var result: String
        internal set
}
