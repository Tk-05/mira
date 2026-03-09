package com.mira.runtime.visitors;

import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.VarDecl;

public interface StmtVisitor<T> {

    public T visitVarDecl(VarDecl varDecl);

    public T visitFuncDecl(FuncDecl funcDecl);

    public T visitReturn(Return ret);

    public T visitAssign(Assign assign);
}
