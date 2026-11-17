package br.com.fiap.projetofase4.entrypoint.ingestao;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.fiap.projetofase4.domain.exception.AvaliacaoInvalidaException;
import br.com.fiap.projetofase4.domain.model.Urgencia;


import java.time.OffsetDateTime;
import java.time.ZoneId;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;
import java.util.UUID;

/**
 * Handler Lambda da API de ingestão de avaliações ({@code POST /avaliacao}).
 *
 * <p>Recebe a requisição do API Gateway (HTTP API, payload 2.0), valida o corpo, calcula a
 * urgência pela regra do domínio ({@link Urgencia#deNota(int)}) e grava a avaliação na tabela
 * {@code avaliacoes} do DynamoDB. O aviso de avaliações críticas não é responsabilidade desta
 * função: ele acontece de forma assíncrona, pelo stream do DynamoDB (ver {@code NotificacaoHandler}).
 *
 * <p><b>Corpo esperado</b> ({@code application/json}):
 * <pre>{@code
 * {
 *   "descricao": "Aula muito boa, explicação clara",
 *   "nota": 9
 * }
 * }</pre>
 *
 * <p><b>Regras de validação:</b>
 * <ul>
 *   <li>{@code descricao} é obrigatória e não pode ser vazia;</li>
 *   <li>{@code nota} é obrigatória e deve ser um inteiro de 0 a 10.</li>
 * </ul>
 *
 * <p><b>Respostas</b> (corpo em texto simples, UTF-8):
 * <ul>
 *   <li>{@code 201} – avaliação gravada; o corpo traz o identificador e a urgência
 *       (ex.: {@code Avaliação registrada: <id> (CRITICA)});</li>
 *   <li>{@code 400} – corpo vazio, JSON inválido, descrição ausente ou vazia, ou nota ausente
 *       ou fora de 0 a 10;</li>
 *   <li>{@code 500} – falha ao gravar no DynamoDB.</li>
 * </ul>
 *
 * <p>Para preservar a privacidade dos alunos, o texto das avaliações não é escrito nos logs.
 *
 * <p><b>Variáveis de ambiente:</b> nenhuma (a tabela é {@code avaliacoes}).
 *
 * <p><b>Permissões IAM necessárias:</b>
 * <ul>
 *   <li>{@code dynamodb:PutItem} na tabela {@code avaliacoes}.</li>
 * </ul>
 *
 * <p><b>Configuração recomendada da Lambda:</b> memória 512 MB, timeout 15 s.
 */

public class IngestaoHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final DynamoDbClient dynamo = DynamoDbClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
    * Corpo da requisição {@code POST /avaliacao}.
    *
    * @param descricao texto livre do aluno; obrigatório e não pode ser vazio
    * @param nota      nota de 0 a 10; é {@link Integer} (e não {@code int}) para que a
    *                  ausência do campo seja detectada como {@code null}, em vez de virar 0
    */

    public record AvaliacaoRequest(String descricao, Integer nota) {}

    /**
    * Monta uma resposta HTTP em texto simples (UTF-8).
    *
    * @param status   código HTTP da resposta (ex.: 201, 400, 500)
    * @param mensagem texto devolvido no corpo da resposta
    * @return resposta pronta para ser devolvida ao API Gateway
    */

    private APIGatewayV2HTTPResponse resposta(int status, String mensagem) {
        return APIGatewayV2HTTPResponse.builder()
            .withStatusCode(status)
            .withHeaders(Map.of("Content-Type", "text/plain; charset=utf-8"))
            .withBody(mensagem)
            .build();
    }

    /**
    * Processa uma requisição {@code POST /avaliacao}.
    *
    * <p>Etapas, nesta ordem: valida que o corpo não está vazio; converte o JSON; valida a
    * descrição e a nota; calcula a urgência pelo domínio; grava no DynamoDB com {@code id}
    * (UUID), {@code urgencia}, {@code descricao}, {@code nota}, {@code dataEnvio} (ISO-8601
    * com offset de São Paulo) e {@code dia} (data local no formato {@code yyyy-MM-dd}).
    * A primeira etapa que falhar encerra o processamento com a resposta de erro correspondente.
    *
    * @param event   evento do API Gateway (HTTP API); o corpo da requisição vem em {@code getBody()}
    * @param context contexto de execução da Lambda, usado para logging
    * @return resposta HTTP com o status e uma mensagem em texto simples
    *         (ver os códigos na documentação da classe)
    */

    @Override 
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String corpo = event.getBody();

        if (corpo == null || corpo.isBlank()){
            return resposta(400,"Corpo de requisição vazio");
        }

        AvaliacaoRequest req;
        String id = UUID.randomUUID().toString();
        OffsetDateTime agora = OffsetDateTime.now(ZoneId.of("America/Sao_Paulo"));
        String dataEnvio = agora.toString();
        String dia = agora.toLocalDate().toString();
        
        try {
            req = mapper.readValue(corpo, AvaliacaoRequest.class);
        }catch (JsonProcessingException e){
            return resposta(400, "JSON Inválido");
        }

        if (req.descricao() == null || req.descricao().isBlank()){
            return resposta(400,"descricao é obrigatória");
        }

        if (req.nota() == null){
            return resposta(400,"nota deve estar entre 0 e 10");
        }

        Urgencia urgencia;
        try{
            urgencia = Urgencia.deNota(req.nota());
        } catch (AvaliacaoInvalidaException e){
            return resposta(400, e.getMessage());
        }
 
        try{
            
            dynamo.putItem(PutItemRequest.builder().tableName("avaliacoes").item(Map.of(
            "id",AttributeValue.fromS(id),
            "urgencia",AttributeValue.fromS(urgencia.name()),
            "descricao",AttributeValue.fromS(req.descricao()),
            "nota",AttributeValue.fromN(String.valueOf(req.nota())),
            "dataEnvio",AttributeValue.fromS(dataEnvio),
            "dia",AttributeValue.fromS(dia)
            ))
            .build());

        }catch(SdkException e){
            context.getLogger().log("erro ao gravar a avaliacao: " + id +" "+ e);
            return resposta(500,"Erro ao gravar a avaliacao");
        }
        return resposta(201,"Avaliação registrada: "+id + " (" + urgencia + ")");        
    }
    
}
