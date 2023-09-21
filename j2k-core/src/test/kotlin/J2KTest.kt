import com.intellij.pom.java.LanguageLevel
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals

class J2KTest {
    @Test
    fun testing() {
        val myJavaClass = JavaFile(
            name = "MyJavaClass",
            packageName = "foo",
            languageLevel = LanguageLevel.JDK_1_8,
            content = """package foo;
               
               public class MyJavaClass {
                 public void testing() {
                   System.out.println("Hello World");
                 }
               }
               
            """.trimIndent()
        )

        val tmp = Files.createTempDirectory("ideaHack")
        System.setProperty("idea.home.path", tmp.absolutePathString())
        System.setProperty("java.awt.headless", "true")
        System.setProperty("psi.sleep.in.validity.check", "1")
        //System.setProperty("kotlin.scripting.fs.roots.storage.enabled", "false")

        val converter = J2KConverter()
        converter.convert(listOf(myJavaClass))

        assertEquals("", myJavaClass.result)
        converter.close()
    }
}
