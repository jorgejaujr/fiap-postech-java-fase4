package br.com.fiap.projetofase4.domain.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Utilitário para interpretar e exibir as datas de envio das avaliações.
 *
 * <p>As datas são gravadas no DynamoDB em ISO-8601 com offset (ex.:
 * {@code 2026-11-10T14:30:00.123-03:00}). Itens antigos podem estar em UTC
 * (terminados em {@code Z}). Esta classe concentra a conversão para o fuso de
 * São Paulo e o formato de exibição, para que a notificação e o relatório
 * apresentem sempre a mesma data.
 *
 * <p>Classe pura de domínio, sem dependência de infraestrutura AWS.
 */
public final class FormatadorData {

    /** Texto exibido quando a data de envio não está disponível. */
    public static final String NAO_INFORMADA = "não informada";

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private FormatadorData() {
    }

    /**
     * Interpreta um texto ISO-8601 com offset.
     *
     * @param texto data em formato ISO-8601 com offset (ex.: {@code 2026-11-10T14:30:00-03:00})
     * @return a data interpretada, ou vazio se o texto for {@code null}, em branco ou inválido
     */
    public static Optional<OffsetDateTime> interpretar(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(texto.strip()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

        /**
     * Normaliza o campo {@code dia} para o formato {@code yyyy-MM-dd}.
     *
     * <p>Versões antigas da ingestão gravaram no campo {@code dia} o carimbo completo
     * (ex.: {@code 2026-11-12T15:46:12.123-03:00}). Este método aproveita só os 10
     * primeiros caracteres quando o texto começa com uma data, para que esses itens
     * sejam contados no dia correto. Qualquer outro texto é devolvido sem alteração.
     *
     * @param texto valor lido do campo {@code dia}
     * @return o dia no formato {@code yyyy-MM-dd}, o texto original (sem espaços nas bordas) ou {@code null} se vazio
     */
    public static String normalizarDia(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpo = texto.strip();
        if (limpo.length() > 10 && limpo.substring(0, 10).matches("\\d{4}-\\d{2}-\\d{2}")) {
            return limpo.substring(0, 10);
        }
        return limpo;
    }

    /**
     * Formata a data no padrão {@code dd/MM/yyyy HH:mm}, no fuso de São Paulo.
     *
     * <p>Nunca lança exceção: se o texto não puder ser interpretado, devolve o
     * próprio texto original, e se for {@code null} ou em branco devolve
     * {@link #NAO_INFORMADA}. Assim, a notificação e o relatório não falham por
     * causa do formato de uma data.
     *
     * @param texto data em formato ISO-8601 com offset
     * @return data formatada (ex.: {@code 10/11/2026 14:30}), o texto original ou {@link #NAO_INFORMADA}
     */
    public static String paraExibicao(String texto) {
        if (texto == null || texto.isBlank()) {
            return NAO_INFORMADA;
        }
        return interpretar(texto)
                .map(data -> data.atZoneSameInstant(FUSO).format(FORMATO))
                .orElse(texto);
    }
}