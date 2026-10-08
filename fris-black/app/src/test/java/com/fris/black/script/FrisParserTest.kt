package com.fris.black.script

import org.junit.Assert.*
import org.junit.Test

class FrisParserTest {

    @Test fun exemploNoiteValido() {
        val src = """
            ALVO YouTube

            QUANDO chegar às 23:00
            SE for depois das 22:00
            ENTÃO abrir YouTube
            ENTÃO esperar até aparecer "Pesquisar" em 10s
            ENTÃO clicar em "Pesquisar"
            ENTÃO digitar "música para dormir"
            ENTÃO esperar 3 segundos
            ENTÃO rolar para baixo
            ENTÃO mostrar aviso "Modo noite ativado"
        """.trimIndent()
        val r = FrisParser.parse(src)
        assertTrue("erros: ${r.errors}", r.errors.isEmpty())
        val script = r.script
        assertNotNull(script)
        assertEquals("YouTube", script!!.targetApp)
        assertEquals(7, script.steps.size)
    }

    @Test fun erroComLinha() {
        val r = FrisParser.parse("QUANDO chegar às 08:00\nENTÃO voar para a lua\n")
        assertTrue(r.errors.isNotEmpty())
        assertTrue(r.errors.any { it.line == 2 })
    }

    @Test fun semGatilhoFalha() {
        val r = FrisParser.parse("ENTÃO abrir YouTube\n")
        assertTrue(r.errors.any { it.message.contains("gatilho") })
    }

    @Test fun limite30Passos() {
        val sb = StringBuilder("QUANDO chegar às 08:00\n")
        repeat(35) { sb.append("ENTÃO esperar 1 segundos\n") }
        val r = FrisParser.parse(sb.toString())
        assertTrue(r.errors.any { it.message.contains("30 passos") })
    }

    @Test fun acaoSensiveExigeConfirmar() {
        val r = FrisParser.parse("QUANDO chegar às 08:00\nENTÃO clicar em \"Enviar pix\"\n")
        assertTrue(r.errors.any { it.message.contains("CONFIRMAR") })
    }

    @Test fun esperaMaxima60s() {
        val r = FrisParser.parse("QUANDO chegar às 08:00\nENTÃO esperar 999 segundos\n")
        assertTrue(r.errors.isNotEmpty())
    }

    @Test fun repeatMax5() {
        val r = FrisParser.parse("QUANDO chegar às 08:00\nENTÃO repetir bloco até 99 vezes\n")
        assertTrue(r.errors.any { it.message.contains("5x") })
    }

    @Test fun whatsappExemploValido() {
        val src = """
            ALVO WhatsApp
            QUANDO chegar às 08:00
            SE for segunda, terça, quarta, quinta, sexta
            ENTÃO abrir WhatsApp
            ENTÃO esperar até aparecer "Conversas" em 10s
            ENTÃO clicar em "Novo chat"
            ENTÃO digitar "João"
            ENTÃO confirmar "Enviar mensagem para João?"
            ENTÃO clicar em "Enviar"
            ENTÃO registrar "Mensagem enviada"
        """.trimIndent()
        val r = FrisParser.parse(src)
        assertTrue("erros: ${r.errors}", r.errors.isEmpty())
    }
}
