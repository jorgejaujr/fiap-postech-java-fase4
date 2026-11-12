package br.com.fiap.projetofase4.entrypoint.notificacao;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;


public class NotificacaoHandler implements RequestHandler<DynamodbEvent, Void> {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final SnsClient sns = SnsClient.create();
    private final String topicArn = System.getenv("TOPIC_ARN");
    

    @Override
    public Void handleRequest(DynamodbEvent event, Context context) {
        if(topicArn == null || topicArn.isBlank()){
            throw new IllegalStateException("TOPIC_ARN não configurado");
        }

        for (DynamodbEvent.DynamodbStreamRecord registro : event.getRecords()) {
            Map<String, AttributeValue> novaImagem = registro.getDynamodb().getNewImage();

            String id = lerTexto(novaImagem, "id");
            if (id == null){
                context.getLogger().log("Registro sem id, ignorado");
                continue;
            }
            
            String urgencia = Objects.requireNonNullElse(lerTexto(novaImagem, "urgencia"), "CRITICA");
            
            String descricaoLida = lerTexto(novaImagem, "descricao");
            if (descricaoLida == null){
                context.getLogger().log("Avaliação " + id + " sem descrição, usando valor padrão.");
            }
            String descricao = Objects.requireNonNullElse(descricaoLida, "(sem descrição)");

            String dataEnvio = lerTexto(novaImagem, "dataEnvio");

            String dataEnvioFormatada = "não informada";

            if (dataEnvio == null){
                context.getLogger().log("Avaliação " + id + " sem data informada.");
            } else {
                dataEnvioFormatada = formatarData(dataEnvio);
            }

            String assunto = "[" + urgencia + "] Nova avaliacao recebida";

            String corpo = """
                Uma nova avaliação foi recebida e precisa de atenção.
                
                Descrição: %s
                Urgência: %s
                Data de envio: %s
                ID da avaliação: %s"""
                .formatted(descricao, urgencia, dataEnvioFormatada, id);
            
            PublishRequest pedido = PublishRequest.builder()
                                    .topicArn(topicArn)
                                    .subject(assunto)
                                    .message(corpo)
                                    .build();
            try {
                sns.publish(pedido);
                context.getLogger().log("Notificação enviada para a avaliação "+id);
            } catch (SdkException e){
                context.getLogger().log("Falha ao notificar a avaliação "+id+" : "+e.getMessage());
                throw e;
            }
        }
        return null;
    }

    static String lerTexto(Map<String, AttributeValue> imagem, String campo){
        if (imagem == null ) { return null;}
        
        AttributeValue valor = imagem.get(campo);
        if (valor == null) { return null;}

        String texto = valor.getS();
        if (texto == null || texto.isBlank()) { return null; }

        return texto.strip();
    }
    
    private String formatarData(String texto){
        try{
            return OffsetDateTime.parse(texto).atZoneSameInstant(ZoneId.of("America/Sao_Paulo")).format(FORMATO);
        } catch (DateTimeParseException e) {
            return texto;
        }

    }
    
}
