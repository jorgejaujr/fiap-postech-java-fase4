package br.com.fiap.projetofase4.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FormatadorDataTest {

    @Test
    @DisplayName("Data com offset -03:00 é exibida no mesmo horário")
    void deveFormatarDataComOffsetDeSaoPaulo() {
        assertEquals("10/11/2026 14:30", FormatadorData.paraExibicao("2026-11-10T14:30:00.123-03:00"));
    }

    @Test
    @DisplayName("Data em UTC (Z) é convertida para o horário de Brasília")
    void deveConverterDataUtcParaHorarioDeBrasilia() {
        assertEquals("10/11/2026 14:30", FormatadorData.paraExibicao("2026-11-10T17:30:00Z"));
    }

    @Test
    @DisplayName("Texto inválido devolve o próprio texto")
    void deveDevolverTextoOriginalQuandoInvalido() {
        assertEquals("ontem", FormatadorData.paraExibicao("ontem"));
    }

    @Test
    @DisplayName("Data nula ou em branco devolve 'não informada'")
    void deveInformarQuandoNaoHaData() {
        assertEquals("não informada", FormatadorData.paraExibicao(null));
        assertEquals("não informada", FormatadorData.paraExibicao("   "));
    }

    @Test
    @DisplayName("interpretar devolve vazio para texto inválido e valor para texto válido")
    void deveInterpretarSomenteTextoValido() {
        assertTrue(FormatadorData.interpretar("xyz").isEmpty());
        assertTrue(FormatadorData.interpretar(null).isEmpty());
        assertTrue(FormatadorData.interpretar("2026-11-10T14:30:00-03:00").isPresent());
    }

        @Test
    @DisplayName("normalizarDia aproveita só a data quando o campo traz o carimbo completo")
    void deveNormalizarDiaComCarimboCompleto() {
        assertEquals("2026-11-12", FormatadorData.normalizarDia("2026-11-12T15:46:12.123-03:00"));
    }

    @Test
    @DisplayName("normalizarDia mantém o dia que já está no formato yyyy-MM-dd")
    void deveManterDiaJaNormalizado() {
        assertEquals("2026-11-12", FormatadorData.normalizarDia("2026-11-12"));
        assertEquals("2026-11-12", FormatadorData.normalizarDia(" 2026-11-12 "));
    }

    @Test
    @DisplayName("normalizarDia devolve nulo para vazio e o texto original para valor desconhecido")
    void deveTratarDiaVazioEDesconhecido() {
        assertEquals(null, FormatadorData.normalizarDia(null));
        assertEquals(null, FormatadorData.normalizarDia("  "));
        assertEquals("abc", FormatadorData.normalizarDia("abc"));
    }
}