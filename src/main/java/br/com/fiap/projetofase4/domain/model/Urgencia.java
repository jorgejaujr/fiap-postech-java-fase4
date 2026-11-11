package br.com.fiap.projetofase4.domain.model;

import br.com.fiap.projetofase4.domain.exception.AvaliacaoInvalidaException;

public enum Urgencia {
    BAIXA,
    MEDIA,
    CRITICA;

    private static final int NOTA_MINIMA = 0;
    private static final int NOTA_MAXIMA = 10;
    private static final int LIMITE_CRITICA = 3;
    private static final int LIMITE_MEDIA = 6;

    /**
     * Converte uma nota numérica em um nível de urgência.
     *
     * @param nota valor inteiro entre {@code NOTA_MINIMA} e {@code NOTA_MAXIMA} (0 a 10)
     * @return {@link Urgencia#CRITICA} se a nota for menor ou igual a {@code LIMITE_CRITICA},
     *         {@link Urgencia#MEDIA} se a nota for menor ou igual a {@code LIMITE_MEDIA},
     *         ou {@link Urgencia#BAIXA} caso contrário
     * @throws AvaliacaoInvalidaException se a nota estiver fora do intervalo permitido
     */

    public static Urgencia deNota(int nota){

        if ( nota < NOTA_MINIMA || nota > NOTA_MAXIMA ){
            throw new AvaliacaoInvalidaException("nota deve estar entre 0 e 10");
        }

        if (nota<=LIMITE_CRITICA){ return CRITICA;} 
        else if (nota<=LIMITE_MEDIA){ return MEDIA;} 
        else { return BAIXA; }
    }
}
