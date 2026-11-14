package br.com.fiap.projetofase4.domain.model;

/**
 * Representa uma avaliação recebida, com todos os campos necessários
 * para geração do relatório semanal.
 *
 * @param id        identificador único (UUID)
 * @param descricao texto descritivo da avaliação
 * @param nota      nota de 0 a 10
 * @param urgencia  nível de urgência calculado a partir da nota
 * @param dataEnvio data e hora de envio no formato ISO-8601 com offset
 * @param dia       data no formato yyyy-MM-dd, usada para filtro no DynamoDB
 */

public record Avaliacao(
    String id,
    String descricao,
    int nota,
    Urgencia urgencia,
    String dataEnvio,
    String dia
){}
