import com.intellij.pom.java.LanguageLevel
import org.jetbrains.kotlin.config.*
import kotlin.test.Test
import kotlin.test.assertEquals

class J2KTest {
    private val myJavaClass = JavaFile(
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

    private val myOtherJavaClass = JavaFile(
        name = "MyOtherJavaClass",
        packageName = "bar",
        languageLevel = LanguageLevel.JDK_1_8,
        content = """package bar;
               
               public class MyOtherJavaClass {
                 public void foo() {
                   new foo.MyJavaClass().testing();
                 }
               }
               
            """.trimIndent()
    )

    @Test
    fun singleFile() {
        val kotlinFiles = J2KConverter().use {
            it.convert(
                listOf(myJavaClass),
                apiVersion = ApiVersion.KOTLIN_1_9,
                languageVersion = LanguageVersion.KOTLIN_1_9,
            )
        }

        assertEquals(
            KotlinFile(
                "MyJavaClass", "foo",
                """package foo
 class MyJavaClass () {
     fun testing() {
        System.out.println("Hello World")
    }
}

        """.trimIndent()
            ), kotlinFiles.single()
        )
    }

    @Test
    fun twoFiles() {
        val kotlinFiles = J2KConverter().use {
            it.convert(
                listOf(myJavaClass, myOtherJavaClass),
                apiVersion = ApiVersion.KOTLIN_1_9,
                languageVersion = LanguageVersion.KOTLIN_1_9,
            )
        }

        assertEquals(
            listOf(
                KotlinFile(
                    "MyJavaClass", "foo",
                    """package foo
 class MyJavaClass () {
     fun testing() {
        System.out.println("Hello World")
    }
}

        """.trimIndent()
                )
            ),
            kotlinFiles
        )
    }
}
