package br.com.fiap.projetofase4.domain.exception;

public class AvaliacaoInvalidaException extends RuntimeException {

    static final long serialVersionUID = 1L;

    public AvaliacaoInvalidaException(String mensagem){
        super(mensagem);
    }
    
}
