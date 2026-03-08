package com.mira.runtime.visitors;

import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.VarDecl;

public interface StmtVisitor<T> {

    public T visitVarDecl(VarDecl varDecl);
    public void visitFuncDecl(FuncDecl funcDecl);
    public T visitReturn(Return ret);
}
