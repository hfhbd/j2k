import com.intellij.pom.java.LanguageLevel
import java.io.File
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals

class J2KTest {
    @Test
    fun testing() {
        val myJavaClass = JavaFile(
            "MyJavaClass.java",
            LanguageLevel.JDK_1_8,
            //language=java
            """
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
        
        val env = J2KConverter()

        env.convert(listOf(myJavaClass))

        assertEquals("", myJavaClass.result)
    }
}
