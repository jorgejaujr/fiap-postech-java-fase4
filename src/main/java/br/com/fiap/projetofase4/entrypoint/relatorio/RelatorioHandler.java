package br.com.fiap.projetofase4.entrypoint.relatorio;

import br.com.fiap.projetofase4.domain.model.Avaliacao;
import br.com.fiap.projetofase4.domain.service.MontadorRelatorio;
import br.com.fiap.projetofase4.domain.service.RelatorioService;
import br.com.fiap.projetofase4.infrastructure.dynamodb.AvaliacaoRepository;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Handler Lambda para geração e envio do relatório semanal de avaliações.
 *
 * <p>Acionado pelo EventBridge (cron), lê no DynamoDB as avaliações da última
 * <b>semana fechada</b> (os 7 dias que terminam na véspera da execução; com o
 * agendamento na segunda-feira, de segunda a domingo), monta o relatório e o
 * publica no tópico SNS definido pela variável de ambiente {@code TOPIC_ARN_RELATORIO}.
 * Avaliações do próprio dia da execução ficam para o relatório seguinte.
 *
 * <p>O fuso usado para calcular o período é {@code America/Sao_Paulo}, o mesmo
 * usado pela ingestão para gravar o campo {@code dia}.
 *
 * <p>O tipo do evento é {@code Map<String, Object>} porque o conteúdo do disparo
 * agendado pelo EventBridge não é relevante para o processamento.
 *
 * <p><b>Variáveis de ambiente obrigatórias:</b>
 * <ul>
 *   <li>{@code TOPIC_ARN_RELATORIO} – ARN do tópico SNS do relatório semanal.</li>
 * </ul>
 *
 * <p><b>Permissões IAM necessárias:</b>
 * <ul>
 *   <li>{@code dynamodb:Scan} na tabela {@code avaliacoes};</li>
 *   <li>{@code sns:Publish} no tópico do relatório.</li>
 * </ul>
 *
 * <p><b>Configuração recomendada da Lambda:</b> memória 512 MB, timeout 60 s.
 */
public class RelatorioHandler implements RequestHandler<Map<String, Object>, Void> {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int DIAS = 7;

    private final AvaliacaoRepository repositorio = new AvaliacaoRepository(DynamoDbClient.create());
    private final MontadorRelatorio montador = new MontadorRelatorio(new RelatorioService());
    private final SnsClient sns = SnsClient.create();
    private final String topicArn = System.getenv("TOPIC_ARN_RELATORIO");

    /**
     * Gera o relatório da última semana fechada e o publica no SNS.
     *
     * @param event   evento do agendamento (não utilizado)
     * @param context contexto de execução da Lambda, usado para logging
     * @return {@code null}, conforme exigido pelo tipo de retorno {@code Void}
     * @throws IllegalStateException se {@code TOPIC_ARN_RELATORIO} não estiver configurada
     * @throws SdkException          se a publicação no SNS falhar
     */
    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        if (topicArn == null || topicArn.isBlank()) {
            throw new IllegalStateException("TOPIC_ARN_RELATORIO não configurado");
        }

        LocalDate fim = LocalDate.now(FUSO).minusDays(1);
        LocalDate inicio = fim.minusDays(DIAS - 1);

        context.getLogger().log(
                "Gerando relatório de " + inicio.format(FORMATO_DATA) + " a " + fim.format(FORMATO_DATA));

        List<Avaliacao> avaliacoes = repositorio.buscarPorPeriodo(inicio, fim);
        context.getLogger().log("Total de avaliações encontradas: " + avaliacoes.size());

        // O assunto do SNS aceita somente ASCII (sem acentos), até 100 caracteres
        String assunto = "Relatorio semanal de avaliacoes - "
                + inicio.format(FORMATO_DATA) + " a " + fim.format(FORMATO_DATA);
        String corpo = montador.montarCorpo(avaliacoes, inicio, fim);

        try {
            sns.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject(assunto)
                    .message(corpo)
                    .build());
            context.getLogger().log("Relatório enviado com sucesso.");
        } catch (SdkException e) {
            context.getLogger().log("Falha ao enviar o relatório: " + e.getMessage());
            throw e;
        }
        return null;
    }
}