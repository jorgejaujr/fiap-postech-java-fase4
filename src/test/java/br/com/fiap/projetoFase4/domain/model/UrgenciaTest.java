package br.com.fiap.projetoFase4.domain.model;

import br.com.fiap.projetofase4.domain.exception.AvaliacaoInvalidaException;
import br.com.fiap.projetofase4.domain.model.Urgencia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrgenciaTest {
    /*CRITICA (Nota 0 até 3) */

    @Test
    @DisplayName("Nota 0 deve retornar CRITICA")
    void deveRetornarCriticaParaNota0() {
        assertEquals(Urgencia.CRITICA, Urgencia.deNota(0));
    }

    @Test
    @DisplayName("Nota 3 (limite crítico) deve retornar CRITICA")
    void deveRetornarCriticaParaNota3() {
        assertEquals(Urgencia.CRITICA, Urgencia.deNota(3));
    }

    /*MEDIA (Nota 4 até 6) */

    @Test
    @DisplayName("Nota 4 (limite acima do crítico) deve retornar MEDIA")
    void deveRetornarMediaParaNota4() {
        assertEquals(Urgencia.MEDIA, Urgencia.deNota(4));
    }

    @Test
    @DisplayName("Nota 6 (limite abaixo do baixa) deve retornar MEDIA")
    void deveRetornarMediaParaNota6() {
        assertEquals(Urgencia.MEDIA, Urgencia.deNota(6));
    }

    /* BAIXA (7 até 10) */

    @Test
    @DisplayName("Nota 7 (limite acima de média) deve retornar BAIXA")
    void deveRetornarBaixaParaNota7() {
        assertEquals(Urgencia.BAIXA, Urgencia.deNota(7));
    }
    @Test
    @DisplayName("Nota 10 (MÁXIMA) deve retornar BAIXA")
    void deveRetornarBaixaParaNota10() {
        assertEquals(Urgencia.BAIXA, Urgencia.deNota(10));
    }

    /* exceções, notas inválidas */

    @Test
    @DisplayName("Nota -1 deve lançar AvaliacaoInvalidaException")
    void deveLancarExcecaoParaNotaNegativa() {
        assertThrows(AvaliacaoInvalidaException.class, () -> Urgencia.deNota(-1));
    }

    @Test
    @DisplayName("Nota 11 deve lançar AvaliacaoInvalidaException")
    void deveLancarExcecaoParaNotaAcimaDoMaximo() {
        assertThrows(AvaliacaoInvalidaException.class, () -> Urgencia.deNota(11));
    }
}
