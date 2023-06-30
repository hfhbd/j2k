import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.JKPostProcessingTarget
import org.jetbrains.kotlin.j2k.PostProcessor
import java.io.File
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals

class J2KTest {
    @Test
    fun testing() {
        val files = Files.createTempDirectory("j2k").toFile()
        val myJavaClass = File(files, "MyJavaClass.java")
        myJavaClass.writeText(
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
        
        val env = J2KEnvironment(listOf(myJavaClass))
        val result = env.convert(object :PostProcessor {
            override val phasesCount: Int = 1

            override fun doAdditionalProcessing(
                target: JKPostProcessingTarget,
                converterContext: ConverterContext?,
                onPhaseChanged: ((Int, String) -> Unit)?
            ) {
                
            }

            override fun insertImport(file: org.jetbrains.kotlin.psi.KtFile, fqName: org.jetbrains.kotlin.name.FqName) {
                
            }
        })
        assertEquals("", myJavaClass.readText())
        assertEquals(emptyList(), result.results)
    }
}
