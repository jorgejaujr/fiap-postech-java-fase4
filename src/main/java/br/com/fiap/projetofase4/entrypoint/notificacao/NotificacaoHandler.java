package br.com.fiap.projetofase4.entrypoint.notificacao;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;

import br.com.fiap.projetofase4.domain.service.FormatadorData;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;
import java.util.Objects;

/**
 * Handler Lambda que escuta o stream do DynamoDB e envia notificações via Amazon SNS
 * sempre que um novo item é inserido na tabela de avaliações.
 *
 * Para cada registro do tipo INSERT recebido no stream, a classe extrai os campos
 * relevantes da nova imagem (id, urgencia, descricao e dataEnvio,
 * monta um e-mail estruturado e o publica no tópico SNS definido pela variável de ambiente TOPIC_ARN.
 *
 * Variáveis de ambiente obrigatórias:
 
 *   {@code TOPIC_ARN} – ARN do tópico SNS que receberá as notificações.
 * 
 *
 * Permissões IAM necessárias:
 * 
 *   {@code sns:Publish} no tópico configurado.
 *   {@code dynamodb:GetRecords}, {@code dynamodb:GetShardIterator},
 *   {@code dynamodb:DescribeStream} e {@code dynamodb:ListStreams} na tabela de origem.
 * 
 */
public class NotificacaoHandler implements RequestHandler<DynamodbEvent, Void> {

    /** Cliente SNS criado com as credenciais do ambiente de execução da Lambda. */
    private final SnsClient sns = SnsClient.create();

    /** ARN do tópico SNS lido da variável de ambiente {@code TOPIC_ARN}. */
    private final String topicArn = System.getenv("TOPIC_ARN");
    
    /**
     * Processa os registros do stream do DynamoDB e publica uma notificação SNS
     * para cada avaliação inserida.
     *
     * Registros sem {@code id} são silenciosamente ignorados. Campos ausentes
     * ({@code urgencia}, {@code descricao}, {@code dataEnvio}) recebem valores padrão
     * para que o envio nunca seja bloqueado por dados incompletos.
     *
     * @param event   evento recebido do DynamoDB Streams, contendo um ou mais registros
     * @param context contexto de execução da Lambda, utilizado para logging
     * @return {@code null}, conforme exigido pelo tipo de retorno {@code Void}
     * @throws IllegalStateException se a variável de ambiente {@code TOPIC_ARN} não estiver configurada
     * @throws software.amazon.awssdk.core.exception.SdkException se a publicação no SNS falhar
     */
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
            if (dataEnvio == null){
                context.getLogger().log("Avaliação " + id + " sem data informada.");
            }
            String dataEnvioFormatada = FormatadorData.paraExibicao(dataEnvio);

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

    /**
     * Lê o valor de um campo do tipo String ({@code S}) da imagem do DynamoDB.
     *
     * <p>Retorna {@code null} se a imagem for {@code null}, se o campo não existir,
     * ou se o valor estiver vazio ou composto apenas de espaços.
     *
     * @param imagem mapa de atributos da imagem do item (nova ou antiga)
     * @param campo  nome do atributo a ser lido
     * @return o valor do campo sem espaços nas bordas, ou {@code null} se ausente/vazio
     */
    static String lerTexto(Map<String, AttributeValue> imagem, String campo){
        if (imagem == null ) { return null;}
        
        AttributeValue valor = imagem.get(campo);
        if (valor == null) { return null;}

        String texto = valor.getS();
        if (texto == null || texto.isBlank()) { return null; }

        return texto.strip();
    }
}
