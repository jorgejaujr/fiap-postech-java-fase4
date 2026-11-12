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

public class IngestaoHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final DynamoDbClient dynamo = DynamoDbClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public record AvaliacaoRequest(String descricao, Integer nota) {}

    private APIGatewayV2HTTPResponse resposta(int status, String mensagem) {
        return APIGatewayV2HTTPResponse.builder()
            .withStatusCode(status)
            .withHeaders(Map.of("Content-Type", "text/plain; charset=utf-8"))
            .withBody(mensagem)
            .build();
    }

    @Override 
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String corpo = event.getBody();
        context.getLogger().log("Body: " + corpo);

        if (corpo == null || corpo.isBlank()){
            return resposta(400,"Corpo de requisição vazio");
        }

        AvaliacaoRequest req;
        String id = UUID.randomUUID().toString();
        OffsetDateTime agora = OffsetDateTime.now(ZoneId.of("America/Sao_Paulo"));
        String dataEnvio = agora.toString();
        String dia = agora.toString();
        
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
