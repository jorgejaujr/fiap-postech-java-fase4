package br.com.fiap.projetofase4.infrastructure.dynamodb;

import br.com.fiap.projetofase4.domain.model.Avaliacao;
import br.com.fiap.projetofase4.domain.model.Urgencia;
import br.com.fiap.projetofase4.domain.service.FormatadorData;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositório responsável por buscar avaliações no DynamoDB.
 *
 * <p>Utiliza o {@code scanPaginator} do SDK v2 para percorrer todas as páginas
 * de resultado, contornando o limite de 1 MB por chamada do DynamoDB Scan.
 * Com o volume deste projeto, um scan com filtro é suficiente; em um sistema
 * maior, o caminho natural seria um índice secundário global pelo campo {@code dia}.
 *
 * <p>Itens com nota ausente ou fora do intervalo de 0 a 10 são ignorados (e
 * registrados em log), para que um item corrompido não distorça a média nem
 * derrube o relatório.
 */
public class AvaliacaoRepository {

    private static final System.Logger LOG = System.getLogger(AvaliacaoRepository.class.getName());

    private static final String TABELA = "avaliacoes";
    private static final String SEM_DESCRICAO = "(sem descrição)";

    private final DynamoDbClient dynamo;

    /**
     * @param dynamo cliente do DynamoDB usado nas consultas
     */
    public AvaliacaoRepository(DynamoDbClient dynamo) {
        this.dynamo = dynamo;
    }

    /**
    * <p>O filtro compara texto no formato {@code yyyy-MM-dd} (ordem lexicográfica =
     * ordem cronológica) usando um intervalo semiaberto: {@code dia >= inicio} e
     * {@code dia < fim + 1 dia}. Assim, itens antigos que gravaram o carimbo completo
     * no campo {@code dia} (ex.: {@code 2026-11-13T10:00:00-03:00}) também entram no
     * último dia do período, o que um {@code BETWEEN} com {@code fim} exato não faria.
     * O alias {@code #dia} evita conflito com palavras reservadas.
     */
    public List<Avaliacao> buscarPorPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || inicio.isAfter(fim)) {
            throw new IllegalArgumentException("Período inválido: " + inicio + " a " + fim);
        }

        ScanRequest requisicao = ScanRequest.builder()
                .tableName(TABELA)
                .filterExpression("#dia >= :inicio AND #dia < :fimExclusivo")
                .expressionAttributeNames(Map.of("#dia", "dia"))
                .expressionAttributeValues(Map.of(
                        ":inicio", AttributeValue.fromS(inicio.toString()),
                        ":fimExclusivo", AttributeValue.fromS(fim.plusDays(1).toString())))
                .build();

        List<Avaliacao> resultado = new ArrayList<>();
        // scanPaginator percorre automaticamente todas as páginas (cada uma com até 1 MB)
        dynamo.scanPaginator(requisicao).forEach(pagina ->
                pagina.items().forEach(item -> converter(item).ifPresent(resultado::add)));
        return resultado;
    }

    /**
     * Converte um item do DynamoDB em {@link Avaliacao}.
     *
     * <p>Descrição ausente recebe um texto padrão. Se a urgência gravada estiver
     * ausente ou desconhecida, ela é recalculada a partir da nota.
     *
     * @param item atributos do item lido da tabela
     * @return a avaliação, ou vazio se a nota estiver ausente ou fora de 0 a 10
     */
    private Optional<Avaliacao> converter(Map<String, AttributeValue> item) {
        String id = lerTexto(item, "id");
        Integer nota = lerNumero(item, "nota");

        if (nota == null || nota < 0 || nota > 10) {
            LOG.log(System.Logger.Level.WARNING,
                    "Item {0} ignorado no relatório: nota ausente ou fora de 0 a 10", id);
            return Optional.empty();
        }

        String descricao = Objects.requireNonNullElse(lerTexto(item, "descricao"), SEM_DESCRICAO);
        return Optional.of(new Avaliacao(
                id,
                descricao,
                nota,
                lerUrgencia(item, nota),
                lerTexto(item, "dataEnvio"),
                FormatadorData.normalizarDia(lerTexto(item, "dia"))));
    }

    private Urgencia lerUrgencia(Map<String, AttributeValue> item, int nota) {
        String texto = lerTexto(item, "urgencia");
        if (texto != null) {
            try {
                return Urgencia.valueOf(texto);
            } catch (IllegalArgumentException e) {
                // valor desconhecido: recalcula pela regra do domínio
            }
        }
        return Urgencia.deNota(nota);
    }

    private String lerTexto(Map<String, AttributeValue> item, String campo) {
        AttributeValue valor = item.get(campo);
        if (valor == null || valor.s() == null || valor.s().isBlank()) {
            return null;
        }
        return valor.s().strip();
    }

    private Integer lerNumero(Map<String, AttributeValue> item, String campo) {
        AttributeValue valor = item.get(campo);
        if (valor == null || valor.n() == null) {
            return null;
        }
        try {
            return Integer.valueOf(valor.n().strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}