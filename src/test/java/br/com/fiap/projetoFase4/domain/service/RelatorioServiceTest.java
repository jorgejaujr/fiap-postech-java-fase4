package br.com.fiap.projetofase4.domain.service;
import br.com.fiap.projetofase4.domain.model.Avaliacao;
import br.com.fiap.projetofase4.domain.model.Urgencia;
import br.com.fiap.projetofase4.domain.service.RelatorioService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class RelatorioServiceTest {
    private RelatorioService service;
    @BeforeEach
    void setUp() {
        service = new RelatorioService();
    }
    // ------------------------------------------------
    // Lista vazia — sem divisão por zero
    // ------------------------------------------------
    @Test
    @DisplayName("Lista vazia: média deve ser 0.0 sem lançar exceção")
    void mediaNaoDeveLancarExcecaoComListaVazia() {
        assertEquals(0.0, service.mediaNotas(List.of()));
    }
    @Test
    @DisplayName("Lista vazia: contagem por dia deve retornar mapa vazio")
    void contagemPorDiaDeveRetornarMapaVazioComListaVazia() {
        assertTrue(service.contagemPorDia(List.of()).isEmpty());
    }
    @Test
    @DisplayName("Lista vazia: contagem por urgência deve retornar mapa vazio")
    void contagemPorUrgenciaDeveRetornarMapaVazioComListaVazia() {
        assertTrue(service.contagemPorUrgencia(List.of()).isEmpty());
    }
    // ------------------------------------------------
    // Média com valores conhecidos
    // ------------------------------------------------
    @Test
    @DisplayName("Média de notas 2, 5 e 9 deve ser aproximadamente 5.333")
    void deveCalcularMediaCorretamente() {
        List<Avaliacao> avaliacoes = List.of(
                new Avaliacao("1", "desc1", 2, Urgencia.CRITICA, "2026-11-10T10:00:00-03:00", "2026-11-10"),
                new Avaliacao("2", "desc2", 5, Urgencia.MEDIA,   "2026-11-11T10:00:00-03:00", "2026-11-11"),
                new Avaliacao("3", "desc3", 9, Urgencia.BAIXA,   "2026-11-12T10:00:00-03:00", "2026-11-12")
        );
        assertEquals(5.333, service.mediaNotas(avaliacoes), 0.001);
    }
    @Test
    @DisplayName("Média de nota única deve ser igual à própria nota")
    void mediaDeUmaAvaliacaoDeveSerIgualANota() {
        List<Avaliacao> avaliacoes = List.of(
                new Avaliacao("1", "desc", 7, Urgencia.BAIXA, "2026-11-10T10:00:00-03:00", "2026-11-10")
        );
        assertEquals(7.0, service.mediaNotas(avaliacoes), 0.001);
    }
    // ------------------------------------------------
    // Contagem por dia
    // ------------------------------------------------
    @Test
    @DisplayName("Deve contar corretamente avaliações por dia")
    void deveContarAvaliacoesPorDia() {
        List<Avaliacao> avaliacoes = List.of(
                new Avaliacao("1", "d1", 5, Urgencia.MEDIA,   "2026-11-10T10:00:00-03:00", "2026-11-10"),
                new Avaliacao("2", "d2", 3, Urgencia.CRITICA, "2026-11-10T11:00:00-03:00", "2026-11-10"),
                new Avaliacao("3", "d3", 8, Urgencia.BAIXA,   "2026-11-11T09:00:00-03:00", "2026-11-11")
        );
        Map<String, Long> resultado = service.contagemPorDia(avaliacoes);
        assertEquals(2L, resultado.get("2026-11-10"));
        assertEquals(1L, resultado.get("2026-11-11"));
    }
    // ------------------------------------------------
    // Contagem por urgência
    // ------------------------------------------------
    @Test
    @DisplayName("Deve contar corretamente avaliações por urgência")
    void deveContarAvaliacoesPorUrgencia() {
        List<Avaliacao> avaliacoes = List.of(
                new Avaliacao("1", "d1", 2, Urgencia.CRITICA, "2026-11-10T10:00:00-03:00", "2026-11-10"),
                new Avaliacao("2", "d2", 1, Urgencia.CRITICA, "2026-11-10T11:00:00-03:00", "2026-11-10"),
                new Avaliacao("3", "d3", 5, Urgencia.MEDIA,   "2026-11-11T09:00:00-03:00", "2026-11-11"),
                new Avaliacao("4", "d4", 9, Urgencia.BAIXA,   "2026-11-12T09:00:00-03:00", "2026-11-12")
        );
        Map<String, Long> resultado = service.contagemPorUrgencia(avaliacoes);
        assertEquals(2L, resultado.get("CRITICA"));
        assertEquals(1L, resultado.get("MEDIA"));
        assertEquals(1L, resultado.get("BAIXA"));
    }
        // ------------------------------------------------
    // Lista nula
    // ------------------------------------------------
    @Test
    @DisplayName("Lista nula: média 0.0 e mapas vazios")
    void deveTratarListaNula() {
        assertEquals(0.0, service.mediaNotas(null));
        assertTrue(service.contagemPorDia(null).isEmpty());
        assertTrue(service.contagemPorUrgencia(null).isEmpty());
        assertTrue(service.maisRecentesPrimeiro(null).isEmpty());
    }

    // ------------------------------------------------
    // Ordenação da mais recente para a mais antiga
    // ------------------------------------------------
    @Test
    @DisplayName("Ordena da mais recente para a mais antiga, misturando offsets -03:00 e Z")
    void deveOrdenarDaMaisRecenteParaAMaisAntiga() {
        Avaliacao antiga = new Avaliacao("antiga", "a", 5, Urgencia.MEDIA, "2026-11-10T10:00:00-03:00", "2026-11-10");
        Avaliacao nova   = new Avaliacao("nova",   "n", 5, Urgencia.MEDIA, "2026-11-12T10:00:00-03:00", "2026-11-12");
        // 20:00 UTC = 17:00 em Brasília, entre as duas anteriores
        Avaliacao meio   = new Avaliacao("meio",   "m", 5, Urgencia.MEDIA, "2026-11-11T20:00:00Z", "2026-11-11");

        List<Avaliacao> resultado = service.maisRecentesPrimeiro(List.of(antiga, nova, meio));

        assertEquals("nova", resultado.get(0).id());
        assertEquals("meio", resultado.get(1).id());
        assertEquals("antiga", resultado.get(2).id());
    }

    @Test
    @DisplayName("Avaliações com data ausente ou inválida vão para o fim")
    void deveColocarDatasInvalidasNoFim() {
        Avaliacao semData  = new Avaliacao("sem",      "s", 5, Urgencia.MEDIA, null, "2026-11-10");
        Avaliacao invalida = new Avaliacao("invalida", "i", 5, Urgencia.MEDIA, "xx", "2026-11-10");
        Avaliacao valida   = new Avaliacao("valida",   "v", 5, Urgencia.MEDIA, "2026-11-12T10:00:00-03:00", "2026-11-12");

        List<Avaliacao> resultado = service.maisRecentesPrimeiro(List.of(semData, invalida, valida));

        assertEquals("valida", resultado.get(0).id());
        assertEquals("sem", resultado.get(1).id());
        assertEquals("invalida", resultado.get(2).id());
    }
}