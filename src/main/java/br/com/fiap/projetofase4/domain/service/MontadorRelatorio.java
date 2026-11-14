package br.com.fiap.projetofase4.domain.service;

import br.com.fiap.projetofase4.domain.model.Avaliacao;
import br.com.fiap.projetofase4.domain.model.Urgencia;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Monta o texto do e-mail do relatório semanal.
 *
 * <p>Conteúdo do relatório, conforme o enunciado do desafio:
 * <ul>
 *   <li>total de avaliações e média das notas;</li>
 *   <li>quantidade de avaliações por dia (todos os dias do período, inclusive com zero);</li>
 *   <li>quantidade de avaliações por urgência (CRITICA, MEDIA e BAIXA, sempre as três);</li>
 *   <li>lista das avaliações, da mais recente para a mais antiga, com urgência,
 *       data de envio e descrição.</li>
 * </ul>
 *
 * <p>Para que o e-mail não fique grande demais, a lista mostra no máximo
 * {@value #MAX_AVALIACOES_LISTADAS} avaliações e cada descrição é cortada em
 * {@value #MAX_CARACTERES_DESCRICAO} caracteres. A média usa vírgula decimal.
 *
 * <p>Classe pura de domínio, sem dependência de infraestrutura AWS.
 */
public class MontadorRelatorio {

    /** Quantidade máxima de avaliações listadas individualmente no e-mail. */
    static final int MAX_AVALIACOES_LISTADAS = 50;

    /** Tamanho máximo, em caracteres, da descrição exibida na lista. */
    static final int MAX_CARACTERES_DESCRICAO = 200;

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<Urgencia> ORDEM_URGENCIA =
            List.of(Urgencia.CRITICA, Urgencia.MEDIA, Urgencia.BAIXA);
    private static final String SEM_DESCRICAO = "(sem descrição)";

    private final RelatorioService servico;

    /**
     * @param servico serviço usado para os cálculos (média, contagens e ordenação)
     */
    public MontadorRelatorio(RelatorioService servico) {
        this.servico = servico;
    }

    /**
     * Monta o corpo do e-mail do relatório.
     *
     * @param avaliacoes avaliações do período (pode ser vazia ou {@code null})
     * @param inicio     primeiro dia do período, inclusive
     * @param fim        último dia do período, inclusive
     * @return texto do relatório, pronto para ser publicado no SNS
     */
    public String montarCorpo(List<Avaliacao> avaliacoes, LocalDate inicio, LocalDate fim) {
        List<Avaliacao> lista = avaliacoes == null ? List.of() : avaliacoes;

        return "Relatório semanal de avaliações\n"
                + "Período: " + inicio.format(FORMATO_DATA) + " a " + fim.format(FORMATO_DATA) + "\n\n"
                + "Total de avaliações: " + lista.size() + "\n"
                + "Média das notas: " + formatarMedia(lista) + "\n\n"
                + "Avaliações por dia:\n" + secaoPorDia(lista, inicio, fim) + "\n\n"
                + "Avaliações por urgência:\n" + secaoPorUrgencia(lista) + "\n\n"
                + "Avaliações da semana (mais recentes primeiro):\n" + secaoLista(lista) + "\n";
    }

    private String formatarMedia(List<Avaliacao> lista) {
        if (lista.isEmpty()) {
            return "-";
        }
        // Locale.ROOT garante o ponto decimal; a troca por vírgula não depende dos dados de idioma da JVM
        return String.format(Locale.ROOT, "%.2f", servico.mediaNotas(lista)).replace('.', ',');
    }

    private String secaoPorDia(List<Avaliacao> lista, LocalDate inicio, LocalDate fim) {
        Map<String, Long> porDia = servico.contagemPorDia(lista);
        StringJoiner linhas = new StringJoiner("\n");
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            linhas.add("  " + dia.format(FORMATO_DATA) + ": " + porDia.getOrDefault(dia.toString(), 0L));
        }
        return linhas.toString();
    }

    private String secaoPorUrgencia(List<Avaliacao> lista) {
        Map<String, Long> porUrgencia = servico.contagemPorUrgencia(lista);
        StringJoiner linhas = new StringJoiner("\n");
        for (Urgencia urgencia : ORDEM_URGENCIA) {
            linhas.add("  " + urgencia.name() + ": " + porUrgencia.getOrDefault(urgencia.name(), 0L));
        }
        return linhas.toString();
    }

    private String secaoLista(List<Avaliacao> lista) {
        if (lista.isEmpty()) {
            return "  (nenhuma avaliação no período)";
        }
        List<Avaliacao> ordenadas = servico.maisRecentesPrimeiro(lista);
        StringJoiner linhas = new StringJoiner("\n");
        ordenadas.stream()
                .limit(MAX_AVALIACOES_LISTADAS)
                .forEach(a -> linhas.add(formatarLinha(a)));
        int naoListadas = ordenadas.size() - MAX_AVALIACOES_LISTADAS;
        if (naoListadas > 0) {
            linhas.add("  ... e mais " + naoListadas + " avaliação(ões) não listada(s)");
        }
        return linhas.toString();
    }

    private String formatarLinha(Avaliacao a) {
        return "  [" + a.urgencia().name() + "] "
                + FormatadorData.paraExibicao(a.dataEnvio())
                + " - " + truncar(a.descricao())
                + " (nota " + a.nota() + ")";
    }

    /** Colapsa quebras de linha e espaços repetidos e corta o texto no limite. */
    private String truncar(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return SEM_DESCRICAO;
        }
        String limpa = descricao.replaceAll("\\s+", " ").strip();
        if (limpa.length() <= MAX_CARACTERES_DESCRICAO) {
            return limpa;
        }
        return limpa.substring(0, MAX_CARACTERES_DESCRICAO - 3).stripTrailing() + "...";
    }
}