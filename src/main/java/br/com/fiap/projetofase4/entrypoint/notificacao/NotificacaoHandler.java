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
* Handler Lambda que escuta o stream do DynamoDB e envia, via Amazon SNS, o e-mail de urgência
* para os administradores.
*
* <p>O gatilho da função tem um <b>filtro de evento</b>: só chegam registros {@code INSERT} com
* {@code urgencia = CRITICA}. Avaliações médias e baixas nem invocam esta função. Para cada
* registro recebido, a classe extrai da nova imagem do item os campos {@code id},
* {@code urgencia}, {@code descricao} e {@code dataEnvio}, monta um e-mail estruturado e o
* publica no tópico SNS definido pela variável de ambiente {@code TOPIC_ARN}.
*
* <p><b>Variáveis de ambiente obrigatórias:</b>
* <ul>
*   <li>{@code TOPIC_ARN} – ARN do tópico SNS que receberá as notificações.</li>
* </ul>
*
* <p><b>Permissões IAM necessárias:</b>
* <ul>
*   <li>{@code sns:Publish} no tópico configurado;</li>
*   <li>{@code dynamodb:GetRecords}, {@code dynamodb:GetShardIterator},
*       {@code dynamodb:DescribeStream} e {@code dynamodb:ListStreams} no stream da tabela
*       de origem.</li>
* </ul>
*
* <p><b>Configuração recomendada do gatilho:</b> tamanho do lote 1 (evita reenviar e-mails já
* entregues quando há nova tentativa), no máximo 2 tentativas e idade máxima do registro de
* 1 hora (evita que um registro problemático trave o stream).
*/

public class NotificacaoHandler implements RequestHandler<DynamodbEvent, Void> {

    /** Cliente SNS criado com as credenciais do ambiente de execução da Lambda. */
    private final SnsClient sns = SnsClient.create();

    /** ARN do tópico SNS lido da variável de ambiente {@code TOPIC_ARN}. */
    private final String topicArn = System.getenv("TOPIC_ARN");
    
    /**
    * Processa os registros do stream do DynamoDB e publica uma notificação SNS para cada
    * avaliação recebida.
    *
    * <p>Registros sem {@code id} são registrados em log e ignorados, pois não há como
    * identificar o alerta e repetir o processamento não resolveria. Campos ausentes
    * ({@code urgencia}, {@code descricao}, {@code dataEnvio}) recebem valores padrão, para que
    * o envio nunca seja bloqueado por dados incompletos. A data é exibida no formato
    * {@code dd/MM/yyyy HH:mm}, no fuso de São Paulo.
    *
    * <p>Se a publicação no SNS falhar, o erro é registrado e <b>relançado</b>, para que a
    * falha conte na métrica de erros (que alimenta o alarme) e o Lambda possa tentar de novo.
    *
    * @param event   evento recebido do DynamoDB Streams, com um ou mais registros
    * @param context contexto de execução da Lambda, usado para logging
    * @return {@code null}, conforme exigido pelo tipo de retorno {@code Void}
    * @throws IllegalStateException se a variável de ambiente {@code TOPIC_ARN} não estiver configurada
    * @throws SdkException          se a publicação no SNS falhar
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
