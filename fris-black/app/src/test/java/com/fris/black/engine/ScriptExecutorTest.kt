package com.fris.black.engine

import com.fris.black.script.FrisParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FakeDriver(var failOn: String? = null) : UiDriver {
    val done = mutableListOf<String>()
    override suspend fun openApp(a: String) = true.also { done += "open:$a" }
    override suspend fun clickText(t: String): Boolean {
        done += "click:$t"
        return failOn != t
    }
    override suspend fun clickId(id: String) = true.also { done += "id:$id" }
    override suspend fun clickXy(x: Int, y: Int) = true
    override suspend fun typeText(t: String) = true.also { done += "type:$t" }
    override suspend fun clearText() = true
    override suspend fun scrollDown() = true
    override suspend fun scrollUp() = true
    override suspend fun swipe(dir: String) = true
    override suspend fun waitAppear(t: String, s: Int) = true
    override suspend fun waitGone(t: String, s: Int) = true
    override suspend fun back() = true
    override suspend fun home() = true
    override suspend fun closeKeyboard() = true
    override fun toast(t: String) { done += "toast:$t" }
    override suspend fun confirm(q: String) = true
    override fun isConfirmedBranch() = null
    override fun log(t: String) {}
}

class ScriptExecutorTest {

    @Test fun rodaSequenciaAteFim() = runTest {
        val r = FrisParser.parse(
            "QUANDO chegar às 08:00\nENTÃO abrir YouTube\nENTÃO clicar em \"Pesquisar\"\nENTÃO registrar \"ok\"\n"
        )
        assertTrue(r.errors.isEmpty())
        val d = FakeDriver()
        val ex = ScriptExecutor(d)
        val out = ex.run(r.script!!)
        assertTrue(out.success)
        assertEquals(listOf("open:YouTube", "click:Pesquisar"), d.done.take(2))
    }

    @Test fun paraNoPrimeiroErro() = runTest {
        val r = FrisParser.parse(
            "QUANDO chegar às 08:00\nENTÃO clicar em \"A\"\nENTÃO clicar em \"B\"\nENTÃO clicar em \"C\"\n"
        )
        assertTrue(r.errors.isEmpty())
        val d = FakeDriver(failOn = "B")
        val ex = ScriptExecutor(d)
        val out = ex.run(r.script!!)
        assertFalse(out.success)
        assertEquals(3, out.stoppedAtLine)
        assertFalse(d.done.any { it == "click:C" })
    }

    @Test fun runIdUnico() = runTest {
        val r = FrisParser.parse("QUANDO chegar às 08:00\nENTÃO esperar 1 segundos\n")
        val ex = ScriptExecutor(FakeDriver())
        val a = ex.run(r.script!!)
        val b = ex.run(r.script!!)
        assertNotEquals(a.runId, b.runId)
    }
}
