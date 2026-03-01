package com.mira.lexer;

import java.util.ArrayList;
import java.util.List;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.vocabulary.Vocabulary;

public class Tokenizer {

    private int columnOffset = 0;

    public List<Token> tokenize(String readFile) {
        if (readFile.length() > 1 && readFile.charAt(readFile.length() - 1) != '\n') {
            readFile += "\n";
        }

        List<Token> tokens = new ArrayList<>();
        int line = 0;

        String lexed = "";
        for (int k = 0; k < readFile.length(); k++) {
            if (readFile.charAt(k) == '\n') {
                if (!lexed.isEmpty()) {
                    tokens = matchToken(tokens, lexed, line, k);
                }
                lexed = "";
                line++;
                columnOffset = k;
                continue;
            }

            int tokenCount = tokens.size();
            tokens = matchToken(tokens, lexed, line, k);
            if (tokenCount == tokens.size()) {
                lexed += readFile.charAt(k);
            } else {
                lexed = "";
                lexed += readFile.charAt(k);
            }
        }

        if (!lexed.isEmpty()) {
            tokens = matchToken(tokens, lexed, line, columnOffset - lexed.length());
        }

        for (int k = tokens.size() - 1; k >= 0; k--) {
            if (tokens.get(k).getLexeme().equals("") || tokens.get(k).getLexeme().equals(" ")) {
                tokens.remove(k);
            }
        }

        return tokens;
    }

    private List<Token> matchToken(List<Token> tokens, String lexed, int line, int column) {
        if (lexed.isEmpty()) {
            return tokens;
        }

        column = column - columnOffset;

        if (Vocabulary.stringIsKeyword(lexed)) {
            tokens.add(new Token(TokenType.KEYWORD, lexed, line, column - lexed.length()));

        } else if (Vocabulary.stringIsDelimiter(String.valueOf(lexed.charAt(0)))) {
            lexed = lexed.replaceAll(" ", "");
            tokens.add(new Token(TokenType.DELIMITER, lexed.substring(0, 1), line, column - lexed.length()));
            String expression = lexed.substring(1);
            TokenType exprType = Vocabulary.stringIsKeyword(expression) ? TokenType.KEYWORD : TokenType.EXPRESSION;
            tokens.add(new Token(exprType, expression, line, column - lexed.length()));

        } else if (Vocabulary.stringIsDelimiter(String.valueOf(lexed.charAt(lexed.length() - 1)))) {
            lexed = lexed.replaceAll(" ", "");
            String expression = lexed.substring(0, lexed.length() - 1);
            TokenType exprType = Vocabulary.stringIsKeyword(expression) ? TokenType.KEYWORD : TokenType.EXPRESSION;
            tokens.add(new Token(exprType, expression, line, column - lexed.length()));
            tokens.add(new Token(TokenType.DELIMITER, lexed.substring(lexed.length() - 1), line, column - lexed.length()));
        }

        return tokens;
    }
}
