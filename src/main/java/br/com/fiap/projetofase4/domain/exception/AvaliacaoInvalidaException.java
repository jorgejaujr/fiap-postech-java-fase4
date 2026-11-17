package br.com.fiap.projetofase4.domain.exception;

/**
 * Exceção de domínio lançada quando os dados de uma avaliação violam as regras de negócio,
 * por exemplo, uma nota fora do intervalo de 0 a 10.
 *
 * <p>É uma exceção não verificada ({@link RuntimeException}), para não poluir as assinaturas
 * dos métodos do domínio com {@code throws}. Não depende de HTTP nem de AWS: quem a captura
 * decide como reagir (a ingestão, por exemplo, a converte em uma resposta {@code 400}).
 *
 * @see br.com.fiap.projetofase4.domain.model.Urgencia#deNota(int)
 */
public class AvaliacaoInvalidaException extends RuntimeException {

    /** Versão de serialização da classe. */
    static final long serialVersionUID = 1L;

    /**
     * Cria a exceção com a mensagem que descreve a regra violada.
     *
     * @param mensagem descrição do problema, devolvida a quem consome a API
     *                 (ex.: {@code nota deve estar entre 0 e 10})
     */
    public AvaliacaoInvalidaException(String mensagem){
        super(mensagem);
    }

}