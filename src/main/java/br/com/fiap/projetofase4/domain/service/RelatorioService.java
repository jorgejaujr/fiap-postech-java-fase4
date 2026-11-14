package br.com.fiap.projetofase4.domain.service;

import br.com.fiap.projetofase4.domain.model.Avaliacao;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serviço de cálculo do relatório semanal.
 *
 * <p>Classe pura de domínio, sem dependência de infraestrutura AWS.
 * Todos os métodos aceitam lista vazia ou {@code null} sem lançar exceção.
 */
public class RelatorioService {

    /**
     * Calcula a média aritmética das notas.
     *
     * @param avaliacoes lista de avaliações (pode ser vazia ou {@code null})
     * @return média das notas, ou {@code 0.0} se a lista for vazia
     */
    public double mediaNotas(List<Avaliacao> avaliacoes) {
        if (avaliacoes == null || avaliacoes.isEmpty()) {
            return 0.0;
        }
        return avaliacoes.stream()
                .mapToInt(Avaliacao::nota)
                .average()
                .orElse(0.0);
    }

    /**
     * Conta avaliações agrupadas por dia (campo {@code dia} no formato yyyy-MM-dd).
     * Avaliações sem {@code dia} são ignoradas.
     *
     * @param avaliacoes lista de avaliações (pode ser vazia ou {@code null})
     * @return mapa de dia para quantidade, ou mapa vazio se a lista for vazia
     */
    public Map<String, Long> contagemPorDia(List<Avaliacao> avaliacoes) {
        if (avaliacoes == null || avaliacoes.isEmpty()) {
            return Map.of();
        }
        return avaliacoes.stream()
                .filter(a -> a.dia() != null)
                .collect(Collectors.groupingBy(Avaliacao::dia, Collectors.counting()));
    }

    /**
     * Conta avaliações agrupadas pelo nome da urgência (BAIXA, MEDIA, CRITICA).
     * Só aparecem no mapa as urgências que têm ao menos uma avaliação.
     *
     * @param avaliacoes lista de avaliações (pode ser vazia ou {@code null})
     * @return mapa de urgência para quantidade, ou mapa vazio se a lista for vazia
     */
    public Map<String, Long> contagemPorUrgencia(List<Avaliacao> avaliacoes) {
        if (avaliacoes == null || avaliacoes.isEmpty()) {
            return Map.of();
        }
        return avaliacoes.stream()
                .filter(a -> a.urgencia() != null)
                .collect(Collectors.groupingBy(a -> a.urgencia().name(), Collectors.counting()));
    }

    /**
     * Ordena as avaliações da mais recente para a mais antiga, pela data de envio.
     *
     * <p>A data é interpretada como {@link OffsetDateTime}, e não comparada como
     * texto, porque itens antigos foram gravados em UTC ({@code Z}) e os novos com
     * offset {@code -03:00}. Avaliações com data ausente ou ilegível vão para o fim,
     * mantendo a ordem original entre si. A lista recebida não é alterada.
     *
     * @param avaliacoes lista de avaliações (pode ser vazia ou {@code null})
     * @return nova lista ordenada, ou lista vazia se não houver avaliações
     */
    public List<Avaliacao> maisRecentesPrimeiro(List<Avaliacao> avaliacoes) {
        if (avaliacoes == null || avaliacoes.isEmpty()) {
            return List.of();
        }
        Comparator<Avaliacao> maisRecenteAntes = Comparator.comparing(
                (Avaliacao a) -> FormatadorData.interpretar(a.dataEnvio()).orElse(null),
                Comparator.nullsLast(Comparator.<OffsetDateTime>reverseOrder()));
        return avaliacoes.stream().sorted(maisRecenteAntes).toList();
    }
}