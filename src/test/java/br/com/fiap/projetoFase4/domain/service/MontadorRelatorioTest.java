package br.com.fiap.projetofase4.domain.service;

import br.com.fiap.projetofase4.domain.model.Avaliacao;
import br.com.fiap.projetofase4.domain.model.Urgencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MontadorRelatorioTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 9);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 15);

    private MontadorRelatorio montador;

    @BeforeEach
    void setUp() {
        montador = new MontadorRelatorio(new RelatorioService());
    }

    private Avaliacao avaliacao(String descricao, int nota, Urgencia urgencia, String dataEnvio, String dia) {
        return new Avaliacao("id", descricao, nota, urgencia, dataEnvio, dia);
    }

    @Test
    @DisplayName("Sem avaliações: mostra total 0, média '-', urgências zeradas e aviso")
    void deveMontarRelatorioSemAvaliacoes() {
        String corpo = montador.montarCorpo(List.of(), INICIO, FIM);

        assertTrue(corpo.contains("Período: 09/11/2026 a 15/11/2026"));
        assertTrue(corpo.contains("Total de avaliações: 0"));
        assertTrue(corpo.contains("Média das notas: -"));
        assertTrue(corpo.contains("  CRITICA: 0"));
        assertTrue(corpo.contains("  MEDIA: 0"));
        assertTrue(corpo.contains("  BAIXA: 0"));
        assertTrue(corpo.contains("(nenhuma avaliação no período)"));
    }

    @Test
    @DisplayName("Lista nula é tratada como lista vazia")
    void deveTratarListaNula() {
        String corpo = montador.montarCorpo(null, INICIO, FIM);
        assertTrue(corpo.contains("Total de avaliações: 0"));
    }

    @Test
    @DisplayName("Média usa vírgula decimal e duas casas")
    void deveFormatarMediaComVirgula() {
        List<Avaliacao> lista = List.of(
                avaliacao("a", 2, Urgencia.CRITICA, "2026-11-10T10:00:00-03:00", "2026-11-10"),
                avaliacao("b", 5, Urgencia.MEDIA, "2026-11-11T10:00:00-03:00", "2026-11-11"),
                avaliacao("c", 9, Urgencia.BAIXA, "2026-11-12T10:00:00-03:00", "2026-11-12"));

        assertTrue(montador.montarCorpo(lista, INICIO, FIM).contains("Média das notas: 5,33"));
    }

    @Test
    @DisplayName("Urgências aparecem na ordem CRITICA, MEDIA, BAIXA")
    void deveListarUrgenciasPorGravidade() {
        String corpo = montador.montarCorpo(List.of(), INICIO, FIM);

        int critica = corpo.indexOf("  CRITICA: ");
        int media = corpo.indexOf("  MEDIA: ");
        int baixa = corpo.indexOf("  BAIXA: ");
        assertTrue(critica < media);
        assertTrue(media < baixa);
    }

    @Test
    @DisplayName("Todos os dias do período aparecem, inclusive os sem avaliação")
    void deveListarTodosOsDiasDoPeriodo() {
        List<Avaliacao> lista = List.of(
                avaliacao("a", 2, Urgencia.CRITICA, "2026-11-10T10:00:00-03:00", "2026-11-10"));

        String corpo = montador.montarCorpo(lista, INICIO, FIM);

        assertTrue(corpo.contains("  10/11/2026: 1"));
        assertTrue(corpo.contains("  09/11/2026: 0"));
        assertTrue(corpo.contains("  15/11/2026: 0"));
    }

    @Test
    @DisplayName("A lista traz urgência, data de envio, descrição e nota")
    void deveListarAvaliacaoComSeusDados() {
        List<Avaliacao> lista = List.of(
                avaliacao("Aula confusa", 2, Urgencia.CRITICA, "2026-11-10T10:00:00-03:00", "2026-11-10"));

        String corpo = montador.montarCorpo(lista, INICIO, FIM);

        assertTrue(corpo.contains("  [CRITICA] 10/11/2026 10:00 - Aula confusa (nota 2)"));
    }

    @Test
    @DisplayName("Descrição longa é cortada em 200 caracteres com reticências")
    void deveCortarDescricaoLonga() {
        List<Avaliacao> lista = List.of(
                avaliacao("a".repeat(300), 8, Urgencia.BAIXA, "2026-11-10T10:00:00-03:00", "2026-11-10"));

        String corpo = montador.montarCorpo(lista, INICIO, FIM);

        assertTrue(corpo.contains("a".repeat(197) + "..."));
        assertFalse(corpo.contains("a".repeat(198)));
    }

    @Test
    @DisplayName("Quebras de linha na descrição viram espaços")
    void deveColapsarQuebrasDeLinha() {
        List<Avaliacao> lista = List.of(
                avaliacao("linha1\nlinha2", 8, Urgencia.BAIXA, "2026-11-10T10:00:00-03:00", "2026-11-10"));

        assertTrue(montador.montarCorpo(lista, INICIO, FIM).contains("linha1 linha2"));
    }

    @Test
    @DisplayName("Lista é limitada a 50 avaliações e informa quantas ficaram de fora")
    void deveLimitarQuantidadeDeAvaliacoesListadas() {
        List<Avaliacao> lista = new ArrayList<>();
        for (int i = 0; i < 55; i++) {
            lista.add(avaliacao("d" + i, 9, Urgencia.BAIXA, "2026-11-10T10:00:00-03:00", "2026-11-10"));
        }

        String corpo = montador.montarCorpo(lista, INICIO, FIM);

        assertEquals(50, corpo.split("\\[BAIXA\\]", -1).length - 1);
        assertTrue(corpo.contains("... e mais 5 avaliação(ões) não listada(s)"));
        assertTrue(corpo.contains("Total de avaliações: 55"));
    }
}